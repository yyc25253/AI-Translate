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
