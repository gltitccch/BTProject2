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
            releaseNotes = "• Heart-Throbbing Golden Focal Perspective Glide & Auto-Fade\n• Dynamic Perspective Reset Button & Matrix Profile Modal"
        )

        val isUpdateAvailable = remoteV131Config.versionCode > friendInstalledCode &&
            remoteV131Config.apkDownloadUrl.startsWith("https://")
        assertTrue(isUpdateAvailable)
    }
}