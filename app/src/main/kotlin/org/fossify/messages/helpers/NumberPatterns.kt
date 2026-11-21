package org.fossify.messages.helpers

import org.fossify.messages.models.Conversation
import org.fossify.messages.models.ConversationGroup

fun numberPatternsOf(raw: String): List<String> {
    return raw.split('\n', ',', ';')
        .map { it.trim() }
        .filter { pattern -> pattern.isNotEmpty() && isUsableNumberPattern(pattern) }
}

fun normalizeNumberPatterns(raw: String): String {
    return raw.split('\n', ',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("\n")
}

fun isUsableNumberPattern(pattern: String): Boolean {
    return pattern.any { it.isDigit() } && regexOrNull(pattern) != null
}

/**
 * [pattern] is a case-insensitive regular expression. It is searched in the number and,
 * separately, in the number's digits, so `989000` matches a number that contains those digits
 * and `^989000` matches a number whose digits start with them.
 */
fun matchesNumberPattern(number: String, pattern: String): Boolean {
    val digits = number.filter { it.isDigit() }
    return matchesAnyRegexForm(pattern, number, digits)
}

fun matchingPatternGroup(
    groups: List<ConversationGroup>,
    phoneNumber: String,
    groupId: Long = 0L,
    isArchived: Boolean = false,
    ignoreNumberPattern: Boolean = false,
): ConversationGroup? {
    if (groupId != 0L || isArchived || ignoreNumberPattern || phoneNumber.isBlank()) {
        return null
    }
    return groups.firstOrNull { group ->
        numberPatternsOf(group.numberPatterns).any { pattern ->
            matchesNumberPattern(phoneNumber, pattern)
        }
    }
}

fun List<Conversation>.excludingPatternGroups(
    groups: List<ConversationGroup>,
): ArrayList<Conversation> {
    val inbox = ArrayList<Conversation>()
    for (conversation in this) {
        val matchedGroup = matchingPatternGroup(
            groups = groups,
            phoneNumber = conversation.phoneNumber,
            groupId = conversation.groupId,
            isArchived = conversation.isArchived,
            ignoreNumberPattern = conversation.ignoreNumberPattern,
        )
        if (matchedGroup == null) {
            inbox.add(conversation)
        }
    }
    return inbox
}

fun List<Conversation>.patternGroupCopies(groups: List<ConversationGroup>): List<Conversation> {
    return mapNotNull { conversation ->
        val group = matchingPatternGroup(
            groups = groups,
            phoneNumber = conversation.phoneNumber,
            groupId = conversation.groupId,
            isArchived = conversation.isArchived,
            ignoreNumberPattern = conversation.ignoreNumberPattern,
        ) ?: return@mapNotNull null
        conversation.copy(groupId = group.id)
    }
}
