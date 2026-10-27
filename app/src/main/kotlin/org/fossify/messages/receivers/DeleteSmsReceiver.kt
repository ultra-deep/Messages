package org.fossify.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.getUnreadCountsByThread
import org.fossify.messages.extensions.markMessageRead
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.moveMessageToRecycleBin
import org.fossify.messages.extensions.updateLastConversationMessage
import org.fossify.messages.helpers.IS_MMS
import org.fossify.messages.helpers.MESSAGE_ID
import org.fossify.messages.helpers.MESSAGE_IDS
import org.fossify.messages.helpers.MESSAGE_IS_MMS
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.models.Message

class DeleteSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val targets = messageTargets(intent)
        context.notificationManager.cancel(threadId.hashCode())
        if (threadId == 0L || targets.isEmpty()) {
            return
        }

        ensureBackgroundThread {
            moveMessagesToRecycleBin(context, threadId, targets)
            updateConversationAfterTrash(context, threadId, targets)
            val unread = context.getUnreadCountsByThread()?.get(threadId)
            if (unread != null) {
                context.conversationsDB.updateUnreadState(
                    threadId,
                    read = unread == 0,
                    unreadCount = unread,
                )
            }
            refreshMessages()
            refreshConversations()
        }
    }

    private fun moveMessagesToRecycleBin(
        context: Context,
        threadId: Long,
        targets: List<Pair<Long, Boolean>>,
    ) {
        val storedMessages = context.messagesDB.getThreadMessages(threadId)
        val needsProviderLookup = targets.any { (id, isMms) ->
            storedMessages.none { it.id == id && it.isMMS == isMms }
        }
        val providerMessages = if (needsProviderLookup) {
            context.getMessages(threadId, includeScheduledMessages = false)
        } else {
            emptyList()
        }

        targets.forEach { (id, isMms) ->
            val alreadyStored = storedMessages.any { it.id == id && it.isMMS == isMms }
            if (!alreadyStored) {
                providerMessages.find { it.id == id && it.isMMS == isMms }?.let { message ->
                    context.messagesDB.insertOrUpdate(message)
                }
            }
            context.moveMessageToRecycleBin(id)
            context.markMessageRead(id, isMms)
        }
    }

    private fun updateConversationAfterTrash(
        context: Context,
        threadId: Long,
        targets: List<Pair<Long, Boolean>>,
    ) {
        context.updateLastConversationMessage(threadId)
        val conversation = context.conversationsDB.getConversationWithThreadId(threadId) ?: return
        val trashed = targets.toSet()
        val latestRemaining = context.getMessages(threadId, includeScheduledMessages = false)
            .filterNot { (it.id to it.isMMS) in trashed }
            .maxWithOrNull(compareBy<Message> { it.date }.thenBy { it.id })

        val updated = if (latestRemaining == null) {
            conversation.copy(snippet = "")
        } else {
            context.messagesDB.insertOrUpdate(latestRemaining)
            conversation.copy(snippet = latestRemaining.body, date = latestRemaining.date)
        }
        context.conversationsDB.insertOrUpdate(updated)
    }

    private fun messageTargets(intent: Intent): List<Pair<Long, Boolean>> {
        val messageIds = intent.getLongArrayExtra(MESSAGE_IDS)
        val isMmsFlags = intent.getBooleanArrayExtra(MESSAGE_IS_MMS)
        if (messageIds != null && isMmsFlags != null && messageIds.size == isMmsFlags.size) {
            return messageIds.indices
                .map { messageIds[it] to isMmsFlags[it] }
                .filter { (id, _) -> id > 0L }
        }

        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        if (messageId == 0L) {
            return emptyList()
        }
        return listOf(messageId to intent.getBooleanExtra(IS_MMS, false))
    }
}
