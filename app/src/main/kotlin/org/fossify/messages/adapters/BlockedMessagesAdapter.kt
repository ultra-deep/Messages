package org.fossify.messages.adapters

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemBlockedMessageBinding
import org.fossify.messages.extensions.RelativeDateTicker
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.deleteBlockedMessages
import org.fossify.messages.extensions.forEachVisiblePosition
import org.fossify.messages.extensions.formatMessageDate
import org.fossify.messages.extensions.moveBlockedMessageToInbox
import org.fossify.messages.models.BlockedMessageEntry

private const val SENDER_FONT_SCALE = 1.1f
private const val BODY_FONT_SCALE = 0.9f
private const val DATE_FONT_SCALE = 0.8f

class BlockedMessagesAdapter(
    activity: SimpleActivity,
    private var messages: ArrayList<BlockedMessageEntry>,
    private val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    private val fontSize = activity.getTextSize()
    private var relativeDateTicker: RelativeDateTicker? = null

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_blocked_messages

    override fun prepareActionMode(menu: Menu) {
        val selected = getSelectedItems()
        menu.apply {
            findItem(R.id.cab_copy_message).isVisible = isOneItemSelected()
            findItem(R.id.cab_move_to_inbox).isVisible =
                selected.isNotEmpty() && selected.all { it.isStoredLocally() }
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_copy_message -> copyMessageToClipboard()
            R.id.cab_move_to_inbox -> moveSelectionToInbox()
            R.id.cab_delete -> deleteSelection()
        }
    }

    override fun getSelectableItemCount() = messages.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = messages.getOrNull(position)?.selectionKey()

    override fun getItemKeyPosition(key: Int) = messages.indexOfFirst { it.selectionKey() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        dateTicker().attach(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        relativeDateTicker?.detach(recyclerView)
        super.onDetachedFromRecyclerView(recyclerView)
    }

    fun refreshDisplayedDates() {
        relativeDateTicker?.refreshNow()
    }

    private fun dateTicker(): RelativeDateTicker {
        return relativeDateTicker ?: RelativeDateTicker(
            isRelativeDateTime = { activity.config.useRelativeDateTime },
            refreshDates = ::refreshVisibleDates,
        ).also { relativeDateTicker = it }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlockedMessageBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.bindView(message, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, message)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = messages.size

    private fun getSelectedItems() = messages.filter { selectedKeys.contains(it.selectionKey()) }

    private fun setupView(view: View, message: BlockedMessageEntry) {
        ItemBlockedMessageBinding.bind(view).apply {
            root.setupViewBackground(activity)
            blockedMessageHolder.isSelected = selectedKeys.contains(message.selectionKey())
            val typeface = FontHelper.getTypeface(activity)
            val style = if (message.read) Typeface.NORMAL else Typeface.BOLD
            blockedMessageSender.apply {
                text = message.title()
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * SENDER_FONT_SCALE)
                setTypeface(typeface, style)
            }
            blockedMessageDate.apply {
                text = message.dateMillis.formatMessageDate(
                    context = context,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = false,
                )
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * DATE_FONT_SCALE)
                setTypeface(typeface, style)
            }
            blockedMessageBody.apply {
                text = message.body
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * BODY_FONT_SCALE)
                setTypeface(typeface, style)
            }

            overflowMenuIcon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }
            overflowMenuIcon.setOnClickListener {
                showPopupMenu(overflowMenuAnchor, message)
            }
        }
    }

    private fun refreshVisibleDates(recyclerView: RecyclerView) {
        if (activity.isDestroyed || activity.isFinishing) {
            return
        }
        recyclerView.forEachVisiblePosition { child, position ->
            val message = messages.getOrNull(position) ?: return@forEachVisiblePosition
            ItemBlockedMessageBinding.bind(child).blockedMessageDate.text =
                message.dateMillis.formatMessageDate(
                    context = child.context,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = false,
                )
        }
    }

    private fun showPopupMenu(view: View, message: BlockedMessageEntry) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)
        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            menu.findItem(R.id.cab_move_to_inbox).isVisible = message.isStoredLocally()
            setOnMenuItemClickListener { item ->
                val messageKey = message.selectionKey()
                when (item.itemId) {
                    R.id.cab_copy_message -> executeItemMenuOperation(messageKey) { copyMessageToClipboard() }
                    R.id.cab_move_to_inbox -> executeItemMenuOperation(messageKey) { moveSelectionToInbox() }
                    R.id.cab_delete -> executeItemMenuOperation(messageKey) { deleteSelection() }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(messageKey: Int, callback: () -> Unit) {
        selectedKeys.add(messageKey)
        callback()
        selectedKeys.remove(messageKey)
    }

    private fun copyMessageToClipboard() {
        val selectedMessage = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(selectedMessage.body)
        finishActMode()
    }

    private fun moveSelectionToInbox() {
        val selected = getSelectedItems()
        finishActMode()
        ensureBackgroundThread {
            selected.forEach { activity.moveBlockedMessageToInbox(it) }
            activity.runOnUiThread { listener?.refreshItems() }
        }
    }

    private fun deleteSelection() {
        val selected = getSelectedItems()
        if (selected.isEmpty()) {
            return
        }

        finishActMode()
        ConfirmationDialog(
            activity = activity,
            message = "",
            messageId = R.string.delete_blocked_messages_confirmation,
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no,
        ) {
            ensureBackgroundThread {
                activity.deleteBlockedMessages(selected)
                activity.runOnUiThread { listener?.refreshItems() }
            }
        }
    }
}
