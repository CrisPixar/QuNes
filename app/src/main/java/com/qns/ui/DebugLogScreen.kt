package com.qns.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qns.utils.DebugLogStore

@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(DebugLogStore.collect(context)) }
    Scaffold(topBar = { TopAppBar(title = { Text("Debug logs") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(logs, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
            Button(onClick = { logs = DebugLogStore.collect(context) }, modifier = Modifier.padding(top = 12.dp)) { Text("Обновить") }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("QNS logs", logs))
            }) { Text("Скопировать") }
            OutlinedButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, logs) }, "Отправить лог"))
            }) { Text("Поделиться") }
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }
    }
}
