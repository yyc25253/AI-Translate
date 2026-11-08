package com.example.miuixtranslator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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
    val context = LocalContext.current
    remember { vm.attach(context); true }

    var showSettings by remember { mutableStateOf(false) }
    var showLangSelector by remember { mutableStateOf(false) }

    // 使用纯 Column 配合 statusBarsPadding 防止内容被状态栏遮挡
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9F9)) // 浅色背景
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "翻译",
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = { showSettings = true }) {
                Text("设置")
            }
        }

        // 2. 原文输入框（强制指定高度，防止被挤压）
        TextField(
            value = vm.sourceText,
            onValueChange = { vm.sourceText = it },
            label = "原文",
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )

        // 3. 目标语言选择器（自己画一个点击行，点击弹窗选择）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp))
                .clickable { showLangSelector = true }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("目标语言", style = MiuixTheme.textStyles.body1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TARGET_LANGS[vm.targetIndex].label,
                    style = MiuixTheme.textStyles.body2,
                    color = Color.Gray
                )
                Text(" ▼", style = MiuixTheme.textStyles.body2, color = Color.Gray)
            }
        }

        // 4. 操作按钮（均分宽度）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { if (vm.isTranslating) vm.cancel() else vm.translate() },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (vm.isTranslating) "取消" else "翻译")
            }
            Button(
                onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("译文", vm.translatedText))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("复制译文")
            }
        }

        // 5. 错误提示
        vm.errorMessage?.let {
            Text(
                text = it,
                color = Color.Red,
                style = MiuixTheme.textStyles.body2
            )
        }

        // 6. 译文输出框
        TextField(
            value = vm.translatedText,
            onValueChange = {},
            label = "译文",
            modifier = Modifier.fillMaxWidth().height(200.dp),
            readOnly = true
        )
    }

    // 设置弹窗（使用原生 Dialog，保证一定弹得出来）
    if (showSettings && vm.settings != null) {
        SettingsDialog(
            settings = vm.settings!!,
            onDismiss = { showSettings = false }
        )
    }

    // 语言选择弹窗
    if (showLangSelector) {
        LanguageSelectorDialog(
            selectedIndex = vm.targetIndex,
            onSelect = {
                vm.targetIndex = it
                showLangSelector = false
            },
            onDismiss = { showLangSelector = false }
        )
    }
}

@Composable
fun SettingsDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var template by remember { mutableStateOf(settings.promptTemplate) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("API 设置", style = MiuixTheme.textStyles.title2)
            TextField(value = baseUrl, onValueChange = { baseUrl = it }, label = "Base URL")
            TextField(value = apiKey, onValueChange = { apiKey = it }, label = "API Key")
            TextField(value = model, onValueChange = { model = it }, label = "模型名")
            TextField(
                value = template,
                onValueChange = { template = it },
                label = "提示词模板",
                modifier = Modifier.height(120.dp)
            )
            Text("可用变量: {target_lang} 与 {source_text}", style = MiuixTheme.textStyles.body2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    settings.baseUrl = baseUrl
                    settings.apiKey = apiKey
                    settings.model = model
                    settings.promptTemplate = template
                    onDismiss()
                }) { Text("保存") }
            }
        }
    }
}

@Composable
fun LanguageSelectorDialog(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("选择目标语言", style = MiuixTheme.textStyles.title2)
            TARGET_LANGS.forEachIndexed { index, lang ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lang.label,
                        style = MiuixTheme.textStyles.body1,
                        color = if (index == selectedIndex) Color(0xFF007AFF) else Color.Black
                    )
                }
            }
        }
    }
}
