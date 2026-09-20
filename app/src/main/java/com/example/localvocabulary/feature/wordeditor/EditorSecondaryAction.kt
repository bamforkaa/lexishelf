package com.example.localvocabulary.feature.wordeditor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Compact visual rows; ViewConfiguration still supplies the platform's expanded touch targets. */
@Composable
internal fun EditorSecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelTag: String,
    fillLabel: Boolean = false,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        TextButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 28.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text,
                modifier = (if (fillLabel) Modifier.weight(1f) else Modifier).testTag(labelTag),
            )
        }
    }
}
