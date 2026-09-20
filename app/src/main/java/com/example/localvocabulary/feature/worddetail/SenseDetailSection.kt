package com.example.localvocabulary.feature.worddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.vocabulary.domain.VocabularySense

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SenseDetailSection(
    sense: VocabularySense,
    index: Int,
    reviewEnabled: Boolean,
    reviewError: String?,
    onToggleReview: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp).testTag("detail_sense_${sense.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MetadataLabel("뜻 ${index + 1}", modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onToggleReview,
                    modifier = Modifier.testTag("review_enable_${sense.stableId}"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) { Text(if (reviewEnabled) "복습 중 · 중지" else "복습에 추가") }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(sense.meaning, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.alignByBaseline().testTag("detail_meaning_${sense.id}"))
                val metadata = listOfNotNull(
                    sense.partOfSpeech.takeIf(String::isNotBlank), sense.grammaticalGender?.displayValue(),
                ).joinToString(" · ")
                if (metadata.isNotBlank()) {
                    MetadataLabel(metadata, modifier = Modifier.alignByBaseline(), maxLines = Int.MAX_VALUE)
                }
            }
        }
        reviewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (sense.examples.isNotEmpty()) SenseContextSection(sense)
    }
}
