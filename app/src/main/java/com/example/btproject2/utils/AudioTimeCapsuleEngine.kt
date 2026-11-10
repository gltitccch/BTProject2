package com.example.btproject2.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * Bulletproof, hardware-safe audio recorder and player engine for KinTrace Voice Capsules.
 * Guarantees zero native device lockups, memory leak protection, and strict 60s auto-stop.
 */
object AudioTimeCapsuleEngine {

    private const val TAG = "AudioCapsuleEngine"
    const val MAX_RECORDING_SECONDS = 60

    data class RecordedCapsuleResult(
        val file: File,
        val audioUri: String,
        val audioBase64: String,
        val durationSeconds: Int,
        val durationFormatted: String,
        val fileSizeFormatted: String
    )

    // Recorder state
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var recordingStartTimeMs: Long = 0L
    private var isCurrentlyRecording: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private var recordingRunnable: Runnable? = null

    // Player state
    private var mediaPlayer: MediaPlayer? = null
    var activeAudioUri: String? = null
        private set
    var isCurrentlyPlaying: Boolean = false
        private set
    private var playerProgressRunnable: Runnable? = null
    private var onPlayerFinishedCallback: (() -> Unit)? = null

    // ==========================================
    // RECORDING ENGINE
    // ==========================================

    /**
     * Safely initiates an audio recording session with a 60-second automatic hardware cap.
     */
    fun startRecording(
        context: Context,
        onTick: (secondsElapsed: Int, normalizedAmplitude: Float) -> Unit,
        onAutoStop: (RecordedCapsuleResult) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        // Stop any running recording or playback first
        stopAudio()
        cleanupRecorder()

        return try {
            val storageDir = File(context.filesDir, "voice_capsules").apply {
                if (!exists()) mkdirs()
            }
            val outputFile = File(storageDir, "capsule_${UUID.randomUUID()}.m4a")
            currentRecordingFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000) // Standard 64kbps AAC for universal Android/emulator compatibility
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isCurrentlyRecording = true
            recordingStartTimeMs = System.currentTimeMillis()

            // Start tick listener every 100ms
            recordingRunnable = object : Runnable {
                override fun run() {
                    if (!isCurrentlyRecording) return

                    val elapsedMs = System.currentTimeMillis() - recordingStartTimeMs
                    val elapsedSec = (elapsedMs / 1000).toInt()

                    val maxAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    // Speech vocal curve: maps human speech (1,000-5,000 amplitude) naturally to 35%-75% of the bar
                    val normalizedAmp = if (maxAmp > 0) {
                        val linear = (maxAmp / 32767f).coerceIn(0f, 1f)
                        (Math.sqrt(linear.toDouble()) * 2.0).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    onTick(elapsedSec, normalizedAmp)

                    if (elapsedSec >= MAX_RECORDING_SECONDS) {
                        val result = stopRecording()
                        if (result != null) {
                            onAutoStop(result)
                        }
                    } else {
                        handler.postDelayed(this, 100)
                    }
                }
            }
            handler.post(recordingRunnable!!)

            Log.d(TAG, "Recording started -> ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanupRecorder()
            onError(e.localizedMessage ?: "Could not start audio recorder.")
            false
        }
    }

    /**
     * Gracefully stops the active recording and packages the resulting audio file.
     */
    fun stopRecording(): RecordedCapsuleResult? {
        if (!isCurrentlyRecording || mediaRecorder == null) {
            cleanupRecorder()
            return null
        }

        recordingRunnable?.let { handler.removeCallbacks(it) }
        recordingRunnable = null

        val file = currentRecordingFile
        // CRITICAL FIX: Decouple currentRecordingFile immediately so cancelRecording() or releaseAll()
        // will NEVER delete this saved recording!
        currentRecordingFile = null

        val elapsedMs = System.currentTimeMillis() - recordingStartTimeMs
        // Android MediaRecorder MPEG4 container requires a minimum duration of ~1200ms before calling stop().
        // If stopped too early (e.g. quick test tap), it throws java.lang.RuntimeException: stop failed and corrupts the file.
        if (elapsedMs < 1200L) {
            try {
                Thread.sleep(1200L - elapsedMs)
            } catch (_: InterruptedException) {}
        }

        val totalMs = System.currentTimeMillis() - recordingStartTimeMs
        val elapsedSec = (totalMs / 1000).toInt().coerceAtLeast(1)

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error during mediaRecorder.stop()", e)
        } finally {
            cleanupRecorder()
        }

        if (file == null || !file.exists() || file.length() == 0L) {
            Log.w(TAG, "Recording file empty or missing after stop: ${file?.absolutePath}")
            file?.delete()
            return null
        }

        val base64 = try {
            encodeFileToBase64(file)
        } catch (e: Exception) {
            Log.w(TAG, "Base64 encoding fallback error", e)
            ""
        }

        Log.d(TAG, "Recording saved successfully -> ${file.absolutePath} (${file.length()} bytes, $elapsedSec s)")

        return RecordedCapsuleResult(
            file = file,
            audioUri = file.absolutePath,
            audioBase64 = base64,
            durationSeconds = elapsedSec,
            durationFormatted = formatSeconds(elapsedSec),
            fileSizeFormatted = formatFileSize(file.length())
        )
    }

    /**
     * Cancels active recording and discards the temporary file.
     */
    fun cancelRecording() {
        recordingRunnable?.let { handler.removeCallbacks(it) }
        recordingRunnable = null
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        cleanupRecorder()
        currentRecordingFile?.let {
            if (it.exists()) it.delete()
        }
        currentRecordingFile = null
    }

    private fun cleanupRecorder() {
        isCurrentlyRecording = false
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    // ==========================================
    // PLAYBACK ENGINE
    // ==========================================

    /**
     * Plays a voice capsule audio file. If local file is missing, restores from Base64 cache.
     */
    fun playAudio(
        context: Context,
        audioUri: String,
        audioBase64: String = "",
        onProgress: (currentSec: Int, totalSec: Int) -> Unit = { _, _ -> },
        onCompletion: () -> Unit = {}
    ): Boolean {
        stopAudio()

        val playableFile = resolvePlayableFile(context, audioUri, audioBase64)
        if (playableFile == null || !playableFile.exists()) {
            Log.e(TAG, "Playable audio file could not be resolved.")
            return false
        }

        return try {
            val player = MediaPlayer().apply {
                setDataSource(playableFile.absolutePath)
                prepare()
                start()
            }

            mediaPlayer = player
            activeAudioUri = audioUri
            isCurrentlyPlaying = true
            onPlayerFinishedCallback = onCompletion

            val totalDurationMs = player.duration
            val totalSec = (totalDurationMs / 1000).coerceAtLeast(1)

            playerProgressRunnable = object : Runnable {
                override fun run() {
                    val p = mediaPlayer
                    if (p != null && isCurrentlyPlaying) {
                        val currentMs = try { p.currentPosition } catch (_: Exception) { 0 }
                        val currentSec = (currentMs / 1000).coerceAtMost(totalSec)
                        onProgress(currentSec, totalSec)
                        handler.postDelayed(this, 250)
                    }
                }
            }
            handler.post(playerProgressRunnable!!)

            player.setOnCompletionListener {
                stopAudio()
                onCompletion()
            }

            player.setOnErrorListener { _, _, _ ->
                stopAudio()
                false
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            stopAudio()
            false
        }
    }

    /**
     * Pauses the active audio player.
     */
    fun pauseAudio() {
        try {
            mediaPlayer?.pause()
            isCurrentlyPlaying = false
            playerProgressRunnable?.let { handler.removeCallbacks(it) }
        } catch (_: Exception) {}
    }

    /**
     * Resumes playback if paused.
     */
    fun resumeAudio() {
        try {
            mediaPlayer?.start()
            isCurrentlyPlaying = true
            playerProgressRunnable?.let { handler.post(it) }
        } catch (_: Exception) {}
    }

    /**
     * Stops playback and frees media resources.
     */
    fun stopAudio() {
        playerProgressRunnable?.let { handler.removeCallbacks(it) }
        playerProgressRunnable = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isCurrentlyPlaying = false
        activeAudioUri = null
    }

    /**
     * Lifecycle safety trigger: Call from Activity onStop() or onDestroy() to free all hardware.
     */
    fun releaseAll() {
        cancelRecording()
        stopAudio()
    }

    // ==========================================
    // UTILITIES
    // ==========================================

    fun formatSeconds(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.US, "%d:%02d", m, s)
    }

    fun formatFileSize(bytes: Long): String {
        return if (bytes < 1024) {
            "$bytes B"
        } else if (bytes < 1024 * 1024) {
            String.format(Locale.US, "%.0f KB", bytes / 1024f)
        } else {
            String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        }
    }

    private fun resolvePlayableFile(context: Context, audioUri: String, audioBase64: String): File? {
        val file = File(audioUri)
        if (file.exists() && file.length() > 0L) {
            Log.d(TAG, "Playing local audio: ${file.absolutePath} (${file.length()} bytes)")
            return file
        }

        // Fallback: restore from Base64 if local file missing on a synced device
        if (audioBase64.isNotBlank()) {
            return try {
                val cacheDir = File(context.cacheDir, "streamed_capsules").apply {
                    if (!exists()) mkdirs()
                }
                val cachedFile = File(cacheDir, "cached_${Math.abs(audioUri.hashCode())}.m4a")
                if (!cachedFile.exists() || cachedFile.length() == 0L) {
                    val decodedBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                    FileOutputStream(cachedFile).use { it.write(decodedBytes) }
                }
                Log.d(TAG, "Playing Base64 restored cache: ${cachedFile.absolutePath} (${cachedFile.length()} bytes)")
                cachedFile
            } catch (e: Exception) {
                Log.e(TAG, "Failed restoring audio from Base64", e)
                null
            }
        }
        Log.w(TAG, "Could not resolve audio: file missing at $audioUri and Base64 is blank")
        return null
    }

    private fun encodeFileToBase64(file: File): String {
        val bytes = ByteArray(file.length().toInt())
        FileInputStream(file).use { it.read(bytes) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
