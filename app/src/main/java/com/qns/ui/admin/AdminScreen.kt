package com.qns.ui.admin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.*; import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.ui.theme.ScamRed; import com.qns.ui.theme.EncryptGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit, vm: AdminViewModel = hiltViewModel()) {
    val users   by vm.users.observeAsState(emptyList())
    val stats   by vm.stats.observeAsState()
    val loading by vm.loading.observeAsState(false)
    val error   by vm.error.observeAsState()
    var scamDialogUser by remember { mutableStateOf<AdminViewModel.AdminUser?>(null) }
    var scamReason     by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadData() }

    // SCAM диалог
    scamDialogUser?.let { user ->
        AlertDialog(
            onDismissRequest = { scamDialogUser = null },
            title = { Text(if (user.isScam) "Снять SCAM?" else "Отметить как SCAM") },
            text  = { if (!user.isScam) OutlinedTextField(scamReason, { scamReason = it }, label = { Text("Причина") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton({ vm.toggleScam(user.id, !user.isScam, scamReason); scamDialogUser = null; scamReason = "" }) {
                    Text(if (user.isScam) "Снять" else "Отметить", color = ScamRed)
                }
            },
            dismissButton = { TextButton({ scamDialogUser = null }) { Text("Отмена") } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Admin Panel 🛡️") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Filled.ArrowBack, null) } }) }
    ) { pad ->
        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(contentPadding = pad) {
            // Ошибка
            error?.let { item { Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer) } } }

            // Статистика
            stats?.let { s -> item {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📊 Статистика", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        StatRow("👥 Пользователей", s.totalUsers)
                        StatRow("💬 Сообщений",     s.totalMessages)
                        StatRow("🚫 SCAM",           s.scamUsers)
                        StatRow("🔗 Активных сессий",s.activeSessions)
                        StatRow("🌐 WS соединений",  s.activeWs)
                    }
                }
            }}

            // Заголовок списка
            item { Text("Пользователи", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal=16.dp, vertical=8.dp)) }

            // Список пользователей
            items(users) { user ->
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.username)
                            if (user.role == "admin") { Spacer(Modifier.width(4.dp)); AssistChip({}, { Text("admin", fontSize=10.sp) }) }
                            if (user.isScam)          { Spacer(Modifier.width(4.dp)); AssistChip({}, { Text("SCAM",  fontSize=10.sp, color=ScamRed) }, colors=AssistChipDefaults.assistChipColors(containerColor=ScamRed.copy(.1f))) }
                        }
                    },
                    supportingContent = { Text("IP: ${user.lastIp ?: "?"} · Сессий: ${user.activeSessions}", fontSize=12.sp) },
                    trailingContent = {
                        Row {
                            // SCAM
                            IconButton({ scamDialogUser = user }) {
                                Icon(if (user.isScam) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                                    tint = if (user.isScam) EncryptGreen else ScamRed)
                            }
                            // Отозвать сессии
                            IconButton({ vm.revokeUserSessions(user.id) }) { Icon(Icons.Filled.PhoneLocked, null) }
                            // Удалить аккаунт
                            IconButton({ vm.deleteUser(user.id) }) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label); Text("$value", style = MaterialTheme.typography.labelLarge)
    }
}
