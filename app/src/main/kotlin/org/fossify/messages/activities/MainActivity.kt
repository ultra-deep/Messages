package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.setSystemBarsAppearance
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
import org.fossify.commons.views.MyTextView
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
import org.fossify.messages.extensions.countUnreadBlockedMessages
import org.fossify.messages.extensions.peekBlockedConversations
import org.fossify.messages.extensions.peekUnreadBlockedCount
import org.fossify.messages.extensions.reloadBlockedConversations
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.getUnreadCountsByThread
import org.fossify.messages.extensions.unreadBlockedBadgeText
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.ConversationTab
import org.fossify.messages.helpers.excludingPatternGroups
import org.fossify.messages.helpers.patternGroupCopies
import org.fossify.messages.helpers.resolveConversationTabs
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
import org.fossify.messages.models.toBadgeConversation
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
    private var blockedRefreshList = false
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
    private var visibleTabs: List<ConversationTab> = emptyList()
    private var groupedConversations = emptyList<Conversation>()
    private var inboxConversations = ArrayList<Conversation>()
    private var archivedConversations = ArrayList<Conversation>()
    private var inboxUnreadCount = 0
    private var groupedUnreadCount = 0
    private var knownBlockedUnreadCount: Int? = null
    private var knownArchivedUnreadCount: Int? = null
    private var knownGroupUnreadCounts = emptyMap<Long, Int>()
    private val pendingTabBadges = HashMap<Int, TabBadge>()
    private var unreadTotalButton: FrameLayout? = null
    private var unreadTotalIcon: ImageView? = null
    private var unreadTotalBadge: MyTextView? = null

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
        applyConversationListBackground()
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

        setupUnreadTotalBadge()

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
        binding.conversationsTabs.clipChildren = false
        binding.conversationsTabs.clipToPadding = false
        (binding.conversationsTabs.parent as? ViewGroup)?.clipChildren = false
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

    private fun blockedTabIndex(): Int = visibleTabs.indexOf(ConversationTab.Blocked)

    private fun archivedTabIndex(): Int = visibleTabs.indexOf(ConversationTab.Archived)

    private fun messagesTabIndex(): Int {
        val index = visibleTabs.indexOf(ConversationTab.Messages)
        return if (index >= 0) index else 0
    }

    private fun groupTabIndex(groupId: Long): Int {
        return visibleTabs.indexOfFirst { tab ->
            tab is ConversationTab.Group && tab.id == groupId
        }
    }

    private fun applyConversationTabVisibility() {
        val showBlocked = config.showBlockedMessagesTab
        val showArchived = config.showArchivedMessagesTab
        val visibilityUnchanged = showBlocked == appliedShowBlockedTab &&
            showArchived == appliedShowArchivedTab &&
            binding.conversationsTabs.tabCount > 0
        if (visibilityUnchanged) {
            openBlockedDespiteHiddenTab = false
            rebuildConversationTabs(conversationGroups)
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

        rebuildConversationTabs(conversationGroups)
        if (leaveBlockedView || leaveArchivedView) {
            showConversationTab(messagesTabIndex())
        }
    }

    private fun rebuildConversationTabs(groups: List<ConversationGroup>) {
        val nextTabs = resolveConversationTabs(
            stored = config.conversationTabOrder,
            groupIdsInOrder = groups.map { it.id },
            showBlocked = config.showBlockedMessagesTab,
            showArchived = config.showArchivedMessagesTab,
            includeHidden = false,
        )
        val nextSignature = tabSignature(nextTabs, groups)
        val currentSignature = tabSignature(visibleTabs, conversationGroups)
        conversationGroups = groups
        if (
            nextSignature == currentSignature &&
            binding.conversationsTabs.tabCount == nextTabs.size
        ) {
            visibleTabs = nextTabs
            return
        }

        visibleTabs = nextTabs
        pendingTabBadges.clear()
        ignoreTabSelection = true
        try {
            val tabs = binding.conversationsTabs
            tabs.removeAllTabs()
            nextTabs.forEach { tab ->
                tabs.addTab(tabs.newTab().setText(tabLabel(tab, groups)), false)
            }
            updateTabMode()
            styleConversationTabs()
            restoreSelectedTab()
            reapplyKnownTabBadges()
            applyConversationListBackground()
        } finally {
            ignoreTabSelection = false
        }
    }

    private fun tabLabel(tab: ConversationTab, groups: List<ConversationGroup>): String {
        return when (tab) {
            ConversationTab.Messages -> getString(R.string.messages)
            ConversationTab.Blocked -> getString(R.string.blocked)
            ConversationTab.Archived -> getString(R.string.archived_conversations)
            is ConversationTab.Group -> groups.firstOrNull { it.id == tab.id }?.name.orEmpty()
        }
    }

    private fun tabSignature(
        tabs: List<ConversationTab>,
        groups: List<ConversationGroup>,
    ): List<String> {
        return tabs.map { tab ->
            when (tab) {
                is ConversationTab.Group -> {
                    val name = groups.firstOrNull { it.id == tab.id }?.name.orEmpty()
                    "${tab.key}:$name"
                }
                else -> tab.key
            }
        }
    }

    private fun styleConversationTabs() {
        val primaryColor = getProperPrimaryColor()
        binding.conversationsTabs.setSelectedTabIndicatorColor(primaryColor)
        binding.conversationsTabs.setTabTextColors(getProperTextColor(), primaryColor)
        binding.conversationsTabs.setBackgroundColor(getProperBackgroundColor())
    }

    private fun conversationListBackgroundColor(): Int {
        return when {
            showingBlockedTab -> config.blockedMessagesBackgroundColor
            showingArchivedTab -> config.archivedMessagesBackgroundColor
            selectedGroupId != 0L -> {
                conversationGroups.firstOrNull { it.id == selectedGroupId }?.backgroundColor
                    ?: Color.TRANSPARENT
            }
            else -> Color.TRANSPARENT
        }
    }

    private fun applyConversationListBackground() {
        val color = conversationListBackgroundColor()
        val resolved = if (color == Color.TRANSPARENT) {
            getProperBackgroundColor()
        } else {
            color
        }
        val useThemeColors = color == Color.TRANSPARENT
        val contentColor = if (useThemeColors) getProperTextColor() else color.getContrastColor()
        binding.mainHolder.setBackgroundColor(resolved)
        binding.conversationsFastscroller.setBackgroundColor(resolved)
        binding.conversationsList.setBackgroundColor(resolved)
        binding.noConversationsPlaceholder.setTextColor(contentColor)
        (binding.conversationsList.adapter as? BaseConversationsAdapter)?.apply {
            updateListBackground(color)
            updateUnreadBadgeColor(currentTabBadgeColor())
        }
        applyTopChromeBackground(resolved, contentColor, useThemeColors)
    }

    private fun applyTopChromeBackground(
        backgroundColor: Int,
        contentColor: Int,
        useThemeColors: Boolean,
    ) {
        val selectedTabColor = if (useThemeColors) getProperPrimaryColor() else contentColor
        val normalTabColor = if (useThemeColors) contentColor else contentColor.adjustAlpha(0.7f)
        binding.conversationsTabs.setBackgroundColor(backgroundColor)
        binding.conversationsTabs.setTabTextColors(normalTabColor, selectedTabColor)
        binding.conversationsTabs.setSelectedTabIndicatorColor(selectedTabColor)
        if (useThemeColors) {
            binding.mainMenu.updateColors()
        } else {
            binding.mainMenu.setBackgroundColor(backgroundColor)
            binding.mainMenu.findViewById<View>(org.fossify.commons.R.id.toolbar_container)
                ?.background?.applyColorFilter(searchBoxColor(backgroundColor))
            binding.mainMenu.findViewById<ImageView>(org.fossify.commons.R.id.top_toolbar_search_icon)
                ?.applyColorFilter(contentColor)
            binding.mainMenu.findViewById<EditText>(org.fossify.commons.R.id.top_toolbar_search)?.apply {
                setTextColor(contentColor)
                setHintTextColor(contentColor.adjustAlpha(0.5f))
            }
            val toolbar = binding.mainMenu.requireToolbar()
            toolbar.setTitleTextColor(contentColor)
            toolbar.overflowIcon?.setTint(contentColor)
            toolbar.navigationIcon?.setTint(contentColor)
            updateMenuItemColors(toolbar.menu, backgroundColor, false)
        }
        updateBackgroundColor(backgroundColor)
        window.setSystemBarsAppearance(backgroundColor)
        styleUnreadTotalIcon(contentColor)
        styleUnreadTotalBadge()
    }

    private fun searchBoxColor(tabBackground: Int): Int {
        val whiteAmount = 0.3f
        val keep = 1f - whiteAmount
        return Color.rgb(
            (Color.red(tabBackground) * keep + 255f * whiteAmount).toInt(),
            (Color.green(tabBackground) * keep + 255f * whiteAmount).toInt(),
            (Color.blue(tabBackground) * keep + 255f * whiteAmount).toInt(),
        )
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
        applyConversationListBackground()
    }

    private fun showingInbox() = !showingBlockedTab && !showingArchivedTab && selectedGroupId == 0L

    private fun groupIdForTab(position: Int): Long {
        return (visibleTabs.getOrNull(position) as? ConversationTab.Group)?.id ?: 0L
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
        knownBlockedUnreadCount = count
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
        peekUnreadBlockedCount()?.let { updateBlockedTabBadge(it) }
        val reloadList = showingBlockedTab
        synchronized(blockedRefreshLock) {
            if (blockedRefreshRunning) {
                blockedRefreshAgain = true
                if (reloadList) {
                    blockedRefreshList = true
                }
                return
            }
            blockedRefreshRunning = true
            blockedRefreshList = reloadList
        }
        ensureBackgroundThread {
            do {
                val reloadListNow = synchronized(blockedRefreshLock) {
                    blockedRefreshAgain = false
                    val pendingList = blockedRefreshList
                    blockedRefreshList = false
                    pendingList
                }
                val fastCount = try {
                    countUnreadBlockedMessages()
                } catch (_: Exception) {
                    null
                }
                if (fastCount != null) {
                    runOnUiThread {
                        if (!isDestroyed && !isFinishing) {
                            updateBlockedTabBadge(fastCount)
                        }
                    }
                }
                if (reloadListNow) {
                    val conversations = try {
                        reloadBlockedConversations()
                    } catch (_: Exception) {
                        arrayListOf()
                    }
                    val count = conversations.sumOf { it.unreadCount }
                    runOnUiThread {
                        if (isDestroyed || isFinishing) {
                            return@runOnUiThread
                        }
                        updateBlockedTabBadge(count)
                        if (showingBlockedTab) {
                            showBlockedConversations(conversations)
                        }
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
            applyConversationListBackground()
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
            applyConversationListBackground()
            updateConversations(conversations)
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
    }

    private fun refreshArchivedTab() {
        ensureBackgroundThread {
            val storedCount = try {
                conversationsDB.getArchivedUnreadBadgeCount().toInt()
            } catch (_: Exception) {
                null
            }
            if (storedCount != null && storedCount > 0) {
                runOnUiThread {
                    if (!isDestroyed && !isFinishing) {
                        updateArchivedTabBadge(storedCount)
                    }
                }
            }
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
            applyConversationListBackground()
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
            applyConversationListBackground()
            updateConversations(sortedConversations)
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
    }

    private fun updateArchivedTabBadge(count: Int) {
        knownArchivedUnreadCount = count
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

    private fun setupUnreadTotalBadge() {
        if (unreadTotalButton != null) {
            return
        }
        val searchBar = binding.mainMenu.findViewById<FrameLayout>(
            org.fossify.commons.R.id.search_bar_container
        ) ?: return
        val buttonSize = unreadTotalButtonSize()
        val badgeHeight = resources.getDimensionPixelSize(R.dimen.small_icon_size)
        val badgeMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        val iconInset = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_message_outline_vector)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPaddingRelative(iconInset, iconInset * 2, iconInset * 2, iconInset)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val badge = MyTextView(this).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            minWidth = badgeHeight
            minimumHeight = badgeHeight
            maxLines = 1
            setPaddingRelative(badgeMargin, 0, badgeMargin, 0)
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(org.fossify.commons.R.dimen.list_tertiary_text_size),
            )
            background = AppCompatResources.getDrawable(
                this@MainActivity,
                org.fossify.commons.R.drawable.circle_background,
            )?.mutate()
            visibility = View.GONE
        }
        val ripple = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        val button = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            contentDescription = getString(R.string.go_to_last_message)
            foreground = AppCompatResources.getDrawable(this@MainActivity, ripple.resourceId)
            setOnClickListener { scrollToLastConversationInCurrentTab() }
            addView(
                icon,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                badge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    badgeHeight,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                },
            )
        }
        searchBar.clipChildren = false
        searchBar.addView(
            button,
            FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            },
        )
        unreadTotalButton = button
        unreadTotalIcon = icon
        unreadTotalBadge = badge
        styleUnreadTotalIcon(getProperTextColor())
        styleUnreadTotalBadge()
        updateUnreadTotalBadge()
    }

    private fun unreadTotalButtonSize(): Int {
        val typedValue = TypedValue()
        val actionBarSize = if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
        } else {
            resources.getDimensionPixelSize(org.fossify.commons.R.dimen.normal_icon_size)
        }
        val verticalPadding = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin) * 2
        return (actionBarSize - verticalPadding).coerceAtLeast(
            resources.getDimensionPixelSize(R.dimen.small_icon_size),
        )
    }

    private fun styleUnreadTotalIcon(color: Int) {
        unreadTotalIcon?.applyColorFilter(color)
    }

    private fun styleUnreadTotalBadge() {
        val badge = unreadTotalBadge ?: return
        badge.setTypeface(FontHelper.getTypeface(this), Typeface.BOLD)
        badge.setTextColor(Color.WHITE)
        badge.background?.applyColorFilter(Color.RED)
    }

    private fun updateMessagesTabBadge() {
        applyTabBadge(messagesTabIndex(), inboxUnreadCount, Color.RED)
    }

    private fun groupTabBadgeColor(group: ConversationGroup): Int {
        return if (group.showNotification) Color.RED else getProperPrimaryColor()
    }

    private fun currentTabBadgeColor(): Int {
        if (showingBlockedTab || showingArchivedTab) {
            return getProperPrimaryColor()
        }
        val group = conversationGroups.firstOrNull { it.id == selectedGroupId }
        return if (group == null) Color.RED else groupTabBadgeColor(group)
    }

    private fun updateUnreadTotalBadge() {
        updateMessagesTabBadge()
        val button = unreadTotalButton ?: return
        val badge = unreadTotalBadge ?: return
        val total = inboxUnreadCount + groupedUnreadCount
        val hasUnread = total > 0
        button.beVisibleIf(hasUnread)
        updateUnreadTotalSearchInset(hasUnread)
        if (!hasUnread) {
            return
        }
        badge.text = unreadBlockedBadgeText(total)
        styleUnreadTotalBadge()
        badge.beVisible()
        button.contentDescription = getString(R.string.go_to_last_message) + ", " + resources.getQuantityString(
            R.plurals.unread_messages_count,
            total,
            total,
        )
    }

    private fun updateUnreadTotalSearchInset(reserveSpace: Boolean) {
        val searchBox = binding.mainMenu.findViewById<View>(
            org.fossify.commons.R.id.toolbar_container
        ) ?: return
        val searchParams = searchBox.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val margin = if (reserveSpace) {
            val gap = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
            unreadTotalButtonSize() + gap
        } else {
            0
        }
        if (searchParams.leftMargin == margin) {
            return
        }
        searchParams.leftMargin = margin
        searchBox.layoutParams = searchParams
    }

    private fun scrollToLastConversationInCurrentTab() {
        val list = binding.conversationsList
        val lastIndex = (list.adapter?.itemCount ?: 0) - 1
        if (lastIndex < 0) {
            return
        }
        list.stopScroll()
        list.smoothScrollToPosition(lastIndex)
    }

    private fun applyTabBadge(
        index: Int,
        count: Int,
        backgroundColor: Int = getProperPrimaryColor(),
    ) {
        val tab = binding.conversationsTabs.getTabAt(index) ?: return
        pendingTabBadges[index] = TabBadge(count, backgroundColor)
        drawTabBadge(tab, count, backgroundColor)
        val tabView = tab.view
        if (!tabView.isLaidOut || tabView.width == 0) {
            tabView.post { drawPendingTabBadge(index) }
        }
    }

    private fun drawPendingTabBadge(index: Int) {
        if (isDestroyed || isFinishing) {
            return
        }
        val pending = pendingTabBadges[index] ?: return
        val tab = binding.conversationsTabs.getTabAt(index) ?: return
        drawTabBadge(tab, pending.count, pending.backgroundColor)
    }

    private fun drawTabBadge(tab: TabLayout.Tab, count: Int, backgroundColor: Int) {
        if (count <= 0) {
            tab.removeBadge()
            return
        }
        tab.orCreateBadge.apply {
            maxCharacterCount = TAB_BADGE_CHARACTERS
            number = count
            setVisible(true)
            setTextAppearance(R.style.TextAppearance_Messages_TabBadge)
            this.backgroundColor = backgroundColor
            badgeTextColor = backgroundColor.getContrastColor()
        }
    }

    private fun reapplyKnownTabBadges() {
        updateMessagesTabBadge()
        knownBlockedUnreadCount?.let { updateBlockedTabBadge(it) }
        knownArchivedUnreadCount?.let { updateArchivedTabBadge(it) }
        if (knownGroupUnreadCounts.isEmpty()) {
            return
        }
        conversationGroups.forEach { group ->
            val count = knownGroupUnreadCounts[group.id] ?: return@forEach
            val index = groupTabIndex(group.id)
            if (index >= 0) {
                applyTabBadge(index, count, groupTabBadgeColor(group))
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
            val quickBadges = try {
                loadQuickGroupBadges()
            } catch (_: Exception) {
                null
            }
            if (quickBadges != null) {
                runOnUiThread {
                    if (isDestroyed || isFinishing || generation != groupRefreshGeneration) {
                        return@runOnUiThread
                    }
                    syncGroupTabs(
                        groups = quickBadges.groups,
                        unreadCounts = quickBadges.unreadCounts,
                        clearEmptyBadges = false,
                    )
                }
            }
            val state = loadConversationGroupState()
            val unreadCounts = state.grouped.groupBy { it.groupId }.mapValues { (_, conversations) ->
                conversations.sumOf { it.unreadCount }
            }
            runOnUiThread {
                if (isDestroyed || isFinishing || generation != groupRefreshGeneration) {
                    return@runOnUiThread
                }
                groupsLoaded = true
                groupedConversations = state.grouped
                if (selectGroupId != 0L) {
                    selectedGroupId = selectGroupId
                    showingBlockedTab = false
                    showingArchivedTab = false
                }
                rememberInbox(state.inbox)
                syncGroupTabs(state.groups, unreadCounts)
                if (selectedGroupId != 0L && !showingBlockedTab && !showingArchivedTab) {
                    showGroupConversations(
                        ArrayList(state.grouped.filter { it.groupId == selectedGroupId })
                    )
                }
                binding.conversationsFab.beVisibleIf(showingInbox())
                refreshMenuItems()
            }
        }
    }

    private fun loadConversationGroupState(): ConversationGroupState {
        val groups = try {
            conversationGroupsDB.getAll()
        } catch (_: Exception) {
            emptyList()
        }
        val explicit = try {
            conversationsDB.getGrouped()
        } catch (_: Exception) {
            emptyList()
        }
        val ungrouped = try {
            conversationsDB.getNonArchived()
        } catch (_: Exception) {
            emptyList()
        }
        val unreadMap = try {
            getUnreadCountsByThread()
        } catch (_: Exception) {
            null
        }
        val explicitUi = explicit.withLiveUnreadCounts(unreadMap)
        val ungroupedUi = ungrouped.withLiveUnreadCounts(unreadMap)
        return ConversationGroupState(
            groups = groups,
            grouped = explicitUi + ungroupedUi.patternGroupCopies(groups),
            inbox = ungroupedUi.excludingPatternGroups(groups),
        )
    }

    private fun List<Conversation>.withLiveUnreadCounts(
        unreadMap: Map<Long, Int>?,
    ): List<Conversation> {
        return map { conversation ->
            if (unreadMap == null) {
                conversation.withVisibleUnreadCount()
            } else {
                conversation.withVisibleUnreadCount(unreadMap[conversation.threadId] ?: 0)
            }
        }
    }

    private fun loadQuickGroupBadges(): QuickGroupBadges {
        val groups = conversationGroupsDB.getAll()
        val conversations = conversationsDB.getBadgeRows().map { it.toBadgeConversation() }
        val explicit = conversations.filter { !it.isArchived && it.groupId != 0L }
        val ungrouped = conversations.filter { !it.isArchived && it.groupId == 0L }
        val grouped = explicit + ungrouped.patternGroupCopies(groups)
        val unreadCounts = grouped.groupBy { it.groupId }.mapValues { (_, items) ->
            items.sumOf { it.withVisibleUnreadCount().unreadCount }
        }
        return QuickGroupBadges(groups, unreadCounts)
    }

    private fun syncGroupTabs(
        groups: List<ConversationGroup>,
        unreadCounts: Map<Long, Int>,
        clearEmptyBadges: Boolean = true,
    ) {
        if (clearEmptyBadges) {
            knownGroupUnreadCounts = unreadCounts
        }
        rebuildConversationTabs(groups)
        groups.forEach { group ->
            val count = unreadCounts[group.id] ?: 0
            if (count > 0 || clearEmptyBadges) {
                val index = groupTabIndex(group.id)
                if (index >= 0) {
                    applyTabBadge(index, count, groupTabBadgeColor(group))
                }
            }
        }
        if (clearEmptyBadges) {
            groupedUnreadCount = groups.sumOf { group ->
                if (group.showNotification) unreadCounts[group.id] ?: 0 else 0
            }
            updateUnreadTotalBadge()
        }
    }

    private fun updateTabMode() {
        val scrollable = visibleTabs.any { it is ConversationTab.Group }
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
        if (selectedGroupId != 0L) {
            val groupIndex = groupTabIndex(selectedGroupId)
            if (groupIndex >= 0) {
                return groupIndex
            }
        }
        selectedGroupId = 0L
        return messagesTabIndex()
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
            applyConversationListBackground()
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
            applyConversationListBackground()
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
            applyConversationListBackground()
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
        ) { form ->
            ensureBackgroundThread {
                val id = createConversationGroup(form)
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
            initialBackgroundColor = group.backgroundColor,
            initialShowNotification = group.showNotification,
            initialNotificationSound = group.notificationSound,
            initialNotificationVibration = group.notificationVibration,
            initialNumberPatterns = group.numberPatterns,
            titleRes = R.string.rename_group,
            isNameTaken = { name ->
                conversationGroups.any { it.id != group.id && it.name.equals(name, ignoreCase = true) }
            },
        ) { form ->
            ensureBackgroundThread {
                renameConversationGroup(group.id, form)
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
                    selectConversationTab(messagesTabIndex())
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
            val groups = try {
                conversationGroupsDB.getAll()
            } catch (_: Exception) {
                emptyList()
            }
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
                setupConversations(conversations.excludingPatternGroups(groups), cached = true)
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

            val groups = try {
                conversationGroupsDB.getAll()
            } catch (_: Exception) {
                emptyList()
            }
            val allConversations = conversationsDB.getNonArchived().excludingPatternGroups(groups)
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

    private fun rememberInbox(conversations: ArrayList<Conversation>) {
        val sortedConversations = conversations
            .sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }.thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>
        inboxConversations = sortedConversations
        inboxUnreadCount = sortedConversations.sumOf { it.unreadCount }
        updateUnreadTotalBadge()
        if (!showingInbox()) {
            return
        }
        val adapter = binding.conversationsList.adapter as? ConversationsAdapter ?: return
        adapter.updateConversations(sortedConversations)
        showOrHidePlaceholder(sortedConversations.isEmpty())
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
        inboxUnreadCount = sortedConversations.sumOf { it.unreadCount }
        updateUnreadTotalBadge()
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
                applyConversationListBackground()
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

    private data class TabBadge(val count: Int, val backgroundColor: Int)

    private data class QuickGroupBadges(
        val groups: List<ConversationGroup>,
        val unreadCounts: Map<Long, Int>,
    )

    private data class ConversationGroupState(
        val groups: List<ConversationGroup>,
        val grouped: List<Conversation>,
        val inbox: ArrayList<Conversation>,
    )

    companion object {
        private const val TAB_BADGE_CHARACTERS = 3
    }
}
