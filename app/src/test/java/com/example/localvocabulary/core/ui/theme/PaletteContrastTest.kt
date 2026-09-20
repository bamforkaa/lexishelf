package com.example.localvocabulary.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {
    @Test fun `light and dark palettes retain readable text and control contrast`() {
        for (colors in listOf(LightColors, DarkColors)) {
            val textPairs = listOf(
                colors.onBackground to colors.background,
                colors.onSurface to colors.surface,
                colors.onSurface to colors.surfaceContainerHighest,
                colors.onSurfaceVariant to colors.surfaceContainerHighest,
                colors.primary to colors.surface,
                colors.primary to colors.primary.copy(alpha = .12f).compositeOver(colors.surface),
                colors.onPrimary to colors.primary,
                colors.onPrimaryContainer to colors.primaryContainer,
                colors.onSecondary to colors.secondary,
                colors.onSecondaryContainer to colors.secondaryContainer,
                colors.onTertiary to colors.tertiary,
                colors.onTertiaryContainer to colors.tertiaryContainer,
                colors.onError to colors.error,
                colors.onErrorContainer to colors.errorContainer,
                colors.inverseOnSurface to colors.inverseSurface,
                colors.inversePrimary to colors.inverseSurface,
            )
            textPairs.forEach { (foreground, background) ->
                assertTrue("Text contrast ${contrast(foreground, background)}", contrast(foreground, background) >= 4.5)
            }
            assertTrue(contrast(colors.outline, colors.surface) >= 3.0)
            assertTrue(contrast(colors.primary, colors.surface) >= 3.0)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = a.luminance().toDouble()
        val second = b.luminance().toDouble()
        return (maxOf(first, second) + .05) / (minOf(first, second) + .05)
    }
}
