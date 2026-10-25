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
)
