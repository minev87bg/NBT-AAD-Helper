package com.nbtaad.editor

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.nbtaad.editor.data.ThemeMode

/**
 * The dark theme in the colours of GitHub's dark theme (the page background is #0d1117, not black: white text on pure
 * black is hard on the eyes). The light theme follows the phone's wallpaper colours on Android 12+ (Material You).
 */
private val GitHubDark = darkColorScheme(
    primary = Color(0xFF3FB950), onPrimary = Color(0xFF0D1117),
    primaryContainer = Color(0xFF238636), onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF56D364), onSecondary = Color(0xFF0D1117),
    secondaryContainer = Color(0xFF1A3A27), onSecondaryContainer = Color(0xFFD7F5DD),
    tertiary = Color(0xFF79C0FF), onTertiary = Color(0xFF0D1117),
    tertiaryContainer = Color(0xFF1B2F4A), onTertiaryContainer = Color(0xFFCFE5FF),
    error = Color(0xFFFF7B72), onError = Color(0xFF0D1117),
    errorContainer = Color(0xFF4A1E1C), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D1117), onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF0D1117), onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF151B23), onSurfaceVariant = Color(0xFF9198A1),
    surfaceTint = Color(0xFF3FB950),
    inverseSurface = Color(0xFFF0F6FC), inverseOnSurface = Color(0xFF0D1117), inversePrimary = Color(0xFF238636),
    outline = Color(0xFF3D444D), outlineVariant = Color(0xFF30363D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF212830), surfaceDim = Color(0xFF0D1117),
    surfaceContainerLowest = Color(0xFF0D1117), surfaceContainerLow = Color(0xFF11161D),
    surfaceContainer = Color(0xFF151B23), surfaceContainerHigh = Color(0xFF1B222B), surfaceContainerHighest = Color(0xFF212830),
)

/**
 * SYSTEM follows the phone's day/night setting; LIGHT/DARK override it (the user's own choice,
 * saved in ThemePrefs).
 */
@Composable
fun NbtAadTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dark -> GitHubDark
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
