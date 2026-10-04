package io.github.marciano.psitlumocnik.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.marciano.psitlumocnik.data.ThemeMode

// Teplá „psí“ paleta – kontrastní i pro horší displej a slabší zrak.
private val Light = lightColorScheme(
    primary = Color(0xFFA34A1C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A1200),
    secondary = Color(0xFF6B5A2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E1A8),
    onSecondaryContainer = Color(0xFF231B00),
    tertiary = Color(0xFF2F6A5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB4F0DC),
    onTertiaryContainer = Color(0xFF002019),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED4),
    onSurfaceVariant = Color(0xFF4A3B33),
    surfaceContainer = Color(0xFFFBEDE5),
    surfaceContainerHigh = Color(0xFFF6E6DD),
    outline = Color(0xFF7A685E),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB693),
    onPrimary = Color(0xFF5A1B00),
    primaryContainer = Color(0xFF7F3307),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFD8C58E),
    onSecondary = Color(0xFF3B2F05),
    secondaryContainer = Color(0xFF524519),
    onSecondaryContainer = Color(0xFFF5E1A8),
    tertiary = Color(0xFF98D4C1),
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0xFF145143),
    onTertiaryContainer = Color(0xFFB4F0DC),
    background = Color(0xFF1A1410),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF1A1410),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFDDC4B8),
    surfaceContainer = Color(0xFF261E19),
    surfaceContainerHigh = Color(0xFF312823),
    outline = Color(0xFFA58D82),
    error = Color(0xFFF2B8B5),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp),
        labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun PsiTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
