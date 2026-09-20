package com.example.localvocabulary.feature.wordeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.originLabel
import com.example.localvocabulary.vocabulary.domain.ExampleOrigin

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContextEditor(
    senseKey: Long,
    example: EditableExample,
    onAction: (WordEditorAction) -> Unit,
) {
    var showDetails by rememberSaveable(example.key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = example.text,
            onValueChange = { onAction(WordEditorAction.ExampleChanged(senseKey, example.key, it)) },
            label = { Text("문맥 / 예문 (선택)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("context_text_${example.key}"),
        )
        val hasMetadata = example.meaning.isNotBlank() || example.sourceTitle.isNotBlank() ||
            example.sourceUrl.isNotBlank() || example.sourceLocator.isNotBlank()
        EditorSecondaryAction(
            text = if (showDetails) "해석 · 출처 · 종류 접기"
                else "해석 · 출처 · 종류${if (hasMetadata) " · 입력됨" else " 추가"}",
            onClick = { showDetails = !showDetails },
            modifier = Modifier.testTag("context_details_${example.key}"),
            labelTag = "context_details_label_${example.key}",
        )
        if (showDetails) {
            ContextMetadataField(senseKey, example, ExampleMetadataField.MEANING, "의미 / 한국어 해석", example.meaning, onAction)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ExampleOrigin.entries.forEach { origin ->
                    FilterChip(
                        selected = example.origin == origin,
                        onClick = { onAction(WordEditorAction.ExampleOriginChanged(senseKey, example.key, origin)) },
                        label = { Text(origin.originLabel()) },
                    )
                }
            }
            ContextMetadataField(senseKey, example, ExampleMetadataField.SOURCE_TITLE, "만난 콘텐츠 · 영상 / 글 / 문서 이름", example.sourceTitle, onAction)
            ContextMetadataField(senseKey, example, ExampleMetadataField.SOURCE_URL, "출처 URL", example.sourceUrl, onAction)
            ContextMetadataField(senseKey, example, ExampleMetadataField.SOURCE_LOCATOR, "위치 · 12:35, p.17, section", example.sourceLocator, onAction)
            TextButton(onClick = { onAction(WordEditorAction.RemoveExample(senseKey, example.key)) }) {
                Text("이 문맥 / 예문 삭제")
            }
        }
    }
}

@Composable
private fun ContextMetadataField(
    senseKey: Long,
    example: EditableExample,
    field: ExampleMetadataField,
    label: String,
    value: String,
    onAction: (WordEditorAction) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onAction(WordEditorAction.ExampleMetadataChanged(senseKey, example.key, field, it)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag("context_${field.name}_${example.key}"),
    )
}
