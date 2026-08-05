package com.qns.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.ui.server.ServerPicker
import com.qns.ui.server.ServerViewModel
import com.qns.ui.theme.EncryptGreen

@Composable
fun AuthScreen(
    onSuccess: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
    serverViewModel: ServerViewModel = hiltViewModel(),
) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val loading by vm.isLoading.observeAsState(false)
    val error by vm.error.observeAsState()
    val success by vm.loginSuccess.observeAsState()
    LaunchedEffect(success) { if (success == true) onSuccess() }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text("QNS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Quantum Secure Messenger", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("ML-KEM · Double Ratchet", color = EncryptGreen, fontSize = 12.sp)
                    Spacer(Modifier.height(22.dp))
                    ServerPicker(serverViewModel)
                    Spacer(Modifier.height(18.dp))

                    Row(Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = isLogin,
                            onClick = { isLogin = true },
                            label = { Text("Войти") },
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                        )
                        FilterChip(
                            selected = !isLogin,
                            onClick = { isLogin = false },
                            label = { Text("Регистрация") },
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Логин") },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        isError = error != null,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (!loading && username.isNotBlank() && password.isNotBlank()) {
                                if (isLogin) vm.login(username, password) else vm.register(username, password)
                            }
                        }),
                        isError = error != null,
                    )
                    AnimatedVisibility(!error.isNullOrBlank()) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { if (isLogin) vm.login(username, password) else vm.register(username, password) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text(if (isLogin) "Войти" else "Создать аккаунт", fontSize = 16.sp)
                    }
                    Text("Соединение защищено TLS", color = EncryptGreen, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
