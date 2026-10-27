package org.fossify.messages.dialogs

import android.app.Activity
import android.text.format.DateFormat
import android.view.View
import android.widget.RadioGroup
import org.fossify.commons.databinding.DialogChangeDateTimeFormatBinding
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.DATE_FORMAT_EIGHT
import org.fossify.commons.helpers.DATE_FORMAT_FIVE
import org.fossify.commons.helpers.DATE_FORMAT_FOUR
import org.fossify.commons.helpers.DATE_FORMAT_ONE
import org.fossify.commons.helpers.DATE_FORMAT_SEVEN
import org.fossify.commons.helpers.DATE_FORMAT_SIX
import org.fossify.commons.helpers.DATE_FORMAT_THREE
import org.fossify.commons.helpers.DATE_FORMAT_TWO
import org.fossify.commons.views.MyCompatRadioButton
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import java.util.Calendar
import java.util.Locale

class ChangeDateTimeFormatDialog(private val activity: Activity, private val callback: () -> Unit) {
    private val binding = DialogChangeDateTimeFormatBinding.inflate(activity.layoutInflater, null, false)
    private val relativeButton = MyCompatRadioButton(activity).apply {
        id = View.generateViewId()
        text = activity.getString(R.string.date_time_format_relative)
        setAllCaps(false)
        layoutParams = RadioGroup.LayoutParams(
            RadioGroup.LayoutParams.MATCH_PARENT,
            RadioGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    init {
        val sampleButton = binding.changeDateTimeDialogRadioFive
        relativeButton.setPadding(
            sampleButton.paddingLeft,
            sampleButton.paddingTop,
            sampleButton.paddingRight,
            sampleButton.paddingBottom,
        )
        binding.changeDateTimeDialogRadioGroup.addView(relativeButton)
        dateFormatButtons().forEach { (button, format) ->
            button.text = formatDateSample(format)
        }
        binding.changeDateTimeDialog24Hour.isChecked = activity.config.use24HourFormat
        selectedButton().isChecked = true

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> confirmSelection() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }

    private fun confirmSelection() {
        val checkedId = binding.changeDateTimeDialogRadioGroup.checkedRadioButtonId
        if (checkedId == relativeButton.id) {
            activity.config.useRelativeDateTime = true
        } else {
            activity.config.useRelativeDateTime = false
            dateFormatButtons().firstOrNull { it.first.id == checkedId }?.let { (_, format) ->
                activity.config.dateFormat = format
            }
        }
        activity.config.use24HourFormat = binding.changeDateTimeDialog24Hour.isChecked
        callback()
    }

    private fun selectedButton(): MyCompatRadioButton {
        if (activity.config.useRelativeDateTime) {
            return relativeButton
        }
        val selectedFormat = activity.config.dateFormat
        return dateFormatButtons().firstOrNull { it.second == selectedFormat }?.first
            ?: binding.changeDateTimeDialogRadioEight
    }

    private fun dateFormatButtons(): List<Pair<MyCompatRadioButton, String>> {
        return listOf(
            binding.changeDateTimeDialogRadioOne to DATE_FORMAT_ONE,
            binding.changeDateTimeDialogRadioTwo to DATE_FORMAT_TWO,
            binding.changeDateTimeDialogRadioThree to DATE_FORMAT_THREE,
            binding.changeDateTimeDialogRadioFour to DATE_FORMAT_FOUR,
            binding.changeDateTimeDialogRadioFive to DATE_FORMAT_FIVE,
            binding.changeDateTimeDialogRadioSix to DATE_FORMAT_SIX,
            binding.changeDateTimeDialogRadioSeven to DATE_FORMAT_SEVEN,
            binding.changeDateTimeDialogRadioEight to DATE_FORMAT_EIGHT,
        )
    }

    private fun formatDateSample(format: String): String {
        val calendar = Calendar.getInstance(Locale.ENGLISH)
        calendar.timeInMillis = TIME_SAMPLE
        return DateFormat.format(format, calendar).toString()
    }

    companion object {
        private const val TIME_SAMPLE = 1676419200000L
    }
}
