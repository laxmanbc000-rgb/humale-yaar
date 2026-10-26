package com.humale.yaar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class Brain(private val ctx: Context) {

    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private val history = JSONArray()

    companion object {
        private const val URL = "https://api.anthropic.com/v1/messages"
        private const val SYSTEM =
            "तिमी 'हुमाले यार' हौ — नेपाली भाषामा बोल्ने मिलनसार स्मार्टफोन सहायक। " +
            "प्रयोगकर्ताले नेपाली, हिन्दी वा अंग्रेजीमा बोल्न सक्छन्; जवाफ सधैँ छोटो र सजिलो नेपालीमा देऊ (आवाजमा पढिन्छ, त्यसैले emoji र markdown नलेख)। " +
            "फोन चलाउनुपर्ने अनुरोध आए टुलहरू प्रयोग गर। नबुझेको कुरा अनुमान नगरी एउटा छोटो प्रश्न सोध।"
    }

    fun reset() { while (history.length() > 0) history.remove(0) }

    suspend fun ask(text: String): String = withContext(Dispatchers.IO) {
        val key = Store.apiKey(ctx)
        if (key.isBlank()) return@withContext "पहिले सेटिङमा API key हाल्नुस्।"
        try {
            history.put(JSONObject().put("role", "user").put("content", text))
            var out = ""
            for (turn in 0 until 6) {
                val body = JSONObject()
                    .put("model", Store.model(ctx))
                    .put("max_tokens", 1024)
                    .put("system", SYSTEM)
                    .put("tools", Tools.schema())
                    .put("messages", history)
                val req = Request.Builder().url(URL)
                    .header("x-api-key", key)
                    .header("anthropic-version", "2023-06-01")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        trimBadTurn()
                        return@withContext "त्रुटि ${resp.code}: ${raw.take(160)}"
                    }
                    val content = JSONObject(raw).getJSONArray("content")
                    history.put(JSONObject().put("role", "assistant").put("content", content))
                    val results = JSONArray()
                    for (i in 0 until content.length()) {
                        val b = content.getJSONObject(i)
                        when (b.getString("type")) {
                            "text" -> out += b.getString("text")
                            "tool_use" -> results.put(
                                JSONObject().put("type", "tool_result")
                                    .put("tool_use_id", b.getString("id"))
                                    .put("content", Tools.run(ctx, b.getString("name"), b.getJSONObject("input")))
                            )
                        }
                    }
                    if (results.length() == 0) return@withContext out.trim()
                    history.put(JSONObject().put("role", "user").put("content", results))
                }
            }
            out.trim().ifEmpty { "भयो।" }
        } catch (e: Exception) {
            trimBadTurn()
            "इन्टरनेट वा सर्भर समस्या: ${e.message}"
        }
    }

    private fun trimBadTurn() {
        // अन्तिम user सन्देश हटाउँ ताकि इतिहास बिग्रिएको नहोस्
        if (history.length() > 0 && history.getJSONObject(history.length() - 1).getString("role") == "user") {
            history.remove(history.length() - 1)
        }
    }
}
