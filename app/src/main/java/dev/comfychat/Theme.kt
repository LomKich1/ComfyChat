package dev.comfychat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFC6613F),
    onPrimary = Color.White,
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF141413),
    surface = Color(0xFFF0EEE6),
    onSurface = Color(0xFF141413),
    surfaceVariant = Color(0xFFE8E6DC),
    onSurfaceVariant = Color(0xFF6B6A68),
    outline = Color(0xFFDAD9D4)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD97757),
    onPrimary = Color(0xFF1F1E1D),
    background = Color(0xFF262624),
    onBackground = Color(0xFFFAF9F5),
    surface = Color(0xFF30302E),
    onSurface = Color(0xFFFAF9F5),
    surfaceVariant = Color(0xFF3A3A37),
    onSurfaceVariant = Color(0xFFA6A39A),
    outline = Color(0xFF4A4945)
)

@Composable
fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ComfyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (mode.resolveDark()) DarkColors else LightColors,
        typography = Typography(
            headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 21.sp, lineHeight = 26.sp)
        ),
        content = content
    )
}
