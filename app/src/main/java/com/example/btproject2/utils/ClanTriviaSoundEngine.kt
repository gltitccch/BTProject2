package com.example.btproject2.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * ClanTriviaSoundEngine:
 * Synthesizes crisp 8-bit retro arcade sound effects in real-time
 * without relying on external MP3/WAV audio assets.
 */
object ClanTriviaSoundEngine {

    private const val SAMPLE_RATE = 44100

    /**
     * Synthesizes the authentic 2-tone 8-bit coin chirp:
     * Tone 1 (B5, 987.77 Hz) -> Tone 2 (E6, 1318.51 Hz).
     */
    fun playCoinSound() {
        thread(isDaemon = true, name = "ClanTriviaSound-Coin") {
            try {
                val dur1 = (SAMPLE_RATE * 0.08).toInt()
                val dur2 = (SAMPLE_RATE * 0.27).toInt()
                val totalSamples = dur1 + dur2
                val buffer = ShortArray(totalSamples)

                val freq1 = 987.77
                val freq2 = 1318.51

                for (i in 0 until dur1) {
                    val phase = 2.0 * PI * i * freq1 / SAMPLE_RATE
                    val value = if (sin(phase) >= 0) 0.3 else -0.3
                    val envelope = 1.0 - (i.toDouble() / dur1 * 0.2)
                    buffer[i] = (value * envelope * Short.MAX_VALUE).toInt().toShort()
                }

                for (i in 0 until dur2) {
                    val phase = 2.0 * PI * i * freq2 / SAMPLE_RATE
                    val value = if (sin(phase) >= 0) 0.3 else -0.3
                    val envelope = 1.0 - (i.toDouble() / dur2 * 0.9)
                    buffer[dur1 + i] = (value * envelope * Short.MAX_VALUE).toInt().toShort()
                }

                playBuffer(buffer)
            } catch (_: Exception) {
                // Ignore audio errors gracefully
            }
        }
    }

    /**
     * Synthesizes an 8-bit pitch-drop thud (sawtooth descending 220Hz -> 60Hz)
     * when a player selects the wrong answer or trapdoor drops.
     */
    fun playWrongSound() {
        thread(isDaemon = true, name = "ClanTriviaSound-Wrong") {
            try {
                val duration = (SAMPLE_RATE * 0.30).toInt()
                val buffer = ShortArray(duration)

                var phase = 0.0
                for (i in 0 until duration) {
                    val progress = i.toDouble() / duration
                    val currentFreq = 220.0 - (160.0 * progress)
                    phase += 2.0 * PI * currentFreq / SAMPLE_RATE
                    val saw = ((phase % (2.0 * PI)) / PI) - 1.0
                    val envelope = 1.0 - (progress * 0.85)
                    buffer[i] = (saw * envelope * 0.35 * Short.MAX_VALUE).toInt().toShort()
                }

                playBuffer(buffer)
            } catch (_: Exception) {
                // Ignore audio errors gracefully
            }
        }
    }

    private fun playBuffer(buffer: ShortArray) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val trackSize = maxOf(buffer.size * 2, minBufferSize)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(trackSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
        Thread.sleep((buffer.size * 1000L / SAMPLE_RATE) + 50)
        audioTrack.stop()
        audioTrack.release()
    }
}
