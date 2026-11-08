#!/usr/bin/env bash
set -euo pipefail

# ============================================================
#  MiuiX Translator — Android 项目生成脚本
#  用法: bash generate.sh [--force]
#  产物: 9 个文件，可被 Android Studio 直接打开
#  不生成 .github/ 目录，可在 GitHub Actions 中安全执行
# ============================================================

AGP_VERSION="8.7.3"
KOTLIN_VERSION="2.1.0"
COMPOSE_BOM="2024.12.01"
MIUIX_VERSION="0.9.3"
OKHTTP_VERSION="4.12.0"
LIFECYCLE_VERSION="2.8.7"
ACTIVITY_COMPOSE_VERSION="1.9.3"

FORCE=false
if [[ "${1:-}" == "--force" ]]; then FORCE=true; fi

# 只检查是否覆盖关键项目文件，不管 .github / 脚本自身
if [[ -f settings.gradle.kts && "$FORCE" != "true" ]]; then
  echo "检测到已有项目文件。使用 --force 覆盖，或换一个空目录。"
  exit 1
fi

mkdir -p app/src/main/java/com/example/miuixtranslator
mkdir -p app/src/main/res/values

# ============================================================
#  1. settings.gradle.kts
# ============================================================
cat > settings.gradle.kts <<'PROJECT_EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MiuiXTranslator"
include(":app")
PROJECT_EOF

# ============================================================
#  2. build.gradle.kts（根）
# ============================================================
cat > build.gradle.kts <<'PROJECT_EOF'
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
PROJECT_EOF

# ============================================================
#  3. gradle.properties
# ============================================================
cat > gradle.properties <<'PROJECT_EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
PROJECT_EOF

# ============================================================
#  4. app/build.gradle.kts
# ============================================================
cat > app/build.gradle.kts <<'PROJECT_EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.miuixtranslator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.miuixtranslator"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("top.yukonga.miuix.kmp:miuix-android:0.9.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
PROJECT_EOF

# ============================================================
#  5. AndroidManifest.xml
# ============================================================
cat > app/src/main/AndroidManifest.xml <<'PROJECT_EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:label="@string/app_name"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
PROJECT_EOF

# ============================================================
#  6. strings.xml
# ============================================================
cat > app/src/main/res/values/strings.xml <<'PROJECT_EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">翻译</string>
</resources>
PROJECT_EOF

# ============================================================
#  7. Translator.kt
# ============================================================
cat > app/src/main/java/com/example/miuixtranslator/Translator.kt <<'PROJECT_EOF'
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
}
PROJECT_EOF

# ============================================================
#  8. MainActivity.kt
# ============================================================
cat > app/src/main/java/com/example/miuixtranslator/MainActivity.kt <<'PROJECT_EOF'
package com.example.miuixtranslator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme(controller = remember { ThemeController() }) {
                AppScreen()
            }
        }
    }
}

@Composable
fun AppScreen(vm: TranslatorViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    remember { vm.attach(context); true }

    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("翻译", style = MiuixTheme.textStyles.title1)
                Button(onClick = { showSettings = true }) { Text("设置") }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = vm.sourceText,
                onValueChange = { vm.sourceText = it },
                label = "原文",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6
            )

            OverlayDropdownPreference(
                title = "目标语言",
                items = TARGET_LANGS.map { it.label },
                selectedIndex = vm.targetIndex,
                onSelectedIndexChange = { vm.targetIndex = it }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.isTranslating) {
                    Button(onClick = { vm.cancel() }) { Text("取消") }
                } else {
                    Button(onClick = { vm.translate() }) { Text("翻译") }
                }
                Button(onClick = {
                    val cb = context.getSystemService(android.content.ClipboardManager::class.java)
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("译文", vm.translatedText))
                }) { Text("复制译文") }
            }

            vm.errorMessage?.let { Text(it, color = MiuixTheme.colorScheme.error) }

            TextField(
                value = vm.translatedText,
                onValueChange = {},
                label = "译文",
                modifier = Modifier.fillMaxWidth().height(200.dp),
                readOnly = true
            )
        }
    }

    if (showSettings && vm.settings != null) {
        SettingsDialog(
            settings = vm.settings!!,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun SettingsDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var template by remember { mutableStateOf(settings.promptTemplate) }

    SuperDialog(
        title = "API 设置",
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = baseUrl, onValueChange = { baseUrl = it }, label = "Base URL")
            TextField(value = apiKey, onValueChange = { apiKey = it }, label = "API Key")
            TextField(value = model, onValueChange = { model = it }, label = "模型名")
            TextField(
                value = template,
                onValueChange = { template = it },
                label = "提示词模板",
                maxLines = 8
            )
            Text(
                "可用变量: {target_lang} 与 {source_text}",
                style = MiuixTheme.textStyles.body2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    settings.baseUrl = baseUrl
                    settings.apiKey = apiKey
                    settings.model = model
                    settings.promptTemplate = template
                    onDismiss()
                }) { Text("保存") }
                Button(onClick = onDismiss) { Text("取消") }
            }
        }
    }
}
PROJECT_EOF

# ============================================================
#  9. .gitignore
# ============================================================
cat > .gitignore <<'PROJECT_EOF'
.gradle/
build/
local.properties
.idea/
*.iml
.DS_Store
PROJECT_EOF

echo ""
echo "=========================================="
echo " 生成完成。文件树:"
echo "=========================================="
find . -type f -not -path './.git/*' -not -path './.github/*' | sort
echo ""
echo "下一步: 用 Android Studio 打开本目录，"
echo "或在 GitHub Actions 中运行 Generate 工作流。"
echo "=========================================="
