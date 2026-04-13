package com.qns.ui.chat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.*; import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.data.local.entity.MessageEntity
import com.qns.ui.theme.EncryptGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit, vm: ChatViewModel = hiltViewModel()) {
    LaunchedEffect(chatId) { vm.init(chatId) }
    val messages by vm.messages.observeAsState(emptyList())
    val isTyping by vm.isTyping.observeAsState(false)
    var input    by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Чат"); Text("🔐 E2EE · Double Ratchet", fontSize = 11.sp, color = EncryptGreen) } },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = { Icon(Icons.Filled.Lock, null, tint = EncryptGreen) }
            )
        },
        bottomBar = {
            BottomAppBar(tonalElevation = 4.dp) {
                OutlinedTextField(value = input, onValueChange = { input = it; vm.sendTypingIndicator() },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = { Text("Сообщение") }, singleLine = true)
                IconButton(onClick = {
                    if (input.isNotBlank()) {
                        // В реальной реализации: зашифровать через DoubleRatchet, затем отправить
                        vm.sendEncrypted(input, null, null)
                        input = ""
                    }
                }) { Icon(Icons.Filled.Send, null, tint = MaterialTheme.colorScheme.primary) }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), reverseLayout = true) {
            if (isTyping) item { Text("  печатает…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            items(messages.reversed()) { msg -> MessageBubble(msg) }
        }
    }
}

@Composable
private fun MessageBubble(msg: MessageEntity) {
    val text = msg.decryptedCache ?: if (msg.decryptionFailed) "⚠️ Ошибка расшифровки" else "🔒 Зашифровано"
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = if (msg.isMine) 4.dp else 1.dp,
            color = if (msg.isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text)
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatTime(msg.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (msg.isMine) Text(if (msg.read) "✓✓" else if (msg.delivered) "✓" else "·", fontSize = 11.sp, color = EncryptGreen)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val d = java.util.Date(ms)
    return String.format("%02d:%02d", d.hours, d.minutes)
}
