package org.fossify.messages.helpers

/**
 * A keyword with no * or ? matches when the body contains it.
 * Otherwise * is any text and ? is one character, and the pattern must match the whole body,
 * the same way an MS-DOS file name pattern matches a file name. Matching ignores case.
 */
fun matchesBlockedKeyword(body: String, keyword: String): Boolean {
    if ('*' !in keyword && '?' !in keyword) {
        return body.contains(keyword, ignoreCase = true)
    }
    return matchesDosPattern(body, keyword)
}

internal fun matchesDosPattern(text: String, pattern: String): Boolean {
    val patternLength = pattern.length
    var next = BooleanArray(patternLength + 1)
    next[patternLength] = true
    for (patternIndex in patternLength - 1 downTo 0) {
        if (pattern[patternIndex] == '*') {
            next[patternIndex] = next[patternIndex + 1]
        } else {
            break
        }
    }

    for (textIndex in text.length - 1 downTo 0) {
        val current = BooleanArray(patternLength + 1)
        for (patternIndex in patternLength - 1 downTo 0) {
            current[patternIndex] = when (val patternChar = pattern[patternIndex]) {
                '*' -> current[patternIndex + 1] || next[patternIndex]
                '?' -> next[patternIndex + 1]
                else -> text[textIndex].equals(patternChar, ignoreCase = true) &&
                    next[patternIndex + 1]
            }
        }
        next = current
    }
    return next[0]
}
