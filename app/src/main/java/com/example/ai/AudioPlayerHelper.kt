package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioPlayerHelper(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false

    fun playBase64Audio(base64Audio: String, mimeType: String = "audio/pcm;rate=24000", onCompletion: () -> Unit = {}) {
        stopAudio()
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            if (audioBytes.isEmpty()) {
                Log.e("AudioPlayerHelper", "Empty audio bytes received")
                onCompletion()
                return
            }

            // Check if data is already MP3/WAV format or raw PCM
            val isMp3 = audioBytes.size > 3 && (
                (audioBytes[0] == 'I'.code.toByte() && audioBytes[1] == 'D'.code.toByte() && audioBytes[2] == '3'.code.toByte()) ||
                (audioBytes[0] == 0xFF.toByte() && (audioBytes[1].toInt() and 0xE0) == 0xE0)
            )
            val isWav = audioBytes.size > 4 && audioBytes[0] == 'R'.code.toByte() && audioBytes[1] == 'I'.code.toByte() && audioBytes[2] == 'F'.code.toByte()

            val finalBytes = if (isMp3 || isWav) {
                audioBytes
            } else {
                // Convert raw PCM to standard playable WAV container
                val sampleRate = if (mimeType.contains("16000")) 16000 else 24000
                createWavFileBytes(audioBytes, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
            }

            val suffix = if (isMp3) ".mp3" else ".wav"
            val tempFile = File.createTempFile("gemini_audio_", suffix, context.cacheDir)
            FileOutputStream(tempFile).use { fos ->
                fos.write(finalBytes)
            }

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    this@AudioPlayerHelper.isPlaying = false
                    try { tempFile.delete() } catch (_: Exception) {}
                    onCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayerHelper", "MediaPlayer error: what=$what, extra=$extra")
                    this@AudioPlayerHelper.isPlaying = false
                    try { tempFile.delete() } catch (_: Exception) {}
                    onCompletion()
                    true
                }
                start()
            }
            mediaPlayer = player
            isPlaying = true
        } catch (e: Exception) {
            Log.e("AudioPlayerHelper", "Error playing base64 audio", e)
            isPlaying = false
            onCompletion()
        }
    }

    fun stopAudio() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                }
                mediaPlayer?.release()
                mediaPlayer = null
            }
        } catch (_: Exception) {}
        isPlaying = false
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying

    private fun createWavFileBytes(pcmData: ByteArray, sampleRate: Int = 24000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalDataLen)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16)
        buffer.putShort(1.toShort()) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(totalAudioLen)

        val output = ByteArrayOutputStream(44 + totalAudioLen)
        output.write(header)
        output.write(pcmData)
        return output.toByteArray()
    }
}
