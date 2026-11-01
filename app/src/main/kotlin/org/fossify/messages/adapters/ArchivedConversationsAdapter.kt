package org.fossify.messages.adapters

import android.view.Menu
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.updateConversationArchivedStatus

class ArchivedConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override val showArchive = false
    override val showUnarchive = true

    override fun getActionMenuId() = R.menu.cab_archived_conversations

    override fun prepareActionMode(menu: Menu) {
        prepareConversationActionMode(menu)
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_unarchive -> unarchiveConversation()
            else -> handleConversationAction(id)
        }
    }

    private fun unarchiveConversation() {
        if (selectedKeys.isEmpty()) {
            return
        }

        ensureBackgroundThread {
            val conversationsToUnarchive = getSelectedItems()
            conversationsToUnarchive.forEach {
                activity.updateConversationArchivedStatus(it.threadId, false)
            }
            removeConversationsFromList(conversationsToUnarchive)
        }
    }
}
