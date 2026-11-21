package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.value
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.DialogAddBlockedKeywordBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.regexOrNull

class AddBlockedKeywordDialog(
    val activity: BaseSimpleActivity,
    private val originalKeyword: String? = null,
    private val initialText: String? = null,
    val callback: () -> Unit,
) {
    init {
        val binding = DialogAddBlockedKeywordBinding.inflate(activity.layoutInflater).apply {
            val startingText = originalKeyword ?: initialText
            if (startingText != null) {
                addBlockedKeywordEdittext.setText(startingText)
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    wirePatternBuilder(binding)
                    alertDialog.showKeyboard(binding.addBlockedKeywordEdittext)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newBlockedKeyword = binding.addBlockedKeywordEdittext.value
                        if (newBlockedKeyword.isNotEmpty() && regexOrNull(newBlockedKeyword) == null) {
                            activity.toast(R.string.blocked_keyword_invalid_pattern)
                            return@setOnClickListener
                        }
                        if (originalKeyword != null && newBlockedKeyword != originalKeyword) {
                            activity.config.removeBlockedKeyword(originalKeyword)
                        }

                        if (newBlockedKeyword.isNotEmpty()) {
                            activity.config.addBlockedKeyword(newBlockedKeyword)
                        }

                        callback()
                        alertDialog.dismiss()
                    }
                }
            }
    }

    private fun wirePatternBuilder(binding: DialogAddBlockedKeywordBinding) {
        val host = activity as? SimpleActivity ?: return
        binding.addBlockedKeywordBuild.underlineText()
        binding.addBlockedKeywordBuild.setTextColor(activity.getProperPrimaryColor())
        binding.addBlockedKeywordBuild.setOnClickListener {
            host.launchRegexBuilder(sampleText = initialText.orEmpty()) { pattern ->
                binding.addBlockedKeywordEdittext.setText(pattern)
            }
        }
    }
}
