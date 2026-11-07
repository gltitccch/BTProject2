package com.example.btproject2

import com.example.btproject2.models.AppVersionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionConfigTest {

    @Test
    fun defaultAppVersionConfig_hasExpectedDefaults() {
        val config = AppVersionConfig()
        assertEquals(1L, config.versionCode)
        assertEquals("1.0.0", config.versionName)
        assertEquals("", config.apkDownloadUrl)
        assertFalse(config.isMandatory)
        assertEquals("admin@kintrace.ph", config.publishedBy)
    }

    @Test
    fun versionComparison_correctlyIdentifiesNewerIncrements() {
        val currentInstalledCode = 1L
        val remoteConfig = AppVersionConfig(
            versionCode = 2L,
            versionName = "v1.2.0",
            releaseNotes = "• Admin Citadel & Broadcaster",
            isMandatory = false
        )

        val isUpdateAvailable = remoteConfig.versionCode > currentInstalledCode
        assertTrue(isUpdateAvailable)
    }

    @Test
    fun versionComparison_doesNotTriggerWhenUpToDate() {
        val currentInstalledCode = 2L
        val remoteConfig = AppVersionConfig(
            versionCode = 2L,
            versionName = "v1.2.0"
        )

        val isUpdateAvailable = remoteConfig.versionCode > currentInstalledCode
        assertFalse(isUpdateAvailable)
    }

    @Test
    fun mandatoryVersionFlag_isPreserved() {
        val mandatoryConfig = AppVersionConfig(
            versionCode = 3L,
            versionName = "v2.0.0",
            isMandatory = true
        )
        assertTrue(mandatoryConfig.isMandatory)
    }

    @Test
    fun v120Client_detectsV130Increment_andResolvesGitHubReleaseApkUrl() {
        val friendInstalledCode = 2L // Friend on v1.2.0
        val releaseUrl = com.example.btproject2.utils.InAppUpdateManager.buildGitHubReleaseApkUrl("v1.3.0")
        assertEquals(
            "https://github.com/gltitccch/BTProject2/releases/download/v1.3.0/app-release.apk",
            releaseUrl
        )

        val remoteV130Config = AppVersionConfig(
            versionCode = 3L,
            versionName = "v1.3.0",
            apkDownloadUrl = releaseUrl,
            releaseNotes = "• Global Freemium 50-Member Cap & Clan Expansion Queue\n• Step-by-Step Friendly Question Tree Health Wizard"
        )

        val isUpdateAvailable = remoteV130Config.versionCode > friendInstalledCode &&
            remoteV130Config.apkDownloadUrl.startsWith("https://")
        assertTrue(isUpdateAvailable)
    }

    @Test
    fun v130Client_detectsV131Increment_andResolvesGitHubReleaseApkUrl() {
        val friendInstalledCode = 3L // Friend on v1.3.0
        val releaseUrl = com.example.btproject2.utils.InAppUpdateManager.buildGitHubReleaseApkUrl("v1.3.1")
        assertEquals(
            "https://github.com/gltitccch/BTProject2/releases/download/v1.3.1/app-release.apk",
            releaseUrl
        )

        val remoteV131Config = AppVersionConfig(
            versionCode = 4L,
            versionName = "v1.3.1",
            apkDownloadUrl = releaseUrl,
            releaseNotes = "• Advanced Bloodline & Affinity Graph Traversal (Multi-MRCA & Consanguinity Degree)\n• Interactive Tree Zero-Allocation Engine & Viewport Frustum Culling (200+ Members)"
        )

        val isUpdateAvailable = remoteV131Config.versionCode > friendInstalledCode &&
            remoteV131Config.apkDownloadUrl.startsWith("https://")
        assertTrue(isUpdateAvailable)
    }

    @Test
    fun v136Client_detectsV137Increment_andResolvesGitHubReleaseApkUrl() {
        val friendInstalledCode = 9L // Friend on v1.3.6
        val releaseUrl = com.example.btproject2.utils.InAppUpdateManager.buildGitHubReleaseApkUrl("v1.3.7")
        assertEquals(
            "https://github.com/gltitccch/BTProject2/releases/download/v1.3.7/app-release.apk",
            releaseUrl
        )

        val targetReleaseNotes = com.example.btproject2.utils.InAppUpdateManager.CURRENT_RELEASE_NOTES

        val remoteV137Config = AppVersionConfig(
            versionCode = 10L,
            versionName = "v1.3.7",
            apkDownloadUrl = releaseUrl,
            releaseNotes = targetReleaseNotes
        )

        val isUpdateAvailable = remoteV137Config.versionCode > friendInstalledCode &&
            remoteV137Config.apkDownloadUrl.startsWith("https://")
        assertTrue(isUpdateAvailable)
    }

    @Test
    fun v137Client_detectsV138Increment_andResolvesGitHubReleaseApkUrl() {
        val friendInstalledCode = 10L // Friend on v1.3.7
        val releaseUrl = com.example.btproject2.utils.InAppUpdateManager.buildGitHubReleaseApkUrl("v1.3.8")
        assertEquals(
            "https://github.com/gltitccch/BTProject2/releases/download/v1.3.8/app-release.apk",
            releaseUrl
        )

        val targetReleaseNotes = com.example.btproject2.utils.InAppUpdateManager.CURRENT_RELEASE_NOTES

        val remoteV138Config = AppVersionConfig(
            versionCode = 11L,
            versionName = "v1.3.8",
            apkDownloadUrl = releaseUrl,
            releaseNotes = targetReleaseNotes
        )

        val isUpdateAvailable = remoteV138Config.versionCode > friendInstalledCode &&
            remoteV138Config.apkDownloadUrl.startsWith("https://")
        assertTrue(isUpdateAvailable)
    }

    @Test
    fun v139Client_detectsV140Increment_andResolvesGitHubReleaseApkUrl() {
        val friendInstalledCode = 12L // Friend on v1.3.9
        val releaseUrl = com.example.btproject2.utils.InAppUpdateManager.buildGitHubReleaseApkUrl("v1.4.0")
        assertEquals(
            "https://github.com/gltitccch/BTProject2/releases/download/v1.4.0/app-release.apk",
            releaseUrl
        )

        val targetReleaseNotes = com.example.btproject2.utils.InAppUpdateManager.CURRENT_RELEASE_NOTES

        val remoteV140Config = AppVersionConfig(
            versionCode = 13L,
            versionName = "v1.4.0",
            apkDownloadUrl = releaseUrl,
            releaseNotes = targetReleaseNotes
        )

        val isUpdateAvailable = remoteV140Config.versionCode > friendInstalledCode &&
            remoteV140Config.apkDownloadUrl.startsWith("https://")
        assertTrue(isUpdateAvailable)
    }

    @Test
    fun postUpdateDetection_triggersWhenCurrentVersionExceedsLastSeenVersion() {
        val lastSeenVersion = 9L // User was on v1.3.6 before updating
        val currentInstalledCode = 10L // User just updated to v1.3.7
        val shouldShowWhatsNew = currentInstalledCode > lastSeenVersion
        assertTrue(shouldShowWhatsNew)
    }

    @Test
    fun postUpdateDetection_suppressedWhenAlreadySeen() {
        val lastSeenVersion = 10L // User already saw v1.3.7 what's new modal
        val currentInstalledCode = 10L // Still on v1.3.7
        val shouldShowWhatsNew = currentInstalledCode > lastSeenVersion
        assertFalse(shouldShowWhatsNew)
    }

    @Test
    fun currentReleaseNotes_containsAllCoreFeatureHighlights() {
        val notes = com.example.btproject2.utils.InAppUpdateManager.CURRENT_RELEASE_NOTES
        assertTrue(notes.contains("Multi-Device Account Vault Cloud Sync"))
        assertTrue(notes.contains("Smart Disposable Email Shield"))
        assertTrue(notes.contains("Quick-Fill Domain Chips"))
        assertTrue(notes.contains("Free-Moving Node Pencil Mode"))
    }
}