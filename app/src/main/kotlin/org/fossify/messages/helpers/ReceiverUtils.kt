package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.messages.extensions.config
import org.fossify.messages.models.BlockedReason

object ReceiverUtils {

    fun isMessageFilteredOut(context: Context, body: String): Boolean {
        for (blockedKeyword in context.config.blockedKeywords) {
            if (matchesBlockedKeyword(body, blockedKeyword)) {
                return true
            }
        }

        return false
    }

    fun getIncomingMessageBlockReason(
        context: Context,
        address: String,
        body: String,
    ): BlockedReason? {
        if (isMessageFilteredOut(context, body)) {
            return BlockedReason.KEYWORD
        }
        if (context.isBlockedSender(address)) {
            return BlockedReason.NUMBER
        }
        if (!context.baseConfig.blockUnknownNumbers) {
            return null
        }

        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        val result = SimpleContactsHelper(context).existsSync(address, privateCursor)
        return if (result == ContactLookupResult.NotFound) {
            BlockedReason.NUMBER
        } else {
            null
        }
    }

    fun isIncomingMessageBlocked(context: Context, address: String, body: String): Boolean {
        return getIncomingMessageBlockReason(context, address, body) != null
    }

    fun inferBlockedReason(context: Context, body: String): BlockedReason {
        return if (isMessageFilteredOut(context, body)) {
            BlockedReason.KEYWORD
        } else {
            BlockedReason.NUMBER
        }
    }
}
