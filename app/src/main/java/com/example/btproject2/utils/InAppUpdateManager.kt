package com.example.btproject2.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.AppVersionConfig
import java.io.File

/**
 * In-App Over-The-Air (OTA) Version Update Manager for KinTrace (FR-11).
 * Detects new APK increments from Cloud Firestore and coordinates background download
 * and PackageInstaller execution without requiring third-party tester tools.
 */
object InAppUpdateManager {

    private val firestoreHelper = FirestoreHelper()

    private const val PREFS_NAME = "kintrace_ota_prefs"
    private const val KEY_DISMISSED_VERSION = "key_dismissed_version"
    private const val KEY_DISMISSED_TIMESTAMP = "key_dismissed_timestamp"
    private const val SUPPRESSION_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    /**
     * Clears suppression cooldown when admin publishes a new increment or user tests manually.
     */
    fun clearDismissalCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Synchronizes current active v1.2.0 increment to Firestore with accurate real release notes.
     */
    fun syncCurrentReleaseToFirestore(onComplete: () -> Unit = {}) {
        firestoreHelper.fetchLatestAppVersion(
            onSuccess = { config ->
                val currentRemoteCode = config?.versionCode ?: 0L
                if (currentRemoteCode <= 2L) {
                    val currentConfig = AppVersionConfig(
                        versionCode = 2L,
                        versionName = "v1.2.0",
                        releaseNotes = "• Twin-Pod Dual Island Header & telemetry\n• Clan Danger Zone & Account Stewardship\n• Verified Demo Vault with instant persona sign-in\n• High-contrast dialogs & WCAG AAA legibility",
                        apkDownloadUrl = config?.apkDownloadUrl ?: "",
                        isMandatory = false,
                        publishedBy = "system@kintrace.ph"
                    )
                    firestoreHelper.publishAppVersion(currentConfig, onSuccess = onComplete, onFailure = { onComplete() })
                } else {
                    onComplete()
                }
            },
            onFailure = { onComplete() }
        )
    }

    /**
     * Retrieves the installed version code directly from Android PackageManager.
     */
    fun getInstalledVersionCode(context: Context): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (_: Exception) {
            2L
        }
    }

    /**
     * Checks Firestore for newer version increments and presents Direction A modal if found.
     */
    fun checkForUpdates(
        activity: AppCompatActivity,
        forcePromptForDemo: Boolean = false,
        onNoUpdate: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        firestoreHelper.fetchLatestAppVersion(
            onSuccess = { config ->
                if (activity.isFinishing || activity.isDestroyed) return@fetchLatestAppVersion

                val currentVersionCode = getInstalledVersionCode(activity)
                val remoteVersionCode = config?.versionCode ?: currentVersionCode
                android.util.Log.d("InAppUpdateManager", "Checking OTA update: local=$currentVersionCode, remote=$remoteVersionCode")

                // Check 24-hour dismissal suppression
                val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastDismissedCode = prefs.getLong(KEY_DISMISSED_VERSION, -1L)
                val lastDismissedTime = prefs.getLong(KEY_DISMISSED_TIMESTAMP, 0L)
                val isSuppressed = !forcePromptForDemo && !(config?.isMandatory ?: false) &&
                    (remoteVersionCode == lastDismissedCode) &&
                    (System.currentTimeMillis() - lastDismissedTime < SUPPRESSION_DURATION_MS)

                if (isSuppressed) {
                    android.util.Log.d("InAppUpdateManager", "OTA prompt suppressed (24h cooldown active for code $remoteVersionCode)")
                    activity.runOnUiThread { onNoUpdate() }
                    return@fetchLatestAppVersion
                }

                if (forcePromptForDemo || remoteVersionCode > currentVersionCode) {
                    val activeConfig = config ?: AppVersionConfig(
                        versionCode = currentVersionCode + 1,
                        versionName = "v1.2.0",
                        releaseNotes = "• Twin-Pod Dual Island Header & telemetry\n• Clan Danger Zone & Account Stewardship\n• Verified Demo Vault with instant persona sign-in\n• High-contrast dialogs & WCAG AAA legibility",
                        apkDownloadUrl = "",
                        isMandatory = false
                    )
                    activity.runOnUiThread {
                        showUpdateDialog(activity, activeConfig)
                    }
                } else {
                    activity.runOnUiThread { onNoUpdate() }
                }
            },
            onFailure = { err ->
                android.util.Log.w("InAppUpdateManager", "fetchLatestAppVersion failed: ${err.message}")
                activity.runOnUiThread { onNoUpdate() }
            }
        )
    }

    /**
     * Displays Direction A (Heritage Crest Modal) with release notes and live progress bar.
     */
    fun showUpdateDialog(activity: AppCompatActivity, config: AppVersionConfig) {
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            val view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)

        val tvVersionTag = view.findViewById<TextView>(R.id.tvVersionTag)
        val tvUpdateTitle = view.findViewById<TextView>(R.id.tvUpdateTitle)
        val tvUpdateDesc = view.findViewById<TextView>(R.id.tvUpdateDesc)
        val tvReleaseNotes = view.findViewById<TextView>(R.id.tvReleaseNotes)
        val layoutProgress = view.findViewById<LinearLayout>(R.id.layoutDownloadProgress)
        val tvProgressStatus = view.findViewById<TextView>(R.id.tvProgressStatus)
        val tvProgressPercent = view.findViewById<TextView>(R.id.tvProgressPercent)
        val pbProgress = view.findViewById<ProgressBar>(R.id.pbDownloadProgress)
        val btnDownload = view.findViewById<Button>(R.id.btnDownloadUpdate)
        val btnLater = view.findViewById<Button>(R.id.btnRemindLater)

        tvVersionTag.text = "KinTrace ${config.versionName} Available"
        if (config.releaseNotes.isNotBlank()) {
            tvReleaseNotes.text = config.releaseNotes
        }

        if (config.isMandatory) {
            tvUpdateTitle.text = "Critical System Increment"
            tvUpdateDesc.text = "A mandatory database schema update is required before continuing."
            btnLater.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(!config.isMandatory)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnLater.setOnClickListener {
            val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_DISMISSED_VERSION, config.versionCode)
                .putLong(KEY_DISMISSED_TIMESTAMP, System.currentTimeMillis())
                .apply()
            dialog.dismiss()
        }

        btnDownload.setOnClickListener {
            btnDownload.isEnabled = false
            btnDownload.text = "⏳ Downloading Package..."
            btnLater.isEnabled = false
            layoutProgress.visibility = View.VISIBLE

            if (config.apkDownloadUrl.isNotBlank() && config.apkDownloadUrl.startsWith("http")) {
                downloadViaDownloadManager(activity, config, tvProgressStatus, tvProgressPercent, pbProgress, btnDownload, dialog)
            } else {
                // Realistic animated simulation for proposal / defense demonstration
                simulateDownloadProgress(activity, tvProgressStatus, tvProgressPercent, pbProgress, btnDownload, dialog)
            }
        }

        dialog.show()
        }
    }

    private fun simulateDownloadProgress(
        activity: AppCompatActivity,
        tvStatus: TextView,
        tvPercent: TextView,
        pb: ProgressBar,
        btnAction: Button,
        dialog: AlertDialog
    ) {
        val handler = Handler(Looper.getMainLooper())
        var progress = 0
        val totalMb = 18.4

        val runnable = object : Runnable {
            override fun run() {
                progress += (8..16).random()
                if (progress >= 100) {
                    progress = 100
                    pb.progress = 100
                    tvPercent.text = "100%"
                    tvStatus.text = "Download complete! Opening package installer..."
                    btnAction.isEnabled = true
                    btnAction.text = "✅ Install Update (PackageInstaller)"
                    btnAction.setOnClickListener {
                        Toast.makeText(
                            activity,
                            "KinTrace OTA: Package downloaded successfully! Launching system installer.",
                            Toast.LENGTH_LONG
                        ).show()
                        dialog.dismiss()
                    }
                } else {
                    pb.progress = progress
                    tvPercent.text = "$progress%"
                    val currentMb = String.format("%.1f", totalMb * progress / 100.0)
                    tvStatus.text = "Downloading package ($currentMb MB / ${totalMb} MB)..."
                    handler.postDelayed(this, 280)
                }
            }
        }
        handler.postDelayed(runnable, 200)
    }

    private fun downloadViaDownloadManager(
        activity: AppCompatActivity,
        config: AppVersionConfig,
        tvStatus: TextView,
        tvPercent: TextView,
        pb: ProgressBar,
        btnAction: Button,
        dialog: AlertDialog
    ) {
        try {
            val fileName = "KinTrace_${config.versionName}.apk"
            val destinationFile = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (destinationFile.exists()) destinationFile.delete()

            val request = DownloadManager.Request(Uri.parse(config.apkDownloadUrl))
                .setTitle("KinTrace Update ${config.versionName}")
                .setDescription("Downloading latest KinTrace package...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destinationFile))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                    if (id == downloadId) {
                        try {
                            activity.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        tvStatus.text = "Download complete! Ready to install."
                        tvPercent.text = "100%"
                        pb.progress = 100
                        btnAction.isEnabled = true
                        btnAction.text = "✅ Install Update Now"
                        btnAction.setOnClickListener {
                            installApk(activity, destinationFile)
                            dialog.dismiss()
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
            } else {
                activity.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }

            tvStatus.text = "Downloading via background DownloadManager..."
            pb.progress = 50
            tvPercent.text = "In Progress"

        } catch (e: Exception) {
            tvStatus.text = "Direct download failed: ${e.message}"
            btnAction.isEnabled = true
            btnAction.text = "Retry Download"
        }
    }

    /**
     * Launches the system PackageInstaller via FileProvider.
     */
    fun installApk(activity: AppCompatActivity, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "Error: APK file not found.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(activity, "Could not open installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
