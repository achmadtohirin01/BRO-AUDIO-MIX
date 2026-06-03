package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    
    // Explicit OkHttpClient configuration with 60-second timeouts conformant with gemini-api skill gotchas
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateDAWAdvice(userPrompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Bro AI Assistant is ready! Please configure your GEMINI_API_KEY in the Secrets panel."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        // Build request payload using standard JSON construction for complete safety
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", userPrompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
            })
            // Systematic DAW instruction keeping responses highly expert, brief, and inspiring
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are BRO AI, an expert music producer and virtual analog synthesizer tutor in the BRO AUDIO MIX Android app. Provide highly expert, concise audio-mixing, synthesis, mastering, and instrument cloning advice (under 3 sentences) in Indonesian.")
                    })
                })
            })
        }

        val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed: ${response.code} / $bodyString")
                    return@withContext "Error: Maaf, asisten AI mendeteksi kendala koneksi (${response.code})."
                }

                if (bodyString.isNullOrEmpty()) {
                    return@withContext "No response from BRO AI."
                }

                // Extract response text using standard JSON parsing
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val firstPart = parts?.optJSONObject(0)
                
                return@withContext firstPart?.optString("text") ?: "Tidak ada teks tanggapan yang ditemukan."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during call: ${e.message}", e)
            return@withContext "Gagal terhubung dengan server AI: ${e.localizedMessage}. Silakan coba lagi."
        }
    }
}
