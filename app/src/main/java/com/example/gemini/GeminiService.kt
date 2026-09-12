package com.example.gemini

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

enum class GeminiModelMode {
    HIGH_THINKING, // gemini-3.1-pro-preview with ThinkingLevel.HIGH
    LOW_LATENCY    // gemini-3.1-flash-lite
}

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateCodeOrAnalysis(
        prompt: String,
        mode: GeminiModelMode,
        systemInstruction: String? = null,
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please add your key in the Secrets panel or workspace settings.")
            )
        }

        val modelName = when (mode) {
            GeminiModelMode.HIGH_THINKING -> "gemini-3.1-pro-preview"
            GeminiModelMode.LOW_LATENCY -> "gemini-3.1-flash-lite"
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        try {
            val root = JSONObject()

            // Contents
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            root.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject()
            if (mode == GeminiModelMode.HIGH_THINKING) {
                val thinkingConfig = JSONObject()
                thinkingConfig.put("thinkingLevel", "HIGH")
                genConfig.put("thinkingConfig", thinkingConfig)
                // Note: User prompt instruction: "Do not set maxOutputTokens"
            }
            root.put("generationConfig", genConfig)

            // System instruction if any
            if (!systemInstruction.isNullOrBlank()) {
                val sysInstObj = JSONObject()
                val sysParts = JSONArray()
                val sysPart = JSONObject()
                sysPart.put("text", systemInstruction)
                sysParts.put(sysPart)
                sysInstObj.put("parts", sysParts)
                root.put("systemInstruction", sysInstObj)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = root.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        val errJson = JSONObject(bodyString)
                        errJson.optJSONObject("error")?.optString("message") ?: response.message
                    } catch (_: Exception) {
                        response.message
                    }
                    return@withContext Result.failure(Exception("Gemini API Error ($modelName): $errMsg"))
                }

                val jsonResponse = JSONObject(bodyString)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text")
                        return@withContext Result.success(text)
                    }
                }
                Result.failure(Exception("No content received from Gemini model."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
