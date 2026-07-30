package com.manimarank.websitemonitor.ui.settings

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.manimarank.websitemonitor.R
import com.manimarank.websitemonitor.databinding.ActivitySettingsBinding
import com.manimarank.websitemonitor.utils.BackupManager
import com.manimarank.websitemonitor.utils.Constants.DEFAULT_INTERVAL_MIN
import com.manimarank.websitemonitor.utils.Constants.IS_DARK_MODE_ENABLED
import com.manimarank.websitemonitor.utils.Constants.MONITORING_INTERVAL
import com.manimarank.websitemonitor.utils.Constants.NOTIFY_ONLY_SERVER_ISSUES
import com.manimarank.websitemonitor.utils.Interval.nameList
import com.manimarank.websitemonitor.utils.Interval.valueList
import com.manimarank.websitemonitor.utils.Print
import com.manimarank.websitemonitor.utils.SharedPrefsManager
import com.manimarank.websitemonitor.utils.SharedPrefsManager.set
import com.manimarank.websitemonitor.utils.Utils
import com.manimarank.websitemonitor.utils.Utils.getMonitorTime
import com.manimarank.websitemonitor.utils.Utils.isCustomRom
import com.manimarank.websitemonitor.utils.Utils.openAutoStartScreen
import com.manimarank.websitemonitor.utils.Utils.startWorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var activitySettingsBinding: ActivitySettingsBinding
    private lateinit var btnMonitorInterval: LinearLayout
    private lateinit var layoutEnableAutoStart: LinearLayout
    private lateinit var btnEnableAutoStart: TextView
    private lateinit var switchNotifyOnlyServerIssues: SwitchMaterial
    private lateinit var txtIntervalDetails: AppCompatTextView

    // Storage Access Framework pickers for the backup file. Registered here (before the activity is
    // started) as required by the Activity Result API.
    private val exportLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { exportTo(it) }
        }
    private val importLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { confirmImport(it) }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activitySettingsBinding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(activitySettingsBinding.root)
        btnMonitorInterval = activitySettingsBinding.btnMonitorInterval
        layoutEnableAutoStart = activitySettingsBinding.layoutEnableAutoStart
        btnEnableAutoStart = activitySettingsBinding.btnEnableAutoStart
        switchNotifyOnlyServerIssues = activitySettingsBinding.switchNotifyOnlyServerIssues
        txtIntervalDetails = activitySettingsBinding.txtIntervalDetails

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        btnMonitorInterval.setOnClickListener { showIntervalChooseDialog() }

        layoutEnableAutoStart.visibility = if (isCustomRom()) View.VISIBLE else View.GONE
        btnEnableAutoStart.setOnClickListener { openAutoStartScreen(this) }

        updateIntervalTimeOnUi()

        switchNotifyOnlyServerIssues.isChecked = SharedPrefsManager.customPrefs.getBoolean(NOTIFY_ONLY_SERVER_ISSUES, false)
        switchNotifyOnlyServerIssues.setOnCheckedChangeListener { _, isChecked ->
            SharedPrefsManager.customPrefs[NOTIFY_ONLY_SERVER_ISSUES] = isChecked
        }

        activitySettingsBinding.switchDarkMode.isChecked = SharedPrefsManager.customPrefs.getBoolean(IS_DARK_MODE_ENABLED, false)
        activitySettingsBinding.switchDarkMode.text  = getString(if (activitySettingsBinding.switchDarkMode.isChecked) R.string.disable_dark_mode else R.string.enable_dark_mode)
        activitySettingsBinding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            SharedPrefsManager.customPrefs[IS_DARK_MODE_ENABLED] = isChecked
            activitySettingsBinding.switchDarkMode.text  = getString(if (isChecked) R.string.disable_dark_mode else R.string.enable_dark_mode)
            Utils.enableDarkMode(isChecked)
        }

        activitySettingsBinding.btnExportData.setOnClickListener {
            exportLauncher.launch(getString(R.string.backup_default_file_name))
        }
        activitySettingsBinding.btnImportData.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }
    }

    private fun showIntervalChooseDialog() {

        val alertBuilder = AlertDialog.Builder(this)
        alertBuilder.setTitle(getString(R.string.choose_interval))

        val checkedItem = valueList.indexOf(SharedPrefsManager.customPrefs.getInt(MONITORING_INTERVAL, DEFAULT_INTERVAL_MIN))
        alertBuilder.setSingleChoiceItems(
            nameList,
            checkedItem
        ) { dialog: DialogInterface, which: Int ->
            SharedPrefsManager.customPrefs[MONITORING_INTERVAL] = valueList[which]
            startWorkManager(this, true)
            updateIntervalTimeOnUi()
            dialog.dismiss()
        }
        alertBuilder.setNegativeButton(getString(R.string.cancel), null)
        val dialog = alertBuilder.create()
        dialog.show()
    }

    private fun updateIntervalTimeOnUi() {
        txtIntervalDetails.text = getMonitorTime()
    }

    private fun exportTo(uri: Uri) {
        lifecycleScope.launch {
            try {
                val json = BackupManager.exportToJson(this@SettingsActivity)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: throw IllegalStateException("Unable to open output stream")
                }
                Utils.showToast(this@SettingsActivity, getString(R.string.backup_export_success))
            } catch (e: Exception) {
                Print.log("Backup export failed: $e")
                Utils.showToast(this@SettingsActivity, getString(R.string.backup_error))
            }
        }
    }

    private fun confirmImport(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_confirm_title))
            .setMessage(getString(R.string.import_confirm_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ -> importFrom(uri) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Unable to open input stream")
                }
                val count = BackupManager.importFromJson(this@SettingsActivity, json)
                applyRestoredSettings()
                Utils.showToast(
                    this@SettingsActivity,
                    getString(R.string.backup_import_success, count)
                )
            } catch (e: Exception) {
                Print.log("Backup import failed: $e")
                Utils.showToast(this@SettingsActivity, getString(R.string.backup_error))
            }
        }
    }

    /** Reflect freshly imported settings in the UI and reschedule background work. */
    private fun applyRestoredSettings() {
        val prefs = SharedPrefsManager.customPrefs
        switchNotifyOnlyServerIssues.isChecked = prefs.getBoolean(NOTIFY_ONLY_SERVER_ISSUES, false)
        activitySettingsBinding.switchDarkMode.isChecked = prefs.getBoolean(IS_DARK_MODE_ENABLED, false)
        Utils.enableDarkMode(prefs.getBoolean(IS_DARK_MODE_ENABLED, false))
        startWorkManager(this, true)
        updateIntervalTimeOnUi()
    }
}
