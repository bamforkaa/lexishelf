package com.example.localvocabulary.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5C5F31),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E5A8),
    onPrimaryContainer = Color(0xFF191B00),
    secondary = Color(0xFF606043),
    background = Color(0xFFFFF8F3),
    surface = Color(0xFFFFF8F3),
    surfaceVariant = Color(0xFFE7E3D2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7C98E),
    onPrimary = Color(0xFF2E3104),
    primaryContainer = Color(0xFF454817),
    onPrimaryContainer = Color(0xFFE3E5A8),
    secondary = Color(0xFFC9C7A5),
)

@Composable
fun LocalVocabularyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
