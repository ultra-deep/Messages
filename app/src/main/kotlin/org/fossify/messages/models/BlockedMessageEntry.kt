package org.fossify.messages.models

data class BlockedMessageEntry(
    val address: String,
    val senderName: String,
    val body: String,
    val dateMillis: Long,
    val subscriptionId: Int = 0,
    val localId: Long = 0,
    val smsId: Long = 0,
    val mmsId: Long = 0,
    val read: Boolean = true,
) {
    fun title(): String {
        return if (senderName.isNotBlank() && senderName != address) senderName else address
    }

    fun readableText(): String {
        val title = title()
        return if (title != address && address.isNotBlank()) {
            "$title\n$address\n\n$body"
        } else {
            "$title\n\n$body"
        }
    }

    fun selectionKey(): Int {
        val source = when {
            localId != 0L -> "local:$localId"
            smsId != 0L -> "sms:$smsId"
            else -> "mms:$mmsId"
        }
        return source.hashCode()
    }

    fun isStoredLocally() = localId != 0L
}
