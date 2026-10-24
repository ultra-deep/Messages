package org.fossify.messages.helpers

import android.content.Context
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
}
