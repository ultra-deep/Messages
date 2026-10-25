package org.fossify.messages.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import android.graphics.Color
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.dialogs.ColorPickerDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogGroupNameBinding
import org.fossify.messages.extensions.showBackgroundColorPreview
import org.fossify.messages.extensions.toBackgroundColorLabel

class GroupNameDialog(
    private val activity: Activity,
    private val initialName: String = "",
    private val initialBackgroundColor: Int = Color.TRANSPARENT,
    @StringRes private val titleRes: Int,
    private val isNameTaken: (String) -> Boolean = { false },
    private val callback: (name: String, backgroundColor: Int) -> Unit,
) {
    private var dialog: AlertDialog? = null

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
                        callback(name, selectedColor)
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
