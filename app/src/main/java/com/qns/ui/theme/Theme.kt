package com.qns.ui.theme
import android.app.Activity; import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.*
import androidx.core.view.WindowCompat
private val Light = lightColorScheme(primary=PrimaryLight, onPrimary=OnPrimary, secondary=SecondaryLight)
private val Dark  = darkColorScheme(primary=PrimaryDark,  onPrimary=OnPrimaryDark, secondary=SecondaryDark)
@Composable
fun QNSTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val cs = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val c = LocalContext.current
            if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
        }
        dark  -> Dark
        else  -> Light
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val w = (view.context as Activity).window
        w.statusBarColor = cs.primary.toArgb()
        WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !dark
    }
    MaterialTheme(colorScheme=cs, typography=AppTypography, content=content)
}
