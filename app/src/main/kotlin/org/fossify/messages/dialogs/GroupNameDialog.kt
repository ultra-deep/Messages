package org.fossify.messages.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import android.graphics.Color
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogGroupNameBinding
import org.fossify.messages.databinding.ItemGroupNumberPatternBinding
import org.fossify.messages.extensions.showBackgroundColorPreview
import org.fossify.messages.extensions.toBackgroundColorLabel
import org.fossify.messages.helpers.isUsableNumberPattern
import org.fossify.messages.helpers.numberPatternsOf
import org.fossify.messages.models.ConversationGroupForm

class GroupNameDialog(
    private val activity: Activity,
    private val initialName: String = "",
    private val initialBackgroundColor: Int = Color.TRANSPARENT,
    private val initialShowNotification: Boolean = true,
    private val initialNotificationSound: Boolean = true,
    private val initialNotificationVibration: Boolean = true,
    private val initialNumberPatterns: String = "",
    @StringRes private val titleRes: Int,
    private val isNameTaken: (String) -> Boolean = { false },
    private val callback: (ConversationGroupForm) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private val patterns = numberPatternsOf(initialNumberPatterns).distinct().toMutableList()

    init {
        var selectedColor = initialBackgroundColor
        val binding = DialogGroupNameBinding.inflate(activity.layoutInflater)
        fun showSelectedColor(color: Int) {
            binding.groupBackgroundColorPreview.showBackgroundColorPreview(color)
            binding.groupBackgroundColorValue.text = color.toBackgroundColorLabel(activity)
        }
        binding.groupNameEditText.setText(initialName)
        showSelectedColor(selectedColor)
        binding.groupBackgroundColorHolder.setOnClickListener {
            val pickerColor = if (selectedColor == Color.TRANSPARENT) {
                activity.getProperPrimaryColor()
            } else {
                selectedColor
            }
            ColorPickerDialog(
                activity = activity,
                color = pickerColor,
                addDefaultColorButton = true,
            ) { wasPositivePressed, pickedColor ->
                if (wasPositivePressed) {
                    selectedColor = pickedColor
                    showSelectedColor(selectedColor)
                }
            }
        }
        binding.groupShowNotificationCheckbox.isChecked = initialShowNotification
        binding.groupNotificationSoundCheckbox.isChecked = initialNotificationSound
        binding.groupNotificationVibrationCheckbox.isChecked = initialNotificationVibration
        val textColor = activity.getProperTextColor()
        binding.groupNumberPatternAdd.applyColorFilter(textColor)
        binding.groupNumberPatternAdd.setOnClickListener { addPattern(binding) }
        binding.groupNumberPatternInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addPattern(binding)
                true
            } else {
                false
            }
        }
        renderPatterns(binding, textColor)
        fun showNotificationOptions() {
            binding.groupNotificationOptions.beVisibleIf(
                binding.groupShowNotificationCheckbox.isChecked
            )
        }
        showNotificationOptions()
        binding.groupShowNotificationCheckbox.setOnCheckedChangeListener { _, _ ->
            showNotificationOptions()
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, titleRes) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(binding.groupNameEditText)
                    alertDialog.getButton(BUTTON_POSITIVE).setOnClickListener {
                        val name = binding.groupNameEditText.text?.toString().orEmpty().trim()
                        if (name.isEmpty()) {
                            activity.toast(org.fossify.commons.R.string.empty_name)
                            return@setOnClickListener
                        }
                        if (isNameTaken(name)) {
                            activity.toast(R.string.group_name_exists)
                            return@setOnClickListener
                        }
                        if (!addPattern(binding)) {
                            return@setOnClickListener
                        }
                        callback(
                            ConversationGroupForm(
                                name = name,
                                backgroundColor = selectedColor,
                                showNotification = binding.groupShowNotificationCheckbox.isChecked,
                                notificationSound = binding.groupNotificationSoundCheckbox.isChecked,
                                notificationVibration = binding.groupNotificationVibrationCheckbox.isChecked,
                                numberPatterns = patterns.joinToString("\n"),
                            )
                        )
                        alertDialog.dismiss()
                    }
                }
            }
    }

    private fun renderPatterns(binding: DialogGroupNameBinding, textColor: Int) {
        val list = binding.groupNumberPatternList
        list.removeAllViews()
        patterns.forEach { pattern ->
            val row = ItemGroupNumberPatternBinding.inflate(activity.layoutInflater, list, false)
            row.groupNumberPatternValue.text = pattern
            row.groupNumberPatternRemove.applyColorFilter(textColor)
            row.groupNumberPatternRemove.setOnClickListener {
                patterns.remove(pattern)
                renderPatterns(binding, textColor)
            }
            list.addView(row.root)
        }
    }

    private fun addPattern(binding: DialogGroupNameBinding): Boolean {
        val pattern = binding.groupNumberPatternInput.text?.toString().orEmpty().trim()
        if (pattern.isEmpty()) {
            return true
        }
        if (!isUsableNumberPattern(pattern)) {
            activity.toast(R.string.group_number_pattern_invalid)
            return false
        }
        if (patterns.any { it.equals(pattern, ignoreCase = true) }) {
            activity.toast(R.string.group_number_pattern_exists)
            return false
        }
        patterns.add(pattern)
        binding.groupNumberPatternInput.text?.clear()
        renderPatterns(binding, activity.getProperTextColor())
        return true
    }
}
