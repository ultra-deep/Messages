package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.tabs.TabLayout
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.appLockManager
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.checkWhatsNew
import org.fossify.commons.extensions.convertToBitmap
import org.fossify.commons.extensions.fadeIn
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_EVENT_BUS
import org.fossify.commons.helpers.LICENSE_INDICATOR_FAST_SCROLL
import org.fossify.commons.helpers.LICENSE_SMS_MMS
import org.fossify.commons.helpers.LOWER_ALPHA
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.SHORT_ANIMATION_DURATION
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.Release
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.adapters.ArchivedConversationsAdapter
import org.fossify.messages.adapters.BaseConversationsAdapter
import org.fossify.messages.adapters.BlockedConversationsAdapter
import org.fossify.messages.adapters.ConversationsAdapter
import org.fossify.messages.adapters.GroupConversationsAdapter
import org.fossify.messages.adapters.SearchResultsAdapter
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.dialogs.GroupNameDialog
import org.fossify.messages.extensions.checkAndDeleteOldRecycleBinMessages
import org.fossify.messages.extensions.clearAllMessagesIfNeeded
import org.fossify.messages.extensions.clearExpiredScheduledMessages
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationGroupsDB
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.createConversationGroup
import org.fossify.messages.extensions.deleteConversationGroup
import org.fossify.messages.extensions.removeAllArchivedConversations
import org.fossify.messages.extensions.renameConversationGroup
import org.fossify.messages.extensions.peekBlockedConversations
import org.fossify.messages.extensions.reloadBlockedConversations
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.getUnreadCountsByThread
import org.fossify.messages.extensions.unreadBlockedBadgeText
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.IS_BLOCKED_CONVERSATION
import org.fossify.messages.helpers.SEARCHED_MESSAGE_ID
import org.fossify.messages.helpers.SHOW_BLOCKED_TAB
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_NUMBER
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.ConversationGroup
import org.fossify.messages.models.Events
import org.fossify.messages.models.Message
import org.fossify.messages.models.SearchResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {
    override var isSearchBarEnabled = true
    
    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var storedTextColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private var bus: EventBus? = null
    private val blockedRefreshLock = Any()
    private var blockedRefreshRunning = false
    private var blockedRefreshAgain = false
    private var showingBlockedTab = false
    private var showingArchivedTab = false
    private var appliedShowBlockedTab: Boolean? = null
    private var appliedShowArchivedTab: Boolean? = null
    private var openBlockedDespiteHiddenTab = false
    private var ignoreTabSelection = false
    private var groupsLoaded = false
    private var groupRefreshGeneration = 0
    private var selectedGroupId = 0L
    private var conversationGroups = emptyList<ConversationGroup>()
    private var groupedConversations = emptyList<Conversation>()
    private var inboxConversations = ArrayList<Conversation>()
    private var archivedConversations = ArrayList<Conversation>()

    private val binding by viewBinding(ActivityMainBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        setupConversationTabs()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))

        checkAndDeleteOldRecycleBinMessages()
        clearAllMessagesIfNeeded {
            loadMessages()
        }

        if (checkAppSideloading()) {
            return
        }
    }

    override fun onResume() {
        super.onResume()
        updateMenuColors()
        refreshMenuItems()

        (binding.conversationsList.adapter as? BaseConversationsAdapter)?.apply {
            if (storedTextColor != getProperTextColor()) {
                updateTextColor(getProperTextColor())
            }

            if (storedFontSize != config.fontSize) {
                updateFontSize()
            }

            updateDrafts()
        }

        applyConversationTabVisibility()
        styleConversationTabs()
        binding.conversationsFab.beVisibleIf(showingInbox())

        updateTextColors(binding.mainCoordinator)
        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())

        val properPrimaryColor = getProperPrimaryColor()
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        checkShortcut()
        refreshBlockedTab()
        refreshArchivedTab()
        refreshConversationGroups()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openBlockedTabIfRequested()
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onBackPressedCompat(): Boolean {
        return if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
            true
        } else {
            appLockManager.lock()
            false
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.requireToolbar().inflateMenu(R.menu.menu_main)
        binding.mainMenu.toggleHideOnScroll(true)
        binding.mainMenu.setupMenu()

        binding.mainMenu.onSearchClosedListener = {
            fadeOutSearch()
        }

        binding.mainMenu.onSearchTextChangedListener = { text ->
            if (text.isNotEmpty()) {
                if (binding.searchHolder.alpha < 1f) {
                    binding.searchHolder.fadeIn()
                }
            } else {
                fadeOutSearch()
            }
            searchTextChanged(text)
        }

        binding.mainMenu.requireToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.show_recycle_bin -> launchRecycleBin()
                R.id.show_archived -> showArchivedTab()
                R.id.empty_archive -> confirmEmptyArchive()
                R.id.show_blocked_messages -> showBlockedTab()
                R.id.add_group -> askCreateGroup()
                R.id.rename_group -> askRenameGroup()
                R.id.delete_group -> confirmDeleteSelectedGroup()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.requireToolbar().menu.apply {
            findItem(R.id.show_archived).isVisible = config.isArchiveAvailable
            findItem(R.id.empty_archive).isVisible =
                showingArchivedTab && archivedConversations.isNotEmpty()
            findItem(R.id.rename_group).isVisible = selectedGroupId != 0L
            findItem(R.id.delete_group).isVisible = selectedGroupId != 0L
        }
    }

    private fun setupConversationTabs() {
        binding.conversationsTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showConversationTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) {
                showConversationTab(tab.position)
            }
        })
        applyConversationTabVisibility()
    }

    private fun blockedTabIndex(): Int {
        if (!config.showBlockedMessagesTab) {
            return -1
        }
        return 1
    }

    private fun archivedTabIndex(): Int {
        if (!config.showArchivedMessagesTab) {
            return -1
        }
        return if (config.showBlockedMessagesTab) 2 else 1
    }

    private fun groupTabOffset(): Int {
        var offset = 1
        if (config.showBlockedMessagesTab) {
            offset++
        }
        if (config.showArchivedMessagesTab) {
            offset++
        }
        return offset
    }

    private fun applyConversationTabVisibility() {
        val showBlocked = config.showBlockedMessagesTab
        val showArchived = config.showArchivedMessagesTab
        if (
            showBlocked == appliedShowBlockedTab &&
            showArchived == appliedShowArchivedTab &&
            binding.conversationsTabs.tabCount > 0
        ) {
            openBlockedDespiteHiddenTab = false
            return
        }

        val leaveBlockedView = appliedShowBlockedTab == true &&
            !showBlocked &&
            showingBlockedTab &&
            !openBlockedDespiteHiddenTab
        val leaveArchivedView = appliedShowArchivedTab == true &&
            !showArchived &&
            showingArchivedTab
        openBlockedDespiteHiddenTab = false
        appliedShowBlockedTab = showBlocked
        appliedShowArchivedTab = showArchived
        if (leaveBlockedView) {
            showingBlockedTab = false
        }
        if (leaveArchivedView) {
            showingArchivedTab = false
        }

        ignoreTabSelection = true
        try {
            val tabs = binding.conversationsTabs
            tabs.removeAllTabs()
            tabs.addTab(tabs.newTab().setText(R.string.messages), false)
            if (showBlocked) {
                tabs.addTab(tabs.newTab().setText(R.string.blocked), false)
            }
            if (showArchived) {
                tabs.addTab(tabs.newTab().setText(R.string.archived_conversations), false)
            }
            conversationGroups.forEach { group ->
                tabs.addTab(tabs.newTab().setText(group.name), false)
            }
            updateTabMode()
            styleConversationTabs()
            restoreSelectedTab()
        } finally {
            ignoreTabSelection = false
        }

        if (leaveBlockedView || leaveArchivedView) {
            showConversationTab(0)
        }
    }

    private fun styleConversationTabs() {
        val primaryColor = getProperPrimaryColor()
        binding.conversationsTabs.setSelectedTabIndicatorColor(primaryColor)
        binding.conversationsTabs.setTabTextColors(getProperTextColor(), primaryColor)
        binding.conversationsTabs.setBackgroundColor(getProperBackgroundColor())
    }

    private fun showConversationTab(position: Int) {
        if (ignoreTabSelection) {
            return
        }
        showingBlockedTab = position == blockedTabIndex()
        showingArchivedTab = position == archivedTabIndex()
        selectedGroupId = groupIdForTab(position)
        binding.conversationsFab.beVisibleIf(showingInbox())
        when {
            showingBlockedTab -> showBlockedTabContent()
            showingArchivedTab -> {
                binding.conversationsProgressBar.hide()
                loadArchivedConversations()
            }
            selectedGroupId != 0L -> showGroupTab()
            else -> {
                binding.conversationsProgressBar.hide()
                setupConversations(inboxConversations)
            }
        }
        refreshMenuItems()
    }

    private fun showingInbox() = !showingBlockedTab && !showingArchivedTab && selectedGroupId == 0L

    private fun groupIdForTab(position: Int): Long {
        val offset = groupTabOffset()
        if (position < offset) {
            return 0L
        }
        return conversationGroups.getOrNull(position - offset)?.id ?: 0L
    }

    private fun showBlockedTab() {
        val index = blockedTabIndex()
        if (index >= 0) {
            selectConversationTab(index)
            return
        }
        showingBlockedTab = true
        showingArchivedTab = false
        selectedGroupId = 0L
        ignoreTabSelection = true
        try {
            binding.conversationsTabs.selectTab(null)
        } finally {
            ignoreTabSelection = false
        }
        binding.conversationsFab.beGone()
        showBlockedTabContent()
        refreshMenuItems()
    }

    private fun showArchivedTab() {
        val index = archivedTabIndex()
        if (index >= 0) {
            selectConversationTab(index)
            return
        }
        showingBlockedTab = false
        showingArchivedTab = true
        selectedGroupId = 0L
        ignoreTabSelection = true
        try {
            binding.conversationsTabs.selectTab(null)
        } finally {
            ignoreTabSelection = false
        }
        binding.conversationsFab.beGone()
        binding.conversationsProgressBar.hide()
        loadArchivedConversations()
        refreshMenuItems()
    }

    private fun selectConversationTab(index: Int) {
        val tab = binding.conversationsTabs.getTabAt(index) ?: return
        if (tab.isSelected) {
            showConversationTab(index)
        } else {
            tab.select()
        }
    }

    private fun openBlockedTabIfRequested() {
        if (!intent.getBooleanExtra(SHOW_BLOCKED_TAB, false)) {
            return
        }
        intent.removeExtra(SHOW_BLOCKED_TAB)
        openBlockedDespiteHiddenTab = true
        showBlockedTab()
    }

    private fun refreshBlockedTab() {
        if (showingBlockedTab) {
            showBlockedTabContent()
            return
        }
        peekBlockedConversations()?.let { conversations ->
            updateBlockedTabBadge(conversations.sumOf { it.unreadCount })
        }
        loadBlockedConversations()
    }

    private fun showBlockedTabContent() {
        val cached = peekBlockedConversations()
        if (cached != null) {
            showBlockedConversations(cached)
        } else if (binding.conversationsList.adapter !is BlockedConversationsAdapter) {
            binding.conversationsFastscroller.beGone()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder2.beGone()
            binding.conversationsProgressBar.show()
        }
        loadBlockedConversations()
    }

    private fun updateBlockedTabBadge(count: Int) {
        applyTabBadge(blockedTabIndex(), count)
        val badgeText = unreadBlockedBadgeText(count)
        val title = if (count > 0) {
            getString(R.string.blocked_messages_with_count, badgeText)
        } else {
            getString(R.string.blocked_messages)
        }
        binding.mainMenu.requireToolbar().menu.findItem(R.id.show_blocked_messages)?.title = title
    }

    private fun loadBlockedConversations() {
        synchronized(blockedRefreshLock) {
            if (blockedRefreshRunning) {
                blockedRefreshAgain = true
                return
            }
            blockedRefreshRunning = true
        }
        ensureBackgroundThread {
            var conversations = arrayListOf<Conversation>()
            do {
                synchronized(blockedRefreshLock) {
                    blockedRefreshAgain = false
                }
                conversations = try {
                    reloadBlockedConversations()
                } catch (_: Exception) {
                    arrayListOf()
                }
                val count = conversations.sumOf { it.unreadCount }
                val latest = conversations
                runOnUiThread {
                    if (isDestroyed || isFinishing) {
                        return@runOnUiThread
                    }
                    updateBlockedTabBadge(count)
                    if (showingBlockedTab) {
                        showBlockedConversations(latest)
                    }
                }
            } while (
                synchronized(blockedRefreshLock) {
                    if (blockedRefreshAgain) {
                        true
                    } else {
                        blockedRefreshRunning = false
                        false
                    }
                }
            )
        }
    }

    private fun showBlockedConversations(conversations: ArrayList<Conversation>) {
        val hasConversations = conversations.isNotEmpty()
        binding.conversationsProgressBar.hide()
        binding.noConversationsPlaceholder.text = getString(R.string.no_blocked_messages)
        binding.noConversationsPlaceholder.beVisibleIf(!hasConversations)
        binding.noConversationsPlaceholder2.beGone()
        binding.conversationsFastscroller.beVisibleIf(hasConversations)

        val currentAdapter = binding.conversationsList.adapter
        if (currentAdapter is BlockedConversationsAdapter) {
            currentAdapter.updateConversations(conversations)
            return
        }

        BlockedConversationsAdapter(
            activity = this,
            recyclerView = binding.conversationsList,
            refreshConversations = { loadBlockedConversations() },
            itemClick = { handleBlockedConversationClick(it) },
        ).apply {
            binding.conversationsList.adapter = this
            updateConversations(conversations)
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
    }

    private fun refreshArchivedTab() {
        ensureBackgroundThread {
            val conversations = archivedConversationsForUi()
            runOnUiThread {
                if (isDestroyed || isFinishing) {
                    return@runOnUiThread
                }
                updateArchivedTabBadge(conversations.sumOf { it.unreadCount })
                if (showingArchivedTab) {
                    showArchivedConversations(ArrayList(conversations))
                }
            }
        }
    }

    private fun loadArchivedConversations() {
        refreshArchivedTab()
    }

    private fun showArchivedConversations(conversations: ArrayList<Conversation>) {
        val sortedConversations = conversations.sortedWith(
            compareByDescending<Conversation> {
                config.pinnedConversations.contains(it.threadId.toString())
            }.thenByDescending { it.date }
        ).toMutableList() as ArrayList<Conversation>
        archivedConversations = sortedConversations
        updateArchivedTabBadge(sortedConversations.sumOf { it.unreadCount })
        refreshMenuItems()

        val hasConversations = sortedConversations.isNotEmpty()
        binding.conversationsProgressBar.hide()
        binding.noConversationsPlaceholder.text = getString(R.string.no_archived_conversations)
        binding.noConversationsPlaceholder.beVisibleIf(!hasConversations)
        binding.noConversationsPlaceholder2.beGone()
        binding.conversationsFastscroller.beVisibleIf(hasConversations)

        val currentAdapter = binding.conversationsList.adapter
        if (currentAdapter is ArchivedConversationsAdapter) {
            currentAdapter.updateConversations(sortedConversations)
            return
        }

        ArchivedConversationsAdapter(
            activity = this,
            recyclerView = binding.conversationsList,
            onRefresh = { loadArchivedConversations() },
            itemClick = { handleConversationClick(it) },
        ).apply {
            binding.conversationsList.adapter = this
            updateConversations(sortedConversations)
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
    }

    private fun updateArchivedTabBadge(count: Int) {
        applyTabBadge(archivedTabIndex(), count)
        val title = if (count > 0) {
            getString(R.string.show_archived_conversations_with_count, unreadBlockedBadgeText(count))
        } else {
            getString(R.string.show_archived_conversations)
        }
        binding.mainMenu.requireToolbar().menu.findItem(R.id.show_archived)?.title = title
    }

    private fun archivedConversationsForUi(): List<Conversation> {
        val stored = try {
            conversationsDB.getAllArchived()
        } catch (_: Exception) {
            return emptyList()
        }
        val unreadMap = try {
            getUnreadCountsByThread()
        } catch (_: Exception) {
            return stored.map { it.withVisibleUnreadCount() }
        }
        return stored.map { conversation ->
            conversation.withVisibleUnreadCount(unreadMap[conversation.threadId] ?: 0)
        }
    }

    private fun Conversation.withVisibleUnreadCount(liveCount: Int = unreadCount): Conversation {
        val count = when {
            liveCount > 0 -> liveCount
            !read -> unreadCount.coerceAtLeast(1)
            else -> 0
        }
        val isRead = count == 0
        return if (unreadCount == count && read == isRead) {
            this
        } else {
            copy(read = isRead, unreadCount = count)
        }
    }

    private fun confirmEmptyArchive() {
        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.empty_archive_confirmation,
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no,
        ) {
            removeAllArchivedConversations {
                loadArchivedConversations()
            }
        }
    }

    private fun applyTabBadge(index: Int, count: Int) {
        val tab = binding.conversationsTabs.getTabAt(index) ?: return
        if (count <= 0) {
            tab.removeBadge()
        } else {
            tab.orCreateBadge.apply {
                maxCharacterCount = TAB_BADGE_CHARACTERS
                number = count
                backgroundColor = getProperPrimaryColor()
                badgeTextColor = getProperPrimaryColor().getContrastColor()
            }
        }
    }

    private fun showGroupTab() {
        showGroupConversations(
            ArrayList(groupedConversations.filter { it.groupId == selectedGroupId })
        )
        refreshConversationGroups()
    }

    private fun refreshConversationGroups(selectGroupId: Long = 0L) {
        val generation = ++groupRefreshGeneration
        ensureBackgroundThread {
            val groups = try {
                conversationGroupsDB.getAll()
            } catch (_: Exception) {
                emptyList()
            }
            val grouped = groupedConversationsForUi()
            val unreadCounts = grouped.groupBy { it.groupId }.mapValues { (_, conversations) ->
                conversations.sumOf { it.unreadCount }
            }
            runOnUiThread {
                if (isDestroyed || isFinishing || generation != groupRefreshGeneration) {
                    return@runOnUiThread
                }
                groupsLoaded = true
                groupedConversations = grouped
                if (selectGroupId != 0L) {
                    selectedGroupId = selectGroupId
                    showingBlockedTab = false
                    showingArchivedTab = false
                }
                syncGroupTabs(groups, unreadCounts)
                if (selectedGroupId != 0L && !showingBlockedTab && !showingArchivedTab) {
                    showGroupConversations(
                        ArrayList(grouped.filter { it.groupId == selectedGroupId })
                    )
                }
                binding.conversationsFab.beVisibleIf(showingInbox())
                refreshMenuItems()
            }
        }
    }

    private fun syncGroupTabs(
        groups: List<ConversationGroup>,
        unreadCounts: Map<Long, Int>,
    ) {
        val tabs = binding.conversationsTabs
        val unchanged = conversationGroups.size == groups.size &&
            conversationGroups.zip(groups).all { (current, updated) ->
                current.id == updated.id && current.name == updated.name
            }
        conversationGroups = groups
        if (!unchanged) {
            ignoreTabSelection = true
            try {
                while (tabs.tabCount > groupTabOffset()) {
                    tabs.removeTabAt(tabs.tabCount - 1)
                }
                groups.forEach { group ->
                    tabs.addTab(tabs.newTab().setText(group.name), false)
                }
                updateTabMode()
                styleConversationTabs()
                restoreSelectedTab()
            } finally {
                ignoreTabSelection = false
            }
        }
        groups.forEachIndexed { index, group ->
            applyTabBadge(groupTabOffset() + index, unreadCounts[group.id] ?: 0)
        }
    }

    private fun updateTabMode() {
        val scrollable = binding.conversationsTabs.tabCount > groupTabOffset()
        binding.conversationsTabs.tabMode = if (scrollable) {
            TabLayout.MODE_SCROLLABLE
        } else {
            TabLayout.MODE_FIXED
        }
        binding.conversationsTabs.tabGravity = if (scrollable) {
            TabLayout.GRAVITY_START
        } else {
            TabLayout.GRAVITY_FILL
        }
    }

    private fun restoreSelectedTab() {
        val hiddenBlocked = showingBlockedTab && blockedTabIndex() < 0
        val hiddenArchived = showingArchivedTab && archivedTabIndex() < 0
        if (hiddenBlocked || hiddenArchived) {
            binding.conversationsTabs.selectTab(null)
            return
        }
        val tab = binding.conversationsTabs.getTabAt(selectedTabIndex()) ?: return
        if (!tab.isSelected) {
            tab.select()
        }
    }

    private fun selectedTabIndex(): Int {
        val blockedIndex = blockedTabIndex()
        if (showingBlockedTab && blockedIndex >= 0) {
            return blockedIndex
        }
        val archivedIndex = archivedTabIndex()
        if (showingArchivedTab && archivedIndex >= 0) {
            return archivedIndex
        }
        val groupIndex = conversationGroups.indexOfFirst { it.id == selectedGroupId }
        if (selectedGroupId != 0L && groupIndex >= 0) {
            return groupTabOffset() + groupIndex
        }
        selectedGroupId = 0L
        return 0
    }

    private fun groupedConversationsForUi(): List<Conversation> {
        val stored = try {
            conversationsDB.getGrouped()
        } catch (_: Exception) {
            return emptyList()
        }
        val unreadMap = try {
            getUnreadCountsByThread()
        } catch (_: Exception) {
            return stored.map { it.withVisibleUnreadCount() }
        }
        return stored.map { conversation ->
            conversation.withVisibleUnreadCount(unreadMap[conversation.threadId] ?: 0)
        }
    }

    private fun showGroupConversations(conversations: ArrayList<Conversation>) {
        if (selectedGroupId == 0L || showingBlockedTab || showingArchivedTab) {
            return
        }
        if (!groupsLoaded) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beGone()
            binding.noConversationsPlaceholder2.beGone()
            binding.conversationsFastscroller.beGone()
            binding.conversationsList.adapter = null
            return
        }

        val sortedConversations = conversations.sortedWith(
            compareByDescending<Conversation> {
                config.pinnedConversations.contains(it.threadId.toString())
            }.thenByDescending { it.date }
        ).toMutableList() as ArrayList<Conversation>

        val hasConversations = sortedConversations.isNotEmpty()
        binding.conversationsProgressBar.hide()
        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_in_group)
        binding.noConversationsPlaceholder.beVisibleIf(!hasConversations)
        binding.noConversationsPlaceholder2.beGone()
        binding.conversationsFastscroller.beVisibleIf(hasConversations)

        val currentAdapter = binding.conversationsList.adapter
        if (currentAdapter is GroupConversationsAdapter) {
            currentAdapter.updateConversations(sortedConversations)
            return
        }

        GroupConversationsAdapter(
            activity = this,
            recyclerView = binding.conversationsList,
            groupId = { selectedGroupId },
            onRefresh = { refreshConversationGroups() },
            itemClick = { handleConversationClick(it) },
        ).apply {
            binding.conversationsList.adapter = this
            updateConversations(sortedConversations)
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
    }

    private fun askCreateGroup() {
        GroupNameDialog(
            activity = this,
            titleRes = R.string.add_group,
            isNameTaken = { name ->
                conversationGroups.any { it.name.equals(name, ignoreCase = true) }
            },
        ) { name ->
            ensureBackgroundThread {
                val id = createConversationGroup(name)
                runOnUiThread {
                    if (isDestroyed || isFinishing) {
                        return@runOnUiThread
                    }
                    if (id == 0L) {
                        toast(R.string.group_name_exists)
                    } else {
                        refreshConversationGroups(selectGroupId = id)
                    }
                }
            }
        }
    }

    private fun askRenameGroup() {
        val group = conversationGroups.find { it.id == selectedGroupId } ?: return
        GroupNameDialog(
            activity = this,
            initialName = group.name,
            titleRes = R.string.rename_group,
            isNameTaken = { name ->
                conversationGroups.any { it.id != group.id && it.name.equals(name, ignoreCase = true) }
            },
        ) { name ->
            ensureBackgroundThread {
                renameConversationGroup(group.id, name)
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        refreshConversationGroups()
                    }
                }
            }
        }
    }

    private fun confirmDeleteSelectedGroup() {
        val group = conversationGroups.find { it.id == selectedGroupId } ?: return
        ConfirmationDialog(this, getString(R.string.delete_group_confirmation, group.name)) {
            ensureBackgroundThread {
                deleteConversationGroup(group.id)
                runOnUiThread {
                    if (isDestroyed || isFinishing) {
                        return@runOnUiThread
                    }
                    selectedGroupId = 0L
                    showingBlockedTab = false
                    showingArchivedTab = false
                    selectConversationTab(0)
                    initMessenger()
                }
            }
        }
    }

    private fun handleBlockedConversationClick(any: Any) {
        val conversation = any as Conversation
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            putExtra(THREAD_NUMBER, conversation.phoneNumber)
            putExtra(IS_BLOCKED_CONVERSATION, true)
            startActivity(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == RESULT_OK) {
                askPermissions()
            } else {
                finish()
            }
        }
    }

    private fun storeStateVariables() {
        storedTextColor = getProperTextColor()
        storedFontSize = config.fontSize
    }

    private fun updateMenuColors() {
        binding.mainMenu.updateColors()
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    // while SEND_SMS and READ_SMS permissions are mandatory, READ_CONTACTS is optional.
    // If we don't have it, we just won't be able to show the contact name in some cases
    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }

                            initMessenger()
                            openBlockedTabIfRequested()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        checkWhatsNewDialog()
        storeStateVariables()
        getCachedConversations()
        binding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }

        binding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }

            val archived = try {
                conversationsDB.getAllArchived()
            } catch (_: Exception) {
                emptyList()
            }
            val archivedForUi = archivedConversationsForUi()

            runOnUiThread {
                updateArchivedTabBadge(archivedForUi.sumOf { it.unreadCount })
                if (showingArchivedTab) {
                    showArchivedConversations(ArrayList(archivedForUi))
                }
                setupConversations(conversations, cached = true)
                refreshConversationGroups()
                getNewConversations(
                    (conversations + archived).toMutableList() as ArrayList<Conversation>
                )
            }
            conversations.forEach {
                clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)

            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    insertOrUpdateConversation(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    conversationsDB.deleteThreadId(threadId)
                }

                val newConversation =
                    conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    // delete the original temporary thread and move any scheduled messages
                    // to the new thread
                    conversationsDB.deleteThreadId(threadId)
                    messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            messagesDB.insertOrUpdate(
                                message.copy(threadId = newConversation.threadId)
                            )
                        }
                    insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversations.find {
                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(
                        old = cachedConv, new = it
                    )
                }
                if (conv != null) {
                    // FIXME: Scheduled message date is being reset here. Conversations with
                    //  scheduled messages will have their original date.
                    insertOrUpdateConversation(conv)
                }
            }

            val allConversations = conversationsDB.getNonArchived() as ArrayList<Conversation>
            val archivedForUi = archivedConversationsForUi()
            runOnUiThread {
                updateArchivedTabBadge(archivedForUi.sumOf { it.unreadCount })
                if (showingArchivedTab) {
                    showArchivedConversations(ArrayList(archivedForUi))
                }
                setupConversations(allConversations)
                refreshConversationGroups()
            }

            if (config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = getMessages(threadId, includeScheduledMessages = false)
                    messages.chunked(30).forEach { currentMessages ->
                        messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        val currentAdapter = binding.conversationsList.adapter
        if (currentAdapter is ConversationsAdapter) {
            return currentAdapter
        }

        hideKeyboard()
        return ConversationsAdapter(
            activity = this,
            recyclerView = binding.conversationsList,
            onRefresh = { notifyDatasetChanged() },
            itemClick = { handleConversationClick(it) }
        ).apply {
            binding.conversationsList.adapter = this
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        val sortedConversations = conversations
            .sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }.thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>

        inboxConversations = sortedConversations
        if (!showingInbox()) {
            return
        }

        if (cached && config.appRunCount == 1) {
            // there are no cached conversations on the first run so we show the
            // loading placeholder and progress until we are done loading from telephony
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations) {
                    if (!cached) {
                        showOrHidePlaceholder(currentList.isEmpty())
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_found)
        binding.noConversationsPlaceholder2.beVisibleIf(show)
    }

    private fun fadeOutSearch() {
        binding.searchHolder.animate()
            .alpha(0f)
            .setDuration(SHORT_ANIMATION_DURATION)
            .withEndAction {
                binding.searchHolder.beGone()
                searchTextChanged("", true)
            }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    @SuppressLint("NewApi")
    private fun checkShortcut() {
        val appIconColor = config.appIconColor
        if (config.lastHandledShortcutColor != appIconColor) {
            val newConversation = getCreateNewContactShortcut(appIconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = appIconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable =
            AppCompatResources.getDrawable(this, org.fossify.commons.R.drawable.shortcut_plus)

        (drawable as LayerDrawable).findDrawableByLayerId(
            org.fossify.commons.R.id.shortcut_plus_background
        ).applyColorFilter(appIconColor)

        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    private fun searchTextChanged(text: String, forceUpdate: Boolean = false) {
        if (!binding.mainMenu.isSearchOpen && !forceUpdate) {
            return
        }

        lastSearchedText = text
        binding.searchPlaceholder2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                if (text == lastSearchedText) {
                    showSearchResults(messages, conversations, text)
                }
            }
        } else {
            binding.searchPlaceholder.beVisible()
            binding.searchResultsList.beGone()
        }
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        searchedText: String,
    ) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = (conversation.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = -1,
                title = conversation.title,
                snippet = conversation.phoneNumber,
                date = date,
                threadId = conversation.threadId,
                photoUri = conversation.photoUri
            )
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                val participantNames = message.participants.map { it.name }
                recipient = TextUtils.join(", ", participantNames)
            }

            val date = (message.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = message.id,
                title = recipient,
                snippet = message.body,
                date = date,
                threadId = message.threadId,
                photoUri = message.senderPhotoUri
            )
            searchResults.add(searchResult)
        }

        runOnUiThread {
            binding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
            binding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = binding.searchResultsList.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(this, searchResults, binding.searchResultsList, searchedText) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.apply {
                    binding.searchResultsList.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }

    private fun launchRecycleBin() {
        hideKeyboard()
        startActivity(Intent(applicationContext, RecycleBinConversationsActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_SMS_MMS or LICENSE_INDICATOR_FAST_SCROLL

        val faqItems = arrayListOf(
            FAQItem(
                title = R.string.faq_2_title,
                text = R.string.faq_2_text
            ),
            FAQItem(
                title = R.string.faq_3_title,
                text = R.string.faq_3_text
            ),
            FAQItem(
                title = R.string.faq_4_title,
                text = R.string.faq_4_text
            ),
            FAQItem(
                title = org.fossify.commons.R.string.faq_9_title_commons,
                text = org.fossify.commons.R.string.faq_9_text_commons
            )
        )

        if (resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)) {
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_2_title_commons,
                    text = org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    title = org.fossify.commons.R.string.faq_6_title_commons,
                    text = org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(@Suppress("unused") event: Events.RefreshConversations) {
        initMessenger()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshBlockedMessages(@Suppress("unused") event: Events.RefreshBlockedMessages) {
        refreshBlockedTab()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    companion object {
        private const val TAB_BADGE_CHARACTERS = 3
    }
}
