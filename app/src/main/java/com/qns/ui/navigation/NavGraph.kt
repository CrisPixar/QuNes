package com.qns.ui.navigation
import androidx.compose.foundation.layout.Box; import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*; import androidx.navigation.compose.*
import com.qns.ui.auth.AuthScreen; import com.qns.ui.auth.AuthViewModel
import com.qns.ui.chat.ChatScreen
import com.qns.ui.chatlist.ChatListScreen
import com.qns.ui.settings.SettingsScreen
import com.qns.ui.admin.AdminScreen

object R { const val AUTH="auth"; const val MAIN="main"; const val ADMIN="admin"
    const val CHAT="chat/{cid}"; fun chat(id:String)="chat/$id" }

@Composable fun NavGraph() {
    val nav = rememberNavController()
    val vm: AuthViewModel = hiltViewModel()
    val loggedIn by vm.isLoggedIn.observeAsState(false)
    val role     by vm.userRole.observeAsState("user")
    NavHost(nav, if(loggedIn) R.MAIN else R.AUTH) {
        composable(R.AUTH) { AuthScreen(onSuccess={ nav.navigate(R.MAIN){ popUpTo(R.AUTH){inclusive=true} } }) }
        composable(R.MAIN) { MainScaffold(nav, role=="admin") { nav.navigate(R.AUTH){ popUpTo(0){inclusive=true} } } }
        composable(R.CHAT, listOf(navArgument("cid"){ type=NavType.StringType })) {
            ChatScreen(it.arguments?.getString("cid")?:"") { nav.popBackStack() }
        }
        composable(R.ADMIN) { AdminScreen { nav.popBackStack() } }
    }
}

@Composable fun MainScaffold(nav: NavHostController, isAdmin: Boolean, onLogout: ()->Unit) {
    val inner = rememberNavController()
    val cur by inner.currentBackStackEntryAsState(); val route = cur?.destination?.route
    Scaffold(bottomBar={
        NavigationBar {
            NavigationBarItem(icon={Icon(Icons.Filled.Chat,"")},    label={Text("Чаты")},      selected=route=="chats",    onClick={inner.navigate("chats"){launchSingleTop=true}})
            NavigationBarItem(icon={Icon(Icons.Filled.Contacts,"")},label={Text("Контакты")},  selected=route=="contacts", onClick={inner.navigate("contacts"){launchSingleTop=true}})
            NavigationBarItem(icon={Icon(Icons.Filled.Settings,"")},label={Text("Настройки")}, selected=route=="settings", onClick={inner.navigate("settings"){launchSingleTop=true}})
        }
    }) { p -> Box(Modifier.padding(p)) {
        NavHost(inner,"chats") {
            composable("chats")    { ChatListScreen { nav.navigate(R.chat(it)) } }
            composable("contacts") { ChatListScreen { nav.navigate(R.chat(it)) } }
            composable("settings") { SettingsScreen(onLogout, if(isAdmin){{ nav.navigate(R.ADMIN) }} else null) }
        }
    }}
}
