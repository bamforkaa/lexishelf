package com.example.localvocabulary.feature.wordeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.core.ui.component.ScreenStatePane
import com.example.localvocabulary.core.ui.component.SectionHeader
import com.example.localvocabulary.core.model.LanguageDisplayNameResolver
import com.example.localvocabulary.feature.language.LanguagePickerField
import com.example.localvocabulary.feature.handwriting.HandwritingInputAction
import com.example.localvocabulary.feature.handwriting.HandwritingInputDialog
import com.example.localvocabulary.feature.handwriting.HandwritingInputUiState
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationError
import com.example.localvocabulary.vocabulary.domain.PronunciationNotation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordEditorScreen(
    state: WordEditorUiState,
    onAction: (WordEditorAction) -> Unit,
    onBack: () -> Unit,
    handwritingState: HandwritingInputUiState = HandwritingInputUiState(),
    onHandwritingAction: (HandwritingInputAction) -> Unit = {},
    onHandwritingCandidateSelected: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.entryId == null) "단어 추가" else "단어 수정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "취소하고 뒤로")
                    }
                },
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
            ScreenStatePane(
                title = "단어를 불러오는 중입니다",
                isLoading = true,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EditorSection {
                    SectionHeader(
                        title = "단어 정보",
                        supportingText = "검색과 저장에 사용할 원문을 입력합니다.",
                    )
                    OutlinedTextField(
                        value = state.headword,
                        onValueChange = { onAction(WordEditorAction.HeadwordChanged(it)) },
                        label = { Text("단어 또는 표현") },
                        singleLine = true,
                        isError = state.validationError == VocabularyValidationError.MissingHeadword,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onHandwritingAction(
                                        HandwritingInputAction.Open(
                                            contextLanguageTag = state.languageTag,
                                            preContext = state.headword,
                                            vocabularyLanguageTags =
                                                state.userLanguageTags.toList(),
                                        ),
                                    )
                                },
                                modifier = Modifier.testTag("open_handwriting"),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "손글씨 입력")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("headword"),
                    )
                    LanguagePickerField(
                        value = state.languageTag,
                        onValueChange = { onAction(WordEditorAction.LanguageTagChanged(it)) },
                        label = "언어",
                        userLanguageTags = state.userLanguageTags,
                        onUserLanguageAdded = {
                            onAction(WordEditorAction.UserLanguageAdded(it))
                        },
                        supportingText = "목록에서 선택하거나 BCP 47 태그를 직접 입력할 수 있습니다.",
                        isError = state.validationError == VocabularyValidationError.InvalidLanguageTag,
                        testTag = "language_tag",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { DictionarySuggestionSection(state = state, onAction = onAction) }

            item {
                EditorSection {
                    SectionHeader(
                        title = "내 단어",
                        supportingText = "여기에 입력하거나 가져온 내용만 저장됩니다.",
                    )
                    OutlinedTextField(
                        value = state.reading,
                        onValueChange = { onAction(WordEditorAction.ReadingChanged(it)) },
                        label = { Text("읽기 / 표기") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("reading"),
                    )
                    state.readingProvenance?.let { provenance ->
                        MetadataLabel(
                            buildString {
                                append(provenance.sourceName)
                                append("에서 가져온 읽기")
                                if (provenance.modifiedAfterImport) append(" · 수정됨")
                            },
                            modifier = Modifier.testTag("reading_provenance"),
                        )
                    }
                    if (state.pronunciations.isNotEmpty()) {
                        Text("발음", style = MaterialTheme.typography.titleMedium)
                        state.pronunciations.forEach { pronunciation ->
                            PronunciationEditor(
                                pronunciation = pronunciation,
                                onAction = onAction,
                            )
                        }
                    }
                    TextButton(
                        onClick = { onAction(WordEditorAction.AddPronunciation) },
                        modifier = Modifier.testTag("add_pronunciation"),
                    ) {
                        Text("+ 발음 추가")
                    }
                    state.externalDictionaryReference?.let { reference ->
                        TextButton(
                            onClick = {
                                onAction(WordEditorAction.OpenExternalDictionaryReference)
                            },
                            modifier = Modifier.testTag("external_dictionary_reference"),
                        ) {
                            Text("${reference.destinationName}에서 발음 확인 ↗")
                        }
                    }
                }
            }

            items(state.senses, key = { it.key }) { sense ->
                SenseEditorSection(
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
                EditorSection {
                    SectionHeader(
                        title = "단어장",
                        supportingText = "한 단어를 여러 단어장에 넣을 수 있습니다.",
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.availableWordbooks.forEach { wordbook ->
                            FilterChip(
                                selected = wordbook.id in state.selectedWordbookIds,
                                onClick = {
                                    onAction(WordEditorAction.WordbookToggled(wordbook.id))
                                },
                                label = { Text(wordbook.name) },
                            )
                        }
                        SuggestionChip(
                            onClick = { onAction(WordEditorAction.CreateWordbookRequested) },
                            label = { Text("+ 새 단어장") },
                            modifier = Modifier.testTag("create_wordbook"),
                        )
                    }
                    state.wordbookCreationMessage?.let {
                        MetadataLabel(
                            text = it,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    SectionHeader(
                        title = "태그",
                        supportingText = "내용을 설명하는 자유로운 라벨입니다.",
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.availableTags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in state.selectedTagIds,
                                onClick = { onAction(WordEditorAction.TagToggled(tag.id)) },
                                label = { Text(tag.name) },
                            )
                        }
                        SuggestionChip(
                            onClick = { onAction(WordEditorAction.CreateTagRequested) },
                            label = { Text("+ 새 태그") },
                            modifier = Modifier.testTag("create_tag"),
                        )
                    }
                    state.tagCreationMessage?.let {
                        MetadataLabel(
                            text = it,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    OutlinedTextField(
                        value = state.notes,
                        onValueChange = { onAction(WordEditorAction.NotesChanged(it)) },
                        label = { Text("메모") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.validationError?.let { error ->
                item {
                    ErrorText(
                        message = validationMessage(error),
                        modifier = Modifier.testTag("validation_error"),
                    )
                }
            }
            state.loadErrorMessage?.let { item { ErrorText(it) } }
            state.saveErrorMessage?.let { item { ErrorText(it) } }
        }
    }

    HandwritingInputDialog(
        state = handwritingState,
        onAction = onHandwritingAction,
        onCandidateSelected = onHandwritingCandidateSelected,
    )

    if (state.isTagCreatorVisible) {
        AlertDialog(
            onDismissRequest = { onAction(WordEditorAction.CreateTagDismissed) },
            title = { Text("새 태그") },
            text = {
                OutlinedTextField(
                    value = state.newTagName,
                    onValueChange = { onAction(WordEditorAction.NewTagNameChanged(it)) },
                    label = { Text("태그 이름") },
                    singleLine = true,
                    isError = state.tagCreationError != null,
                    supportingText = state.tagCreationError?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth().testTag("new_tag_name"),
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(WordEditorAction.CreateTagConfirmed) },
                    enabled = !state.isCreatingTag,
                    modifier = Modifier.testTag("confirm_create_tag"),
                ) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(WordEditorAction.CreateTagDismissed) }) {
                    Text("취소")
                }
            },
        )
    }
    if (state.isWordbookCreatorVisible) {
        AlertDialog(
            onDismissRequest = { onAction(WordEditorAction.CreateWordbookDismissed) },
            title = { Text("새 단어장") },
            text = {
                OutlinedTextField(
                    value = state.newWordbookName,
                    onValueChange = { onAction(WordEditorAction.NewWordbookNameChanged(it)) },
                    label = { Text("단어장 이름") },
                    singleLine = true,
                    isError = state.wordbookCreationError != null,
                    supportingText = state.wordbookCreationError?.let { message ->
                        { Text(message) }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("new_wordbook_name"),
                )
            },
            confirmButton = {
                Button(
                    onClick = { onAction(WordEditorAction.CreateWordbookConfirmed) },
                    enabled = !state.isCreatingWordbook,
                    modifier = Modifier.testTag("confirm_create_wordbook"),
                ) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = { onAction(WordEditorAction.CreateWordbookDismissed) }) {
                    Text("취소")
                }
            },
        )
    }
    state.duplicateCandidate?.let { duplicate ->
        AlertDialog(
            onDismissRequest = { onAction(WordEditorAction.DismissDuplicateWarning) },
            title = { Text("이미 저장된 단어입니다") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(duplicate.headword, style = MaterialTheme.typography.titleMedium)
                    duplicate.senses.firstOrNull()?.meaning?.let { Text(it) }
                    MetadataLabel(
                        LanguageDisplayNameResolver.resolve(duplicate.languageTag).name,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onAction(WordEditorAction.OpenExistingDuplicate) }) {
                    Text("기존 단어 열기")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onAction(WordEditorAction.DismissDuplicateWarning) }) {
                        Text("취소")
                    }
                    TextButton(onClick = { onAction(WordEditorAction.SaveDuplicateAnyway) }) {
                        Text("별도 단어로 저장")
                    }
                }
            },
        )
    }
}

@Composable
private fun EditorSection(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SenseEditorSection(
    sense: EditableSense,
    canRemove: Boolean,
    onAction: (WordEditorAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("뜻", style = MaterialTheme.typography.titleMedium)
            if (canRemove) {
                IconButton(onClick = { onAction(WordEditorAction.RemoveSense(sense.key)) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "이 뜻 삭제",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        OutlinedTextField(
            value = sense.meaning,
            onValueChange = { onAction(WordEditorAction.MeaningChanged(sense.key, it)) },
            label = { Text("의미") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        sense.provenance?.let { provenance ->
            MetadataLabel(
                text = buildString {
                    append(provenance.sourceName)
                    append(" 기반")
                    if (provenance.modifiedAfterImport) append(" · 수정됨")
                },
                modifier = Modifier.testTag("sense_provenance"),
            )
        }
        OutlinedTextField(
            value = sense.partOfSpeech,
            onValueChange = { onAction(WordEditorAction.PartOfSpeechChanged(sense.key, it)) },
            label = { Text("품사") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (sense.isGrammaticalGenderVisible) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = sense.grammaticalGender,
                    onValueChange = {
                        onAction(WordEditorAction.GrammaticalGenderChanged(sense.key, it))
                    },
                    label = { Text("문법 성") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("grammatical_gender"),
                )
                IconButton(
                    onClick = {
                        onAction(
                            WordEditorAction.GrammaticalGenderVisibilityChanged(
                                sense.key,
                                false,
                            ),
                        )
                    },
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "문법 성 삭제",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
            TextButton(
                onClick = {
                    onAction(
                        WordEditorAction.GrammaticalGenderVisibilityChanged(sense.key, true),
                    )
                },
            ) {
                Text("+ 문법 성 추가")
            }
        }
        Text("예문", style = MaterialTheme.typography.labelMedium)
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
                    IconButton(
                        onClick = {
                            onAction(WordEditorAction.RemoveExample(sense.key, example.key))
                        },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "예문 삭제",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        TextButton(onClick = { onAction(WordEditorAction.AddExample(sense.key)) }) {
            Text("예문 추가")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PronunciationEditor(
    pronunciation: EditablePronunciation,
    onAction: (WordEditorAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PronunciationNotation.entries.forEach { notation ->
                FilterChip(
                    selected = pronunciation.notation == notation,
                    onClick = {
                        onAction(
                            WordEditorAction.PronunciationNotationChanged(
                                pronunciation.key,
                                notation,
                            ),
                        )
                    },
                    label = { Text(notation.displayLabel()) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = pronunciation.value,
                onValueChange = {
                    onAction(
                        WordEditorAction.PronunciationValueChanged(pronunciation.key, it),
                    )
                },
                label = { Text(pronunciation.notation.displayLabel()) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("pronunciation"),
            )
            IconButton(
                onClick = {
                    onAction(WordEditorAction.RemovePronunciation(pronunciation.key))
                },
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "발음 삭제",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        pronunciation.provenance?.let { provenance ->
            MetadataLabel(
                buildString {
                    append(provenance.sourceName)
                    append("에서 가져온 발음")
                    if (provenance.modifiedAfterImport) append(" · 수정됨")
                },
                modifier = Modifier.testTag("pronunciation_provenance"),
            )
        }
    }
}

private fun PronunciationNotation.displayLabel(): String = when (this) {
    PronunciationNotation.IPA -> "IPA"
    PronunciationNotation.PHONETIC -> "음성 표기"
    PronunciationNotation.ROMANIZATION -> "로마자 표기"
    PronunciationNotation.OTHER -> "기타"
}

@Composable
private fun ErrorText(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

private fun validationMessage(error: VocabularyValidationError): String = when (error) {
    VocabularyValidationError.MissingHeadword -> "단어 또는 표현을 입력하세요."
    VocabularyValidationError.InvalidLanguageTag -> "유효한 BCP 47 언어 태그를 입력하세요."
    VocabularyValidationError.MissingSense -> "뜻을 하나 이상 추가하세요."
    is VocabularyValidationError.MissingMeaning -> "뜻 ${error.senseIndex + 1}의 의미를 입력하세요."
    is VocabularyValidationError.InvalidPronunciationLanguageTag ->
        "발음의 BCP 47 언어 태그가 올바르지 않습니다."
}
