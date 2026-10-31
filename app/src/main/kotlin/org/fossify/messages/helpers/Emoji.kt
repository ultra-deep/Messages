package org.fossify.messages.helpers

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator

private const val MAX_STANDALONE_EMOJI_COUNT = 3
private const val ZWJ = 0x200D
private const val VARIATION_SELECTOR_15 = 0xFE0E
private const val VARIATION_SELECTOR_16 = 0xFE0F
private const val COMBINING_ENCLOSING_KEYCAP = 0x20E3
private const val REGIONAL_INDICATOR_START = 0x1F1E6
private const val REGIONAL_INDICATOR_END = 0x1F1FF
private const val TAG_START = 0xE0020
private const val TAG_END = 0xE007F

/**
 * Returns how many emoji grapheme clusters [this] contains when the whole string is
 * emoji-only (optionally with whitespace), and at most [MAX_STANDALONE_EMOJI_COUNT].
 * Returns null when the text should render as a normal message bubble.
 */
fun String.standaloneEmojiCount(): Int? {
    val text = trim()
    if (text.isEmpty()) {
        return null
    }

    var emojiCount = 0
    val breakIterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    var start = breakIterator.first()
    var end = breakIterator.next()
    while (end != BreakIterator.DONE) {
        val cluster = text.substring(start, end)
        if (cluster.isBlank()) {
            start = end
            end = breakIterator.next()
            continue
        }
        if (!cluster.isEmojiGrapheme()) {
            return null
        }
        emojiCount++
        if (emojiCount > MAX_STANDALONE_EMOJI_COUNT) {
            return null
        }
        start = end
        end = breakIterator.next()
    }

    return emojiCount.takeIf { it in 1..MAX_STANDALONE_EMOJI_COUNT }
}

private fun String.isEmojiGrapheme(): Boolean {
    var index = 0
    var hasEmojiPresentation = false
    var hasEmojiComponent = false
    var hasKeycap = false
    var hasVariationSelector16 = false

    while (index < length) {
        val codePoint = codePointAt(index)
        when {
            codePoint == COMBINING_ENCLOSING_KEYCAP -> hasKeycap = true
            codePoint == VARIATION_SELECTOR_16 -> hasVariationSelector16 = true
            codePoint == ZWJ || codePoint == VARIATION_SELECTOR_15 || codePoint in TAG_START..TAG_END -> Unit
            UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_MODIFIER) -> Unit
            codePoint.isEmojiPresentationBase() -> hasEmojiPresentation = true
            UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI)
                || UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_COMPONENT) -> {
                hasEmojiComponent = true
            }
            else -> return false
        }
        index += Character.charCount(codePoint)
    }

    return hasEmojiPresentation || (hasEmojiComponent && (hasKeycap || hasVariationSelector16))
}

private fun Int.isEmojiPresentationBase(): Boolean {
    return UCharacter.hasBinaryProperty(this, UProperty.EXTENDED_PICTOGRAPHIC)
        || UCharacter.hasBinaryProperty(this, UProperty.EMOJI_PRESENTATION)
        || this in REGIONAL_INDICATOR_START..REGIONAL_INDICATOR_END
}
