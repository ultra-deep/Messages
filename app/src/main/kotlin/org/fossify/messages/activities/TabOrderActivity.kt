package org.fossify.messages.activities

import android.os.Bundle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.TabOrderAdapter
import org.fossify.messages.adapters.TabOrderEntry
import org.fossify.messages.databinding.ActivityTabOrderBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationGroupsDB
import org.fossify.messages.helpers.ConversationTab
import org.fossify.messages.helpers.encodeConversationTabs
import org.fossify.messages.helpers.resolveConversationTabs
import org.fossify.messages.models.ConversationGroup

class TabOrderActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityTabOrderBinding::inflate)
    private val adapter = TabOrderAdapter(this)
    private var loaded = false
    private var dragging = false
    private var loadGeneration = 0

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0,
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                return false
            }
            adapter.move(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                dragging = true
                viewHolder?.itemView?.alpha = 0.7f
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = 1f
            dragging = false
            saveOrder()
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        adapter.setItemTouchHelper(itemTouchHelper)
        binding.tabOrderList.adapter = adapter
        itemTouchHelper.attachToRecyclerView(binding.tabOrderList)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.tabOrderList))
        setupMaterialScrollListener(
            scrollingView = binding.tabOrderList,
            topAppBar = binding.tabOrderAppbar,
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.tabOrderAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.tabOrderWrapper)
        if (!dragging) {
            loadTabs()
        }
    }

    override fun onPause() {
        super.onPause()
        saveOrder()
    }

    private fun loadTabs() {
        val generation = ++loadGeneration
        ensureBackgroundThread {
            val groups = try {
                conversationGroupsDB.getAll()
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isDestroyed && !isFinishing && generation == loadGeneration) {
                        showErrorToast(e)
                    }
                }
                return@ensureBackgroundThread
            }
            val entries = tabEntries(groups)
            runOnUiThread {
                if (
                    isDestroyed ||
                    isFinishing ||
                    dragging ||
                    generation != loadGeneration
                ) {
                    return@runOnUiThread
                }
                adapter.submit(entries)
                loaded = true
            }
        }
    }

    private fun tabEntries(groups: List<ConversationGroup>): List<TabOrderEntry> {
        val tabs = resolveConversationTabs(
            stored = config.conversationTabOrder,
            groupIdsInOrder = groups.map { it.id },
            showBlocked = config.showBlockedMessagesTab,
            showArchived = config.showArchivedMessagesTab,
            includeHidden = true,
        )
        return tabs.map { tab ->
            TabOrderEntry(
                tab = tab,
                title = tabTitle(tab, groups),
                hidden = when (tab) {
                    ConversationTab.Blocked -> !config.showBlockedMessagesTab
                    ConversationTab.Archived -> !config.showArchivedMessagesTab
                    else -> false
                },
            )
        }
    }

    private fun tabTitle(tab: ConversationTab, groups: List<ConversationGroup>): String {
        return when (tab) {
            ConversationTab.Messages -> getString(R.string.messages)
            ConversationTab.Blocked -> getString(R.string.blocked)
            ConversationTab.Archived -> getString(R.string.archived_conversations)
            is ConversationTab.Group -> groups.firstOrNull { it.id == tab.id }?.name.orEmpty()
        }
    }

    private fun saveOrder() {
        if (!loaded) {
            return
        }
        loadGeneration++
        config.conversationTabOrder = encodeConversationTabs(adapter.orderedTabs())
    }
}
