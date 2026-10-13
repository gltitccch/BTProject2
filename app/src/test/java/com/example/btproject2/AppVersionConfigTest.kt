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
}
