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
import org.fossify.messages.helpers.matchingPatternGroup
import org.fossify.messages.helpers.matchesNumberPattern
import org.fossify.messages.helpers.normalizeNumberPatterns
import org.fossify.messages.helpers.numberPatternsOf
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.models.ConversationGroup
import org.fossify.messages.models.ConversationGroupForm

private const val NEW_GROUP_ITEM_ID = -1

data class GroupMessageAlert(
    val show: Boolean,
    val sound: Boolean,
    val vibration: Boolean,
)

private val defaultGroupMessageAlert = GroupMessageAlert(show = true, sound = true, vibration = true)

fun Context.createConversationGroup(form: ConversationGroupForm): Long {
    val trimmed = form.name.trim()
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
            backgroundColor = form.backgroundColor,
            showNotification = form.showNotification,
            notificationSound = form.notificationSound,
            notificationVibration = form.notificationVibration,
            numberPatterns = normalizeNumberPatterns(form.numberPatterns),
        )
    )
}

fun Context.renameConversationGroup(groupId: Long, form: ConversationGroupForm) {
    conversationGroupsDB.update(
        id = groupId,
        name = form.name.trim(),
        backgroundColor = form.backgroundColor,
        showNotification = form.showNotification,
        notificationSound = form.notificationSound,
        notificationVibration = form.notificationVibration,
        numberPatterns = normalizeNumberPatterns(form.numberPatterns),
    )
}

fun Context.groupMessageAlert(threadId: Long, address: String): GroupMessageAlert {
    return try {
        val groups = conversationGroupsDB.getAll()
        val conversation = conversationsDB.getConversationWithThreadId(threadId)
        val explicitGroup = if (conversation != null && conversation.groupId != 0L) {
            groups.find { it.id == conversation.groupId }
        } else {
            null
        }
        val patternBlocked = conversation?.isArchived == true || conversation?.ignoreNumberPattern == true
        val group = explicitGroup ?: if (patternBlocked) {
            null
        } else {
            groups.firstOrNull { candidate ->
                matchesGroupPattern(candidate, address) ||
                    (conversation != null && matchesGroupPattern(candidate, conversation.phoneNumber))
            }
        }
        if (group == null) {
            defaultGroupMessageAlert
        } else {
            GroupMessageAlert(
                show = group.showNotification,
                sound = group.notificationSound,
                vibration = group.notificationVibration,
            )
        }
    } catch (_: Exception) {
        defaultGroupMessageAlert
    }
}

fun Context.conversationTabBackgroundColor(threadId: Long): Int {
    val conversation = conversationsDB.getConversationWithThreadId(threadId) ?: return Color.TRANSPARENT
    val groups = conversationGroupsDB.getAll()
    val group = if (conversation.groupId != 0L) {
        groups.find { it.id == conversation.groupId }
    } else {
        matchingPatternGroup(
            groups = groups,
            phoneNumber = conversation.phoneNumber,
            groupId = conversation.groupId,
            isArchived = conversation.isArchived,
            ignoreNumberPattern = conversation.ignoreNumberPattern,
        )
    }
    return group?.backgroundColor ?: Color.TRANSPARENT
}

private fun matchesGroupPattern(group: ConversationGroup, number: String): Boolean {
    return numberPatternsOf(group.numberPatterns).any { pattern ->
        matchesNumberPattern(number, pattern)
    }
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

fun Context.removeConversationsFromGroup(threadIds: Collection<Long>, fromGroupId: Long = 0L) {
    val sourcePatterns = if (fromGroupId == 0L) {
        emptyList()
    } else {
        val source = conversationGroupsDB.getAll().find { it.id == fromGroupId }
        numberPatternsOf(source?.numberPatterns.orEmpty())
    }
    threadIds.forEach { threadId ->
        val conversation = conversationsDB.getConversationWithThreadId(threadId) ?: return@forEach
        val matchedBySource = sourcePatterns.any { pattern ->
            matchesNumberPattern(conversation.phoneNumber, pattern)
        }
        if (conversation.groupId == 0L || matchedBySource) {
            conversationsDB.removeFromGroupAndIgnorePattern(threadId)
        } else {
            conversationsDB.removeFromGroup(threadId)
        }
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
    ) { form ->
        ensureBackgroundThread {
            val groupId = createConversationGroup(form)
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
