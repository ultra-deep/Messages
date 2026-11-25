package org.fossify.messages.dialogs

import android.util.TypedValue
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.views.MyAppCompatCheckbox
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.DialogImportAppDataBinding
import org.fossify.messages.helpers.ImportableSection
import org.fossify.messages.models.AppDataSection

class ImportAppDataDialog(
    private val activity: SimpleActivity,
    private val sections: List<ImportableSection>,
    private val callback: (Set<AppDataSection>) -> Unit,
) {
    private val checks = ArrayList<Pair<AppDataSection, MyAppCompatCheckbox>>()

    init {
        val binding = DialogImportAppDataBinding.inflate(activity.layoutInflater)
        sections.forEach { section ->
            val checkbox = checkbox(section)
            checks.add(section.section to checkbox)
            binding.importSections.addView(checkbox)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(
                    view = binding.root,
                    dialog = this,
                    titleId = R.string.import_app_data,
                ) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val selected = checks
                            .filter { it.second.isChecked }
                            .map { it.first }
                            .toSet()
                        if (selected.isEmpty()) {
                            activity.toast(R.string.no_option_selected)
                            return@setOnClickListener
                        }
                        callback(selected)
                        alertDialog.dismiss()
                    }
                }
            }
    }

    private fun checkbox(section: ImportableSection): MyAppCompatCheckbox {
        val margin = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val bottom = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        return MyAppCompatCheckbox(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = margin
                marginEnd = margin
                bottomMargin = bottom
            }
            setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                activity.resources.getDimension(org.fossify.commons.R.dimen.normal_text_size),
            )
            maxLines = 4
            isChecked = true
            text = label(section)
        }
    }

    private fun label(section: ImportableSection): String {
        val title = activity.getString(titleRes(section.section))
        val counted = section.count?.let { count ->
            activity.getString(R.string.import_section_with_count, title, count)
        } ?: title
        val details = detailsRes(section.section)?.let { activity.getString(it) }
        return if (details == null) counted else "$counted\n$details"
    }

    private fun titleRes(section: AppDataSection): Int {
        return when (section) {
            AppDataSection.GROUPS -> R.string.import_section_groups
            AppDataSection.BLOCKED_NUMBERS -> R.string.import_section_blocked_numbers
            AppDataSection.BLOCKED_KEYWORDS -> R.string.import_section_blocked_keywords
            AppDataSection.ARCHIVED_NUMBERS -> R.string.import_section_archived_numbers
            AppDataSection.PINNED_CONVERSATIONS -> R.string.import_section_pinned_conversations
            AppDataSection.BLOCKED_MESSAGES_BACKGROUND -> R.string.import_section_blocked_background
            AppDataSection.ARCHIVED_MESSAGES_BACKGROUND -> R.string.import_section_archived_background
            AppDataSection.THEME_COLORS -> R.string.import_section_theme
            AppDataSection.SETTINGS -> R.string.import_section_settings
        }
    }

    private fun detailsRes(section: AppDataSection): Int? {
        return when (section) {
            AppDataSection.GROUPS -> R.string.import_section_groups_details
            AppDataSection.THEME_COLORS -> R.string.import_section_theme_details
            AppDataSection.SETTINGS -> R.string.import_section_settings_details
            else -> null
        }
    }
}
