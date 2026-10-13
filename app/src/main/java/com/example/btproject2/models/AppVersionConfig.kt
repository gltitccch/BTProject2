package com.example.btproject2.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Configuration record for Over-The-Air (OTA) in-app updates (FR-11).
 * Stored in Cloud Firestore at `system_config/app_version`.
 */
@IgnoreExtraProperties
data class AppVersionConfig(
    @get:PropertyName("versionCode") @set:PropertyName("versionCode")
    var versionCode: Long = 1L,

    @get:PropertyName("versionName") @set:PropertyName("versionName")
    var versionName: String = "1.0.0",

    @get:PropertyName("apkDownloadUrl") @set:PropertyName("apkDownloadUrl")
    var apkDownloadUrl: String = "",

    @get:PropertyName("releaseNotes") @set:PropertyName("releaseNotes")
    var releaseNotes: String = "",

    @get:PropertyName("fileSizeBytes") @set:PropertyName("fileSizeBytes")
    var fileSizeBytes: Long = 0L,

    @get:PropertyName("isMandatory") @set:PropertyName("isMandatory")
    var isMandatory: Boolean = false,

    @get:PropertyName("publishedAt") @set:PropertyName("publishedAt")
    var publishedAt: Long = System.currentTimeMillis(),

    @get:PropertyName("publishedBy") @set:PropertyName("publishedBy")
    var publishedBy: String = "admin@kintrace.ph"
)
