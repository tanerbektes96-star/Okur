package com.okur.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.okur.reader.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C4A2F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF370E00),
    secondary = Color(0xFF77574A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160C),
    tertiary = Color(0xFF6A5F2F),
    tertiaryContainer = Color(0xFFF3E3A7),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF5DED5),
    onSurfaceVariant = Color(0xFF53433D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFFCEAE3),
    surfaceContainerHigh = Color(0xFFF6E5DD),
    surfaceContainerHighest = Color(0xFFF0DFD7),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2BA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB596),
    onPrimary = Color(0xFF55200A),
    primaryContainer = Color(0xFF71351C),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFE7BDAD),
    onSecondary = Color(0xFF442A1F),
    secondaryContainer = Color(0xFF5D4034),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD7C78D),
    tertiaryContainer = Color(0xFF51471A),
    onTertiaryContainer = Color(0xFFF3E3A7),
    background = Color(0xFF1A1210),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF1A1210),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF53433D),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceContainerLowest = Color(0xFF140D0B),
    surfaceContainerLow = Color(0xFF221A17),
    surfaceContainer = Color(0xFF271E1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D332F),
    outline = Color(0xFFA08D85),
    outlineVariant = Color(0xFF53433D),
)

private val base = Typography()
private val Serif = FontFamily.Serif

val OkurTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val CoverTitleStyle = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun OkurTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(mode)
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, typography = OkurTypography, content = content)
}
