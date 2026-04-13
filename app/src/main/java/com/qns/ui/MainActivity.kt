package com.qns.ui
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.qns.ui.navigation.NavGraph
import com.qns.ui.theme.QNSTheme
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        installSplashScreen(); super.onCreate(s); enableEdgeToEdge()
        setContent { QNSTheme { NavGraph() } }
    }
}
