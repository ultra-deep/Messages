package org.fossify.messages.helpers

const val TAB_MESSAGES = "messages"
const val TAB_BLOCKED = "blocked"
const val TAB_ARCHIVED = "archived"

private const val TAB_GROUP_PREFIX = "g:"

sealed class ConversationTab {
    abstract val key: String

    data object Messages : ConversationTab() {
        override val key = TAB_MESSAGES
    }

    data object Blocked : ConversationTab() {
        override val key = TAB_BLOCKED
    }

    data object Archived : ConversationTab() {
        override val key = TAB_ARCHIVED
    }

    data class Group(val id: Long) : ConversationTab() {
        override val key = "$TAB_GROUP_PREFIX$id"
    }

    companion object {
        fun fromKey(key: String): ConversationTab? {
            return when (key) {
                TAB_MESSAGES -> Messages
                TAB_BLOCKED -> Blocked
                TAB_ARCHIVED -> Archived
                else -> {
                    if (!key.startsWith(TAB_GROUP_PREFIX)) {
                        null
                    } else {
                        key.removePrefix(TAB_GROUP_PREFIX).toLongOrNull()?.let { Group(it) }
                    }
                }
            }
        }
    }
}

fun resolveConversationTabs(
    stored: String,
    groupIdsInOrder: List<Long>,
    showBlocked: Boolean,
    showArchived: Boolean,
    includeHidden: Boolean,
): List<ConversationTab> {
    val catalog = buildList {
        add(TAB_MESSAGES)
        add(TAB_BLOCKED)
        add(TAB_ARCHIVED)
        groupIdsInOrder.forEach { add("$TAB_GROUP_PREFIX$it") }
    }
    val available = catalog.filter { key ->
        when (key) {
            TAB_BLOCKED -> includeHidden || showBlocked
            TAB_ARCHIVED -> includeHidden || showArchived
            else -> true
        }
    }
    val availableKeys = available.toSet()
    val used = mutableSetOf<String>()
    val orderedKeys = mutableListOf<String>()
    stored.split(',')
        .map { it.trim() }
        .filter { key -> key.isNotEmpty() && key in availableKeys && used.add(key) }
        .forEach { orderedKeys.add(it) }
    available.forEach { key ->
        if (used.add(key)) {
            orderedKeys.add(key)
        }
    }
    return orderedKeys.mapNotNull { ConversationTab.fromKey(it) }
}

fun encodeConversationTabs(tabs: List<ConversationTab>): String {
    return tabs.joinToString(",") { it.key }
}
