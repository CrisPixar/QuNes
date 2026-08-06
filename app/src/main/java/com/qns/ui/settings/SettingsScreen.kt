package com.qns.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.data.remote.model.SessionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onAdminPanel: (() -> Unit)?,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val loggedOut by vm.loggedOut.observeAsState()
    val error by vm.error.observeAsState()
    val themeMode by vm.themeMode.observeAsState("system")
    LaunchedEffect(loggedOut) { if (loggedOut == true) onLogout() }

    var themeDialog by remember { mutableStateOf(false) }
    var keysDialog by remember { mutableStateOf(false) }
    var sessionsDialog by remember { mutableStateOf(false) }
    var securityDialog by remember { mutableStateOf(false) }

    if (themeDialog) ThemeDialog(vm, themeMode) { themeDialog = false }
    if (keysDialog) InfoDialog("Ключи шифрования", "Ключи устройства хранятся локально. Приватные части не отправляются на сервер.") { keysDialog = false }
    if (securityDialog) InfoDialog("Защита", "Соединение использует TLS. Сервер хранит только payload, который передаёт клиент. Проверяйте ключи собеседника перед важным разговором.") { securityDialog = false }
    if (sessionsDialog) SessionsDialog(vm) { sessionsDialog = false }

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("QNS", style = MaterialTheme.typography.titleLarge)
                        Text("Управление аккаунтом и устройством", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (!error.isNullOrBlank()) item {
                Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item { SettingsItem(Icons.Filled.Palette, "Тема", "System, Light или Dark") { themeDialog = true } }
            item { SettingsItem(Icons.Filled.Key, "Ключи шифрования", "Просмотр состояния ключей") { keysDialog = true } }
            item { SettingsItem(Icons.Filled.Devices, "Активные сессии", "Управление устройствами") { sessionsDialog = true; vm.loadSessions() } }
            item { SettingsItem(Icons.Filled.Security, "Уровень защиты", "TLS и состояние ключей") { securityDialog = true } }
            if (onAdminPanel != null) item {
                SettingsItem(Icons.Filled.AdminPanelSettings, "Панель администратора", "Пользователи, SCAM и сессии", onAdminPanel)
            }
            item {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { vm.logout() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Выйти")
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

@Composable
private fun ThemeDialog(vm: SettingsViewModel, currentMode: String, onClose: () -> Unit) {
    var selected by remember(currentMode) { mutableStateOf(currentMode) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Тема приложения") },
        text = {
            Column {
                listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная").forEach { (id, label) ->
                    Row(Modifier.fillMaxWidth().clickable { selected = id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == id, onClick = { selected = id })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.setTheme(selected); onClose() }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Отмена") } },
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onClose) { Text("Понятно") } },
    )
}

@Composable
private fun SessionsDialog(vm: SettingsViewModel, onClose: () -> Unit) {
    val sessions by vm.sessions.observeAsState(emptyList())
    val loading by vm.sessionsLoading.observeAsState(false)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Активные сессии") },
        text = {
            if (loading) CircularProgressIndicator()
            else if (sessions.isEmpty()) Text("Активных сессий нет")
            else LazyColumn {
                items(sessions) { session ->
                    SessionRow(session) { vm.revokeSession(session.id) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.revokeAllSessions(); onClose() }, enabled = sessions.isNotEmpty()) { Text("Отозвать все") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
}

@Composable
private fun SessionRow(session: SessionInfo, onRevoke: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(session.userAgent.ifBlank { "Устройство" }, maxLines = 1)
            Text(session.ip, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onRevoke) { Text("Отозвать") }
    }
}
