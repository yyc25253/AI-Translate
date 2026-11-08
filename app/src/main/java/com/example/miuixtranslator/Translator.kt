package com.example.miuixtranslator

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DefaultPrompt {
    const val TEMPLATE: String = """You are a translation expert, skilled in translating various languages, and maintaining accuracy, faithfulness, and elegance in translation.
Next, I will send you text. Please translate it into {target_lang}, and return the translation result directly, without adding any explanations or other content.

Please translate the <source_text> section:

<source_text>
{source_text}
</source_text>"""

    fun render(template: String, targetLang: String, sourceText: String): String =
        template
            .replace("{target_lang}", targetLang)
            .replace("{source_text}", sourceText)
}

data class TargetLang(val label: String, val promptValue: String)

val TARGET_LANGS = listOf(
    TargetLang("中文", "Chinese"),
    TargetLang("English", "English"),
    TargetLang("日本語", "Japanese"),
    TargetLang("한국어", "Korean"),
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("translator", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "https://api.openai.com/v1") ?: ""
        set(v) = prefs.edit().putString("base_url", v).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit().putString("api_key", v).apply()

    var model: String
        get() = prefs.getString("model", "gpt-4o-mini") ?: ""
        set(v) = prefs.edit().putString("model", v).apply()

    var promptTemplate: String
        get() = prefs.getString("prompt_template", DefaultPrompt.TEMPLATE) ?: DefaultPrompt.TEMPLATE
        set(v) = prefs.edit().putString("prompt_template", v).apply()
}

class OpenAiCompatibleEngine(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun translateStream(renderedPrompt: String): Flow<String> = flow {
        val json = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("messages", org.json.JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", renderedPrompt)
            }))
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.body?.string()?.take(300)}")
            }
            val source = response.body?.source() ?: error("empty body")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                if (data.isEmpty()) continue
                runCatching {
                    val delta = JSONObject(data)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content")
                        .orEmpty()
                    if (delta.isNotEmpty()) emit(delta)
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}

class TranslatorViewModel : ViewModel() {
    var sourceText by mutableStateOf("")
    var translatedText by mutableStateOf("")
        private set
    var isTranslating by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var targetIndex by mutableStateOf(0)
    var settings by mutableStateOf<SettingsStore?>(null)
        private set

    private var job: Job? = null

    fun attach(context: Context) {
        if (settings == null) settings = SettingsStore(context)
    }

    fun translate() {
        val s = settings ?: return
        if (sourceText.isBlank()) return
        job?.cancel()
        job = viewModelScope.launch {
            isTranslating = true
            translatedText = ""
            errorMessage = null
            val lang = TARGET_LANGS[targetIndex].promptValue
            val prompt = DefaultPrompt.render(s.promptTemplate, lang, sourceText)
            OpenAiCompatibleEngine(s.baseUrl, s.apiKey, s.model)
                .translateStream(prompt)
                .catch { e -> errorMessage = e.message ?: "翻译失败" }
                .collect { chunk -> translatedText += chunk }
            isTranslating = false
        }
    }

    fun cancel() {
        job?.cancel()
        isTranslating = false
    }
    fun clear() {
        job?.cancel()
        isTranslating = false
        sourceText = ""
        translatedText = ""
        errorMessage = null
    }
}
