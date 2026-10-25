package org.fossify.messages.extensions

import android.app.Activity
import android.content.Context
import android.graphics.Color
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.dialogs.GroupNameDialog
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.models.ConversationGroup

private const val NEW_GROUP_ITEM_ID = -1

fun Context.createConversationGroup(
    name: String,
    backgroundColor: Int = Color.TRANSPARENT,
): Long {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) {
        return 0L
    }
    if (conversationGroupsDB.getAll().any { it.name.equals(trimmed, ignoreCase = true) }) {
        return 0L
    }
    return conversationGroupsDB.insert(
        ConversationGroup(
            name = trimmed,
            position = conversationGroupsDB.nextPosition(),
            backgroundColor = backgroundColor,
        )
    )
}

fun Context.renameConversationGroup(groupId: Long, name: String, backgroundColor: Int) {
    conversationGroupsDB.update(groupId, name.trim(), backgroundColor)
}

fun Context.deleteConversationGroup(groupId: Long) {
    conversationsDB.clearGroup(groupId)
    conversationGroupsDB.delete(groupId)
}

fun Context.moveConversationsToGroup(threadIds: Collection<Long>, groupId: Long) {
    threadIds.forEach { threadId ->
        val existing = conversationsDB.getConversationWithThreadId(threadId)
        val conversation = existing ?: getConversations(threadId).firstOrNull() ?: return@forEach
        if (existing == null) {
            insertOrUpdateConversation(conversation)
        }
        if (conversation.isArchived) {
            updateConversationArchivedStatus(threadId, false)
        }
        conversationsDB.moveToGroup(threadId, groupId)
    }
}

fun Context.removeConversationsFromGroup(threadIds: Collection<Long>) {
    threadIds.forEach { threadId ->
        conversationsDB.removeFromGroup(threadId)
    }
}

fun Activity.promptMoveConversationsToGroup(
    threadIds: Collection<Long>,
    currentGroupId: Long = 0L,
    onMoved: () -> Unit = {},
) {
    if (threadIds.isEmpty()) {
        return
    }
    ensureBackgroundThread {
        val allGroups = try {
            conversationGroupsDB.getAll()
        } catch (_: Exception) {
            emptyList()
        }
        val destinations = allGroups.filter { it.id != currentGroupId }
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                return@runOnUiThread
            }
            val items = ArrayList<RadioItem>(destinations.size + 1)
            destinations.forEach { group ->
                items.add(RadioItem(group.id.toInt(), group.name))
            }
            items.add(RadioItem(NEW_GROUP_ITEM_ID, getString(R.string.new_group)))
            RadioGroupDialog(
                activity = this,
                items = items,
                titleId = R.string.move_to_group,
            ) { selected ->
                val selectedId = selected as Int
                if (selectedId == NEW_GROUP_ITEM_ID) {
                    promptCreateGroupAndMove(allGroups, threadIds, onMoved)
                } else {
                    moveToGroupAndRefresh(selectedId.toLong(), threadIds, onMoved)
                }
            }
        }
    }
}

private fun Activity.promptCreateGroupAndMove(
    existingGroups: List<ConversationGroup>,
    threadIds: Collection<Long>,
    onMoved: () -> Unit,
) {
    GroupNameDialog(
        activity = this,
        titleRes = R.string.new_group,
        isNameTaken = { name ->
            existingGroups.any { it.name.equals(name, ignoreCase = true) }
        },
    ) { name, backgroundColor ->
        ensureBackgroundThread {
            val groupId = createConversationGroup(name, backgroundColor)
            if (groupId == 0L) {
                runOnUiThread { toast(R.string.group_name_exists) }
                return@ensureBackgroundThread
            }
            moveConversationsToGroup(threadIds, groupId)
            runOnUiThread {
                refreshConversations()
                onMoved()
            }
        }
    }
}

private fun Activity.moveToGroupAndRefresh(
    groupId: Long,
    threadIds: Collection<Long>,
    onMoved: () -> Unit,
) {
    ensureBackgroundThread {
        moveConversationsToGroup(threadIds, groupId)
        runOnUiThread {
            refreshConversations()
            onMoved()
        }
    }
}
