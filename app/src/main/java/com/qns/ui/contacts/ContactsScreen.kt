package com.qns.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.ui.theme.EncryptGreen
import com.qns.ui.theme.ScamRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(onChatClick: (String) -> Unit, vm: ContactsViewModel = hiltViewModel()) {
    var query by remember { mutableStateOf("") }
    val contacts by vm.contacts.observeAsState(emptyList())
    val loading by vm.loading.observeAsState(false)
    val error by vm.error.observeAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Контакты") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("Поиск пользователя") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    IconButton(onClick = { vm.search(query) }) { Icon(Icons.Filled.PersonSearch, null) }
                },
                singleLine = true,
            )
            if (!error.isNullOrBlank()) Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
            if (!loading && contacts.isEmpty()) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Text("Найдите пользователя по логину и начните защищённый чат.", modifier = Modifier.padding(16.dp))
                }
            }
            LazyColumn {
                items(contacts, key = { it.id }) { contact ->
                    ListItem(
                        headlineContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(contact.username)
                                if (contact.verified) Text("✓", color = EncryptGreen, fontSize = 16.sp)
                                if (contact.scam) Text("SCAM", color = ScamRed, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        supportingContent = {
                            if (contact.scam) {
                                Text(
                                    "⚠ " + (contact.scamReason.ifBlank { "Причина не указана" }),
                                    color = ScamRed,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                        leadingContent = { Icon(Icons.Filled.PersonSearch, null) },
                        enabled = !loading,
                        modifier = Modifier.clickable(enabled = !loading) { vm.openChat(contact.id) { chatId -> onChatClick(chatId) } },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
