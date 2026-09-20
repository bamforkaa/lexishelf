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

internal val LightColors = lightColorScheme(
    primary = Color(0xFF4F6A58),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFEADF),
    onPrimaryContainer = Color(0xFF253C2E),
    secondary = Color(0xFF5C665F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E9E4),
    onSecondaryContainer = Color(0xFF303A33),
    tertiary = Color(0xFF53676B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E8EB),
    onTertiaryContainer = Color(0xFF253A3E),
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF202521),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202521),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF535C55),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF6F6F6),
    surfaceContainerHigh = Color(0xFFF2F2F2),
    surfaceContainerHighest = Color(0xFFECECEC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE0E0E0),
    outline = Color(0xFF737C75),
    outlineVariant = Color(0xFFCCCCCC),
    inverseSurface = Color(0xFF303632),
    inverseOnSurface = Color(0xFFF3F5F1),
    inversePrimary = Color(0xFFAEC8B4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color.Transparent,
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC8B4),
    onPrimary = Color(0xFF203829),
    primaryContainer = Color(0xFF354E3D),
    onPrimaryContainer = Color(0xFFD1E5D6),
    secondary = Color(0xFFBEC9C0),
    onSecondary = Color(0xFF293A30),
    secondaryContainer = Color(0xFF3D4B41),
    onSecondaryContainer = Color(0xFFDAE7DD),
    tertiary = Color(0xFFAECACE),
    onTertiary = Color(0xFF19363B),
    tertiaryContainer = Color(0xFF304D51),
    onTertiaryContainer = Color(0xFFC9E6EA),
    background = Color(0xFF151917),
    onBackground = Color(0xFFE3E7E2),
    surface = Color(0xFF151917),
    onSurface = Color(0xFFE3E7E2),
    surfaceVariant = Color(0xFF414843),
    onSurfaceVariant = Color(0xFFBDC6BD),
    surfaceContainerLowest = Color(0xFF101412),
    surfaceContainerLow = Color(0xFF1D211F),
    surfaceContainer = Color(0xFF222624),
    surfaceContainerHigh = Color(0xFF2B302D),
    surfaceContainerHighest = Color(0xFF353A37),
    surfaceBright = Color(0xFF353A37),
    surfaceDim = Color(0xFF151917),
    outline = Color(0xFF8E9990),
    outlineVariant = Color(0xFF414943),
    inverseSurface = Color(0xFFE3E7E2),
    inverseOnSurface = Color(0xFF2B302D),
    inversePrimary = Color(0xFF4F6A58),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color.Transparent,
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
