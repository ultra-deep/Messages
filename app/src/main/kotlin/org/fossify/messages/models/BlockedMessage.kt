package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_messages")
data class BlockedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Int,
    @ColumnInfo(name = "read") val read: Boolean = false,
)
