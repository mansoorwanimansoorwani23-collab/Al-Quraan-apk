package com.example.ai

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.IslamicDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GroundingSource(
    val title: String,
    val url: String
)

data class AiScholarResponse(
    val text: String,
    val audioBase64: String? = null,
    val sources: List<GroundingSource> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val isError: Boolean = false
)

object GeminiScholarService {

    private const val TAG = "GeminiScholarService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_INSTRUCTION = """
You are 'DeenMate AI Scholar', an authentic, compassionate, and highly knowledgeable Islamic lifestyle and knowledge assistant in the DeenMate app (developed by Rauf).
Guidelines:
1. Provide authentic, respectful, and well-referenced answers based on the Holy Qur'an, Sahih Hadith (Bukhari, Muslim, Tirmidhi, etc.), and mainstream scholarly consensus.
2. For questions regarding current events, news, global Ramadan dates, moon sighting updates, and contemporary Islamic affairs, use real-time Google Search data and cite facts accurately.
3. Be welcoming, warm, and encourage good deeds, mindfulness, and peace.
4. When discussing Quranic verses or Hadith, quote clearly with chapter/verse or narrator references.
"""

    /**
     * Ask a question with Google Search Grounding for real-time news, current events, and fact-checking.
     * Falls back to standard Gemini models or built-in Islamic Knowledge Base if API or network is unavailable.
     */
    suspend fun askGroundedScholar(prompt: String): AiScholarResponse = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext generateOfflineIslamicResponse(prompt)
        }

        // 1. Try with Google Search Grounding
        try {
            val response = tryGenerateGroundedContent(apiKey, prompt)
            if (response != null) {
                return@withContext response
            }
        } catch (e: Exception) {
            Log.w(TAG, "Grounded content generation failed, attempting standard generation: ${e.message}")
        }

        // 2. Try standard Gemini generation without grounding tools
        try {
            val standardResponse = tryGenerateStandardContent(apiKey, prompt)
            if (standardResponse != null) {
                return@withContext standardResponse
            }
        } catch (e: Exception) {
            Log.w(TAG, "Standard Gemini generation failed, falling back to local database: ${e.message}")
        }

        // 3. Robust local knowledge fallback
        generateOfflineIslamicResponse(prompt)
    }

    private fun tryGenerateGroundedContent(apiKey: String, prompt: String): AiScholarResponse? {
        val endpoint = "$BASE_URL/gemini-2.0-flash:generateContent?key=$apiKey"

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

            // Enable Google Search Grounding Tool
            val toolsArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("googleSearch", JSONObject())
                })
            }
            put("tools", toolsArr)

            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", SYSTEM_INSTRUCTION) })
                })
            })

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("topP", 0.95)
            })
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(endpoint).post(body).build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.w(TAG, "Grounded API returned ${response.code}: $responseBody")
            return null
        }

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        val textBuilder = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i)
                val t = p?.optString("text") ?: ""
                textBuilder.append(t)
            }
        }

        val sources = mutableListOf<GroundingSource>()
        val searchQueries = mutableListOf<String>()

        val groundingMetadata = firstCandidate?.optJSONObject("groundingMetadata")
        if (groundingMetadata != null) {
            val queriesArr = groundingMetadata.optJSONArray("webSearchQueries")
            if (queriesArr != null) {
                for (i in 0 until queriesArr.length()) {
                    searchQueries.add(queriesArr.getString(i))
                }
            }

            val chunks = groundingMetadata.optJSONArray("groundingChunks")
            if (chunks != null) {
                for (i in 0 until chunks.length()) {
                    val chunkObj = chunks.optJSONObject(i)
                    val web = chunkObj?.optJSONObject("web")
                    if (web != null) {
                        val title = web.optString("title", "Islamic Reference")
                        val uri = web.optString("uri", "")
                        if (uri.isNotEmpty()) {
                            sources.add(GroundingSource(title, uri))
                        }
                    }
                }
            }
        }

        val fullText = textBuilder.toString()
        if (fullText.isBlank()) return null

        return AiScholarResponse(
            text = fullText,
            sources = sources.distinctBy { it.url },
            searchQueries = searchQueries
        )
    }

    private fun tryGenerateStandardContent(apiKey: String, prompt: String): AiScholarResponse? {
        val endpoint = "$BASE_URL/gemini-2.0-flash:generateContent?key=$apiKey"

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
                    put(JSONObject().apply { put("text", SYSTEM_INSTRUCTION) })
                })
            })

            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("topP", 0.95)
            })
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(endpoint).post(body).build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.w(TAG, "Standard API returned ${response.code}: $responseBody")
            return null
        }

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        val textBuilder = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i)
                val t = p?.optString("text") ?: ""
                textBuilder.append(t)
            }
        }

        val text = textBuilder.toString()
        if (text.isBlank()) return null
        return AiScholarResponse(text = text)
    }

    /**
     * Gemini Live Voice Conversation:
     * Generates native audio voice response directly from Gemini (without using standard Android TTS).
     */
    suspend fun generateLiveVoiceResponse(userQuery: String): AiScholarResponse = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext generateOfflineIslamicResponse(userQuery)
        }

        try {
            val endpoint = "$BASE_URL/gemini-2.0-flash:generateContent?key=$apiKey"

            val requestJson = JSONObject().apply {
                val contentsArr = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", userQuery) })
                        })
                    })
                }
                put("contents", contentsArr)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are DeenMate AI conversational voice scholar by Rauf. Respond in a soothing, clear, and concise voice with authentic Islamic wisdom.")
                        })
                    })
                })

                // Request both AUDIO and TEXT modalities
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                        put("TEXT")
                    })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Kore")
                            })
                        })
                    })
                })
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(TAG, "Audio endpoint fallback: ${response.code}, falling back to grounded text")
                return@withContext askGroundedScholar(userQuery)
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            var textResult = ""
            var audioBase64: String? = null

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i)
                    if (p != null) {
                        if (p.has("text")) {
                            textResult += p.optString("text")
                        }
                        if (p.has("inlineData")) {
                            val inlineData = p.optJSONObject("inlineData")
                            val mime = inlineData?.optString("mimeType") ?: ""
                            if (mime.startsWith("audio/")) {
                                audioBase64 = inlineData?.optString("data")
                            }
                        }
                    }
                }
            }

            AiScholarResponse(
                text = textResult.ifBlank { "Assalamu Alaikum wa Rahmatullah. May Allah grant you beneficial knowledge and peace." },
                audioBase64 = audioBase64
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception in generateLiveVoiceResponse", e)
            askGroundedScholar(userQuery)
        }
    }

    private fun generateOfflineIslamicResponse(prompt: String): AiScholarResponse {
        val qLower = prompt.lowercase()

        val matchingHadith = IslamicDataSource.HADITHS.find {
            qLower.contains(it.category.lowercase()) ||
            it.chapterTitle.lowercase().split(" ").any { w -> w.length > 3 && qLower.contains(w) } ||
            it.englishTranslation.lowercase().split(" ").any { w -> w.length > 4 && qLower.contains(w) }
        }

        val matchingDua = IslamicDataSource.DUAS_AND_AZKAR.find {
            qLower.contains(it.category.lowercase()) ||
            it.title.lowercase().split(" ").any { w -> w.length > 3 && qLower.contains(w) }
        }

        val answerText = buildString {
            append("Assalamu Alaikum wa Rahmatullah,\n\n")
            if (qLower.contains("tahajjud") || qLower.contains("night prayer")) {
                append("The Night Prayer (Tahajjud) is one of the most rewarding voluntary acts of worship in Islam.\n\n")
                append("📖 **Holy Qur'an (17:79):**\n\"And from [part of] the night, pray with it as additional [worship] for you; it is expected that your Lord will resurrect you to a praised station.\"\n\n")
                append("✨ **Best Practice:** Performed after waking up in the last third of the night in pairs of 2 rak'ahs followed by Witr.")
            } else if (qLower.contains("kursi") || qLower.contains("2:255")) {
                append("Ayat al-Kursi (Surah Al-Baqarah 2:255) is regarded as the greatest verse in the Holy Qur'an.\n\n")
                append("📖 **Ayat al-Kursi:**\n\"اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ...\"\n\"Allah - there is no deity except Him, the Ever-Living, the Sustainer of all existence...\"\n\n")
                append("📜 **Hadith (Sahih Muslim):** The Prophet ﷺ confirmed to Ubayy ibn Ka'b (RA) that Ayat al-Kursi is the greatest verse in the Book of Allah.")
            } else if (qLower.contains("friday") || qLower.contains("jumu")) {
                append("The Sunnahs of Friday (Jumu'ah) include:\n")
                append("1. Taking a bath (Ghusl) & wearing clean clothes\n")
                append("2. Applying pleasant scent/attar\n")
                append("3. Arriving early to the Masjid and listening attentively to the Khutbah\n")
                append("4. Reciting Surah Al-Kahf (18)\n")
                append("5. Sending abundant Salawat (blessings) upon the Prophet Muhammad ﷺ.")
            } else if (qLower.contains("fasting") || qLower.contains("ramadan")) {
                append("Fasting (Sawm) during Ramadan is the 4th pillar of Islam.\n\n")
                append("📖 **Holy Qur'an (2:183):**\n\"O you who have believed, decreed upon you is fasting as it was decreed upon those before you that you may become righteous.\"\n\n")
                append("Travelers, the ill, and those unable are granted exemptions to make up missed days or pay Fidyah (2:184-185).")
            } else if (matchingHadith != null) {
                append("Here is an authentic narration regarding your inquiry:\n\n")
                append("📜 **${matchingHadith.collection} (${matchingHadith.hadithNumber}):**\n")
                append("\"${matchingHadith.englishTranslation}\"\n")
                append("— Narrated by ${matchingHadith.narrator} (${matchingHadith.grade})\n\n")
                append("Arabic: ${matchingHadith.arabicText}")
            } else if (matchingDua != null) {
                append("Here is a recommended authentic supplication from the Sunnah:\n\n")
                append("🤲 **${matchingDua.title}** (${matchingDua.category}):\n")
                append("${matchingDua.arabicText}\n\n")
                append("📝 *Transliteration:* ${matchingDua.transliteration}\n\n")
                append("💬 *Translation:* \"${matchingDua.englishTranslation}\"\n\n")
                append("📚 *Reference:* ${matchingDua.reference}")
            } else {
                append("In Islam, seeking beneficial knowledge, maintaining constant mindfulness (Taqwa), establishing timely daily prayers, and treating all creation with kindness are central tenets.\n\n")
                append("📜 **Sahih al-Bukhari:** \"Actions are judged by their intentions, and every person will get what they intended.\"\n\n")
                append("How else may I assist you with Quranic insights, Hadith, or prayer timings?")
            }
        }

        return AiScholarResponse(
            text = answerText,
            sources = listOf(
                GroundingSource("Holy Qur'an & Sunnah Archives", "https://quran.com"),
                GroundingSource("Sunnah.com Hadith Database", "https://sunnah.com")
            )
        )
    }
}
