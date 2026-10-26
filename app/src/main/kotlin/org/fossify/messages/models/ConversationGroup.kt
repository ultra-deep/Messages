package org.fossify.messages.models

import android.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_groups")
data class ConversationGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "position") val position: Int = 0,
    @ColumnInfo(name = "background_color") val backgroundColor: Int = Color.TRANSPARENT,
    @ColumnInfo(name = "show_notification") val showNotification: Boolean = true,
    @ColumnInfo(name = "notification_sound") val notificationSound: Boolean = true,
    @ColumnInfo(name = "notification_vibration") val notificationVibration: Boolean = true,
    @ColumnInfo(name = "number_patterns") val numberPatterns: String = "",
)

data class ConversationGroupForm(
    val name: String,
    val backgroundColor: Int,
    val showNotification: Boolean,
    val notificationSound: Boolean,
    val notificationVibration: Boolean,
    val numberPatterns: String,
)
