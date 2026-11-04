package org.fossify.messages.extensions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.darkenColor
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.lightenColor
import org.fossify.commons.extensions.setFillWithStroke
import org.fossify.commons.extensions.toHex
import org.fossify.messages.R

fun Int.toBackgroundColorLabel(context: Context): String {
    return if (this == Color.TRANSPARENT) {
        context.getString(R.string.transparent)
    } else {
        toHex()
    }
}

fun ImageView.showBackgroundColorPreview(color: Int) {
    setFillWithStroke(color, context.getProperBackgroundColor())
    if (color == Color.TRANSPARENT) {
        val strokeWidth = (2 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        (background as? GradientDrawable)?.setStroke(
            strokeWidth,
            context.getProperTextColor().adjustAlpha(0.5f),
        )
    }
}

fun Context.isDarkThemeBackground(): Boolean {
    return getProperBackgroundColor().getContrastColor() == Color.WHITE
}

fun Context.receivedMessageBubbleColor(conversationBackgroundColor: Int = Color.TRANSPARENT): Int {
    if (conversationBackgroundColor == Color.TRANSPARENT) {
        val colorRes = if (isDarkThemeBackground()) {
            org.fossify.commons.R.color.md_grey_700
        } else {
            org.fossify.commons.R.color.md_grey_300
        }
        return ResourcesCompat.getColor(resources, colorRes, theme)
    }
    return makeMutedBubbleColor(shiftedBubbleColor(conversationBackgroundColor))
}

fun Context.sentMessageBubbleColor(conversationBackgroundColor: Int = Color.TRANSPARENT): Int {
    if (conversationBackgroundColor == Color.TRANSPARENT) {
        val colorRes = if (isDarkThemeBackground()) {
            org.fossify.commons.R.color.md_blue_700
        } else {
            org.fossify.commons.R.color.md_blue_500
        }
        return ResourcesCompat.getColor(resources, colorRes, theme)
    }
    return makeRicherBubbleColor(shiftedBubbleColor(conversationBackgroundColor))
}

fun Context.composeBarBackgroundColor(): Int {
    val colorRes = if (isDarkThemeBackground()) {
        org.fossify.commons.R.color.md_grey_800
    } else {
        org.fossify.commons.R.color.md_grey_200
    }
    return ResourcesCompat.getColor(resources, colorRes, theme)
}

private fun shiftedBubbleColor(baseColor: Int): Int {
    return if (baseColor.getContrastColor() == Color.WHITE) {
        baseColor.lightenColor()
    } else {
        baseColor.darkenColor()
    }
}

private fun makeMutedBubbleColor(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[1] = (hsv[1] * 0.45f).coerceAtMost(0.35f)
    return Color.HSVToColor(hsv)
}

private fun Context.makeRicherBubbleColor(color: Int): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    val primaryHsv = FloatArray(3)
    Color.colorToHSV(getProperPrimaryColor(), primaryHsv)
    if (hsv[1] < 0.15f) {
        hsv[0] = primaryHsv[0]
        hsv[1] = (primaryHsv[1] * 0.7f).coerceIn(0.35f, 1f)
    } else {
        hsv[1] = (hsv[1] * 1.5f).coerceAtMost(1f)
    }
    return Color.HSVToColor(hsv)
}

fun Context.resolveTabBackgroundColor(lightColor: Int, darkColor: Int): Int {
    return if (isDarkThemeBackground()) darkColor else lightColor
}

fun Context.blockedMessagesTabBackgroundColor(): Int {
    return resolveTabBackgroundColor(
        lightColor = config.blockedMessagesBackgroundColor,
        darkColor = config.blockedMessagesDarkBackgroundColor,
    )
}

fun Context.archivedMessagesTabBackgroundColor(): Int {
    return resolveTabBackgroundColor(
        lightColor = config.archivedMessagesBackgroundColor,
        darkColor = config.archivedMessagesDarkBackgroundColor,
    )
}
