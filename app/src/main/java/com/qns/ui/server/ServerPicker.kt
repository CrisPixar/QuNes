package com.qns.ui.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.data.remote.ServerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPicker(viewModel: ServerViewModel = hiltViewModel()) {
    val servers by viewModel.servers.observeAsState(emptyList())
    val current by viewModel.current.observeAsState()
    val error by viewModel.error.observeAsState()
    var expanded by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = current?.name ?: "Сервер",
            onValueChange = {},
            readOnly = true,
            label = { Text("Сервер") },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Check, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            servers.forEach { server ->
                DropdownMenuItem(
                    text = { Text(server.name) },
                    onClick = {
                        viewModel.select(server.id)
                        expanded = false
                    },
                    trailingIcon = if (server.id == current?.id) {
                        { androidx.compose.material3.Icon(Icons.Filled.Check, null) }
                    } else null,
                )
            }
            DropdownMenuItem(
                text = { Text("Добавить сервер") },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Add, null) },
                onClick = {
                    expanded = false
                    dialog = true
                },
            )
        }
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Новый сервер") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Адрес API") },
                        placeholder = { Text("https://example.com/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    if (!error.isNullOrBlank()) Text(error ?: "", color = androidx.compose.material3.MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.add(name, url)
                    if (viewModel.error.value == null) {
                        name = ""
                        url = ""
                        dialog = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Отмена") } },
        )
    }
}
