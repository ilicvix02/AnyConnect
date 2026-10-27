package com.viktor.anyconnect.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Gemini client. Given the task and a description of the current screen,
 * it asks the model for exactly one next action, returned as JSON.
 */
class GeminiClient(private val apiKey: String, private val model: String) {

    data class Action(
        val thought: String,
        val action: String,
        val index: Int,
        val text: String,
        val direction: String
    )

    suspend fun decideNextAction(
        task: String,
        pkg: String,
        elements: String,
        history: String
    ): Action = withContext(Dispatchers.IO) {

        val system = """
            You are an agent that operates an Android phone to accomplish a user's task.
            You see the elements currently on screen and choose ONE action at a time.
            You act inside whichever app is currently open.

            Reply with ONLY a JSON object, no prose and no code fences:
            {"thought":"short reason","action":"click|setText|scroll|back|home|recents|wait|done","index":<element number or -1>,"text":"<text for setText, else empty>","direction":"down|up"}

            Rules:
            - For click and setText, "index" is the [number] of an element in the list.
            - setText types into a field; tap the submit/search element in a later step.
            - Use "wait" if the screen is still loading or the target app is not open yet.
            - Use "scroll" with a direction to reveal more content.
            - Use "done" only when the task is fully complete.
            - Exactly one action per reply.
        """.trimIndent()

        val user = buildString {
            append("TASK: ").append(task).append("\n\n")
            append("CURRENT APP: ").append(pkg).append("\n\n")
            append("SCREEN ELEMENTS:\n")
            append(if (elements.isBlank()) "(none visible)" else elements)
            append("\n\nHISTORY:\n")
            append(if (history.isBlank()) "(none)" else history)
            append("\n\nNext action as JSON:")
        }

        val payload = JSONObject().apply {
            put("system_instruction", JSONObject().put(
                "parts", JSONArray().put(JSONObject().put("text", system))
            ))
            put("contents", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", user)))
            ))
            put("generationConfig", JSONObject()
                .put("temperature", 0)
                .put("responseMimeType", "application/json"))
        }

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20000
            readTimeout = 45000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()

        if (code !in 200..299) {
            throw RuntimeException("API $code: ${resp.take(240)}")
        }

        val text = JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
            .getJSONObject(0).getString("text")

        parse(text)
    }

    private fun parse(raw: String): Action {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        // In case the model wraps extra text, grab the first {...} block.
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start >= 0 && end > start) s = s.substring(start, end + 1)

        val o = JSONObject(s)
        return Action(
            thought = o.optString("thought", ""),
            action = o.optString("action", "wait").lowercase(),
            index = o.optInt("index", -1),
            text = o.optString("text", ""),
            direction = o.optString("direction", "down")
        )
    }
}
