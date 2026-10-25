package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.fossify.messages.models.ConversationGroup

@Dao
interface ConversationGroupsDao {
    @Query("SELECT * FROM conversation_groups ORDER BY position ASC, id ASC")
    fun getAll(): List<ConversationGroup>

    @Insert
    fun insert(group: ConversationGroup): Long

    @Query("UPDATE conversation_groups SET name = :name, background_color = :backgroundColor WHERE id = :id")
    fun update(id: Long, name: String, backgroundColor: Int)

    @Query("DELETE FROM conversation_groups WHERE id = :id")
    fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM conversation_groups")
    fun nextPosition(): Int
}
