package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.fossify.messages.models.BlockedMessage

@Dao
interface BlockedMessagesDao {
    @Insert
    fun insert(message: BlockedMessage)

    @Query("SELECT * FROM blocked_messages ORDER BY date DESC")
    fun getAll(): List<BlockedMessage>

    @Query("SELECT COUNT(*) FROM blocked_messages WHERE read = 0")
    fun getUnreadCount(): Int

    @Query("UPDATE blocked_messages SET read = 1 WHERE id = :id")
    fun markRead(id: Long)

    @Query("UPDATE blocked_messages SET read = 0 WHERE id = :id")
    fun markUnread(id: Long)

    @Query("DELETE FROM blocked_messages WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM blocked_messages")
    fun deleteAll()
}
