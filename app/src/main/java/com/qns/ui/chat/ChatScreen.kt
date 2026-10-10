package com.qns.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.data.local.entity.MessageEntity
import com.qns.ui.theme.EncryptGreen
import com.qns.ui.theme.FurryIcon
import com.qns.ui.theme.ScamRed
import com.qns.ui.theme.TypingIndicatorCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit, vm: ChatViewModel = hiltViewModel()) {
    LaunchedEffect(chatId) { vm.init(chatId) }
    val messages by vm.messages.observeAsState(emptyList())
    val chat by vm.chat.observeAsState()
    val connected by vm.connected.observeAsState(false)
    val isTyping by vm.isTyping.observeAsState(false)
    val error by vm.error.observeAsState()
    val fingerprint by vm.fingerprint.observeAsState("")
    var input by remember { mutableStateOf("") }
    var showSafety by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { if (!error.isNullOrBlank()) snackbar.showSnackbar(error ?: "Ошибка") }

    if (showSafety) {
        AlertDialog(
            onDismissRequest = { showSafety = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = EncryptGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ключ квантовой безопасности")
                }
            },
            text = {
                Column {
                    Text(
                        "Сравните отпечаток с собеседником. Сквозное постквантовое шифрование гарантирует защиту даже при компрометации классических каналов.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = if (fingerprint.isNullOrBlank()) "Сессия ещё не установлена. Отправьте первое сообщение." else fingerprint,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSafety = false }) { Text("Закрыть") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(chat?.otherUsername?.ifBlank { "Чат" } ?: "Чат", style = MaterialTheme.typography.titleMedium)
                            if (chat?.otherUserVerified == true) {
                                Spacer(Modifier.width(6.dp))
                                Text("✓", color = EncryptGreen, fontSize = 16.sp)
                            }
                        }
                        PulsingNetworkStatus(connected = connected)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { FurryIcon("chat-back", Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showSafety = true }) { FurryIcon("chat-lock", Icons.Filled.Lock, null, Modifier) }
                },
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
                    shape = RoundedCornerShape(24.dp),
                )
                IconButton(onClick = {
                    if (input.isNotBlank()) {
                        vm.sendText(input)
                        input = ""
                    }
                }) { FurryIcon("chat-send", Icons.AutoMirrored.Filled.Send, null) }
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
                        "Внимание: пользователь отмечен как SCAM. Не отправляйте коды, пароли и деньги.\nПричина: " + (chat?.otherUserScamReason?.ifBlank { "не указана" } ?: "не указана"),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), reverseLayout = true) {
                item {
                    AnimatedVisibility(
                        visible = isTyping,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        TypingIndicatorDots()
                    }
                }
                items(messages.reversed(), key = { it.id }) { message ->
                    AnimatedMessageItem(message)
                }
            }
        }
    }
}

/**
 * Пульсирующий индикатор сетевого соединения (Pulse Micro-animation).
 */
@Composable
private fun PulsingNetworkStatus(connected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .scale(if (connected) pulseScale else 1.0f)
                .alpha(if (connected) pulseAlpha else 1.0f)
                .background(if (connected) EncryptGreen else MaterialTheme.colorScheme.error, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (connected) "E2EE Защищено (Quantum-Safe)" else "Нет соединения с сервером",
            fontSize = 11.sp,
            color = if (connected) EncryptGreen else MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Плавный трёхточечный индикатор набора текста с волновой анимацией.
 */
@Composable
private fun TypingIndicatorDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "d1",
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "d2",
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 1.1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "d3",
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Печатает", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(5.dp).scale(dot1Scale).background(TypingIndicatorCyan, CircleShape))
            Box(Modifier.size(5.dp).scale(dot2Scale).background(TypingIndicatorCyan, CircleShape))
            Box(Modifier.size(5.dp).scale(dot3Scale).background(TypingIndicatorCyan, CircleShape))
        }
    }
}

/**
 * Анимированное появление сообщений с плавным Fade & Scale.
 */
@Composable
private fun AnimatedMessageItem(message: MessageEntity) {
    MessageBubble(message)
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val text = message.decryptedCache ?: when {
        !message.decryptionError.isNullOrBlank() && message.decryptionError == "WAITING_FOR_SESSION" -> "Ожидается ключ сессии"
        !message.decryptionError.isNullOrBlank() && message.decryptionError == "UNSUPPORTED_VERSION" -> "Неподдерживаемая версия сообщения"
        message.decryptionFailed -> "Не удалось расшифровать: ${message.decryptionError ?: "ошибка ключа"}"
        else -> "Зашифрованное сообщение"
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = if (message.isMine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            tonalElevation = if (message.isMine) 6.dp else 2.dp,
            color = if (message.isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (message.isMine) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    else Modifier
                ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(markdownText(text), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatTime(message.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (message.isMine) Text(if (message.read) "✓✓" else if (message.delivered) "✓" else "·", fontSize = 11.sp, color = EncryptGreen)
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(milliseconds))
