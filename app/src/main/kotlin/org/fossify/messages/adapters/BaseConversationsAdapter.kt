package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Parcelable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.addBlockedNumber
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.launchActivityIntent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.KEY_PHONE
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemConversationBinding
import org.fossify.messages.dialogs.RenameConversationDialog
import org.fossify.messages.extensions.RelativeDateTicker
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.dialNumber
import org.fossify.messages.extensions.forEachVisiblePosition
import org.fossify.messages.extensions.formatMessageDate
import org.fossify.messages.extensions.getAllDrafts
import org.fossify.messages.extensions.invalidateBlockedConversationCache
import org.fossify.messages.extensions.launchConversationDetails
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.extensions.markThreadMessagesUnread
import org.fossify.messages.extensions.promptMoveConversationsToGroup
import org.fossify.messages.extensions.renameConversation
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.refreshBlockedMessages
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.models.Conversation

@Suppress("LeakingThis")
abstract class BaseConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewListAdapter<Conversation>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = ConversationDiffCallback(),
    itemClick = itemClick,
    onRefresh = onRefresh
),
    RecyclerViewFastScroller.OnPopupTextUpdate {
    private var fontSize = activity.getTextSize()
    private var drafts = HashMap<Long, String>()
    private var listBackgroundColor = Color.TRANSPARENT
    private var unreadBadgeColor: Int? = null

    private var recyclerViewState: Parcelable? = null
    private var relativeDateTicker: RelativeDateTicker? = null

    init {
        setupDragListener(true)
        setHasStableIds(true)
        updateDrafts()

        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = restoreRecyclerViewState()
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) =
                restoreRecyclerViewState()

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) =
                restoreRecyclerViewState()
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateFontSize() {
        fontSize = activity.getTextSize()
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateListBackground(color: Int) {
        if (listBackgroundColor == color) {
            return
        }
        listBackgroundColor = color
        if (itemCount > 0) {
            notifyDataSetChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateUnreadBadgeColor(color: Int) {
        if (unreadBadgeColor == color) {
            return
        }
        unreadBadgeColor = color
        if (itemCount > 0) {
            notifyDataSetChanged()
        }
    }

    fun updateConversations(
        newConversations: ArrayList<Conversation>,
        commitCallback: (() -> Unit)? = null,
    ) {
        saveRecyclerViewState()
        submitList(newConversations.toList(), commitCallback)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateDrafts() {
        ensureBackgroundThread {
            val newDrafts = HashMap<Long, String>()
            fetchDrafts(newDrafts)
            activity.runOnUiThread {
                if (drafts.hashCode() != newDrafts.hashCode()) {
                    drafts = newDrafts
                    notifyDataSetChanged()
                }
            }
        }
    }

    override fun getSelectableItemCount() = itemCount

    protected fun getSelectedItems() = currentList.filter {
        selectedKeys.contains(it.hashCode())
    } as ArrayList<Conversation>

    protected open val showBlockNumber: Boolean = true
    protected open val showUnblockNumber: Boolean = false
    protected open val showArchive: Boolean = true
    protected open val showUnarchive: Boolean = false
    protected open val showRemoveFromGroup: Boolean = false
    protected open fun currentGroupIdForMove(): Long = 0L

    protected fun prepareConversationActionMode(menu: Menu) {
        val selectedItems = getSelectedItems()
        val isSingleSelection = isOneItemSelected()
        val selectedConversation = selectedItems.firstOrNull() ?: return
        val isGroupConversation = selectedConversation.isGroupConversation
        val archiveAvailable = activity.config.isArchiveAvailable

        menu.apply {
            findItem(R.id.cab_block_number)?.let { item ->
                item.isVisible = showBlockNumber
                if (showBlockNumber) {
                    item.title = activity.getString(org.fossify.commons.R.string.block_number)
                }
            }
            findItem(R.id.cab_unblock_number)?.let { item ->
                item.isVisible = showUnblockNumber
                if (showUnblockNumber) {
                    item.title = activity.getString(org.fossify.commons.R.string.unblock)
                }
            }
            findItem(R.id.cab_add_number_to_contact)?.isVisible =
                isSingleSelection && !isGroupConversation
            findItem(R.id.cab_dial_number)?.isVisible =
                isSingleSelection && !isGroupConversation &&
                    !isShortCodeWithLetters(selectedConversation.phoneNumber)
            findItem(R.id.cab_copy_number)?.isVisible = isSingleSelection && !isGroupConversation
            findItem(R.id.cab_rename_conversation)?.isVisible =
                isSingleSelection && isGroupConversation
            findItem(R.id.cab_conversation_details)?.isVisible = isSingleSelection
            findItem(R.id.cab_mark_as_read)?.isVisible = selectedItems.any { !it.read }
            findItem(R.id.cab_mark_as_unread)?.isVisible = selectedItems.any { it.read }
            findItem(R.id.cab_archive)?.isVisible = showArchive && archiveAvailable
            findItem(R.id.cab_unarchive)?.isVisible = showUnarchive
            findItem(R.id.cab_remove_from_group)?.isVisible = showRemoveFromGroup
            findItem(R.id.cab_move_to_group)?.isVisible = true
            checkPinBtnVisibility(this)
        }
    }

    protected fun handleConversationAction(id: Int): Boolean {
        if (selectedKeys.isEmpty()) {
            return true
        }

        when (id) {
            R.id.cab_add_number_to_contact -> addNumberToContact()
            R.id.cab_block_number -> askConfirmBlock()
            R.id.cab_dial_number -> dialNumber()
            R.id.cab_copy_number -> copyNumberToClipboard()
            R.id.cab_delete -> askConfirmDeleteConversations()
            R.id.cab_archive -> askConfirmArchive()
            R.id.cab_move_to_group -> moveSelectedToGroup()
            R.id.cab_rename_conversation -> renameSelectedConversation()
            R.id.cab_conversation_details ->
                activity.launchConversationDetails(getSelectedItems().first().threadId)

            R.id.cab_mark_as_read -> markAsRead()
            R.id.cab_mark_as_unread -> markAsUnread()
            R.id.cab_pin_conversation -> pinConversation(pin = true)
            R.id.cab_unpin_conversation -> pinConversation(pin = false)
            R.id.cab_select_all -> selectAll()
            else -> return false
        }
        return true
    }

    protected fun pinConversation(pin: Boolean) {
        val conversations = getSelectedItems()
        if (conversations.isEmpty()) {
            return
        }

        if (pin) {
            activity.config.addPinnedConversations(conversations)
        } else {
            activity.config.removePinnedConversations(conversations)
        }

        getSelectedItemPositions().forEach {
            notifyItemChanged(it)
        }
        activity.runOnUiThread {
            refreshConversations()
            finishActMode()
        }
    }

    protected fun checkPinBtnVisibility(menu: Menu) {
        val pinnedConversations = activity.config.pinnedConversations
        val selectedConversations = getSelectedItems()
        menu.findItem(R.id.cab_pin_conversation)?.isVisible =
            selectedConversations.any { !pinnedConversations.contains(it.threadId.toString()) }
        menu.findItem(R.id.cab_unpin_conversation)?.isVisible =
            selectedConversations.all { pinnedConversations.contains(it.threadId.toString()) }
    }

    protected open fun markAsRead() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsRead = getSelectedItems()
        ensureBackgroundThread {
            conversationsMarkedAsRead.filter { conversation -> !conversation.read }.forEach {
                activity.markThreadMessagesRead(it.threadId)
            }
            refreshConversationsAndFinishActMode()
        }
    }

    protected open fun markAsUnread() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsMarkedAsUnread = getSelectedItems()
        ensureBackgroundThread {
            conversationsMarkedAsUnread.filter { conversation -> conversation.read }.forEach {
                activity.markThreadMessagesUnread(it.threadId)
            }
            refreshConversationsAndFinishActMode()
        }
    }

    protected open fun askConfirmDeleteConversations() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)
        val question = String.format(
            resources.getString(org.fossify.commons.R.string.deletion_confirmation),
            items
        )

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteSelectedConversations()
            }
        }
    }

    protected open fun deleteSelectedConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = getSelectedItems()
        conversationsToRemove.forEach {
            activity.deleteConversation(it.threadId)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }
        removeConversationsFromList(conversationsToRemove)
    }

    protected fun removeConversationsFromList(removedConversations: List<Conversation>) {
        val newList = try {
            currentList.toMutableList().apply { removeAll(removedConversations) }
        } catch (_: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshConversations()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }

    private fun askConfirmBlock() {
        val numbers = getSelectedItems().distinctBy { it.phoneNumber }.map { it.phoneNumber }
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(
            resources.getString(org.fossify.commons.R.string.block_confirmation),
            numbersString
        )

        ConfirmationDialog(activity, question) {
            blockNumbers()
        }
    }

    private fun blockNumbers() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val numbersToBlock = getSelectedItems()
        val newList = currentList.toMutableList().apply { removeAll(numbersToBlock) }

        ensureBackgroundThread {
            numbersToBlock.forEach { conversation ->
                activity.addBlockedNumber(conversation.phoneNumber)
                activity.conversationsDB.deleteThreadId(conversation.threadId)
            }
            invalidateBlockedConversationCache()
            refreshBlockedMessages()
            refreshConversations()
            activity.runOnUiThread {
                submitList(newList)
                finishActMode()
            }
        }
    }

    private fun dialNumber() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        activity.dialNumber(conversation.phoneNumber) {
            finishActMode()
        }
    }

    private fun copyNumberToClipboard() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        activity.copyToClipboard(conversation.phoneNumber)
        finishActMode()
    }

    private fun askConfirmArchive() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)
        val question = String.format(resources.getString(R.string.archive_confirmation), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                archiveConversations()
            }
        }
    }

    private fun archiveConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = getSelectedItems()
        conversationsToRemove.forEach {
            activity.updateConversationArchivedStatus(it.threadId, true)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }
        removeConversationsFromList(conversationsToRemove)
    }

    private fun moveSelectedToGroup() {
        val threadIds = getSelectedItems().map { it.threadId }
        activity.promptMoveConversationsToGroup(threadIds, currentGroupIdForMove()) {
            finishActMode()
        }
    }

    private fun renameSelectedConversation() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        RenameConversationDialog(activity, conversation) {
            ensureBackgroundThread {
                val updatedConv = activity.renameConversation(conversation, newTitle = it)
                activity.runOnUiThread {
                    finishActMode()
                    currentList.toMutableList().apply {
                        set(indexOf(conversation), updatedConv)
                        updateConversations(this as ArrayList<Conversation>)
                    }
                }
            }
        }
    }

    private fun addNumberToContact() {
        val conversation = getSelectedItems().firstOrNull() ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, conversation.phoneNumber)
            activity.launchActivityIntent(this)
        }
    }

    private fun refreshConversationsAndFinishActMode() {
        activity.runOnUiThread {
            refreshConversations()
            finishActMode()
        }
    }

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.hashCode() == key }

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
        val binding = ItemConversationBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bindView(
            conversation,
            allowSingleClick = true,
            allowLongClick = true
        ) { itemView, _ ->
            setupView(itemView, conversation)
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int) = getItem(position).threadId

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val itemView = ItemConversationBinding.bind(holder.itemView)
            Glide.with(activity).clear(itemView.conversationImage)
        }
    }

    private fun refreshVisibleDates(recyclerView: RecyclerView) {
        if (activity.isDestroyed || activity.isFinishing) {
            return
        }
        recyclerView.forEachVisiblePosition { child, position ->
            val conversation = currentList.getOrNull(position) ?: return@forEachVisiblePosition
            ItemConversationBinding.bind(child).conversationDate.text =
                (conversation.date * 1000L).formatMessageDate(
                    context = child.context,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = false,
                )
        }
    }

    private fun fetchDrafts(drafts: HashMap<Long, String>) {
        drafts.clear()
        for ((threadId, draft) in activity.getAllDrafts()) {
            drafts[threadId] = draft
        }
    }

    private fun setupView(view: View, conversation: Conversation) {
        ItemConversationBinding.bind(view).apply {
            root.setupViewBackground(activity)
            val smsDraft = drafts[conversation.threadId]
            draftIndicator.beVisibleIf(!smsDraft.isNullOrEmpty())
            draftIndicator.setTextColor(properPrimaryColor)

            pinIndicator.beVisibleIf(
                activity.config.pinnedConversations.contains(conversation.threadId.toString())
            )
            val contentColor = contentTextColor()
            pinIndicator.applyColorFilter(contentColor)

            conversationFrame.isSelected = selectedKeys.contains(conversation.hashCode())

            conversationAddress.apply {
                text = conversation.title
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.2f)
            }

            conversationBodyShort.apply {
                text = smsDraft ?: conversation.snippet
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            conversationDate.apply {
                text = (conversation.date * 1000L).formatMessageDate(
                    context = context,
                    hideTimeOnOtherDays = true,
                    showCurrentYear = false,
                )

                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            val isUnread = !conversation.read
            val style = if (isUnread) {
                conversationBodyShort.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD
            } else {
                conversationBodyShort.alpha = 0.7f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            }
            val customTypeface = FontHelper.getTypeface(activity)
            conversationAddress.setTypeface(customTypeface, style)
            conversationBodyShort.setTypeface(customTypeface, style)
            conversationDate.setTypeface(customTypeface, style)

            arrayListOf(conversationAddress, conversationBodyShort, conversationDate).forEach {
                it.setTextColor(contentColor)
            }

            setupBadgeCount(unreadCountBadge, isUnread, conversation.unreadCount)
            // at group conversations we use an icon as the placeholder, not any letter
            val placeholder = if (conversation.isGroupConversation) {
                SimpleContactsHelper(activity).getColoredGroupIcon(conversation.title)
            } else {
                null
            }

            SimpleContactsHelper(activity).loadContactImage(
                path = conversation.photoUri,
                imageView = conversationImage,
                placeholderName = conversation.title,
                placeholderImage = placeholder
            )
        }
    }

    private fun contentTextColor(): Int {
        return if (listBackgroundColor == Color.TRANSPARENT) {
            textColor
        } else {
            listBackgroundColor.getContrastColor()
        }
    }

    private fun setupBadgeCount(view: TextView, isUnread: Boolean, count: Int) {
        view.apply {
            beVisibleIf(isUnread)
            if (isUnread) {
                text = when {
                    count > MAX_UNREAD_BADGE_COUNT -> "$MAX_UNREAD_BADGE_COUNT+"
                    count == 0 -> ""
                    else -> count.toString()
                }
                val badgeColor = unreadBadgeColor ?: properPrimaryColor
                setTypeface(FontHelper.getTypeface(activity), Typeface.BOLD)
                setTextColor(badgeColor.getContrastColor())
                background?.applyColorFilter(badgeColor)
            }
        }
    }

    override fun onChange(position: Int) = currentList.getOrNull(position)?.title ?: ""

    private fun saveRecyclerViewState() {
        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    private fun restoreRecyclerViewState() {
        recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }

    private class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areItemsTheSame(oldItem, newItem)
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areContentsTheSame(oldItem, newItem)
        }
    }

    companion object {
        private const val MAX_UNREAD_BADGE_COUNT = 99
    }
}
