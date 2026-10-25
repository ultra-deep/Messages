package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.messages.extensions.config

object ReceiverUtils {

    fun isMessageFilteredOut(context: Context, body: String): Boolean {
        for (blockedKeyword in context.config.blockedKeywords) {
            if (matchesBlockedKeyword(body, blockedKeyword)) {
                return true
            }
        }

        return false
    }

    fun isIncomingMessageBlocked(context: Context, address: String, body: String): Boolean {
        if (isMessageFilteredOut(context, body)) {
            return true
        }
        if (context.isBlockedSender(address)) {
            return true
        }
        if (!context.baseConfig.blockUnknownNumbers) {
            return false
        }

        val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        val result = SimpleContactsHelper(context).existsSync(address, privateCursor)
        return result == ContactLookupResult.NotFound
    }
}
