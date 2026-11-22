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
 * `0912…` and `+98912…` are treated as the same number, so either pattern can match both.
 */
fun matchesNumberPattern(number: String, pattern: String): Boolean {
    return patternMatchesNumber(pattern, number)
}

/**
 * Group for an ungrouped conversation.
 * [priorityNumbers] come first (the sender of the new message). The pattern that contains
 * more digits wins, so a shorter pattern on another number cannot take the badge. When two
 * patterns are equally specific, the sender's group wins.
 */
fun matchingPatternGroup(
    groups: List<ConversationGroup>,
    phoneNumber: String,
    groupId: Long = 0L,
    isArchived: Boolean = false,
    ignoreNumberPattern: Boolean = false,
    priorityNumbers: List<String> = emptyList(),
): ConversationGroup? {
    if (groupId != 0L || isArchived || ignoreNumberPattern) {
        return null
    }
    val numbers = ArrayList<String>(priorityNumbers.size + 1)
    priorityNumbers.forEach { number ->
        if (number.isNotBlank()) {
            numbers.add(number)
        }
    }
    if (phoneNumber.isNotBlank()) {
        numbers.add(phoneNumber)
    }
    return bestPatternGroup(groups, numbers)
}

fun List<Conversation>.excludingPatternGroups(
    groups: List<ConversationGroup>,
    priorityNumbers: Map<Long, String> = emptyMap(),
): ArrayList<Conversation> {
    val inbox = ArrayList<Conversation>()
    for (conversation in this) {
        val matchedGroup = matchingPatternGroup(
            groups = groups,
            phoneNumber = conversation.phoneNumber,
            groupId = conversation.groupId,
            isArchived = conversation.isArchived,
            ignoreNumberPattern = conversation.ignoreNumberPattern,
            priorityNumbers = listOfNotNull(priorityNumbers[conversation.threadId]),
        )
        if (matchedGroup == null) {
            inbox.add(conversation)
        }
    }
    return inbox
}

fun List<Conversation>.patternGroupCopies(
    groups: List<ConversationGroup>,
    priorityNumbers: Map<Long, String> = emptyMap(),
): List<Conversation> {
    return mapNotNull { conversation ->
        val group = matchingPatternGroup(
            groups = groups,
            phoneNumber = conversation.phoneNumber,
            groupId = conversation.groupId,
            isArchived = conversation.isArchived,
            ignoreNumberPattern = conversation.ignoreNumberPattern,
            priorityNumbers = listOfNotNull(priorityNumbers[conversation.threadId]),
        ) ?: return@mapNotNull null
        conversation.copy(groupId = group.id)
    }
}

private fun bestPatternGroup(
    groups: List<ConversationGroup>,
    numbers: List<String>,
): ConversationGroup? {
    var bestGroup: ConversationGroup? = null
    var bestScore = 0
    var bestNumberIndex = Int.MAX_VALUE
    var bestGroupIndex = Int.MAX_VALUE
    numbers.forEachIndexed { numberIndex, rawNumber ->
        val number = rawNumber.trim()
        if (number.isEmpty()) {
            return@forEachIndexed
        }
        groups.forEachIndexed { groupIndex, group ->
            val score = groupPatternScore(group, number)
            if (score <= 0) {
                return@forEachIndexed
            }
            val better = score > bestScore ||
                (score == bestScore && numberIndex < bestNumberIndex) ||
                (score == bestScore && numberIndex == bestNumberIndex && groupIndex < bestGroupIndex)
            if (better) {
                bestGroup = group
                bestScore = score
                bestNumberIndex = numberIndex
                bestGroupIndex = groupIndex
            }
        }
    }
    return bestGroup
}

private fun groupPatternScore(group: ConversationGroup, number: String): Int {
    var best = 0
    for (pattern in numberPatternsOf(group.numberPatterns)) {
        if (!patternMatchesNumber(pattern, number)) {
            continue
        }
        val score = pattern.count { it.isDigit() }
        if (score > best) {
            best = score
        }
    }
    return best
}

private fun patternMatchesNumber(pattern: String, number: String): Boolean {
    if (number.isBlank()) {
        return false
    }
    val digits = number.filter { it.isDigit() }
    if (matchesAnyRegexForm(pattern, number, digits)) {
        return true
    }
    val alternates = alternateDialForms(digits)
    if (alternates.isEmpty()) {
        return false
    }
    return matchesAnyRegexForm(pattern, *alternates)
}

/**
 * Local `0…` and international `98…` forms of the same subscriber number.
 * Short codes stay unchanged so a broad prefix cannot absorb them.
 */
private fun alternateDialForms(digits: String): Array<String> {
    var national = digits
    if (national.startsWith("00") && national.length > 4) {
        national = national.drop(2)
    }
    national = when {
        national.startsWith("98") && national.length > 10 -> national.drop(2)
        national.startsWith("0") && national.length > 9 -> national.drop(1)
        else -> return emptyArray()
    }
    if (national.startsWith("0") && national.length > 9) {
        national = national.drop(1)
    }
    if (national.length !in 8..12) {
        return emptyArray()
    }
    return arrayOf("0$national", national, "98$national")
}
