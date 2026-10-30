package com.example.btproject2.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.btproject2.R
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.AppVersionConfig
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Zero-Cost GitHub Releases Over-The-Air (OTA) Auto-Update Manager for KinTrace.
 *
 * Implements Direction 1 (Silent Background Download + Non-Intrusive Floating Ready Pill):
 * 1. Never blocks HomeActivity startup with modal dialogs.
 * 2. Automatically heals ghost/orphan Firestore version records that lack a valid HTTPS APK link.
 * 3. Streams real APK binaries from GitHub Releases CDN directly into app-internal cacheDir/updates/
 *    (zero storage permissions required, full Android 8–16 Scoped Storage compliance).
 * 4. Presents a sleek floating pill above the navigation island only when the APK is verified on disk.
 * 5. Checks canRequestPackageInstalls() and invokes Android's native PackageInstaller via FileProvider.
 */
object InAppUpdateManager {

    private val firestoreHelper by lazy { FirestoreHelper() }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val downloadExecutor = Executors.newSingleThreadExecutor()
    private val isDownloading = AtomicBoolean(false)
    private var currentReadyPill: WeakReference<View>? = null

    const val DEFAULT_GITHUB_REPO = "gltitccch/BTProject2"
    private const val PREFS_NAME = "kintrace_ota_prefs"
    private const val KEY_DISMISSED_VERSION = "key_dismissed_version"
    private const val KEY_DISMISSED_TIMESTAMP = "key_dismissed_timestamp"
    const val KEY_LAST_SEEN_VERSION = "key_last_seen_version"
    private const val SUPPRESSION_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours

    const val CURRENT_RELEASE_NOTES = "• Successive Remarriages & Prior Spouses Engine with Philippine Family Code Art. 35/37/38/41 Validation\n• Guided Family Tree Multi-Option Resolution Stepper with Context-Aware Decision Choices\n• Legal Adoption Childbirth Age Exemption under Philippine Domestic Adoption Law (RA 8552 / RA 11642)\n• Real-Time Tree Health & Integrity Synchronization across Interactive Canvas and Audit Screens\n• Warm Linen & Amber Bronze Theme Standards with Zero Green in Light Mode"

    /**
     * Builds the canonical zero-cost GitHub Releases direct APK download link for a given version tag.
     */
    fun buildGitHubReleaseApkUrl(versionName: String): String {
        val cleanTag = if (versionName.startsWith("v", ignoreCase = true)) versionName.trim() else "v${versionName.trim()}"
        return "https://github.com/$DEFAULT_GITHUB_REPO/releases/download/$cleanTag/app-release.apk"
    }

    /**
     * Clears suppression cooldown when admin publishes a new increment or user tests manually.
     */
    fun clearDismissalCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_DISMISSED_VERSION)
            .remove(KEY_DISMISSED_TIMESTAMP)
            .apply()
    }

    /**
     * Phase 1 Triage & Baseline Alignment:
     * Synchronizes current active release to Firestore and automatically repairs any ghost version
     * records (where remoteVersionCode > installedCode but apkDownloadUrl is blank or invalid,
     * or legacy records pointing to app-debug.apk).
     */
    fun syncCurrentReleaseToFirestore(onComplete: () -> Unit = {}) {
        firestoreHelper.fetchLatestAppVersion(
            onSuccess = { config ->
                val currentRemoteCode = config?.versionCode ?: 0L
                val rawUrl = config?.apkDownloadUrl?.trim().orEmpty()
                val hasValidRemoteApk = rawUrl.startsWith("https://", ignoreCase = true) &&
                    !rawUrl.contains("app-debug.apk", ignoreCase = true)

                val targetReleaseNotes = CURRENT_RELEASE_NOTES

                // If remote code is < 8L OR remote record lacks a valid release HTTPS APK link,
                // OR remote releaseNotes need updating to include the latest fixes,
                // align Firestore to the active v1.3.5 (versionCode = 8) release pointing to GitHub Releases.
                if (currentRemoteCode < 8L || (currentRemoteCode == 8L && (!hasValidRemoteApk || config?.releaseNotes != targetReleaseNotes))) {
                    val currentConfig = AppVersionConfig(
                        versionCode = 8L,
                        versionName = "v1.3.5",
                        releaseNotes = targetReleaseNotes,
                        apkDownloadUrl = buildGitHubReleaseApkUrl("v1.3.5"),
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
            8L
        }
    }

    /**
     * Returns the internal files directory dedicated to OTA APK packages (/files/updates/).
     * Mapped in res/xml/file_paths.xml via <files-path name="apk_updates_files" path="updates/" />.
     * Using filesDir instead of cacheDir prevents Android StorageManager from wiping the staged APK
     * during activity recreation when device disk usage exceeds 90%.
     */
    fun getUpdatesCacheDir(context: Context): File {
        val dir = File(context.filesDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Purges any cached APK files for versions <= currentInstalledCode to keep storage clean.
     */
    private fun cleanupStaleCacheApks(context: Context, currentInstalledCode: Long) {
        try {
            val dir = getUpdatesCacheDir(context)
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".part") || file.name.contains("_${currentInstalledCode}.apk")) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Checks Firestore for newer version increments.
     * Direction 1: Never blocks the screen with a dialog on launch. Quietly downloads the real APK
     * in the background and shows the non-intrusive Floating Ready Pill once the APK is verified on disk.
     */
    fun checkForUpdates(
        activity: AppCompatActivity,
        forcePromptForDemo: Boolean = false,
        onNoUpdate: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val currentVersionCode = getInstalledVersionCode(activity)
        cleanupStaleCacheApks(activity, currentVersionCode)

        firestoreHelper.fetchLatestAppVersion(
            onSuccess = { config ->
                if (activity.isFinishing || activity.isDestroyed) return@fetchLatestAppVersion

                val remoteVersionCode = config?.versionCode ?: currentVersionCode
                val downloadUrl = config?.apkDownloadUrl?.trim().orEmpty()
                val hasValidUrl = downloadUrl.startsWith("https://", ignoreCase = true)

                android.util.Log.d(
                    "InAppUpdateManager",
                    "Checking OTA update: local=$currentVersionCode, remote=$remoteVersionCode, hasValidUrl=$hasValidUrl"
                )

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

                // Only trigger update flow when remoteVersionCode > currentVersionCode AND a valid HTTPS APK URL exists
                if (remoteVersionCode > currentVersionCode && hasValidUrl && config != null) {
                    val targetApkFile = File(getUpdatesCacheDir(activity), "KinTrace_v${config.versionCode}.apk")
                    if (targetApkFile.exists() && targetApkFile.length() > 10_000L) {
                        // APK is already downloaded and verified in cacheDir -> show Direction 1 Floating Pill immediately
                        activity.runOnUiThread {
                            showUpdateReadyPill(activity, config, targetApkFile)
                        }
                    } else {
                        // Quietly download the real APK in the background; show pill only when ready
                        downloadApkSilentlyInBackground(activity, config, targetApkFile)
                    }
                } else {
                    // Clean baseline: local version is up to date (or no valid remote APK URL)
                    if (remoteVersionCode <= currentVersionCode) {
                        clearDismissalCache(activity)
                    }
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
     * Phase 2: Streams the real APK binary from GitHub Releases CDN (handling 302/307 redirects)
     * into context.cacheDir/updates/ on a background worker thread.
     */
    private fun downloadApkSilentlyInBackground(
        activity: AppCompatActivity,
        config: AppVersionConfig,
        targetApkFile: File,
        onProgressUpdate: ((Int, Long, Long) -> Unit)? = null,
        onDownloadFailed: ((String) -> Unit)? = null
    ) {
        if (!isDownloading.compareAndSet(false, true)) return

        val appContext = activity.applicationContext
        val activityRef = WeakReference(activity)

        downloadExecutor.execute {
            val partFile = File(getUpdatesCacheDir(appContext), "KinTrace_v${config.versionCode}.apk.part")
            var connection: HttpURLConnection? = null
            try {
                if (partFile.exists()) partFile.delete()

                var currentUrl = config.apkDownloadUrl.trim()
                var redirects = 0
                while (redirects < 6) {
                    val urlObj = URL(currentUrl)
                    connection = (urlObj.openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/octet-stream, */*")
                        setRequestProperty("User-Agent", "KinTrace-Android-OTA/${config.versionName}")
                    }
                    connection.connect()
                    val code = connection.responseCode
                    if (code in 301..308) {
                        val location = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (location.isNullOrBlank()) break
                        currentUrl = location
                        redirects++
                    } else {
                        break
                    }
                }

                val responseCode = connection?.responseCode ?: -1
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("GitHub CDN returned HTTP $responseCode")
                }

                val totalBytes = connection?.contentLengthLong ?: -1L
                connection?.inputStream?.use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloadedBytes = 0L
                        var bytesRead: Int
                        var lastReportedProgress = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (progress != lastReportedProgress) {
                                    lastReportedProgress = progress
                                    mainHandler.post {
                                        onProgressUpdate?.invoke(progress, downloadedBytes, totalBytes)
                                    }
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (!partFile.exists() || partFile.length() < 10_000L) {
                    partFile.delete()
                    throw IllegalStateException("Downloaded package is incomplete or not a valid APK binary")
                }

                if (targetApkFile.exists()) targetApkFile.delete()
                val renamed = partFile.renameTo(targetApkFile)
                val finalFile = if (renamed) targetApkFile else partFile

                mainHandler.post {
                    val act = activityRef.get()
                    if (act != null && !act.isFinishing && !act.isDestroyed) {
                        showUpdateReadyPill(act, config, finalFile)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("InAppUpdateManager", "Background OTA download skipped/failed: ${e.message}")
                try { partFile.delete() } catch (_: Exception) {}
                mainHandler.post {
                    onDownloadFailed?.invoke(e.message ?: "Download failed")
                }
            } finally {
                try { connection?.disconnect() } catch (_: Exception) {}
                isDownloading.set(false)
            }
        }
    }

    /**
     * Phase 3: Displays Direction 1 (Non-Intrusive Floating Ready Pill) anchored above the navigation island.
     */
    fun showUpdateReadyPill(
        activity: AppCompatActivity,
        config: AppVersionConfig,
        apkFile: File
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        mainHandler.post {
            try {
                val decorView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@post

                // Remove any existing ready pill
                dismissUpdateReadyPill(immediate = true)

                val inflater = LayoutInflater.from(activity)
                val pillView = inflater.inflate(R.layout.layout_update_ready_pill, decorView, false)

                val tvTitle = pillView.findViewById<TextView>(R.id.tvUpdatePillTitle)
                val tvSubtitle = pillView.findViewById<TextView>(R.id.tvUpdatePillSubtitle)
                val btnInstall = pillView.findViewById<TextView>(R.id.btnUpdatePillInstall)
                val btnDismiss = pillView.findViewById<TextView>(R.id.btnUpdatePillDismiss)

                val sizeMb = String.format("%.1f MB", apkFile.length() / (1024.0 * 1024.0))
                tvTitle.text = "KinTrace ${config.versionName} Ready ($sizeMb)"

                val cleanSummary = config.releaseNotes
                    .lines()
                    .firstOrNull { it.isNotBlank() }
                    ?.removePrefix("•")
                    ?.trim()
                    ?: "New features ready to install"
                tvSubtitle.text = cleanSummary

                // Direct install on button tap
                btnInstall.setOnClickListener {
                    installApk(activity, apkFile)
                }
                // Tapping pill body opens full patch notes dialog before installing
                pillView.setOnClickListener {
                    showUpdateDialog(activity, config)
                }

                btnDismiss.setOnClickListener {
                    val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putLong(KEY_DISMISSED_VERSION, config.versionCode)
                        .putLong(KEY_DISMISSED_TIMESTAMP, System.currentTimeMillis())
                        .apply()
                    dismissUpdateReadyPill(immediate = false)
                }

                val lp = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    val marginH = (16 * activity.resources.displayMetrics.density).toInt()
                    val marginB = (88 * activity.resources.displayMetrics.density).toInt()
                    setMargins(marginH, 0, marginH, marginB)
                }
                pillView.layoutParams = lp

                pillView.translationY = 160f
                pillView.alpha = 0f
                decorView.addView(pillView)
                currentReadyPill = WeakReference(pillView)

                pillView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(340)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()
            } catch (_: Exception) {}
        }
    }

    fun dismissUpdateReadyPill(immediate: Boolean = false) {
        val pill = currentReadyPill?.get() ?: return
        currentReadyPill = null
        val parent = pill.parent as? ViewGroup ?: return
        if (immediate) {
            parent.removeView(pill)
        } else {
            pill.animate()
                .translationY(160f)
                .alpha(0f)
                .setDuration(220)
                .withEndAction {
                    try { parent.removeView(pill) } catch (_: Exception) {}
                }
                .start()
        }
    }

    /**
     * Manual update dialog fallback (used only when explicitly invoked by admin/manual check).
     * Uses the real GitHub cache streaming downloader instead of any fake simulation.
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
                tvUpdateTitle.text = "Critical System Update"
                tvUpdateDesc.text = "A mandatory system update is required before continuing."
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

            val targetApkFile = File(getUpdatesCacheDir(activity), "KinTrace_v${config.versionCode}.apk")
            if (targetApkFile.exists() && targetApkFile.length() > 10_000L) {
                btnDownload.text = "⚡ 1-Tap Install Update (${config.versionName})"
            }

            btnDownload.setOnClickListener {
                val url = config.apkDownloadUrl.trim()
                if (!url.startsWith("https://", ignoreCase = true)) {
                    KinTraceAlert.warning(activity, "Missing Release Package", "No valid GitHub HTTPS APK link is attached to ${config.versionName}.")
                    dialog.dismiss()
                    return@setOnClickListener
                }

                if (targetApkFile.exists() && targetApkFile.length() > 10_000L) {
                    dialog.dismiss()
                    installApk(activity, targetApkFile)
                    return@setOnClickListener
                }

                btnDownload.isEnabled = false
                btnDownload.text = "⏳ Downloading from GitHub..."
                btnLater.isEnabled = false
                layoutProgress.visibility = View.VISIBLE

                downloadApkSilentlyInBackground(
                    activity = activity,
                    config = config,
                    targetApkFile = targetApkFile,
                    onProgressUpdate = { progress, downloaded, total ->
                        pbProgress.progress = progress
                        tvProgressPercent.text = "$progress%"
                        val currentMb = String.format("%.1f", downloaded / (1024.0 * 1024.0))
                        val totalMb = String.format("%.1f", total / (1024.0 * 1024.0))
                        tvProgressStatus.text = "Downloading package ($currentMb MB / $totalMb MB)..."
                    },
                    onDownloadFailed = { errMsg ->
                        tvProgressStatus.text = "Download failed: $errMsg"
                        btnDownload.isEnabled = true
                        btnLater.isEnabled = true
                        btnDownload.text = "Retry GitHub Download"
                    }
                )
            }

            dialog.show()
        }
    }

    /**
     * Checks if the currently installed version is newer than the last-seen version recorded in SharedPreferences.
     * When true (e.g. user just updated to v1.3.3), displays the Dark Emerald & Gold "What's New" release notes modal.
     */
    fun checkAndShowPostUpdateWhatsNew(
        activity: AppCompatActivity,
        onDismiss: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val currentVersionCode = getInstalledVersionCode(activity)
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeenVersion = prefs.getLong(KEY_LAST_SEEN_VERSION, 0L)

        if (currentVersionCode > lastSeenVersion) {
            prefs.edit().putLong(KEY_LAST_SEEN_VERSION, currentVersionCode).apply()
            firestoreHelper.fetchLatestAppVersion(
                onSuccess = { config ->
                    if (activity.isFinishing || activity.isDestroyed) return@fetchLatestAppVersion
                    val vName = config?.versionName ?: "v1.3.4"
                    val notes = if (!config?.releaseNotes.isNullOrBlank()) config!!.releaseNotes else CURRENT_RELEASE_NOTES
                    showWhatsNewDialog(activity, vName, notes, onDismiss)
                },
                onFailure = {
                    if (activity.isFinishing || activity.isDestroyed) return@fetchLatestAppVersion
                    showWhatsNewDialog(activity, "v1.3.4", CURRENT_RELEASE_NOTES, onDismiss)
                }
            )
        } else {
            onDismiss()
        }
    }

    /**
     * Shows the elegant Dark Emerald & Gold "What's New in KinTrace" post-update announcement dialog.
     */
    fun showWhatsNewDialog(
        activity: AppCompatActivity,
        versionName: String,
        releaseNotes: String,
        onDismiss: () -> Unit = {}
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            try {
                val view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)

                val tvVersionTag = view.findViewById<TextView>(R.id.tvVersionTag)
                val tvUpdateTitle = view.findViewById<TextView>(R.id.tvUpdateTitle)
                val tvUpdateDesc = view.findViewById<TextView>(R.id.tvUpdateDesc)
                val tvReleaseNotes = view.findViewById<TextView>(R.id.tvReleaseNotes)
                val layoutProgress = view.findViewById<LinearLayout>(R.id.layoutDownloadProgress)
                val btnAction = view.findViewById<Button>(R.id.btnDownloadUpdate)
                val btnLater = view.findViewById<Button>(R.id.btnRemindLater)

                val cleanTag = if (versionName.startsWith("v", ignoreCase = true)) versionName else "v$versionName"
                tvVersionTag.text = "KinTrace $cleanTag Active"
                tvUpdateTitle.text = "Welcome to KinTrace $cleanTag!"
                tvUpdateDesc.text = "Here is what was recently added in this milestone to enhance your family heritage experience."
                tvReleaseNotes.text = releaseNotes.ifBlank { CURRENT_RELEASE_NOTES }

                layoutProgress.visibility = View.GONE
                btnLater.visibility = View.GONE
                btnAction.text = "Awesome, Let's Explore! ✨"

                val dialog = AlertDialog.Builder(activity)
                    .setView(view)
                    .setCancelable(true)
                    .create()

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                btnAction.setOnClickListener {
                    dialog.dismiss()
                }

                dialog.setOnDismissListener {
                    onDismiss()
                }

                dialog.show()
            } catch (_: Exception) {
                onDismiss()
            }
        }
    }

    /**
     * Phase 2 & 3: Verifies unknown app installation permission on Android 8.0+ (API 26+)
     * and launches Android's native PackageInstaller via FileProvider.
     */
    fun installApk(activity: AppCompatActivity, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            KinTraceAlert.warning(activity, "Package Missing", "APK file not found in cache. Please retry download.")
            return
        }

        // Fact-checked Android 8.0+ (API 26+) security requirement:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                KinTraceAlert.security(
                    activity,
                    "Allow App Updates",
                    "Please toggle 'Allow from this source' once so Android can update KinTrace.",
                    "PERMISSION"
                )
                try {
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(permissionIntent)
                } catch (e: Exception) {
                    KinTraceAlert.show(activity, "Could not open permission settings: ${e.message}")
                }
                return
            }
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
            dismissUpdateReadyPill(immediate = true)
            activity.startActivity(installIntent)
        } catch (e: Exception) {
            KinTraceAlert.show(activity, "Could not open installer: ${e.message}")
        }
    }
}