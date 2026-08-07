package com.qns.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qns.BuildConfig
import com.qns.ui.DebugLogScreen
import com.qns.ui.admin.AdminScreen
import com.qns.ui.auth.AuthScreen
import com.qns.ui.auth.AuthViewModel
import com.qns.ui.chat.ChatScreen
import com.qns.ui.chatlist.ChatListScreen
import com.qns.ui.contacts.ContactsScreen
import com.qns.ui.settings.SettingsScreen
import com.qns.ui.theme.FurryIcon

object Routes {
    const val AUTH = "auth"
    const val MAIN = "main"
    const val ADMIN = "admin"
    const val CHAT = "chat/{chatId}"
    fun chat(id: String) = "chat/$id"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val loggedIn by authViewModel.isLoggedIn.observeAsState(false)
    val role by authViewModel.userRole.observeAsState("user")
    val betaTester by authViewModel.isBetaTester.observeAsState(false)
    val forcedLogout by authViewModel.forcedLogout.observeAsState(false)

    // Принудительный выход при 401/отзыве сессии.
    LaunchedEffect(forcedLogout) {
        if (forcedLogout) {
            authViewModel.consumeForcedLogout()
            navController.navigate(Routes.AUTH) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = if (loggedIn) Routes.MAIN else Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen(onSuccess = {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            })
        }
        composable(Routes.MAIN) {
            MainScaffold(
                navController = navController,
                isAdmin = role == "admin",
                showDebugLogs = BuildConfig.DEBUG && betaTester,
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.CHAT, arguments = listOf(navArgument("chatId") { type = NavType.StringType })) { entry ->
            ChatScreen(
                chatId = entry.arguments?.getString("chatId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ADMIN) { AdminScreen(onBack = { navController.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(navController: NavHostController, isAdmin: Boolean, showDebugLogs: Boolean, onLogout: () -> Unit) {
    val inner = rememberNavController()
    val current by inner.currentBackStackEntryAsState()
    val route = current?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == "chats",
                    onClick = { inner.navigate("chats") { launchSingleTop = true; popUpTo("chats") } },
                    icon = { FurryIcon("chats", Icons.AutoMirrored.Filled.Chat, null) },
                    label = { Text("Чаты") },
                )
                NavigationBarItem(
                    selected = route == "contacts",
                    onClick = { inner.navigate("contacts") { launchSingleTop = true } },
                    icon = { FurryIcon("contacts", Icons.Filled.Contacts, null) },
                    label = { Text("Контакты") },
                )
                NavigationBarItem(
                    selected = route == "settings",
                    onClick = { inner.navigate("settings") { launchSingleTop = true } },
                    icon = { FurryIcon("settings", Icons.Filled.Settings, null) },
                    label = { Text("Настройки") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(inner, startDestination = "chats") {
                composable("chats") {
                    ChatListScreen(
                        onChatClick = { navController.navigate(Routes.chat(it)) },
                        onAdd = { safeNavigate(inner, "contacts") },
                    )
                }
                composable("contacts") { ContactsScreen(onChatClick = { chatId -> navController.navigate(Routes.chat(chatId)) }) }
                composable("settings") { SettingsScreen(onLogout, if (isAdmin) ({ navController.navigate(Routes.ADMIN) }) else null, if (showDebugLogs) ({ safeNavigate(inner, "debug_logs") }) else null) }
                if (showDebugLogs) composable("debug_logs") { DebugLogScreen { inner.popBackStack() } }
                // Fallback для неизвестных маршрутов — не даём «белый экран».
                composable("unknown") { UnknownRoute(onBack = { inner.popBackStack() }) }
            }
        }
    }
}

/** Навигация, которая не бросает исключение при отсутствии маршрута (не даёт белого экрана). */
private fun safeNavigate(controller: NavHostController, route: String) {
    try {
        controller.navigate(route) {
            launchSingleTop = true
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
    } catch (error: Exception) {
        // маршрут недоступен (например, debug-экран отключён в release) — молча игнорируем
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnknownRoute(onBack: () -> Unit) {
    androidx.compose.material3.Scaffold(
        topBar = { androidx.compose.material3.TopAppBar(title = { Text("Ошибка") }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Text("Экран не найден. Вернитесь в меню.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) { Text("Назад") }
        }
    }
}
