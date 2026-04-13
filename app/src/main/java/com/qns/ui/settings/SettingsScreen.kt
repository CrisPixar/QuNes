package com.qns.ui.settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier; import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onLogout: () -> Unit, onAdminPanel: (()->Unit)?, vm: SettingsViewModel = hiltViewModel()) {
    val loggedOut by vm.loggedOut.observeAsState()
    LaunchedEffect(loggedOut) { if (loggedOut == true) onLogout() }

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { pad ->
        LazyColumn(contentPadding = pad) {
            item { SettingsItem(Icons.Filled.Palette,  "Тема",              "System / Light / Dark") }
            item { SettingsItem(Icons.Filled.Key,      "Ключи шифрования",  "ML-KEM-1024, Ed25519…") }
            item { SettingsItem(Icons.Filled.Devices,  "Активные сессии",   "Устройства с доступом") }
            item { SettingsItem(Icons.Filled.Security, "Уровень защиты",    "Quantum-Resistant") }
            if (onAdminPanel != null) {
                item {
                    ListItem(headlineContent = { Text("Admin Panel", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Filled.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.error) },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                        modifier = Modifier.clickable(indication=null, interactionSource=remember{MutableInteractionSource()}) { onAdminPanel() })
                    HorizontalDivider()
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.logout() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                    Icon(Icons.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("Выйти")
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) }, trailingContent = { Icon(Icons.Filled.ChevronRight, null) })
    HorizontalDivider()
}
