package com.aikeyboard.assistant.ime

import com.aikeyboard.assistant.BuildConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

data class GifResult(val previewUrl: String, val fullUrl: String)

/** Thin client for Giphy's trending-GIFs endpoint. */
object GiphyClient {
    private const val TRENDING_ENDPOINT = "https://api.giphy.com/v1/gifs/trending"
    private val client = OkHttpClient()

    suspend fun trending(limit: Int = 24): Result<List<GifResult>> = withContext(Dispatchers.IO) {
        if (BuildConfig.GIPHY_API_KEY.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing GIPHY_API_KEY"))
        }
        val url = "$TRENDING_ENDPOINT?api_key=${BuildConfig.GIPHY_API_KEY}&limit=$limit&rating=pg-13"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Giphy request failed (${response.code})"))
                }
                val data = JSONObject(bodyString).getJSONArray("data")
                val results = (0 until data.length()).mapNotNull { i ->
                    val images = data.getJSONObject(i).optJSONObject("images") ?: return@mapNotNull null
                    val preview = images.optJSONObject("fixed_width_small")?.optString("url")
                    val full = images.optJSONObject("original")?.optString("url")
                    if (preview.isNullOrBlank() || full.isNullOrBlank()) null else GifResult(preview, full)
                }
                Result.success(results)
            }
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: JSONException) {
            Result.failure(e)
        }
    }
}
