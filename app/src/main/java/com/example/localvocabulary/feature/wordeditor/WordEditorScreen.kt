package com.example.localvocabulary.feature.wordeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationError

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordEditorScreen(
    state: WordEditorUiState,
    onAction: (WordEditorAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.entryId == null) "단어 추가" else "단어 수정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("취소") } },
                actions = {
                    TextButton(
                        onClick = { onAction(WordEditorAction.Save) },
                        enabled = !state.isLoading && !state.isSaving,
                        modifier = Modifier.testTag("save_word"),
                    ) {
                        Text(if (state.isSaving) "저장 중" else "저장")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = state.headword,
                        onValueChange = { onAction(WordEditorAction.HeadwordChanged(it)) },
                        label = { Text("단어 또는 표현") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("headword"),
                    )
                    OutlinedTextField(
                        value = state.languageTag,
                        onValueChange = { onAction(WordEditorAction.LanguageTagChanged(it)) },
                        label = { Text("언어 태그 (BCP 47)") },
                        supportingText = { Text("예: en, ko, ja, zh-Hant") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("language_tag"),
                    )
                }
            }

            items(state.senses, key = { it.key }) { sense ->
                SenseEditorCard(
                    sense = sense,
                    canRemove = state.senses.size > 1,
                    onAction = onAction,
                )
            }

            item {
                TextButton(
                    onClick = { onAction(WordEditorAction.AddSense) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text("뜻 추가") }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("태그", style = MaterialTheme.typography.titleMedium)
                    if (state.availableTags.isEmpty()) {
                        Text("태그 관리 화면에서 태그를 먼저 만들 수 있습니다.")
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.availableTags.forEach { tag ->
                                FilterChip(
                                    selected = tag.id in state.selectedTagIds,
                                    onClick = { onAction(WordEditorAction.TagToggled(tag.id)) },
                                    label = { Text(tag.name) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { onAction(WordEditorAction.NotesChanged(it)) },
                    label = { Text("메모") },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            state.validationError?.let { error ->
                item {
                    Text(
                        text = validationMessage(error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .testTag("validation_error"),
                    )
                }
            }
            state.loadErrorMessage?.let { message ->
                item { ErrorText(message) }
            }
            state.saveErrorMessage?.let { message ->
                item { ErrorText(message) }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SenseEditorCard(
    sense: EditableSense,
    canRemove: Boolean,
    onAction: (WordEditorAction) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("뜻", style = MaterialTheme.typography.titleMedium)
                if (canRemove) {
                    TextButton(
                        onClick = { onAction(WordEditorAction.RemoveSense(sense.key)) },
                    ) { Text("삭제") }
                }
            }
            OutlinedTextField(
                value = sense.meaning,
                onValueChange = { onAction(WordEditorAction.MeaningChanged(sense.key, it)) },
                label = { Text("의미") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = sense.partOfSpeech,
                onValueChange = { onAction(WordEditorAction.PartOfSpeechChanged(sense.key, it)) },
                label = { Text("품사") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("예문", style = MaterialTheme.typography.labelLarge)
            sense.examples.forEach { example ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = example.text,
                        onValueChange = {
                            onAction(WordEditorAction.ExampleChanged(sense.key, example.key, it))
                        },
                        label = { Text("예문") },
                        modifier = Modifier.weight(1f),
                    )
                    if (sense.examples.size > 1) {
                        AssistChip(
                            onClick = {
                                onAction(WordEditorAction.RemoveExample(sense.key, example.key))
                            },
                            label = { Text("삭제") },
                        )
                    }
                }
            }
            TextButton(onClick = { onAction(WordEditorAction.AddExample(sense.key)) }) {
                Text("예문 추가")
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

private fun validationMessage(error: VocabularyValidationError): String = when (error) {
    VocabularyValidationError.MissingHeadword -> "단어 또는 표현을 입력하세요."
    VocabularyValidationError.InvalidLanguageTag -> "유효한 BCP 47 언어 태그를 입력하세요."
    VocabularyValidationError.MissingSense -> "뜻을 하나 이상 추가하세요."
    is VocabularyValidationError.MissingMeaning -> "뜻 ${error.senseIndex + 1}의 의미를 입력하세요."
}
