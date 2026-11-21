package org.fossify.messages.activities

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.messages.R

open class SimpleActivity : BaseSimpleActivity() {
    private var regexBuilderCallback: ((String) -> Unit)? = null

    private val regexBuilderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pattern = result.data?.getStringExtra(RegexBuilderActivity.EXTRA_PATTERN).orEmpty()
        val callback = regexBuilderCallback
        regexBuilderCallback = null
        if (result.resultCode == RESULT_OK && pattern.isNotEmpty()) {
            callback?.invoke(pattern)
        }
    }

    fun launchRegexBuilder(sampleText: String = "", onPattern: (String) -> Unit) {
        regexBuilderCallback = onPattern
        val intent = Intent(this, RegexBuilderActivity::class.java)
        if (sampleText.isNotEmpty()) {
            intent.putExtra(RegexBuilderActivity.EXTRA_SAMPLE, sampleText)
        }
        regexBuilderLauncher.launch(intent)
    }

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher_orange,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Messages"
}
