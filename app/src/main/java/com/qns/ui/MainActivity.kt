package com.qns.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

import com.qns.ui.navigation.NavGraph
import com.qns.ui.theme.QNSTheme
import com.qns.ui.theme.ThemeRepository

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var themeRepository: ThemeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crashReporter = com.qns.utils.CrashReporter.install(this)
        setContent {
            val mode by themeRepository.mode.observeAsState("system")
            var showCrash by remember { mutableStateOf(crashReporter.hasReport()) }
            QNSTheme(mode = mode) {
                if (showCrash) CrashReportScreen(crashReporter.read()) {
                    crashReporter.clear()
                    showCrash = false
                } else NavGraph()
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 700)
        }
    }
}
