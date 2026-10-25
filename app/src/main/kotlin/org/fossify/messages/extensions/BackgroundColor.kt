package org.fossify.messages.extensions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
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
