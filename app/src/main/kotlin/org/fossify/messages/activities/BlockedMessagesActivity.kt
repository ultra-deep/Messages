package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.messages.R
import org.fossify.messages.adapters.BlockedMessagesAdapter
import org.fossify.messages.databinding.ActivityBlockedMessagesBinding
import org.fossify.messages.dialogs.SelectTextDialog
import org.fossify.messages.extensions.deleteBlockedMessages
import org.fossify.messages.extensions.getBlockedMessageEntries
import org.fossify.messages.models.BlockedMessageEntry
import org.fossify.messages.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class BlockedMessagesActivity : SimpleActivity(), RefreshRecyclerViewListener {
    private var bus: EventBus? = null
    private var messages = ArrayList<BlockedMessageEntry>()
    private val binding by viewBinding(ActivityBlockedMessagesBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.blockedMessagesList))
        setupMaterialScrollListener(
            scrollingView = binding.blockedMessagesList,
            topAppBar = binding.blockedMessagesAppbar,
        )
        updateTextColors(binding.blockedMessagesWrapper)

        bus = EventBus.getDefault()
        try {
            bus?.register(this)
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.blockedMessagesAppbar, NavigationIcon.Arrow)
        loadBlockedMessages()
    }

    override fun onDestroy() {
        bus?.unregister(this)
        super.onDestroy()
    }

    override fun refreshItems() {
        loadBlockedMessages()
    }

    private fun setupOptionsMenu() {
        binding.blockedMessagesToolbar.menu?.findItem(R.id.empty_blocked_messages)?.isVisible = false
        binding.blockedMessagesToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.empty_blocked_messages -> {
                    confirmDeleteAll()
                    true
                }

                else -> false
            }
        }
    }

    private fun loadBlockedMessages() {
        ensureBackgroundThread {
            val loadedMessages = try {
                getBlockedMessageEntries()
            } catch (_: Exception) {
                ArrayList()
            }
            runOnUiThread {
                if (isDestroyed) {
                    return@runOnUiThread
                }
                messages = loadedMessages
                showMessages(loadedMessages)
            }
        }
    }

    private fun showMessages(loadedMessages: ArrayList<BlockedMessageEntry>) {
        binding.blockedMessagesPlaceholder.beVisibleIf(loadedMessages.isEmpty())
        binding.blockedMessagesList.beVisibleIf(loadedMessages.isNotEmpty())
        binding.blockedMessagesToolbar.menu?.findItem(R.id.empty_blocked_messages)?.isVisible =
            loadedMessages.isNotEmpty()

        (binding.blockedMessagesList.adapter as? BlockedMessagesAdapter)?.finishActMode()
        binding.blockedMessagesList.adapter = BlockedMessagesAdapter(
            activity = this,
            messages = loadedMessages,
            listener = this,
            recyclerView = binding.blockedMessagesList,
        ) { selected ->
            SelectTextDialog(this, (selected as BlockedMessageEntry).readableText())
        }
    }

    private fun confirmDeleteAll() {
        if (messages.isEmpty()) {
            return
        }

        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.empty_blocked_messages_confirmation,
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no,
        ) {
            val messagesToDelete = messages
            ensureBackgroundThread {
                deleteBlockedMessages(messagesToDelete)
                loadBlockedMessages()
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshBlockedMessages(@Suppress("unused") event: Events.RefreshBlockedMessages) {
        loadBlockedMessages()
    }
}
