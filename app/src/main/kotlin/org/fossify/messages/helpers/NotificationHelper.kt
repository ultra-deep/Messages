package org.fossify.messages.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.shortcutHelper
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.receivers.DeleteSmsReceiver
import org.fossify.messages.receivers.DirectReplyReceiver
import org.fossify.messages.receivers.MarkAsReadReceiver

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager
    private val soundUri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    private val user = Person.Builder()
        .setName(context.getString(R.string.me))
        .build()

    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        isMms: Boolean,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        sender: String?,
        alertOnlyOnce: Boolean = false,
        playSound: Boolean = true,
        vibrate: Boolean = true,
    ) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val notificationChannelId = if (hasCustomNotifications) {
            threadId.toString()
        } else {
            receivedSmsChannelId(playSound, vibrate)
        }
        if (!hasCustomNotifications) {
            createChannel(
                id = notificationChannelId,
                name = context.getString(receivedSmsChannelName(playSound, vibrate)),
                playSound = playSound,
                vibrate = vibrate,
            )
        }

        val notificationId = threadId.hashCode()
        val contentIntent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        val (messageIds, messageIsMms) = notifiedMessages(notificationId, messageId, isMms, sender)
        val deleteSmsIntent = Intent(context, DeleteSmsReceiver::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(MESSAGE_ID, messageId)
            putExtra(IS_MMS, isMms)
            putExtra(MESSAGE_IDS, messageIds)
            putExtra(MESSAGE_IS_MMS, messageIsMms)
        }
        val deleteSmsPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                deleteSmsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            action = MARK_AS_READ
            putExtra(THREAD_ID, threadId)
        }
        val markAsReadPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                markAsReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        var replyAction: NotificationCompat.Action? = null
        val isNoReplySms = isShortCodeWithLetters(address)
        if (!isNoReplySms) {
            val replyLabel = context.getString(R.string.reply)
            val remoteInput = RemoteInput.Builder(REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(THREAD_ID, threadId)
                putExtra(THREAD_NUMBER, address)
            }

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    context.applicationContext,
                    notificationId,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_send_vector,
                replyLabel,
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()
        }

        val largeIcon = bitmap ?: if (sender != null) {
            SimpleContactsHelper(context).getContactLetterIcon(sender)
        } else {
            null
        }
        val builder = NotificationCompat.Builder(context, notificationChannelId).apply {
            when (context.config.lockScreenVisibilitySetting) {
                LOCK_SCREEN_SENDER_MESSAGE -> {
                    setLargeIcon(largeIcon)
                    setStyle(getMessagesStyle(address, body, notificationId, sender))
                }

                LOCK_SCREEN_SENDER -> {
                    setContentTitle(sender)
                    setLargeIcon(largeIcon)
                    val summaryText = context.getString(R.string.new_message)
                    setStyle(
                        NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body)
                    )
                }
            }

            color = context.getProperPrimaryColor()
            setSmallIcon(R.drawable.ic_messenger)
            setContentIntent(contentPendingIntent)
            priority = NotificationCompat.PRIORITY_MAX
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setOnlyAlertOnce(alertOnlyOnce)
            val alertSound = hasCustomNotifications || playSound
            val alertVibration = hasCustomNotifications || vibrate
            if (alertVibration) {
                setDefaults(Notification.DEFAULT_LIGHTS or Notification.DEFAULT_VIBRATE)
            } else {
                setDefaults(Notification.DEFAULT_LIGHTS)
                setVibrate(longArrayOf(0L))
            }
            if (alertSound) {
                setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
            } else {
                setSound(null)
            }
        }

        if (replyAction != null && context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
            builder.addAction(replyAction)
        }

        builder.addAction(
            org.fossify.commons.R.drawable.ic_check_vector,
            context.getString(R.string.mark_as_read),
            markAsReadPendingIntent
        )
        builder.addAction(
            org.fossify.commons.R.drawable.ic_delete_vector,
            context.getString(R.string.trash),
            deleteSmsPendingIntent
        ).setChannelId(notificationChannelId)
        builder.extras.putLongArray(MESSAGE_IDS, messageIds)
        builder.extras.putBooleanArray(MESSAGE_IS_MMS, messageIsMms)

        var shortcut = context.shortcutHelper.getShortcut(threadId)
        if (shortcut == null) {
            ensureBackgroundThread {
                shortcut = context.shortcutHelper.createOrUpdateShortcut(threadId)
                builder.setShortcutInfo(shortcut)
                notificationManager.notify(notificationId, builder.build())
                context.shortcutHelper.reportReceiveMessageUsage(threadId)
            }
        } else {
            builder.setShortcutInfo(shortcut)
            notificationManager.notify(notificationId, builder.build())
            ensureBackgroundThread {
                context.shortcutHelper.reportReceiveMessageUsage(threadId)
            }
        }
    }

    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val notificationChannelId =
            if (hasCustomNotifications) threadId.toString() else NOTIFICATION_CHANNEL_ID
        if (!hasCustomNotifications) {
            createChannel(notificationChannelId, context.getString(R.string.message_not_sent_short))
        }

        val notificationId = generateRandomId().hashCode()
        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val summaryText =
            String.format(context.getString(R.string.message_sending_error), recipientName)
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_messenger)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(notificationChannelId)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun receivedSmsChannelId(playSound: Boolean, vibrate: Boolean): String {
        return when {
            playSound && vibrate -> NOTIFICATION_CHANNEL_ID
            playSound -> NOTIFICATION_CHANNEL_SOUND
            vibrate -> NOTIFICATION_CHANNEL_VIBRATE
            else -> NOTIFICATION_CHANNEL_SILENT
        }
    }

    private fun receivedSmsChannelName(playSound: Boolean, vibrate: Boolean): Int {
        return when {
            playSound && vibrate -> R.string.channel_received_sms
            playSound -> R.string.channel_received_sms_sound
            vibrate -> R.string.channel_received_sms_vibrate
            else -> R.string.channel_received_sms_silent
        }
    }

    private fun createChannel(
        id: String,
        name: String,
        playSound: Boolean = true,
        vibrate: Boolean = true,
    ) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build()

        val importance = IMPORTANCE_HIGH
        NotificationChannel(id, name, importance).apply {
            setBypassDnd(false)
            enableLights(true)
            if (playSound) {
                setSound(soundUri, audioAttributes)
            } else {
                setSound(null, null)
            }
            enableVibration(vibrate)
            notificationManager.createNotificationChannel(this)
        }
    }

    private fun getMessagesStyle(
        address: String,
        body: String,
        notificationId: Int,
        name: String?
    ): NotificationCompat.MessagingStyle {
        val sender = if (name != null) {
            Person.Builder()
                .setName(name)
                .setKey(address)
                .build()
        } else {
            null
        }

        return NotificationCompat.MessagingStyle(user).also { style ->
            getOldMessages(notificationId).forEach {
                style.addMessage(it)
            }
            val newMessage =
                NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
            style.addMessage(newMessage)
        }
    }

    private fun notifiedMessages(
        notificationId: Int,
        messageId: Long,
        isMms: Boolean,
        sender: String?,
    ): Pair<LongArray, BooleanArray> {
        val currentNotification = notificationManager.activeNotifications
            .find { it.id == notificationId }
            ?.notification
        val existingIds = currentNotification?.extras?.getLongArray(MESSAGE_IDS) ?: longArrayOf()
        val existingFlags = currentNotification?.extras?.getBooleanArray(MESSAGE_IS_MMS) ?: booleanArrayOf()
        val ids = existingIds.toMutableList()
        val flags = existingFlags.toMutableList()
        if (existingIds.size != existingFlags.size) {
            ids.clear()
            flags.clear()
        }
        if (sender != null && messageId > 0L && ids.indices.none { ids[it] == messageId && flags[it] == isMms }) {
            ids.add(messageId)
            flags.add(isMms)
        }
        return ids.toLongArray() to flags.toBooleanArray()
    }

    private fun getOldMessages(notificationId: Int): List<NotificationCompat.MessagingStyle.Message> {
        val currentNotification =
            notificationManager.activeNotifications.find { it.id == notificationId }
        return if (currentNotification != null) {
            val activeStyle =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
                    currentNotification.notification
                )
            activeStyle?.messages.orEmpty()
        } else {
            emptyList()
        }
    }
}
