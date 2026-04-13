package com.qns.ui.chatlist
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.*; import androidx.compose.ui.graphics.Color; import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.data.local.entity.ChatEntity
import com.qns.ui.theme.EncryptGreen; import com.qns.ui.theme.ScamRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onChatClick: (String) -> Unit, vm: ChatListViewModel = hiltViewModel()) {
    val chats   by vm.chats.observeAsState(emptyList())
    val loading by vm.loading.observeAsState(false)

    Scaffold(
        topBar = { LargeTopAppBar(title = { Text("QNS") }) },
        floatingActionButton = { FloatingActionButton({}) { Icon(Icons.Filled.Add, null) } }
    ) { pad ->
        if (loading && chats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(contentPadding = pad) {
                items(chats) { chat -> ChatListItem(chat, onChatClick) }
            }
        }
    }
}

@Composable
private fun ChatListItem(chat: ChatEntity, onClick: (String) -> Unit) {
    val name = chat.otherUsername ?: chat.name ?: "Chat"
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name)
                if (chat.otherUserScam) {
                    Spacer(Modifier.width(4.dp))
                    AssistChip({}, { Text("SCAM", fontSize = 10.sp, color = ScamRed) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = ScamRed.copy(.1f)))
                }
            }
        },
        supportingContent = { Text("🔒 Зашифровано", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
        leadingContent = {
            Box {
                Surface(Modifier.size(48.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text(name.first().uppercase(), fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                }
                if (chat.otherUserOnline)
                    Surface(Modifier.size(12.dp).align(Alignment.BottomEnd), shape = MaterialTheme.shapes.small, color = Color(0xFF4CAF50)) {}
            }
        },
        trailingContent = {
            if (chat.unreadCount > 0) Badge { Text("${chat.unreadCount}") }
        },
        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick(chat.id) }
    )
    HorizontalDivider()
}
