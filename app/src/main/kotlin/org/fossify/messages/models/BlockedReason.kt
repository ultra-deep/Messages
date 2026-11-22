package org.fossify.messages.models

import android.content.Context
import org.fossify.messages.R

enum class BlockedReason(val value: Int) {
    UNSPECIFIED(0),
    KEYWORD(1),
    NUMBER(2);

    fun label(context: Context): String {
        return when (this) {
            KEYWORD -> context.getString(R.string.blocked_by_keyword)
            NUMBER, UNSPECIFIED -> context.getString(R.string.blocked_number)
        }
    }

    fun listMarker(): String {
        return when (this) {
            KEYWORD -> "#W"
            NUMBER, UNSPECIFIED -> "#N"
        }
    }

    companion object {
        fun fromValue(value: Int): BlockedReason {
            return entries.firstOrNull { it.value == value } ?: UNSPECIFIED
        }
    }
}
