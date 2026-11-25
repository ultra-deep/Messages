package org.fossify.messages.helpers

import org.fossify.messages.models.ConversationGroup

fun messagePatternsOf(raw: String): List<String> {
    return raw.lineSequence()
        .map { it.trim() }
        .filter { pattern -> pattern.isNotEmpty() && isUsableMessagePattern(pattern) }
        .toList()
}

fun normalizeMessagePatterns(raw: String): String {
    return raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("\n")
}

fun isUsableMessagePattern(pattern: String): Boolean {
    return regexOrNull(pattern) != null
}

/**
 * Group whose message pattern matches [body].
 * A pattern is a case-insensitive regular expression searched in the message text.
 * The longest matching pattern wins. When two patterns are the same length, the earlier group wins.
 */
fun matchingMessagePatternGroup(
    groups: List<ConversationGroup>,
    body: String,
): ConversationGroup? {
    if (body.isBlank()) {
        return null
    }
    var bestGroup: ConversationGroup? = null
    var bestScore = 0
    var bestGroupIndex = Int.MAX_VALUE
    groups.forEachIndexed { groupIndex, group ->
        val score = groupMessagePatternScore(group, body)
        if (score <= 0) {
            return@forEachIndexed
        }
        val better = score > bestScore || (score == bestScore && groupIndex < bestGroupIndex)
        if (better) {
            bestGroup = group
            bestScore = score
            bestGroupIndex = groupIndex
        }
    }
    return bestGroup
}

private fun groupMessagePatternScore(group: ConversationGroup, body: String): Int {
    var best = 0
    for (pattern in messagePatternsOf(group.messagePatterns)) {
        if (!matchesAnyRegexForm(pattern, body)) {
            continue
        }
        if (pattern.length > best) {
            best = pattern.length
        }
    }
    return best
}
