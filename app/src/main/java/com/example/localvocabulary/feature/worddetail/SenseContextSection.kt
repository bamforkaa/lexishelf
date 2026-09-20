package com.example.localvocabulary.feature.worddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.core.ui.component.originLabel
import com.example.localvocabulary.vocabulary.domain.ExampleSentence
import com.example.localvocabulary.vocabulary.domain.VocabularySense

private const val CollapsedContextCount = 2

@Composable
internal fun SenseContextSection(sense: VocabularySense) {
    var expanded by rememberSaveable(sense.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("contexts_${sense.id}")) {
        if (sense.examples.size > 1) MetadataLabel("문맥 ${sense.examples.size}개")
        // The repository supplies stored sortOrder; origin does not change the display order.
        val visible = if (expanded) sense.examples else sense.examples.take(CollapsedContextCount)
        visible.forEach { example ->
            key(example.id) { ContextRecord(example) }
        }
        if (sense.examples.size > CollapsedContextCount) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("contexts_toggle_${sense.id}"),
            ) {
                Text(if (expanded) "접기" else "나머지 ${sense.examples.size - CollapsedContextCount}개 보기")
            }
        }
    }
}

@Composable
private fun ContextRecord(example: ExampleSentence) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("detail_context_${example.id}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MetadataLabel(example.origin.originLabel(), maxLines = Int.MAX_VALUE)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(example.text, style = MaterialTheme.typography.bodyLarge)
                if (example.meaning.isNotBlank()) {
                    Text(example.meaning, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val source = listOfNotNull(example.sourceTitle, example.sourceLocator)
            .filter(String::isNotBlank).joinToString(" · ")
        val url = example.sourceUrl?.takeIf(String::isNotBlank)
        if (source.isNotBlank() || url != null) {
            val label = source.ifBlank { "출처 열기" }
            val linkColor = MaterialTheme.colorScheme.primary
            val text = buildAnnotatedString {
                if (url == null) append(label) else {
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(style = SpanStyle(
                        color = linkColor, textDecoration = TextDecoration.Underline,
                    )))) { append(label) }
                }
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("context_source_${example.id}"))
        }
    }
}
