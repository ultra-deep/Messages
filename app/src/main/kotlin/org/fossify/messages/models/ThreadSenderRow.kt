package org.fossify.messages.models

import androidx.room.ColumnInfo

data class ThreadSenderRow(
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "sender_phone_number") val senderPhoneNumber: String,
)
