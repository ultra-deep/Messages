package org.fossify.messages.helpers

import android.content.Context
import android.telephony.PhoneNumberUtils
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.models.BlockedNumber

private const val PHONE_PUNCTUATION = " ().-"

/**
 * A blocked number made of digits, spaces, and phone punctuation is compared exactly.
 * Any other value is a case-insensitive regular expression, matched against the number,
 * the number with separators removed, and the digits only. An invalid expression matches nothing.
 */
fun Context.isBlockedSender(
    number: String,
    blockedNumbers: ArrayList<BlockedNumber> = getBlockedNumbers(),
): Boolean {
    if (number.isEmpty() || blockedNumbers.isEmpty()) {
        return false
    }

    val matchedPattern = blockedNumbers.any { blockedNumber ->
        val pattern = blockedNumber.number
        !isExactBlockedNumber(pattern) && matchesBlockedNumberPattern(number, pattern)
    }
    if (matchedPattern) {
        return true
    }

    val exactNumbers = blockedNumbers.filterTo(ArrayList()) { blockedNumber ->
        isExactBlockedNumber(blockedNumber.number)
    }
    return exactNumbers.isNotEmpty() && isNumberBlocked(number, exactNumbers)
}

private fun isExactBlockedNumber(value: String): Boolean {
    if (value.isEmpty()) {
        return true
    }
    val start = if (value[0] == '+') 1 else 0
    for (index in start until value.length) {
        val character = value[index]
        if (!character.isDigit() && character !in PHONE_PUNCTUATION) {
            return false
        }
    }
    return true
}

private fun matchesBlockedNumberPattern(number: String, pattern: String): Boolean {
    val strippedNumber = PhoneNumberUtils.stripSeparators(number).orEmpty()
    val digits = number.filter { it.isDigit() }
    return matchesAnyRegexForm(pattern, number, strippedNumber, digits)
}
