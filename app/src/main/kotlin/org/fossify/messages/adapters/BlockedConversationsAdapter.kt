package org.fossify.messages.adapters

import android.text.TextUtils
import android.view.Menu
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.deleteBlockedNumber
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.deleteBlockedConversations
import org.fossify.messages.extensions.invalidateBlockedConversationCache
import org.fossify.messages.extensions.markBlockedConversationsRead
import org.fossify.messages.extensions.markBlockedConversationsUnread
import org.fossify.messages.helpers.refreshBlockedMessages
import org.fossify.messages.helpers.refreshConversations as refreshAllConversations

class BlockedConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    private val refreshConversations: () -> Unit,
    itemClick: (Any) -> Unit,
) : BaseConversationsAdapter(activity, recyclerView, refreshConversations, itemClick) {
    override fun getActionMenuId() = R.menu.cab_blocked_conversations

    override fun prepareActionMode(menu: Menu) {
        menu.findItem(R.id.cab_unblock_number).title =
            activity.getString(org.fossify.commons.R.string.unblock)
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_select_all -> selectAll()
            R.id.cab_mark_as_read -> markSelected(read = true)
            R.id.cab_mark_as_unread -> markSelected(read = false)
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_unblock_number -> askConfirmUnblock()
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

    private fun askConfirmUnblock() {
        val numbers = getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber }
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(
            activity.getString(R.string.unblock_confirmation),
            numbersString
        )

        ConfirmationDialog(activity, question) {
            unblockNumbers(numbers)
        }
    }

    private fun unblockNumbers(numbers: List<String>) {
        ensureBackgroundThread {
            numbers.forEach { number ->
                activity.deleteBlockedNumber(number)
            }
            invalidateBlockedConversationCache()
            refreshBlockedMessages()
            refreshAllConversations()
            activity.runOnUiThread {
                finishActMode()
                refreshConversations()
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
