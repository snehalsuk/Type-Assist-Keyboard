package com.aikeyboard.assistant.ime

import com.aikeyboard.assistant.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Thin client for Groq's OpenAI-compatible chat completions endpoint. */
object GroqClient {
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"
    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun complete(systemPrompt: String, userText: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (BuildConfig.GROQ_API_KEY.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Missing GROQ_API_KEY"))
            }
            if (userText.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Nothing to send"))
            }

            val payload = JSONObject().apply {
                put("model", MODEL)
                put("temperature", 0.3)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                        put(JSONObject().put("role", "user").put("content", userText))
                    },
                )
            }

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                .post(payload.toString().toRequestBody(JSON))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException("Groq request failed (${response.code})"),
                        )
                    }
                    val content = JSONObject(bodyString)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                    Result.success(content)
                }
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: org.json.JSONException) {
                Result.failure(e)
            }
        }
}
