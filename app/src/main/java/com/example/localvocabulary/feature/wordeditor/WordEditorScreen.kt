package com.example.localvocabulary.feature.wordeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.DictionaryProvenanceLabel
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
import kotlinx.coroutines.launch

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
    var showNotes by rememberSaveable { mutableStateOf(false) }
    var showLinguistics by rememberSaveable { mutableStateOf(false) }
    var showOrganization by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    if (state.reviewChanges.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { onAction(WordEditorAction.DismissReviewChanges) },
            title = { Text("이 의미에는 기존 복습 기록이 있습니다") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("기본값은 학습 기록 유지입니다. 초기화할 뜻만 선택하세요. 과거 평가와 문맥은 보존됩니다.") }
                    items(state.reviewChanges, key = { it.stateId }) { change ->
                        Column {
                            Text(change.meaning)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = change.stateId in state.reviewResetIds,
                                    onCheckedChange = { onAction(WordEditorAction.ReviewResetToggled(change.stateId)) })
                                Text("이 뜻의 학습 기록 초기화")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onAction(WordEditorAction.ConfirmReviewChanges) }) {
                Text(if (state.reviewResetIds.isEmpty()) "학습 기록 유지하고 저장" else "선택한 학습 초기화 후 저장")
            } },
            dismissButton = { TextButton(onClick = { onAction(WordEditorAction.DismissReviewChanges) }) { Text("취소") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.entryId == null) "표현 추가" else "표현 수정") },
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .testTag("editor_scroll"),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                EditorSection {
                    OutlinedTextField(
                        value = state.headword,
                        onValueChange = { onAction(WordEditorAction.HeadwordChanged(it)) },
                        label = { Text("단어 또는 표현") },
                        minLines = 1,
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
                        isError = state.validationError == VocabularyValidationError.InvalidLanguageTag,
                        testTag = "language_tag",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "dictionary_suggestions") {
                DictionarySuggestionSection(state = state, onAction = onAction)
            }

            items(state.senses, key = { it.key }) { sense ->
                SenseEditorSection(
                    sense = sense,
                    canRemove = state.senses.size > 1,
                    reviewEnabled = sense.reviewEnabled ?: if (state.entryId == null)
                        sense.key == state.senses.firstOrNull()?.key else sense.stableId in state.enabledReviewSenseIds,
                    isNewEntry = state.entryId == null,
                    onAction = onAction,
                )
            }

            item(key = "add_sense") {
                EditorSecondaryAction(
                    text = "+ 뜻 추가",
                    onClick = {
                        onAction(WordEditorAction.AddSense)
                        if (state.synthesizedSuggestionGroups.any { it.candidates.isNotEmpty() }) {
                            focusManager.clearFocus()
                            scope.launch { listState.animateScrollToItem(1) }
                        }
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag("add_sense"),
                    labelTag = "add_sense_label",
                )
            }
            item(key = "notes_toggle") {
                EditorDisclosureButton(
                    title = "메모", expanded = showNotes, hasContent = state.notes.isNotBlank(),
                    onClick = { showNotes = !showNotes }, testTag = "editor_notes",
                )
            }
            if (showNotes) {
                item(key = "notes") {
                    EditorSection {
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = { onAction(WordEditorAction.NotesChanged(it)) },
                            label = { Text("메모") }, minLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("notes"),
                        )
                    }
                }
            }
            item(key = "linguistics_toggle") {
                EditorDisclosureButton(
                    title = "언어 정보 · 발음, 표기, 품사", expanded = showLinguistics,
                    hasContent = state.reading.isNotBlank() || state.pronunciations.isNotEmpty() ||
                        state.senses.any { it.partOfSpeech.isNotBlank() || it.grammaticalGender.isNotBlank() },
                    onClick = { showLinguistics = !showLinguistics }, testTag = "editor_linguistics",
                )
            }
            if (showLinguistics) {
                item {
                    EditorSection {
                        OutlinedTextField(
                            value = state.reading,
                            onValueChange = { onAction(WordEditorAction.ReadingChanged(it)) },
                            label = { Text("읽기 / 표기") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("reading"),
                        )
                        state.readingProvenance?.let { provenance ->
                            DictionaryProvenanceLabel(
                                provenance, modifier = Modifier.testTag("reading_provenance"),
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

                items(state.senses, key = { "linguistics_${it.key}" }) { sense ->
                    EditorSection {
                        Text("뜻 ${state.senses.indexOf(sense) + 1} · ${sense.meaning}",
                            style = MaterialTheme.typography.labelLarge)
                        SenseLinguisticsEditor(sense, onAction)
                    }
                }
            }
            item(key = "organization_toggle") {
                EditorDisclosureButton(
                    title = "정리 · 단어장, 태그", expanded = showOrganization,
                    hasContent = state.selectedTagIds.isNotEmpty() || state.selectedWordbookIds.isNotEmpty(),
                    onClick = { showOrganization = !showOrganization }, testTag = "editor_organization",
                )
            }
            if (showOrganization) {
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

                    }
                }

            }
            if (state.draftRecoveryLimited) {
                item { ErrorText("초안이 커서 앱 종료 후 자동 복구할 수 없습니다. 이동 전에 저장하세요.") }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SenseEditorSection(
    sense: EditableSense,
    canRemove: Boolean,
    reviewEnabled: Boolean,
    isNewEntry: Boolean,
    onAction: (WordEditorAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            modifier = Modifier.fillMaxWidth().testTag("meaning_${sense.key}"),
        )
        sense.provenance?.let { provenance ->
            DictionaryProvenanceLabel(
                provenance, modifier = Modifier.testTag("sense_provenance"),
            )
        }
        TextButton(
            onClick = { onAction(WordEditorAction.SetSenseReviewEnabled(sense.key, !reviewEnabled)) },
            modifier = Modifier.testTag("editor_review_${sense.key}"),
        ) {
            Text(if (!reviewEnabled) "복습에 추가" else if (isNewEntry) "저장 시 복습 · 끄기" else "복습 중 · 중지")
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            sense.examples.forEach { example ->
                key(example.key) { ContextEditor(sense.key, example, onAction) }
            }
            EditorSecondaryAction(
                text = "문맥 / 예문 추가",
                onClick = { onAction(WordEditorAction.AddExample(sense.key)) },
                modifier = Modifier.padding(top = 2.dp).testTag("add_context_${sense.key}"),
                labelTag = "add_context_label_${sense.key}",
            )
        }
    }
}

@Composable
private fun SenseLinguisticsEditor(sense: EditableSense, onAction: (WordEditorAction) -> Unit) {
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
            DictionaryProvenanceLabel(
                provenance, modifier = Modifier.testTag("pronunciation_provenance"),
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
    VocabularyValidationError.InvalidChildIdentity -> "뜻 또는 문맥 ID가 중복되거나 올바르지 않습니다."
    is VocabularyValidationError.InvalidExample -> "뜻 ${error.senseIndex + 1}의 문맥 ${error.exampleIndex + 1}: 원문을 입력하세요. 기록 시각은 음수일 수 없습니다."
    VocabularyValidationError.MissingHeadword -> "단어 또는 표현을 입력하세요."
    VocabularyValidationError.InvalidLanguageTag -> "유효한 BCP 47 언어 태그를 입력하세요."
    VocabularyValidationError.MissingSense -> "뜻을 하나 이상 추가하세요."
    is VocabularyValidationError.MissingMeaning -> "뜻 ${error.senseIndex + 1}의 의미를 입력하세요."
    is VocabularyValidationError.InvalidPronunciationLanguageTag ->
        "발음의 BCP 47 언어 태그가 올바르지 않습니다."
}

@Composable
private fun EditorDisclosureButton(
    title: String,
    expanded: Boolean,
    hasContent: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    EditorSecondaryAction(
        text = if (expanded) "$title 접기" else "+ $title${if (hasContent) " · 입력됨" else ""}",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp).testTag(testTag),
        labelTag = "${testTag}_label",
        fillLabel = true,
    )
}
