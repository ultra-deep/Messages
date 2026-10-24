package org.fossify.messages.helpers

import android.content.Context
import android.telephony.PhoneNumberUtils
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.models.BlockedNumber

/**
 * A blocked number with * or ? is matched like a blocked keyword: * is any text, ? is one
 * character, and the pattern must match the whole number. Other blocked numbers keep the
 * existing exact comparison.
 */
fun Context.isBlockedSender(
    number: String,
    blockedNumbers: ArrayList<BlockedNumber> = getBlockedNumbers(),
): Boolean {
    if (number.isEmpty() || blockedNumbers.isEmpty()) {
        return false
    }

    val strippedNumber = PhoneNumberUtils.stripSeparators(number) ?: ""
    val matchedPattern = blockedNumbers.any { blockedNumber ->
        val pattern = blockedNumber.number
        if ('*' !in pattern && '?' !in pattern) {
            false
        } else {
            matchesDosPattern(number, pattern) ||
                (strippedNumber != number && matchesDosPattern(strippedNumber, pattern))
        }
    }
    if (matchedPattern) {
        return true
    }

    val exactNumbers = blockedNumbers.filterTo(ArrayList()) { blockedNumber ->
        '*' !in blockedNumber.number && '?' !in blockedNumber.number
    }
    return exactNumbers.isNotEmpty() && isNumberBlocked(number, exactNumbers)
}
