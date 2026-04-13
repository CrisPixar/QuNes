package com.qns.ui.auth
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.*; import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*; import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.qns.ui.theme.EncryptGreen

@Composable
fun AuthScreen(onSuccess: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    var isLogin  by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    val loading by vm.isLoading.observeAsState(false)
    val errMsg  by vm.error.observeAsState()
    val success by vm.loginSuccess.observeAsState()

    LaunchedEffect(success) { if (success == true) onSuccess() }

    Scaffold { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {

            Icon(Icons.Filled.Lock, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("QNS", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Quantum Secure Messenger", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AssistChip({}, label = { Text("🔐 ML-KEM-1024 + Double Ratchet", fontSize = 11.sp, color = EncryptGreen) })
            Spacer(Modifier.height(28.dp))

            Row(Modifier.fillMaxWidth()) {
                FilterChip(isLogin,  { isLogin = true },  { Text("Войти") },    Modifier.weight(1f).padding(end = 4.dp))
                FilterChip(!isLogin, { isLogin = false }, { Text("Регистрация") }, Modifier.weight(1f).padding(start = 4.dp))
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Логин") },
                leadingIcon = { Icon(Icons.Filled.Person, null) }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                isError = errMsg != null)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Пароль") },
                leadingIcon = { Icon(Icons.Filled.Lock, null) }, modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { IconButton({ showPw = !showPw }) { Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                isError = errMsg != null)

            AnimatedVisibility(!errMsg.isNullOrEmpty()) {
                Text(errMsg ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(20.dp))

            Button(onClick = { if (isLogin) vm.login(username, password) else vm.register(username, password) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled  = !loading && username.isNotBlank() && password.isNotBlank()) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text(if (isLogin) "Войти" else "Создать аккаунт", fontSize = 16.sp)
            }
        }
    }
}
