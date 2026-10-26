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

/** Gemini API (Google AI Studio को निःशुल्क key चल्छ) */
class Brain(private val ctx: Context) {

    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private val history = JSONArray()

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
        private const val SYSTEM =
            "तिमी 'हुमाले यार' हौ — नेपाली भाषामा बोल्ने मिलनसार स्मार्टफोन सहायक। " +
            "प्रयोगकर्ताले नेपाली, हिन्दी वा अंग्रेजीमा बोल्न सक्छन्; जवाफ सधैँ छोटो र सजिलो नेपालीमा देऊ (आवाजमा पढिन्छ, त्यसैले emoji र markdown नलेख)। " +
            "फोन चलाउनुपर्ने अनुरोध आए टुलहरू प्रयोग गर। नबुझेको कुरा अनुमान नगरी एउटा छोटो प्रश्न सोध।"
    }

    fun reset() { while (history.length() > 0) history.remove(0) }

    private fun declarations(): JSONArray {
        val src = Tools.schema()
        val out = JSONArray()
        for (i in 0 until src.length()) {
            val t = src.getJSONObject(i)
            val params = JSONObject(t.getJSONObject("input_schema").toString())
            params.put("type", "OBJECT")
            val props = params.getJSONObject("properties")
            val names = props.keys().asSequence().toList()
            for (k in names) {
                val p = props.getJSONObject(k)
                p.put("type", p.getString("type").uppercase())
            }
            out.put(
                JSONObject().put("name", t.getString("name"))
                    .put("description", t.getString("description"))
                    .put("parameters", params)
            )
        }
        return out
    }

    private fun textPart(s: String) = JSONArray().put(JSONObject().put("text", s))

    suspend fun ask(text: String): String = withContext(Dispatchers.IO) {
        val key = Store.apiKey(ctx)
        if (key.isBlank()) return@withContext "पहिले सेटिङमा Gemini API key हाल्नुस्।"
        try {
            history.put(JSONObject().put("role", "user").put("parts", textPart(text)))
            var out = ""
            for (turn in 0 until 6) {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", textPart(SYSTEM)))
                    .put("contents", history)
                    .put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations())))
                val req = Request.Builder()
                    .url(BASE + Store.model(ctx) + ":generateContent")
                    .header("x-goog-api-key", key)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        trimBadTurn()
                        return@withContext if (resp.code == 429) "सीमा नाघ्यो — एक मिनेट पर्खेर फेरि भन्नुस्।"
                        else "त्रुटि ${resp.code}: ${raw.take(160)}"
                    }
                    val parts = JSONObject(raw).optJSONArray("candidates")
                        ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    if (parts == null) {
                        trimBadTurn()
                        return@withContext "जवाफ आएन, फेरि भन्नुस्।"
                    }
                    history.put(JSONObject().put("role", "model").put("parts", parts))
                    val results = JSONArray()
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (p.has("text")) out += p.getString("text")
                        val fc = p.optJSONObject("functionCall")
                        if (fc != null) {
                            val name = fc.getString("name")
                            val r = Tools.run(ctx, name, fc.optJSONObject("args") ?: JSONObject())
                            results.put(
                                JSONObject().put(
                                    "functionResponse",
                                    JSONObject().put("name", name).put("response", JSONObject().put("result", r))
                                )
                            )
                        }
                    }
                    if (results.length() == 0) return@withContext out.trim().ifEmpty { "भयो।" }
                    history.put(JSONObject().put("role", "user").put("parts", results))
                }
            }
            out.trim().ifEmpty { "भयो।" }
        } catch (e: Exception) {
            trimBadTurn()
            "इन्टरनेट वा सर्भर समस्या: ${e.message}"
        }
    }

    private fun trimBadTurn() {
        if (history.length() > 0 && history.getJSONObject(history.length() - 1).getString("role") == "user") {
            history.remove(history.length() - 1)
        }
    }
}
