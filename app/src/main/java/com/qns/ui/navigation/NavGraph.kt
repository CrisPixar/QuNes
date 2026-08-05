package com.qns.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qns.ui.admin.AdminScreen
import com.qns.ui.auth.AuthScreen
import com.qns.ui.auth.AuthViewModel
import com.qns.ui.chat.ChatScreen
import com.qns.ui.chatlist.ChatListScreen
import com.qns.ui.contacts.ContactsScreen
import com.qns.ui.settings.SettingsScreen

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

@Composable
private fun MainScaffold(navController: NavHostController, isAdmin: Boolean, onLogout: () -> Unit) {
    val inner = rememberNavController()
    val current by inner.currentBackStackEntryAsState()
    val route = current?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == "chats",
                    onClick = { inner.navigate("chats") { launchSingleTop = true; popUpTo("chats") } },
                    icon = { Icon(Icons.Filled.Chat, null) },
                    label = { Text("Чаты") },
                )
                NavigationBarItem(
                    selected = route == "contacts",
                    onClick = { inner.navigate("contacts") { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.Contacts, null) },
                    label = { Text("Контакты") },
                )
                NavigationBarItem(
                    selected = route == "settings",
                    onClick = { inner.navigate("settings") { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.Settings, null) },
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
                        onAdd = { inner.navigate("contacts") },
                    )
                }
                composable("contacts") { ContactsScreen(onChatClick = { chatId -> navController.navigate(Routes.chat(chatId)) }) }
                composable("settings") { SettingsScreen(onLogout, if (isAdmin) ({ navController.navigate(Routes.ADMIN) }) else null) }
            }
        }
    }
}
