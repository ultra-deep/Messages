package org.fossify.messages.helpers

import android.content.Context
import android.telephony.PhoneNumberUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.fossify.commons.extensions.addBlockedNumber
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationGroupsDB
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.models.AppDataBackup
import org.fossify.messages.models.AppDataSection
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.ConversationGroup
import org.fossify.messages.models.ExportedGroup
import org.fossify.messages.models.ExportedSettings
import org.fossify.messages.models.ExportedTheme
import java.io.InputStream
import java.io.OutputStream

data class ImportableSection(
    val section: AppDataSection,
    val count: Int? = null,
)

object AppDataTransfer {
    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun collect(context: Context): AppDataBackup {
        val config = context.config
        val conversations = context.conversationsDB.getAll()
        val pinnedIds = config.pinnedConversations
        return AppDataBackup(
            groups = collectGroups(context, conversations),
            blockedNumbers = context.getBlockedNumbers()
                .map { it.number }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            blockedKeywords = config.blockedKeywords.filter { it.isNotBlank() }.sorted(),
            archivedNumbers = phoneNumbers(conversations) { it.isArchived },
            pinnedNumbers = phoneNumbers(conversations) { it.threadId.toString() in pinnedIds },
            blockedMessagesBackgroundColor = config.blockedMessagesBackgroundColor,
            blockedMessagesDarkBackgroundColor = config.blockedMessagesDarkBackgroundColor,
            archivedMessagesBackgroundColor = config.archivedMessagesBackgroundColor,
            archivedMessagesDarkBackgroundColor = config.archivedMessagesDarkBackgroundColor,
            theme = collectTheme(config),
            settings = collectSettings(config),
        )
    }

    fun availableSections(backup: AppDataBackup): List<ImportableSection> {
        return buildList {
            if (backup.groups.isNotEmpty()) {
                add(ImportableSection(AppDataSection.GROUPS, backup.groups.size))
            }
            if (backup.blockedNumbers.isNotEmpty()) {
                add(ImportableSection(AppDataSection.BLOCKED_NUMBERS, backup.blockedNumbers.size))
            }
            if (backup.blockedKeywords.isNotEmpty()) {
                add(ImportableSection(AppDataSection.BLOCKED_KEYWORDS, backup.blockedKeywords.size))
            }
            if (backup.archivedNumbers.isNotEmpty()) {
                add(ImportableSection(AppDataSection.ARCHIVED_NUMBERS, backup.archivedNumbers.size))
            }
            if (backup.pinnedNumbers.isNotEmpty()) {
                add(ImportableSection(AppDataSection.PINNED_CONVERSATIONS, backup.pinnedNumbers.size))
            }
            val hasBlockedBackground = backup.blockedMessagesBackgroundColor != null ||
                backup.blockedMessagesDarkBackgroundColor != null
            if (hasBlockedBackground) {
                add(ImportableSection(AppDataSection.BLOCKED_MESSAGES_BACKGROUND))
            }
            val hasArchivedBackground = backup.archivedMessagesBackgroundColor != null ||
                backup.archivedMessagesDarkBackgroundColor != null
            if (hasArchivedBackground) {
                add(ImportableSection(AppDataSection.ARCHIVED_MESSAGES_BACKGROUND))
            }
            if (backup.theme != null) {
                add(ImportableSection(AppDataSection.THEME_COLORS))
            }
            if (backup.settings != null) {
                add(ImportableSection(AppDataSection.SETTINGS))
            }
        }
    }

    fun apply(context: Context, backup: AppDataBackup, sections: Set<AppDataSection>) {
        AppDataImporter.apply(context, backup, sections)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun read(input: InputStream): AppDataBackup {
        return json.decodeFromStream(input)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun write(backup: AppDataBackup, output: OutputStream) {
        json.encodeToStream(backup, output)
    }

    private fun collectGroups(context: Context, conversations: List<Conversation>): List<ExportedGroup> {
        return context.conversationGroupsDB.getAll().map { group ->
            ExportedGroup(
                name = group.name,
                position = group.position,
                backgroundColor = group.backgroundColor,
                darkBackgroundColor = group.darkBackgroundColor,
                showNotification = group.showNotification,
                notificationSound = group.notificationSound,
                notificationVibration = group.notificationVibration,
                numberPatterns = group.numberPatterns,
                messagePatterns = group.messagePatterns,
                numbers = conversations
                    .filter { it.groupId == group.id && !it.isArchived && it.phoneNumber.isNotBlank() }
                    .map { it.phoneNumber }
                    .distinct()
                    .sorted(),
            )
        }
    }

    private fun collectTheme(config: Config) = ExportedTheme(
        textColor = config.textColor,
        backgroundColor = config.backgroundColor,
        primaryColor = config.primaryColor,
        accentColor = config.accentColor,
        appIconColor = config.appIconColor,
        customTextColor = config.customTextColor,
        customBackgroundColor = config.customBackgroundColor,
        customPrimaryColor = config.customPrimaryColor,
        customAccentColor = config.customAccentColor,
        customAppIconColor = config.customAppIconColor,
    )

    private fun collectSettings(config: Config) = ExportedSettings(
        showCharacterCounter = config.showCharacterCounter,
        useSimpleCharacters = config.useSimpleCharacters,
        sendOnEnter = config.sendOnEnter,
        enableDeliveryReports = config.enableDeliveryReports,
        sendLongMessageMms = config.sendLongMessageMMS,
        sendGroupMessageMms = config.sendGroupMessageMMS,
        sendGroupMessageMmsSet = config.isGroupMessageMmsPreferenceResolved,
        lockScreenVisibility = config.lockScreenVisibilitySetting,
        mmsFileSizeLimit = config.mmsFileSizeLimit,
        useRecycleBin = config.useRecycleBin,
        keepConversationsArchived = config.keepConversationsArchived,
        showBlockedMessagesTab = config.showBlockedMessagesTab,
        showArchivedMessagesTab = config.showArchivedMessagesTab,
        conversationTabOrder = config.conversationTabOrder,
        useRelativeDateTime = config.useRelativeDateTime,
        fontSize = config.fontSize,
        dateFormat = config.dateFormat,
        use24HourFormat = config.use24HourFormat,
        blockUnknownNumbers = config.blockUnknownNumbers,
        blockHiddenNumbers = config.blockHiddenNumbers,
        useEnglish = config.useEnglish,
        wasUseEnglishToggled = config.wasUseEnglishToggled,
    )

    private fun phoneNumbers(
        conversations: List<Conversation>,
        include: (Conversation) -> Boolean,
    ): List<String> {
        return conversations
            .filter { include(it) && it.phoneNumber.isNotBlank() }
            .map { it.phoneNumber }
            .distinct()
            .sorted()
    }
}

private object AppDataImporter {
    fun apply(context: Context, backup: AppDataBackup, sections: Set<AppDataSection>) {
        val config = context.config
        if (AppDataSection.SETTINGS in sections) {
            backup.settings?.let { applySettings(config, it) }
        }
        if (AppDataSection.THEME_COLORS in sections) {
            backup.theme?.let { applyTheme(config, it) }
        }
        if (AppDataSection.BLOCKED_MESSAGES_BACKGROUND in sections) {
            backup.blockedMessagesBackgroundColor?.let { config.blockedMessagesBackgroundColor = it }
            backup.blockedMessagesDarkBackgroundColor?.let { config.blockedMessagesDarkBackgroundColor = it }
        }
        if (AppDataSection.ARCHIVED_MESSAGES_BACKGROUND in sections) {
            backup.archivedMessagesBackgroundColor?.let { config.archivedMessagesBackgroundColor = it }
            backup.archivedMessagesDarkBackgroundColor?.let { config.archivedMessagesDarkBackgroundColor = it }
        }
        if (AppDataSection.BLOCKED_NUMBERS in sections) {
            importBlockedNumbers(context, backup.blockedNumbers)
        }
        if (AppDataSection.BLOCKED_KEYWORDS in sections) {
            importKeywords(config, backup.blockedKeywords)
        }

        val touchesConversations = AppDataSection.GROUPS in sections ||
            AppDataSection.ARCHIVED_NUMBERS in sections ||
            AppDataSection.PINNED_CONVERSATIONS in sections
        if (!touchesConversations) {
            return
        }
        val conversations = context.conversationsDB.getAll()
        if (AppDataSection.GROUPS in sections) {
            importGroups(context, backup.groups, conversations)
        }
        if (AppDataSection.ARCHIVED_NUMBERS in sections) {
            archiveNumbers(context, backup.archivedNumbers, conversations)
        }
        if (AppDataSection.PINNED_CONVERSATIONS in sections) {
            pinNumbers(context, backup.pinnedNumbers, conversations)
        }
    }

    private fun applyTheme(config: Config, theme: ExportedTheme) {
        config.textColor = theme.textColor
        config.backgroundColor = theme.backgroundColor
        config.primaryColor = theme.primaryColor
        config.accentColor = theme.accentColor
        config.appIconColor = theme.appIconColor
        config.customTextColor = theme.customTextColor
        config.customBackgroundColor = theme.customBackgroundColor
        config.customPrimaryColor = theme.customPrimaryColor
        config.customAccentColor = theme.customAccentColor
        config.customAppIconColor = theme.customAppIconColor
    }

    private fun applySettings(config: Config, settings: ExportedSettings) {
        config.showCharacterCounter = settings.showCharacterCounter
        config.useSimpleCharacters = settings.useSimpleCharacters
        config.sendOnEnter = settings.sendOnEnter
        config.enableDeliveryReports = settings.enableDeliveryReports
        config.sendLongMessageMMS = settings.sendLongMessageMms
        if (settings.sendGroupMessageMmsSet) {
            config.sendGroupMessageMMS = settings.sendGroupMessageMms
        }
        config.lockScreenVisibilitySetting = settings.lockScreenVisibility
        config.mmsFileSizeLimit = settings.mmsFileSizeLimit
        config.useRecycleBin = settings.useRecycleBin
        config.keepConversationsArchived = settings.keepConversationsArchived
        config.showBlockedMessagesTab = settings.showBlockedMessagesTab
        config.showArchivedMessagesTab = settings.showArchivedMessagesTab
        config.conversationTabOrder = settings.conversationTabOrder
        config.useRelativeDateTime = settings.useRelativeDateTime
        config.fontSize = settings.fontSize
        config.dateFormat = settings.dateFormat
        config.use24HourFormat = settings.use24HourFormat
        config.blockUnknownNumbers = settings.blockUnknownNumbers
        config.blockHiddenNumbers = settings.blockHiddenNumbers
        config.useEnglish = settings.useEnglish
        config.wasUseEnglishToggled = settings.wasUseEnglishToggled
    }

    private fun importGroups(
        context: Context,
        groups: List<ExportedGroup>,
        conversations: List<Conversation>,
    ) {
        val existingByName = LinkedHashMap<String, ConversationGroup>()
        context.conversationGroupsDB.getAll().forEach { group ->
            existingByName.putIfAbsent(group.name.trim().lowercase(), group)
        }
        groups.forEach { exported ->
            val name = exported.name.trim()
            if (name.isEmpty()) {
                return@forEach
            }
            val numberPatterns = normalizeNumberPatterns(exported.numberPatterns)
            val messagePatterns = normalizeMessagePatterns(exported.messagePatterns)
            val existing = existingByName[name.lowercase()]
            val groupId = if (existing == null) {
                context.conversationGroupsDB.insert(
                    ConversationGroup(
                        name = name,
                        position = exported.position,
                        backgroundColor = exported.backgroundColor,
                        darkBackgroundColor = exported.darkBackgroundColor,
                        showNotification = exported.showNotification,
                        notificationSound = exported.notificationSound,
                        notificationVibration = exported.notificationVibration,
                        numberPatterns = numberPatterns,
                        messagePatterns = messagePatterns,
                    )
                )
            } else {
                context.conversationGroupsDB.update(
                    id = existing.id,
                    name = name,
                    backgroundColor = exported.backgroundColor,
                    darkBackgroundColor = exported.darkBackgroundColor,
                    showNotification = exported.showNotification,
                    notificationSound = exported.notificationSound,
                    notificationVibration = exported.notificationVibration,
                    numberPatterns = numberPatterns,
                    messagePatterns = messagePatterns,
                )
                context.conversationGroupsDB.updatePosition(existing.id, exported.position)
                existing.id
            }
            if (groupId == 0L) {
                return@forEach
            }
            existingByName[name.lowercase()] = ConversationGroup(
                id = groupId,
                name = name,
                position = exported.position,
            )
            assignNumbers(context, conversations, groupId, exported.numbers)
        }
    }

    private fun assignNumbers(
        context: Context,
        conversations: List<Conversation>,
        groupId: Long,
        numbers: List<String>,
    ) {
        numbers.forEach { number ->
            matchingConversations(context, conversations, number).forEach { conversation ->
                context.conversationsDB.moveToGroup(conversation.threadId, groupId)
            }
        }
    }

    private fun archiveNumbers(context: Context, numbers: List<String>, conversations: List<Conversation>) {
        numbers.forEach { number ->
            matchingConversations(context, conversations, number).forEach { conversation ->
                context.updateConversationArchivedStatus(conversation.threadId, true)
            }
        }
    }

    private fun pinNumbers(context: Context, numbers: List<String>, conversations: List<Conversation>) {
        numbers.forEach { number ->
            matchingConversations(context, conversations, number).forEach { conversation ->
                context.config.addPinnedConversationByThreadId(conversation.threadId)
            }
        }
    }

    private fun importBlockedNumbers(context: Context, numbers: List<String>) {
        val seen = context.getBlockedNumbers().mapTo(HashSet()) { it.number }
        numbers.forEach { raw ->
            val number = raw.trim()
            if (number.isEmpty() || !seen.add(number)) {
                return@forEach
            }
            if (!context.addBlockedNumber(number)) {
                seen.remove(number)
            }
        }
    }

    private fun importKeywords(config: Config, keywords: List<String>) {
        val incoming = keywords.map { it.trim() }.filter { it.isNotEmpty() }
        if (incoming.isEmpty()) {
            return
        }
        config.blockedKeywords = config.blockedKeywords + incoming
    }

    @Suppress("DEPRECATION")
    private fun matchingConversations(
        context: Context,
        conversations: List<Conversation>,
        number: String,
    ): List<Conversation> {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        return conversations.filter { conversation ->
            val phoneNumber = conversation.phoneNumber
            phoneNumber.isNotEmpty() && (
                phoneNumber.equals(trimmed, ignoreCase = true) ||
                    PhoneNumberUtils.compare(context, phoneNumber, trimmed)
                )
        }
    }
}
