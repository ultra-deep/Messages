package org.fossify.messages.helpers

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private const val REGEX_MATCH_FLAGS =
    Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.DOTALL

/**
 * Compiles [pattern] as a case-insensitive regular expression.
 * `.` matches any character, including a line break. Returns null when [pattern] is empty or invalid.
 */
fun regexOrNull(pattern: String): Regex? {
    if (pattern.isEmpty()) {
        return null
    }
    return try {
        Pattern.compile(pattern, REGEX_MATCH_FLAGS).toRegex()
    } catch (_: PatternSyntaxException) {
        null
    }
}

fun matchesAnyRegexForm(pattern: String, vararg texts: String): Boolean {
    val regex = regexOrNull(pattern) ?: return false
    return texts.any { text -> text.isNotEmpty() && regex.containsMatchIn(text) }
}
