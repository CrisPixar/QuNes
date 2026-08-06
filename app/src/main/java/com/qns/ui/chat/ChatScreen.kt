package com.qns.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.data.local.entity.MessageEntity
import com.qns.ui.theme.EncryptGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit, vm: ChatViewModel = hiltViewModel()) {
    LaunchedEffect(chatId) { vm.init(chatId) }
    val messages by vm.messages.observeAsState(emptyList())
    val chat by vm.chat.observeAsState()
    val isTyping by vm.isTyping.observeAsState(false)
    val error by vm.error.observeAsState()
    var input by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { if (!error.isNullOrBlank()) snackbar.showSnackbar(error ?: "Ошибка") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Чат")
                        Text("Зашифрованный конверт", fontSize = 11.sp, color = EncryptGreen)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { Icon(Icons.Filled.Lock, null, tint = EncryptGreen) },
            )
        },
        bottomBar = {
            BottomAppBar {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; vm.sendTypingIndicator() },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
                    placeholder = { Text("Сообщение") },
                    singleLine = true,
                )
                IconButton(onClick = {
                    if (input.isNotBlank()) {
                        vm.sendText(input)
                        input = ""
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.primary) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (chat?.otherUserScam == true) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        "Внимание: пользователь отмечен как SCAM. Не отправляйте коды, пароли и деньги.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), reverseLayout = true) {
                if (isTyping) item { Text("Печатает…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                items(messages.reversed(), key = { it.id }) { message -> MessageBubble(message) }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val text = message.decryptedCache ?: if (message.decryptionFailed) "Не удалось расшифровать" else "Зашифрованное сообщение"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = if (message.isMine) 4.dp else 1.dp,
            color = if (message.isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text)
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatTime(message.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (message.isMine) Text(if (message.read) "✓✓" else if (message.delivered) "✓" else "·", fontSize = 11.sp, color = EncryptGreen)
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(milliseconds))
