package com.example.localvocabulary.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5C5F31),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E5A8),
    onPrimaryContainer = Color(0xFF191B00),
    secondary = Color(0xFF606043),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E4C2),
    onSecondaryContainer = Color(0xFF1C1D08),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFF8F3),
    onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFE7E3D2),
    onSurfaceVariant = Color(0xFF49483D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF4EC),
    surfaceContainer = Color(0xFFF8EEE7),
    surfaceContainerHigh = Color(0xFFF2E8E1),
    surfaceContainerHighest = Color(0xFFECE2DB),
    outline = Color(0xFF7A786C),
    outlineVariant = Color(0xFFCBC7B8),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7C98E),
    onPrimary = Color(0xFF2E3104),
    primaryContainer = Color(0xFF454817),
    onPrimaryContainer = Color(0xFFE3E5A8),
    secondary = Color(0xFFC9C7A5),
    onSecondary = Color(0xFF313218),
    secondaryContainer = Color(0xFF47482D),
    onSecondaryContainer = Color(0xFFE6E4C2),
    background = Color(0xFF17130F),
    onBackground = Color(0xFFECE0D9),
    surface = Color(0xFF17130F),
    onSurface = Color(0xFFECE0D9),
    surfaceVariant = Color(0xFF49483D),
    onSurfaceVariant = Color(0xFFCBC7B8),
    surfaceContainerLowest = Color(0xFF120E0A),
    surfaceContainerLow = Color(0xFF201B17),
    surfaceContainer = Color(0xFF241F1B),
    surfaceContainerHigh = Color(0xFF2E2925),
    surfaceContainerHighest = Color(0xFF393430),
    outline = Color(0xFF959184),
    outlineVariant = Color(0xFF49483D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppTypography = Typography().let { defaults ->
    defaults.copy(
        headlineMedium = defaults.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 36.sp,
        ),
        titleLarge = defaults.titleLarge.copy(
            fontWeight = FontWeight.Medium,
            lineHeight = 30.sp,
        ),
        titleMedium = defaults.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        ),
        bodyLarge = defaults.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = defaults.bodyMedium.copy(lineHeight = 20.sp),
        labelMedium = defaults.labelMedium.copy(lineHeight = 16.sp),
        bodySmall = defaults.bodySmall.copy(lineHeight = 18.sp),
    )
}

@Composable
fun LocalVocabularyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
