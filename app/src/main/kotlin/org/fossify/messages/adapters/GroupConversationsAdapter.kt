package org.fossify.messages.adapters

import android.view.Menu
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.removeConversationsFromGroup
import org.fossify.messages.helpers.refreshConversations

class GroupConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    private val groupId: () -> Long,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override val showRemoveFromGroup = true

    override fun currentGroupIdForMove() = groupId()

    override fun getActionMenuId() = R.menu.cab_group_conversations

    override fun prepareActionMode(menu: Menu) {
        prepareConversationActionMode(menu)
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_remove_from_group -> removeFromGroup()
            else -> handleConversationAction(id)
        }
    }

    private fun removeFromGroup() {
        val threadIds = getSelectedItems().map { it.threadId }
        ensureBackgroundThread {
            activity.removeConversationsFromGroup(threadIds)
            activity.runOnUiThread {
                refreshConversations()
                activity.toast(R.string.removed_from_group)
                finishActMode()
            }
        }
    }
}
