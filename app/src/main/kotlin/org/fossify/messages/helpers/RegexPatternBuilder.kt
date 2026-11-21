package org.fossify.messages.helpers

enum class RegexAnchor {
    ANYWHERE,
    START,
    END,
    WHOLE,
}

enum class RegexPieceKind {
    TEXT,
    ANY_TEXT,
    CHARACTERS,
    DIGITS,
    ONE_OF,
}

enum class RegexCountMode {
    ONE,
    ONE_OR_MORE,
    EXACTLY,
    AT_LEAST,
    BETWEEN,
}

data class RegexPiece(
    var kind: RegexPieceKind = RegexPieceKind.TEXT,
    var text: String = "",
    var countMode: RegexCountMode = RegexCountMode.ONE,
    var count: Int = 1,
    var countEnd: Int = 2,
)

internal const val MIN_REGEX_COUNT = 1
internal const val MAX_REGEX_COUNT = 999

private val REGEX_LITERAL_CHARACTERS = charArrayOf(
    '\\', '.', '^', '$', '*', '+', '?', '{', '}', '[', ']', '(', ')', '|',
)

fun buildRegex(anchor: RegexAnchor, pieces: List<RegexPiece>): String {
    val body = buildString {
        pieces.forEach { piece ->
            append(regexPiece(piece))
        }
    }
    if (body.isEmpty()) {
        return ""
    }
    return when (anchor) {
        RegexAnchor.ANYWHERE -> body
        RegexAnchor.START -> "^$body"
        RegexAnchor.END -> body + "$"
        RegexAnchor.WHOLE -> "^" + body + "$"
    }
}

private fun regexPiece(piece: RegexPiece): String {
    return when (piece.kind) {
        RegexPieceKind.TEXT -> escapeRegexLiteral(piece.text)
        RegexPieceKind.ANY_TEXT -> ".*"
        RegexPieceKind.CHARACTERS -> quantified(".", piece)
        RegexPieceKind.DIGITS -> quantified("\\d", piece)
        RegexPieceKind.ONE_OF -> oneOf(piece.text)
    }
}

private fun quantified(unit: String, piece: RegexPiece): String {
    val start = piece.count.coerceIn(MIN_REGEX_COUNT, MAX_REGEX_COUNT)
    val end = piece.countEnd.coerceIn(MIN_REGEX_COUNT, MAX_REGEX_COUNT)
    return when (piece.countMode) {
        RegexCountMode.ONE -> unit
        RegexCountMode.ONE_OR_MORE -> "$unit+"
        RegexCountMode.EXACTLY -> "$unit{$start}"
        RegexCountMode.AT_LEAST -> "$unit{$start,}"
        RegexCountMode.BETWEEN -> "$unit{${minOf(start, end)},${maxOf(start, end)}}"
    }
}

private fun oneOf(raw: String): String {
    val options = raw.split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { escapeRegexLiteral(it) }
    return when (options.size) {
        0 -> ""
        1 -> options.first()
        else -> options.joinToString(separator = "|", prefix = "(?:", postfix = ")")
    }
}

private fun escapeRegexLiteral(text: String): String {
    if (text.isEmpty()) {
        return ""
    }
    return buildString {
        text.forEach { character ->
            if (character in REGEX_LITERAL_CHARACTERS) {
                append('\\')
            }
            append(character)
        }
    }
}
