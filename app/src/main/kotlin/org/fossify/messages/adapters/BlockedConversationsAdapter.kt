package org.fossify.messages.adapters

import android.view.Menu
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.deleteBlockedConversations
import org.fossify.messages.extensions.markBlockedConversationsRead
import org.fossify.messages.extensions.markBlockedConversationsUnread

class BlockedConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    private val refreshConversations: () -> Unit,
    itemClick: (Any) -> Unit,
) : BaseConversationsAdapter(activity, recyclerView, refreshConversations, itemClick) {
    override fun getActionMenuId() = R.menu.cab_blocked_conversations

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_select_all -> selectAll()
            R.id.cab_mark_as_read -> markSelected(read = true)
            R.id.cab_mark_as_unread -> markSelected(read = false)
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    private fun askConfirmDelete() {
        val addresses = getSelectedItems().map { it.phoneNumber }
        ConfirmationDialog(activity, activity.getString(R.string.delete_blocked_messages_confirmation)) {
            ensureBackgroundThread {
                activity.deleteBlockedConversations(addresses)
                activity.runOnUiThread {
                    finishActMode()
                    refreshConversations()
                }
            }
        }
    }

    private fun markSelected(read: Boolean) {
        val addresses = getSelectedItems().map { it.phoneNumber }
        ensureBackgroundThread {
            if (read) {
                activity.markBlockedConversationsRead(addresses)
            } else {
                activity.markBlockedConversationsUnread(addresses)
            }
            activity.runOnUiThread {
                finishActMode()
                refreshConversations()
            }
        }
    }
}
