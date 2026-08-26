package com.example.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Ayah
import com.example.data.model.Surah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

data class ReciterVoice(
    val id: String,
    val name: String,
    val arabicName: String,
    val geminiVoiceName: String,
    val cdnFolder: String,
    val styleDescription: String
)

object ReciterVoicePacks {
    val RECITERS = listOf(
        ReciterVoice(
            id = "mishary",
            name = "Mishary Rashid Alafasy",
            arabicName = "مشاري راشد العفاسي",
            geminiVoiceName = "Puck",
            cdnFolder = "Alafasy_128kbps",
            styleDescription = "Melodic, crystal clear, emotionally resonant Tajweed"
        ),
        ReciterVoice(
            id = "sudais",
            name = "Abdul Rahman Al-Sudais",
            arabicName = "عبد الرحمن السديس",
            geminiVoiceName = "Fenrir",
            cdnFolder = "Abdurrahmaan_As-Sudais_192kbps",
            styleDescription = "Grand Haram Makkah style, powerful & authoritative"
        ),
        ReciterVoice(
            id = "muaiqly",
            name = "Maher Al-Muaiqly",
            arabicName = "ماهر المعيقلي",
            geminiVoiceName = "Charon",
            cdnFolder = "MaherAlMuaiqly128kbps",
            styleDescription = "Calm, steady, and spiritually soothing tempo"
        ),
        ReciterVoice(
            id = "ghamdi",
            name = "Saad Al-Ghamdi",
            arabicName = "سعد الغامدي",
            geminiVoiceName = "Aoede",
            cdnFolder = "Ghamadi_40kbps",
            styleDescription = "Gentle, reverent, and rhythmic flow"
        ),
        ReciterVoice(
            id = "husary",
            name = "Mahmoud Khalil Al-Husary",
            arabicName = "محمود خليل الحصري",
            geminiVoiceName = "Kore",
            cdnFolder = "Husary_128kbps",
            styleDescription = "Master standard classical Murattal & strict Tajweed"
        ),
        ReciterVoice(
            id = "shatri",
            name = "Abu Bakr Al-Shatri",
            arabicName = "أبو بكر الشاطري",
            geminiVoiceName = "Orus",
            cdnFolder = "Abu_Bakr_Ash-Shaatree_128kbps",
            styleDescription = "Deep, contemplative, and soulful recitation"
        )
    )

    val DEFAULT_RECITER = RECITERS[0]
}

sealed class QuranAudioResult {
    data class Success(val audioFile: File, val isFromAi: Boolean) : QuranAudioResult()
    data class Error(val message: String, val canRetry: Boolean = true) : QuranAudioResult()
}

object QuranAudioService {

    private const val TAG = "QuranAudioService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Complete Audio Pipeline:
     * 1. Check local on-device cache for this Ayah and Reciter.
     * 2. If not cached, call Cloud AI API (Gemini Native Audio) with selected reciter voice style.
     * 3. Decode & convert PCM/WAV/MP3 bytes properly with standard RIFF WAV container.
     * 4. If AI API is unconfigured/fails, seamlessly stream/cache from verified authentic Quran Audio CDN.
     */
    suspend fun getAyahAudioFile(
        context: Context,
        surah: Surah,
        ayah: Ayah,
        reciter: ReciterVoice
    ): QuranAudioResult = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "quran_audio")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val cachedFile = File(cacheDir, "${reciter.id}_${surah.number}_${ayah.numberInSurah}.wav")
        val cachedMp3 = File(cacheDir, "${reciter.id}_${surah.number}_${ayah.numberInSurah}.mp3")

        if (cachedFile.exists() && cachedFile.length() > 1000) {
            return@withContext QuranAudioResult.Success(cachedFile, isFromAi = true)
        }
        if (cachedMp3.exists() && cachedMp3.length() > 1000) {
            return@withContext QuranAudioResult.Success(cachedMp3, isFromAi = false)
        }

        // Try Generating via Cloud AI API
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val aiResult = generateAyahAudioWithGemini(
                    apiKey = apiKey,
                    surah = surah,
                    ayah = ayah,
                    reciter = reciter,
                    outputFile = cachedFile
                )
                if (aiResult is QuranAudioResult.Success) {
                    return@withContext aiResult
                }
                Log.w(TAG, "Gemini audio generation fallback, attempting CDN stream: ${(aiResult as? QuranAudioResult.Error)?.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Gemini API exception, fallback to CDN: ${e.message}")
            }
        }

        // Fallback to verified authentic recitation CDN
        try {
            val cdnResult = downloadAyahFromCdn(
                surahNumber = surah.number,
                ayahNumberInSurah = ayah.numberInSurah,
                reciter = reciter,
                outputFile = cachedMp3
            )
            return@withContext cdnResult
        } catch (e: Exception) {
            Log.e(TAG, "Error in audio pipeline", e)
            return@withContext QuranAudioResult.Error(
                message = "Unable to load recitation for Ayah ${ayah.numberInSurah}. Please check your connection or retry.",
                canRetry = true
            )
        }
    }

    private suspend fun generateAyahAudioWithGemini(
        apiKey: String,
        surah: Surah,
        ayah: Ayah,
        reciter: ReciterVoice,
        outputFile: File
    ): QuranAudioResult = withContext(Dispatchers.IO) {
        val endpoint = "$BASE_URL/gemini-2.0-flash:generateContent?key=$apiKey"

        val prompt = "Recite Holy Qur'an Surah ${surah.nameEnglish} (Chapter ${surah.number}), Ayah ${ayah.numberInSurah}: \"${ayah.arabicText}\". Recite purely in Arabic with authentic Tajweed rules in the melodic, devotional style of Moulana ${reciter.name}."

        val systemPrompt = "You are an expert Qari and Quran reciter. You strictly recite Holy Quran verses in classical Arabic Tajweed with correct Madd, Ghunnah, Qalqalah, and waqf. Adopt the vocal timbre, pace, and reverence of ${reciter.name} (${reciter.styleDescription}). Return only the recitation audio."

        val requestJson = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            }
            put("contents", contentsArr)

            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })

            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply {
                    put("AUDIO")
                })
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", reciter.geminiVoiceName)
                        })
                    })
                })
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini Audio API returned status ${response.code}: $responseBody")
            return@withContext QuranAudioResult.Error("API returned error code ${response.code}")
        }

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        var rawAudioBytes: ByteArray? = null
        var mimeType = "audio/pcm;rate=24000"

        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i)
                val inlineData = p?.optJSONObject("inlineData")
                if (inlineData != null) {
                    val data = inlineData.optString("data")
                    mimeType = inlineData.optString("mimeType", "audio/pcm;rate=24000")
                    if (data.isNotBlank()) {
                        rawAudioBytes = Base64.decode(data, Base64.DEFAULT)
                        break
                    }
                }
            }
        }

        if (rawAudioBytes == null || rawAudioBytes.isEmpty()) {
            return@withContext QuranAudioResult.Error("Empty audio data returned from API")
        }

        // Convert PCM or raw bytes to standard playable WAV file
        val sampleRate = if (mimeType.contains("16000")) 16000 else 24000
        val playableWavBytes = if (mimeType.contains("pcm") || !mimeType.contains("mp3")) {
            createWavFileBytes(rawAudioBytes, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
        } else {
            rawAudioBytes
        }

        FileOutputStream(outputFile).use { fos ->
            fos.write(playableWavBytes)
        }

        return@withContext QuranAudioResult.Success(outputFile, isFromAi = true)
    }

    private suspend fun downloadAyahFromCdn(
        surahNumber: Int,
        ayahNumberInSurah: Int,
        reciter: ReciterVoice,
        outputFile: File
    ): QuranAudioResult = withContext(Dispatchers.IO) {
        val sStr = String.format(java.util.Locale.US, "%03d", surahNumber)
        val aStr = String.format(java.util.Locale.US, "%03d", ayahNumberInSurah)

        // EveryAyah URL pattern: e.g. https://everyayah.com/data/Alafasy_128kbps/001001.mp3
        val url = "https://everyayah.com/data/${reciter.cdnFolder}/$sStr$aStr.mp3"

        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful || response.body == null) {
            return@withContext QuranAudioResult.Error("Failed to fetch CDN recitation (${response.code})")
        }

        val bytes = response.body!!.bytes()
        if (bytes.isEmpty()) {
            return@withContext QuranAudioResult.Error("Empty audio stream received")
        }

        FileOutputStream(outputFile).use { fos ->
            fos.write(bytes)
        }

        return@withContext QuranAudioResult.Success(outputFile, isFromAi = false)
    }

    /**
     * Builds a standard 44-byte RIFF/WAVE header for linear PCM audio data.
     * Allows Android MediaPlayer and AudioTrack to play raw PCM seamlessly.
     */
    fun createWavFileBytes(pcmData: ByteArray, sampleRate: Int = 24000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalDataLen)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // 'fmt ' subchunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1.toShort()) // AudioFormat 1 = PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // 'data' subchunk
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
