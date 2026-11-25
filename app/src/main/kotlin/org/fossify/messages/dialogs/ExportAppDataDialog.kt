package org.fossify.messages.dialogs

import android.net.Uri
import android.provider.DocumentsContract
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getCurrentFormattedDateTime
import org.fossify.commons.extensions.isAValidFilename
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.DialogExportAppDataBinding
import org.fossify.messages.helpers.AppDataTransfer

class ExportAppDataDialog(
    private val activity: SimpleActivity,
    private val callback: (fileName: String) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private val binding = DialogExportAppDataBinding.inflate(activity.layoutInflater).apply {
        val name = buildString {
            append(activity.getString(R.string.app_launcher_name))
            append('_')
            append(activity.getString(R.string.settings_backup_filename))
            append('_')
            append(activity.getCurrentFormattedDateTime())
        }
        exportAppDataFilename.setText(name)
    }

    init {
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(
                    view = binding.root,
                    dialog = this,
                    titleId = R.string.export_app_data,
                ) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = binding.exportAppDataFilename.value
                        when {
                            filename.isEmpty() -> activity.toast(org.fossify.commons.R.string.empty_name)
                            filename.isAValidFilename() -> callback(filename)
                            else -> activity.toast(org.fossify.commons.R.string.invalid_name)
                        }
                    }
                }
            }
    }

    fun export(uri: Uri) {
        ensureBackgroundThread {
            var success = false
            try {
                val backup = AppDataTransfer.collect(activity)
                val stream = activity.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    activity.toast(org.fossify.commons.R.string.exporting_failed)
                } else {
                    stream.buffered().use { output ->
                        AppDataTransfer.write(backup, output)
                    }
                    success = true
                    activity.toast(org.fossify.commons.R.string.exporting_successful)
                }
            } catch (e: Throwable) {
                activity.showErrorToast(e.toString())
            } finally {
                if (!success) {
                    runCatching {
                        DocumentsContract.deleteDocument(activity.contentResolver, uri)
                    }
                }
                activity.runOnUiThread { dialog?.dismiss() }
            }
        }
    }
}
