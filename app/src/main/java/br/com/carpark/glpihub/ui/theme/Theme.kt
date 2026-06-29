package br.com.carpark.glpihub.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryColor,
    background = BackgroundDark,
    surface = SurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryColor,
    secondary = SecondaryColor,
    background = BackgroundLight,
    surface = SurfaceLight,
)

private val MonokaiColorScheme = darkColorScheme(
    primary = MonokaiPrimary,
    secondary = MonokaiSecondary,
    tertiary = MonokaiTertiary,
    background = MonokaiBackground,
    surface = MonokaiSurface,
    onPrimary = MonokaiBackground,
    onSecondary = MonokaiBackground,
    onBackground = MonokaiText,
    onSurface = MonokaiText,
    onSurfaceVariant = MonokaiTextMuted
)

private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkPrimary,
    secondary = CyberpunkSecondary,
    tertiary = CyberpunkTertiary,
    background = CyberpunkBackground,
    surface = CyberpunkSurface,
    onPrimary = CyberpunkBackground,
    onSecondary = CyberpunkBackground,
    onBackground = CyberpunkText,
    onSurface = CyberpunkText,
    onSurfaceVariant = CyberpunkTextMuted
)

@Composable
fun GLPIHubTheme(
    themeType: String = "light",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeType) {
        "dark" -> DarkColorScheme
        "monokai" -> MonokaiColorScheme
        "cyberpunk" -> CyberpunkColorScheme
        else -> LightColorScheme
    }
    
    val darkTheme = themeType != "light"
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
