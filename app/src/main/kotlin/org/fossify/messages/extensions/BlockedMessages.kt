package org.fossify.messages.extensions

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import com.google.android.mms.pdu_alt.PduHeaders
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getIntValueOr
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.extensions.trimToComparableNumber
import org.fossify.commons.models.BlockedNumber
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.R
import org.fossify.messages.helpers.isBlockedSender
import org.fossify.messages.helpers.refreshBlockedMessages
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.models.BlockedMessage
import org.fossify.messages.models.BlockedMessageEntry
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Message

private const val SECONDS_DATE_LIMIT = 10_000_000_000L
private const val MAX_UNREAD_BADGE_COUNT = 99
private const val BLOCKED_THREAD_ID_MASK = 0x7fffffffL
private const val BLOCKED_MMS_MESSAGE_ID_OFFSET = 281474976710656L
private const val MILLIS_IN_SECOND = 1000L
private const val MMS_ADDRESS_URI = "content://mms/addr"
private const val MMS_PART_URI = "content://mms/part"
private const val MMS_INSERT_ADDRESS_TOKEN = "insert-address-token"
private const val MMS_TEXT_PLAIN = "text/plain"

private val blockedCacheLock = Any()
private var cachedBlockedEntries: ArrayList<BlockedMessageEntry>? = null
private var cachedBlockedConversations: ArrayList<Conversation>? = null
private var cachedUnreadBlockedCount: Int? = null

fun peekBlockedConversations(): ArrayList<Conversation>? {
    synchronized(blockedCacheLock) {
        return cachedBlockedConversations?.let { ArrayList(it) }
    }
}

fun peekUnreadBlockedCount(): Int? {
    synchronized(blockedCacheLock) {
        return cachedUnreadBlockedCount
    }
}

fun noteBlockedUnreadChange(delta: Int) {
    if (delta == 0) {
        return
    }
    synchronized(blockedCacheLock) {
        val current = cachedUnreadBlockedCount
        cachedUnreadBlockedCount = when {
            current == null && delta > 0 -> delta
            current == null -> null
            else -> (current + delta).coerceAtLeast(0)
        }
    }
}

fun invalidateBlockedConversationCache() {
    synchronized(blockedCacheLock) {
        cachedBlockedEntries = null
        cachedBlockedConversations = null
    }
}

private fun storeUnreadBlockedCount(count: Int) {
    synchronized(blockedCacheLock) {
        cachedUnreadBlockedCount = count
    }
}

fun unreadBlockedBadgeText(count: Int): String {
    return if (count > MAX_UNREAD_BADGE_COUNT) "$MAX_UNREAD_BADGE_COUNT+" else count.toString()
}

fun Context.saveBlockedMessage(
    address: String,
    body: String,
    dateMillis: Long,
    subscriptionId: Int,
) {
    try {
        blockedMessagesDB.insert(
            BlockedMessage(
                address = address,
                body = body,
                date = dateMillis,
                subscriptionId = subscriptionId,
            )
        )
        noteBlockedUnreadChange(1)
        invalidateBlockedConversationCache()
        refreshBlockedMessages()
    } catch (_: Exception) {
    }
}

fun Context.getUnreadBlockedMessageCount(): Int {
    return getBlockedConversations().sumOf { it.unreadCount }
}

fun Context.markBlockedMessagesRead(entries: List<BlockedMessageEntry>) {
    val unread = entries.filter { !it.read }
    if (unread.isEmpty()) {
        return
    }

    unread.forEach { entry ->
        try {
            if (entry.localId != 0L) {
                blockedMessagesDB.markRead(entry.localId)
            }
            if (entry.smsId != 0L) {
                markMessageRead(entry.smsId, isMMS = false)
            }
            if (entry.mmsId != 0L) {
                markMessageRead(entry.mmsId, isMMS = true)
            }
        } catch (_: Exception) {
        }
    }
    noteBlockedUnreadChange(-unread.size)
    invalidateBlockedConversationCache()
    refreshBlockedMessages()
}

fun Context.markBlockedMessagesUnread(entries: List<BlockedMessageEntry>) {
    val readEntries = entries.filter { it.read }
    if (readEntries.isEmpty()) {
        return
    }

    readEntries.forEach { entry ->
        try {
            if (entry.localId != 0L) {
                blockedMessagesDB.markUnread(entry.localId)
            }
            if (entry.smsId != 0L) {
                markProviderMessageUnread(entry.smsId, isMMS = false)
            }
            if (entry.mmsId != 0L) {
                markProviderMessageUnread(entry.mmsId, isMMS = true)
            }
        } catch (_: Exception) {
        }
    }
    noteBlockedUnreadChange(readEntries.size)
    invalidateBlockedConversationCache()
    refreshBlockedMessages()
}

fun Context.countUnreadBlockedMessages(): Int {
    val count = countLocalUnreadBlockedMessages() + countUnreadProviderBlockedMessages()
    storeUnreadBlockedCount(count)
    return count
}

fun Context.getBlockedMessageEntries(): ArrayList<BlockedMessageEntry> {
    synchronized(blockedCacheLock) {
        cachedBlockedEntries?.let { return ArrayList(it) }
    }
    reloadBlockedConversations()
    synchronized(blockedCacheLock) {
        return ArrayList(cachedBlockedEntries.orEmpty())
    }
}

fun Context.getBlockedConversations(): ArrayList<Conversation> {
    synchronized(blockedCacheLock) {
        cachedBlockedConversations?.let { return ArrayList(it) }
    }
    return reloadBlockedConversations()
}

fun Context.reloadBlockedConversations(): ArrayList<Conversation> {
    val entries = queryBlockedMessageEntries()
    val conversations = toBlockedConversations(entries)
    synchronized(blockedCacheLock) {
        cachedBlockedEntries = entries
        cachedBlockedConversations = conversations
        cachedUnreadBlockedCount = entries.count { !it.read }
    }
    return ArrayList(conversations)
}

fun Context.getBlockedConversationMessages(address: String): List<Message> {
    val key = address.conversationKey()
    val threadId = stableBlockedThreadId(key)
    return getBlockedMessageEntries()
        .filter { it.conversationKey() == key }
        .map { messageFromBlockedEntry(it, threadId) }
        .toSortedMessages()
}

fun Context.markBlockedConversationRead(address: String) {
    markBlockedMessagesRead(entriesForAddress(address))
}

fun Context.markBlockedConversationUnread(address: String) {
    markBlockedMessagesUnread(entriesForAddress(address))
}

fun Context.markBlockedConversationsRead(addresses: List<String>) {
    markBlockedMessagesRead(entriesForAddresses(addresses))
}

fun Context.markBlockedConversationsUnread(addresses: List<String>) {
    markBlockedMessagesUnread(entriesForAddresses(addresses))
}

fun Context.deleteBlockedConversations(addresses: List<String>) {
    deleteBlockedMessages(entriesForAddresses(addresses))
}

fun Message.toBlockedMessageEntry(): BlockedMessageEntry {
    val localId = if (id < 0L) -id else 0L
    val mmsId = if (id >= BLOCKED_MMS_MESSAGE_ID_OFFSET) id - BLOCKED_MMS_MESSAGE_ID_OFFSET else 0L
    val smsId = if (id > 0L && mmsId == 0L) id else 0L
    return BlockedMessageEntry(
        address = senderPhoneNumber,
        senderName = senderName,
        body = body,
        dateMillis = millis(),
        subscriptionId = subscriptionId,
        localId = localId,
        smsId = smsId,
        mmsId = mmsId,
        read = read,
    )
}

fun Context.deleteBlockedMessages(entries: List<BlockedMessageEntry>) {
    var deletedProviderMessage = false
    entries.forEach { entry ->
        if (entry.localId != 0L) {
            blockedMessagesDB.delete(entry.localId)
        }
        if (entry.smsId != 0L) {
            deleteMessage(entry.smsId, isMMS = false)
            deletedProviderMessage = true
        }
        if (entry.mmsId != 0L) {
            deleteMessage(entry.mmsId, isMMS = true)
            deletedProviderMessage = true
        }
    }
    if (deletedProviderMessage) {
        refreshConversations()
    }
    noteBlockedUnreadChange(-entries.count { !it.read })
    invalidateBlockedConversationCache()
    refreshBlockedMessages()
}

fun Context.moveBlockedMessageToInbox(entry: BlockedMessageEntry) {
    if (!entry.isStoredLocally() || entry.address.isBlank()) {
        return
    }

    val threadId = getThreadId(entry.address)
    val messageId = insertNewSMS(
        address = entry.address,
        subject = "",
        body = entry.body,
        date = entry.dateMillis,
        read = if (entry.read) 1 else 0,
        threadId = threadId,
        type = Sms.MESSAGE_TYPE_INBOX,
        subscriptionId = entry.subscriptionId,
    )
    if (messageId == 0L) {
        return
    }

    blockedMessagesDB.delete(entry.localId)
    if (!entry.read) {
        noteBlockedUnreadChange(-1)
    }
    getConversations(threadId).firstOrNull()?.let { conversation ->
        insertOrUpdateConversation(conversation)
    }
    refreshConversations()
    invalidateBlockedConversationCache()
    refreshBlockedMessages()
}

private fun Context.queryBlockedMessageEntries(): ArrayList<BlockedMessageEntry> {
    val entries = ArrayList<BlockedMessageEntry>()
    entries.addAll(loadLocalBlockedMessages())
    entries.addAll(loadProviderBlockedMessages())
    val withText = withMmsText(entries)
    withText.sortByDescending { it.dateMillis }
    return withText
}

private fun Context.toBlockedConversations(entries: List<BlockedMessageEntry>): ArrayList<Conversation> {
    val conversations = entries.groupBy { it.conversationKey() }
        .map { (_, groupedEntries) -> toBlockedConversation(groupedEntries) }
        .sortedByDescending { it.date }
    return ArrayList(conversations)
}

private fun Context.loadLocalBlockedMessages(): List<BlockedMessageEntry> {
    return try {
        blockedMessagesDB.getAll().map { message ->
            BlockedMessageEntry(
                address = message.address,
                senderName = message.address,
                body = message.body,
                dateMillis = message.date,
                subscriptionId = message.subscriptionId,
                localId = message.id,
                read = message.read,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun Context.loadProviderBlockedMessages(): List<BlockedMessageEntry> {
    val blockedNumbers = getBlockedNumbers()
    if (blockedNumbers.isEmpty()) {
        return emptyList()
    }

    val entries = ArrayList<BlockedMessageEntry>()
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms._ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.READ),
        selection = "${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}",
        selectionArgs = null,
        sortOrder = "${Sms.DATE} DESC",
        showErrors = false,
    ) { cursor ->
        val address = cursor.getStringValue(Sms.ADDRESS).orEmpty()
        if (address.isEmpty() || !isBlockedSender(address, blockedNumbers)) {
            return@queryCursor
        }
        entries.add(
            BlockedMessageEntry(
                address = address,
                senderName = address,
                body = cursor.getStringValue(Sms.BODY).orEmpty(),
                dateMillis = cursor.getLongValue(Sms.DATE),
                smsId = cursor.getLongValue(Sms._ID),
                read = cursor.getIntValue(Sms.READ) == 1,
            )
        )
    }

    val mmsSenders = loadMmsSenders()
    queryCursor(
        uri = Mms.CONTENT_URI,
        projection = arrayOf(Mms._ID, Mms.DATE, Mms.SUBSCRIPTION_ID, Mms.READ),
        selection = "${Mms.MESSAGE_BOX} = ${Mms.MESSAGE_BOX_INBOX}",
        selectionArgs = null,
        sortOrder = "${Mms.DATE} DESC",
        showErrors = false,
    ) { cursor ->
        val messageId = cursor.getLongValue(Mms._ID)
        val address = mmsSenders[messageId] ?: if (mmsSenders.isEmpty()) getMMSSender(messageId) else ""
        if (address.isEmpty() || !isBlockedSender(address, blockedNumbers)) {
            return@queryCursor
        }
        entries.add(
            BlockedMessageEntry(
                address = address,
                senderName = address,
                body = "",
                dateMillis = cursor.getLongValue(Mms.DATE).toMillis(),
                subscriptionId = cursor.getIntValueOr(Mms.SUBSCRIPTION_ID, defaultValue = 0),
                mmsId = messageId,
                read = cursor.getIntValue(Mms.READ) == 1,
            )
        )
    }
    return entries
}

private fun Context.countLocalUnreadBlockedMessages(): Int {
    return try {
        blockedMessagesDB.getUnreadCount()
    } catch (_: Exception) {
        0
    }
}

private fun Context.countUnreadProviderBlockedMessages(): Int {
    val blockedNumbers = try {
        getBlockedNumbers()
    } catch (_: Exception) {
        return 0
    }
    if (blockedNumbers.isEmpty()) {
        return 0
    }
    return countUnreadBlockedSms(blockedNumbers) + countUnreadBlockedMms(blockedNumbers)
}

private fun Context.countUnreadBlockedSms(blockedNumbers: ArrayList<BlockedNumber>): Int {
    var count = 0
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms.ADDRESS),
        selection = "${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX} AND ${Sms.READ} = 0",
        selectionArgs = null,
        sortOrder = null,
        showErrors = false,
    ) { cursor ->
        val address = cursor.getStringValue(Sms.ADDRESS).orEmpty()
        if (address.isNotEmpty() && isBlockedSender(address, blockedNumbers)) {
            count++
        }
    }
    return count
}

private fun Context.countUnreadBlockedMms(blockedNumbers: ArrayList<BlockedNumber>): Int {
    val unreadIds = ArrayList<Long>()
    queryCursor(
        uri = Mms.CONTENT_URI,
        projection = arrayOf(Mms._ID),
        selection = "${Mms.MESSAGE_BOX} = ${Mms.MESSAGE_BOX_INBOX} AND ${Mms.READ} = 0",
        selectionArgs = null,
        sortOrder = null,
        showErrors = false,
    ) { cursor ->
        unreadIds.add(cursor.getLongValue(Mms._ID))
    }
    if (unreadIds.isEmpty()) {
        return 0
    }
    val senders = loadMmsSenders(unreadIds)
    var count = 0
    unreadIds.forEach { messageId ->
        val address = senders[messageId] ?: getMMSSender(messageId)
        if (address.isNotEmpty() && isBlockedSender(address, blockedNumbers)) {
            count++
        }
    }
    return count
}

private fun Context.loadMmsSenders(messageIds: Collection<Long>? = null): Map<Long, String> {
    if (messageIds != null && messageIds.isEmpty()) {
        return emptyMap()
    }
    val senders = HashMap<Long, String>()
    val selection = if (messageIds == null) {
        "${Mms.Addr.TYPE} = ?"
    } else {
        "${Mms.Addr.TYPE} = ? AND ${Mms.Addr.MSG_ID} IN (${messageIds.joinToString(",")})"
    }
    try {
        queryCursor(
            uri = Uri.parse(MMS_ADDRESS_URI),
            projection = arrayOf(Mms.Addr.MSG_ID, Mms.Addr.ADDRESS),
            selection = selection,
            selectionArgs = arrayOf(PduHeaders.FROM.toString()),
            sortOrder = null,
            showErrors = false,
        ) { cursor ->
            val address = cursor.getStringValue(Mms.Addr.ADDRESS).orEmpty()
            if (address.isNotEmpty() && !address.equals(MMS_INSERT_ADDRESS_TOKEN, ignoreCase = true)) {
                senders[cursor.getLongValue(Mms.Addr.MSG_ID)] = address
            }
        }
    } catch (_: Exception) {
        return emptyMap()
    }
    return senders
}

private fun Context.withMmsText(entries: List<BlockedMessageEntry>): ArrayList<BlockedMessageEntry> {
    val mmsIds = entries.map { it.mmsId }.filter { it != 0L }
    if (mmsIds.isEmpty()) {
        return ArrayList(entries)
    }
    val texts = loadMmsPlainText(mmsIds)
    val attachmentLabel = getString(R.string.attachment)
    return ArrayList(
        entries.map { entry ->
            if (entry.mmsId == 0L) {
                entry
            } else {
                entry.copy(body = texts[entry.mmsId]?.ifBlank { attachmentLabel } ?: attachmentLabel)
            }
        }
    )
}

private fun Context.loadMmsPlainText(messageIds: Collection<Long>): Map<Long, String> {
    if (messageIds.isEmpty()) {
        return emptyMap()
    }
    val texts = HashMap<Long, String>()
    val ids = messageIds.joinToString(",")
    val uri = if (isQPlus()) Mms.Part.CONTENT_URI else Uri.parse(MMS_PART_URI)
    try {
        queryCursor(
            uri = uri,
            projection = arrayOf(Mms.Part.MSG_ID, Mms.Part.TEXT),
            selection = "${Mms.Part.MSG_ID} IN ($ids) AND ${Mms.Part.CONTENT_TYPE} = ?",
            selectionArgs = arrayOf(MMS_TEXT_PLAIN),
            sortOrder = null,
            showErrors = false,
        ) { cursor ->
            val text = cursor.getStringValue(Mms.Part.TEXT).orEmpty()
            if (text.isNotBlank()) {
                texts[cursor.getLongValue(Mms.Part.MSG_ID)] = text
            }
        }
    } catch (_: Exception) {
    }
    return texts
}

private fun Long.toMillis(): Long {
    return if (this >= SECONDS_DATE_LIMIT) this else this * MILLIS_IN_SECOND
}

private fun Context.entriesForAddress(address: String): List<BlockedMessageEntry> {
    val key = address.conversationKey()
    return getBlockedMessageEntries().filter { it.conversationKey() == key }
}

private fun Context.entriesForAddresses(addresses: List<String>): List<BlockedMessageEntry> {
    val keys = addresses.map { it.conversationKey() }.toSet()
    return getBlockedMessageEntries().filter { it.conversationKey() in keys }
}

private fun Context.toBlockedConversation(entries: List<BlockedMessageEntry>): Conversation {
    val latest = entries.maxBy { it.dateMillis }
    val unreadCount = entries.count { !it.read }
    val namePhoto = getNameAndPhotoFromPhoneNumber(latest.address)
    return Conversation(
        threadId = stableBlockedThreadId(latest.conversationKey()),
        snippet = latest.body,
        date = latest.dateMillis.toConversationDate(),
        read = unreadCount == 0,
        title = namePhoto.name.ifBlank { latest.address },
        photoUri = namePhoto.photoUri.orEmpty(),
        isGroupConversation = false,
        phoneNumber = latest.address,
        unreadCount = unreadCount,
    )
}

private fun Context.messageFromBlockedEntry(entry: BlockedMessageEntry, threadId: Long): Message {
    val localId = entry.localId
    val mmsId = entry.mmsId
    val smsId = entry.smsId
    val address = entry.address
    val body = entry.body
    val dateMillis = entry.dateMillis
    val read = entry.read
    val subscriptionId = entry.subscriptionId
    val messageId = when {
        localId != 0L -> -localId
        mmsId != 0L -> BLOCKED_MMS_MESSAGE_ID_OFFSET + mmsId
        else -> smsId
    }
    val attachment = if (mmsId != 0L && localId == 0L) {
        try {
            getMmsAttachment(mmsId)
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }
    val namePhoto = getNameAndPhotoFromPhoneNumber(address)
    val participant = SimpleContact(
        rawId = address.hashCode(),
        contactId = address.hashCode(),
        name = entry.title(),
        photoUri = namePhoto.photoUri.orEmpty(),
        phoneNumbers = arrayListOf(PhoneNumber(address, 0, "", address)),
        birthdays = ArrayList(),
        anniversaries = ArrayList(),
    )
    return Message(
        id = messageId,
        body = body,
        type = Sms.MESSAGE_TYPE_INBOX,
        status = Sms.STATUS_NONE,
        participants = arrayListOf(participant),
        date = dateMillis.toConversationDate(),
        read = read,
        threadId = threadId,
        isMMS = attachment?.attachments?.isNotEmpty() == true,
        attachment = attachment,
        senderPhoneNumber = address,
        senderName = entry.title(),
        senderPhotoUri = namePhoto.photoUri.orEmpty(),
        subscriptionId = subscriptionId,
    )
}

private fun Context.markProviderMessageUnread(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val values = ContentValues().apply {
        put(Sms.READ, 0)
        put(Sms.SEEN, 0)
    }
    contentResolver.update(uri, values, "${Sms._ID} = ?", arrayOf(id.toString()))
}

private fun BlockedMessageEntry.conversationKey(): String = address.conversationKey()

private fun String.conversationKey(): String = trimToComparableNumber().ifEmpty { this }

private fun stableBlockedThreadId(addressKey: String): Long {
    val hash = addressKey.hashCode().toLong() and BLOCKED_THREAD_ID_MASK
    return -(hash + 1L)
}

private fun Long.toConversationDate(): Int {
    return (this / MILLIS_IN_SECOND).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
