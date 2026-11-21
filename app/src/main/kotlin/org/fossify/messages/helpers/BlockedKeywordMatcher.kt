package org.fossify.messages.helpers

/**
 * A keyword is a case-insensitive regular expression searched in the message body.
 * `^` and `$` anchor the start and end. An invalid expression matches nothing.
 */
fun matchesBlockedKeyword(body: String, keyword: String): Boolean {
    return matchesAnyRegexForm(keyword, body)
}
