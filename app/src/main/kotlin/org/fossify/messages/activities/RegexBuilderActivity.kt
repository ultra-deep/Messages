package org.fossify.messages.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityRegexBuilderBinding
import org.fossify.messages.databinding.ItemRegexPieceBinding
import org.fossify.messages.helpers.MAX_REGEX_COUNT
import org.fossify.messages.helpers.MIN_REGEX_COUNT
import org.fossify.messages.helpers.RegexAnchor
import org.fossify.messages.helpers.RegexCountMode
import org.fossify.messages.helpers.RegexPiece
import org.fossify.messages.helpers.RegexPieceKind
import org.fossify.messages.helpers.buildRegex
import org.fossify.messages.helpers.matchesAnyRegexForm
import org.fossify.messages.helpers.regexOrNull

class RegexBuilderActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityRegexBuilderBinding::inflate)
    private val pieces = ArrayList<RegexPiece>()
    private var defaultSampleStroke: ColorStateList? = null
    private val rows by lazy {
        RegexBuilderRows(this, binding, pieces) { refreshPreview() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        defaultSampleStroke = binding.regexSampleLayout.boxStrokeColorStateList
        val restored = savedInstanceState?.readRegexPieces().orEmpty()
        pieces.addAll(restored.ifEmpty { listOf(RegexPiece()) })

        binding.regexBuilderToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.regex_use_pattern) {
                usePattern()
                true
            } else {
                false
            }
        }
        binding.regexMatchGroup.setOnCheckedChangeListener { _, _ -> refreshPreview() }
        if (savedInstanceState == null) {
            val sample = intent.getStringExtra(EXTRA_SAMPLE).orEmpty()
            if (sample.isNotEmpty()) {
                binding.regexSampleInput.setText(sample)
            }
        }
        binding.regexSampleInput.watchText { refreshPreview() }
        binding.regexAddPiece.setOnClickListener {
            pieces.add(RegexPiece())
            rows.render(focusLast = true)
        }
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.regexBuilderScroll))
        setupMaterialScrollListener(
            scrollingView = binding.regexBuilderScroll,
            topAppBar = binding.regexBuilderAppbar,
        )
        rows.render()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.regexBuilderAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.regexBuilderContent)
        binding.regexAddPiece.underlineText()
        binding.regexAddPiece.setTextColor(getProperPrimaryColor())
        rows.tintIcons()
        refreshPreview()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.writeRegexPieces(pieces)
    }

    private fun refreshPreview() {
        val pattern = buildRegex(selectedAnchor(), pieces)
        binding.regexPatternPreview.text = pattern.ifEmpty { getString(R.string.regex_pattern_empty) }
        val sample = binding.regexSampleInput.text?.toString().orEmpty()
        val showResult = pattern.isNotEmpty() && sample.isNotEmpty()
        binding.regexTryResult.beVisibleIf(showResult)
        if (!showResult) {
            binding.regexSampleInput.setTextColor(getProperTextColor())
            defaultSampleStroke?.let { binding.regexSampleLayout.setBoxStrokeColorStateList(it) }
            return
        }
        val matched = matchesAnyRegexForm(pattern, sample)
        val color = getColor(
            if (matched) {
                org.fossify.commons.R.color.md_green_500
            } else {
                org.fossify.commons.R.color.md_red_500
            },
        )
        binding.regexTryResult.setTextColor(color)
        binding.regexSampleInput.setTextColor(color)
        binding.regexSampleLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(color))
        binding.regexTryResult.text = getString(
            if (matched) R.string.regex_matches else R.string.regex_no_match,
        )
    }

    private fun usePattern() {
        val pattern = buildRegex(selectedAnchor(), pieces)
        if (pattern.isEmpty() || regexOrNull(pattern) == null) {
            toast(R.string.regex_pattern_empty)
            return
        }
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PATTERN, pattern))
        finish()
    }

    private fun selectedAnchor(): RegexAnchor {
        return when (binding.regexMatchGroup.checkedRadioButtonId) {
            R.id.regex_match_start -> RegexAnchor.START
            R.id.regex_match_end -> RegexAnchor.END
            R.id.regex_match_whole -> RegexAnchor.WHOLE
            else -> RegexAnchor.ANYWHERE
        }
    }

    companion object {
        const val EXTRA_PATTERN = "regex_pattern"
        const val EXTRA_SAMPLE = "regex_sample"
    }
}

private class RegexBuilderRows(
    private val activity: RegexBuilderActivity,
    private val binding: ActivityRegexBuilderBinding,
    private val pieces: ArrayList<RegexPiece>,
    private val onChanged: () -> Unit,
) {
    fun render(focusLast: Boolean = false) {
        val list = binding.regexPieces
        list.removeAllViews()
        pieces.forEachIndexed { index, piece ->
            bindPiece(index, piece)
        }
        if (focusLast) {
            val field = list.getChildAt(list.childCount - 1)
                ?.findViewById<EditText>(R.id.regex_piece_text)
            field?.post { activity.showKeyboard(field) }
        }
        onChanged()
    }

    fun tintIcons() {
        val color = activity.getProperTextColor()
        for (index in 0 until binding.regexPieces.childCount) {
            val row = binding.regexPieces.getChildAt(index)
            row.findViewById<ImageView>(R.id.regex_piece_up).applyColorFilter(color)
            row.findViewById<ImageView>(R.id.regex_piece_down).applyColorFilter(color)
            row.findViewById<ImageView>(R.id.regex_piece_remove).applyColorFilter(color)
        }
    }

    private fun bindPiece(index: Int, piece: RegexPiece) {
        val row = ItemRegexPieceBinding.inflate(activity.layoutInflater, binding.regexPieces, false)
        wireKindSpinner(row, piece)
        wireCountSpinner(row, piece)
        row.regexPieceText.setText(piece.text)
        row.regexPieceText.watchText { text ->
            piece.text = text
            onChanged()
        }
        row.regexPieceCount.setText(piece.count.toString())
        row.regexPieceCount.watchText { text ->
            piece.count = text.toCount()
            onChanged()
        }
        row.regexPieceCountEnd.setText(piece.countEnd.toString())
        row.regexPieceCountEnd.watchText { text ->
            piece.countEnd = text.toCount()
            onChanged()
        }
        wirePieceButtons(row, index)
        showPieceFields(row, piece)
        binding.regexPieces.addView(row.root)
    }

    private fun wireKindSpinner(row: ItemRegexPieceBinding, piece: RegexPiece) {
        wireSpinner(
            spinner = row.regexPieceKind,
            items = RegexPieceKind.entries,
            selected = piece.kind,
            label = { activity.getString(it.labelRes()) },
        ) { kind ->
            if (piece.kind == kind) {
                return@wireSpinner
            }
            piece.kind = kind
            when (kind) {
                RegexPieceKind.CHARACTERS -> piece.countMode = RegexCountMode.ONE
                RegexPieceKind.DIGITS -> piece.countMode = RegexCountMode.ONE_OR_MORE
                RegexPieceKind.TEXT, RegexPieceKind.ANY_TEXT, RegexPieceKind.ONE_OF -> Unit
            }
            row.regexPieceCountMode.setSelection(RegexCountMode.entries.indexOf(piece.countMode))
            showPieceFields(row, piece)
            onChanged()
        }
    }

    private fun wireCountSpinner(row: ItemRegexPieceBinding, piece: RegexPiece) {
        wireSpinner(
            spinner = row.regexPieceCountMode,
            items = RegexCountMode.entries,
            selected = piece.countMode,
            label = { activity.getString(it.labelRes()) },
        ) { mode ->
            if (piece.countMode == mode) {
                return@wireSpinner
            }
            piece.countMode = mode
            showPieceFields(row, piece)
            onChanged()
        }
    }

    private fun wirePieceButtons(row: ItemRegexPieceBinding, index: Int) {
        val color = activity.getProperTextColor()
        val canMoveUp = index > 0
        val canMoveDown = index < pieces.lastIndex
        row.regexPieceUp.applyColorFilter(color)
        row.regexPieceDown.applyColorFilter(color)
        row.regexPieceRemove.applyColorFilter(color)
        row.regexPieceUp.isEnabled = canMoveUp
        row.regexPieceDown.isEnabled = canMoveDown
        if (!canMoveUp) {
            row.regexPieceUp.alpha = DISABLED_ICON_ALPHA
        }
        if (!canMoveDown) {
            row.regexPieceDown.alpha = DISABLED_ICON_ALPHA
        }
        row.regexPieceUp.setOnClickListener { movePiece(index, index - 1) }
        row.regexPieceDown.setOnClickListener { movePiece(index, index + 1) }
        row.regexPieceRemove.setOnClickListener {
            pieces.removeAt(index)
            if (pieces.isEmpty()) {
                pieces.add(RegexPiece())
            }
            render()
        }
    }

    private fun showPieceFields(row: ItemRegexPieceBinding, piece: RegexPiece) {
        val showText = piece.kind == RegexPieceKind.TEXT || piece.kind == RegexPieceKind.ONE_OF
        row.regexPieceTextLayout.beVisibleIf(showText)
        row.regexPieceTextLayout.hint = when (piece.kind) {
            RegexPieceKind.ONE_OF -> activity.getString(R.string.regex_one_of_hint)
            else -> activity.getString(R.string.regex_words_hint)
        }
        val showCount = piece.kind == RegexPieceKind.CHARACTERS || piece.kind == RegexPieceKind.DIGITS
        row.regexPieceCountRow.beVisibleIf(showCount)
        val showNumber = piece.countMode == RegexCountMode.EXACTLY ||
            piece.countMode == RegexCountMode.AT_LEAST ||
            piece.countMode == RegexCountMode.BETWEEN
        row.regexPieceCount.beVisibleIf(showCount && showNumber)
        val showEnd = showCount && piece.countMode == RegexCountMode.BETWEEN
        row.regexPieceCountTo.beVisibleIf(showEnd)
        row.regexPieceCountEnd.beVisibleIf(showEnd)
    }

    private fun movePiece(from: Int, to: Int) {
        if (from !in pieces.indices || to !in pieces.indices) {
            return
        }
        val piece = pieces.removeAt(from)
        pieces.add(to, piece)
        render()
    }

    private fun <T> wireSpinner(
        spinner: Spinner,
        items: List<T>,
        selected: T,
        label: (T) -> String,
        onSelected: (T) -> Unit,
    ) {
        val labels = items.map(label)
        val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val index = items.indexOf(selected).coerceAtLeast(0)
        var acceptSelection = false
        spinner.adapter = adapter
        spinner.setSelection(index)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (acceptSelection) {
                    onSelected(items[position])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spinner.post { acceptSelection = true }
    }
}

private fun RegexPieceKind.labelRes(): Int {
    return when (this) {
        RegexPieceKind.TEXT -> R.string.regex_piece_text
        RegexPieceKind.ANY_TEXT -> R.string.regex_piece_any_text
        RegexPieceKind.CHARACTERS -> R.string.regex_piece_characters
        RegexPieceKind.DIGITS -> R.string.regex_piece_digits
        RegexPieceKind.ONE_OF -> R.string.regex_piece_one_of
    }
}

private fun RegexCountMode.labelRes(): Int {
    return when (this) {
        RegexCountMode.ONE -> R.string.regex_count_one
        RegexCountMode.ONE_OR_MORE -> R.string.regex_count_one_or_more
        RegexCountMode.EXACTLY -> R.string.regex_count_exactly
        RegexCountMode.AT_LEAST -> R.string.regex_count_at_least
        RegexCountMode.BETWEEN -> R.string.regex_count_between
    }
}

private fun String.toCount(): Int {
    return toIntOrNull()?.coerceIn(MIN_REGEX_COUNT, MAX_REGEX_COUNT) ?: MIN_REGEX_COUNT
}

private fun TextView.watchText(onChange: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            onChange(s?.toString().orEmpty())
        }
    })
}

private fun Bundle.writeRegexPieces(pieces: List<RegexPiece>) {
    putIntArray(STATE_KIND, pieces.map { it.kind.ordinal }.toIntArray())
    putIntArray(STATE_MODE, pieces.map { it.countMode.ordinal }.toIntArray())
    putIntArray(STATE_COUNT, pieces.map { it.count }.toIntArray())
    putIntArray(STATE_COUNT_END, pieces.map { it.countEnd }.toIntArray())
    putStringArray(STATE_TEXT, pieces.map { it.text }.toTypedArray())
}

private fun Bundle.readRegexPieces(): List<RegexPiece>? {
    val kinds = getIntArray(STATE_KIND) ?: return null
    val modes = getIntArray(STATE_MODE) ?: return null
    val counts = getIntArray(STATE_COUNT) ?: return null
    val countEnds = getIntArray(STATE_COUNT_END) ?: return null
    val texts = getStringArray(STATE_TEXT) ?: return null
    val width = kinds.size
    val aligned = listOf(modes.size, counts.size, countEnds.size, texts.size).all { it == width }
    if (!aligned) {
        return emptyList()
    }
    return List(width) { index ->
        RegexPiece(
            kind = RegexPieceKind.entries.getOrNull(kinds[index]) ?: RegexPieceKind.TEXT,
            text = texts[index].orEmpty(),
            countMode = RegexCountMode.entries.getOrNull(modes[index]) ?: RegexCountMode.ONE,
            count = counts[index],
            countEnd = countEnds[index],
        )
    }
}

private const val DISABLED_ICON_ALPHA = 0.4f
private const val STATE_KIND = "regex_piece_kind"
private const val STATE_MODE = "regex_piece_mode"
private const val STATE_COUNT = "regex_piece_count"
private const val STATE_COUNT_END = "regex_piece_count_end"
private const val STATE_TEXT = "regex_piece_text"
