package org.fossify.messages.models

import androidx.room.ColumnInfo

data class ConversationBadgeRow(
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "phone_number") val phoneNumber: String,
    @ColumnInfo(name = "read") val read: Boolean,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
    @ColumnInfo(name = "archived") val isArchived: Boolean,
    @ColumnInfo(name = "group_id") val groupId: Long,
    @ColumnInfo(name = "ignore_number_pattern") val ignoreNumberPattern: Boolean,
)

fun ConversationBadgeRow.toBadgeConversation(): Conversation {
    return Conversation(
        threadId = threadId,
        snippet = "",
        date = 0,
        read = read,
        title = "",
        photoUri = "",
        isGroupConversation = false,
        phoneNumber = phoneNumber,
        isArchived = isArchived,
        unreadCount = unreadCount,
        groupId = groupId,
        ignoreNumberPattern = ignoreNumberPattern,
    )
}
