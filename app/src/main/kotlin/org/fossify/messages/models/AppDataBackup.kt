package org.fossify.messages.models

import kotlinx.serialization.Serializable
import org.fossify.commons.helpers.FONT_SIZE_MEDIUM
import org.fossify.messages.helpers.FILE_SIZE_200_KB
import org.fossify.messages.helpers.LOCK_SCREEN_SENDER_MESSAGE

@Serializable
data class AppDataBackup(
    val version: Int = 1,
    val type: String = "messages-settings",
    val groups: List<ExportedGroup> = emptyList(),
    val blockedNumbers: List<String> = emptyList(),
    val blockedKeywords: List<String> = emptyList(),
    val archivedNumbers: List<String> = emptyList(),
    val pinnedNumbers: List<String> = emptyList(),
    val blockedMessagesBackgroundColor: Int? = null,
    val blockedMessagesDarkBackgroundColor: Int? = null,
    val archivedMessagesBackgroundColor: Int? = null,
    val archivedMessagesDarkBackgroundColor: Int? = null,
    val theme: ExportedTheme? = null,
    val settings: ExportedSettings? = null,
)

@Serializable
data class ExportedGroup(
    val name: String,
    val position: Int = 0,
    val backgroundColor: Int = 0,
    val darkBackgroundColor: Int = 0,
    val showNotification: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val numberPatterns: String = "",
    val messagePatterns: String = "",
    val numbers: List<String> = emptyList(),
)

@Serializable
data class ExportedTheme(
    val textColor: Int = 0,
    val backgroundColor: Int = 0,
    val primaryColor: Int = 0,
    val accentColor: Int = 0,
    val appIconColor: Int = 0,
    val customTextColor: Int = 0,
    val customBackgroundColor: Int = 0,
    val customPrimaryColor: Int = 0,
    val customAccentColor: Int = 0,
    val customAppIconColor: Int = 0,
)

@Serializable
data class ExportedSettings(
    val showCharacterCounter: Boolean = false,
    val useSimpleCharacters: Boolean = false,
    val sendOnEnter: Boolean = false,
    val enableDeliveryReports: Boolean = false,
    val sendLongMessageMms: Boolean = false,
    val sendGroupMessageMms: Boolean = false,
    val sendGroupMessageMmsSet: Boolean = false,
    val lockScreenVisibility: Int = LOCK_SCREEN_SENDER_MESSAGE,
    val mmsFileSizeLimit: Long = FILE_SIZE_200_KB,
    val useRecycleBin: Boolean = false,
    val keepConversationsArchived: Boolean = false,
    val showBlockedMessagesTab: Boolean = true,
    val showArchivedMessagesTab: Boolean = true,
    val conversationTabOrder: String = "",
    val useRelativeDateTime: Boolean = false,
    val fontSize: Int = FONT_SIZE_MEDIUM,
    val dateFormat: String = "",
    val use24HourFormat: Boolean = false,
    val blockUnknownNumbers: Boolean = false,
    val blockHiddenNumbers: Boolean = false,
    val useEnglish: Boolean = false,
    val wasUseEnglishToggled: Boolean = false,
)

enum class AppDataSection {
    GROUPS,
    BLOCKED_NUMBERS,
    BLOCKED_KEYWORDS,
    ARCHIVED_NUMBERS,
    PINNED_CONVERSATIONS,
    BLOCKED_MESSAGES_BACKGROUND,
    ARCHIVED_MESSAGES_BACKGROUND,
    THEME_COLORS,
    SETTINGS,
}
