package org.fossify.messages.adapters

import android.view.Menu
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.promptMoveConversationsToGroup
import org.fossify.messages.extensions.removeConversationsFromGroup
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.models.Conversation

class GroupConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    private val groupId: () -> Long,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override fun getActionMenuId() = R.menu.cab_group_conversations

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_remove_from_group -> removeFromGroup()
            R.id.cab_move_to_group -> moveToGroup()
            R.id.cab_select_all -> selectAll()
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)
        val question = String.format(
            resources.getString(org.fossify.commons.R.string.deletion_confirmation),
            items,
        )
        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteConversations()
            }
        }
    }

    private fun deleteConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = selectedConversations()
        conversationsToRemove.forEach {
            activity.deleteConversation(it.threadId)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }
        finishAfterChange()
    }

    private fun removeFromGroup() {
        val threadIds = selectedConversations().map { it.threadId }
        ensureBackgroundThread {
            activity.removeConversationsFromGroup(threadIds)
            activity.runOnUiThread {
                refreshConversations()
                activity.toast(R.string.removed_from_group)
                finishActMode()
            }
        }
    }

    private fun moveToGroup() {
        val threadIds = selectedConversations().map { it.threadId }
        activity.promptMoveConversationsToGroup(threadIds, groupId()) {
            finishActMode()
        }
    }

    private fun selectedConversations(): List<Conversation> {
        return currentList.filter { selectedKeys.contains(it.hashCode()) }
    }

    private fun finishAfterChange() {
        activity.runOnUiThread {
            refreshConversations()
            finishActMode()
        }
    }
}
