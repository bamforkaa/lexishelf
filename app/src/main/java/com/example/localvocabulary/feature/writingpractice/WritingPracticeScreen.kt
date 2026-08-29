package com.example.localvocabulary.feature.writingpractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.model.LanguageDisplayNameResolver
import com.example.localvocabulary.feature.handwriting.HandwritingInputAction
import com.example.localvocabulary.feature.handwriting.HandwritingInputUiState
import com.example.localvocabulary.feature.handwriting.InlineHandwritingInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingPracticeScreen(
    state: WritingPracticeUiState,
    onAction: (WritingPracticeAction) -> Unit,
    onBack: () -> Unit,
    handwritingState: HandwritingInputUiState = HandwritingInputUiState(),
    onHandwritingAction: (HandwritingInputAction) -> Unit = {},
    onHandwritingCandidateSelected: (String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("쓰기 연습") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        when (state.phase) {
            PracticePhase.SETUP -> PracticeSetup(
                state = state,
                onAction = onAction,
                modifier = Modifier.padding(padding),
            )
            PracticePhase.QUESTION -> PracticeQuestionContent(
                state = state,
                onAction = onAction,
                handwritingState = handwritingState,
                onHandwritingAction = onHandwritingAction,
                onHandwritingCandidateSelected = onHandwritingCandidateSelected,
                modifier = Modifier.padding(padding),
            )
            PracticePhase.COMPLETE -> PracticeSummary(
                state = state,
                onAction = onAction,
                onExit = onBack,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun PracticeSetup(
    state: WritingPracticeUiState,
    onAction: (WritingPracticeAction) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("practice_setup"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("연습 범위", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                PracticeScopeType.entries.forEach { type ->
                    FilterChip(
                        selected = state.scopeType == type,
                        onClick = { onAction(WritingPracticeAction.ScopeTypeSelected(type)) },
                        label = { Text(type.label()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

        when (state.scopeType) {
            PracticeScopeType.ALL -> Unit
            PracticeScopeType.LANGUAGE -> item {
                ScopeOptionsHeader("언어 선택")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.languages.forEach { languageTag ->
                        FilterChip(
                            selected = state.selectedLanguageTag == languageTag,
                            onClick = {
                                onAction(WritingPracticeAction.LanguageSelected(languageTag))
                            },
                            label = { Text(LanguageDisplayNameResolver.resolve(languageTag).name) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            PracticeScopeType.WORDBOOK -> item {
                ScopeOptionsHeader("단어장 선택")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.wordbooks.forEach { wordbook ->
                        FilterChip(
                            selected = state.selectedWordbookId == wordbook.id,
                            onClick = {
                                onAction(WritingPracticeAction.WordbookSelected(wordbook.id))
                            },
                            label = { Text(wordbook.name) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
            PracticeScopeType.TAG -> item {
                ScopeOptionsHeader("태그 선택")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.tags.forEach { tag ->
                        FilterChip(
                            selected = state.selectedTagId == tag.id,
                            onClick = { onAction(WritingPracticeAction.TagSelected(tag.id)) },
                            label = { Text(tag.name) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }

        item {
            Text("세션 길이", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                PracticeSessionLength.entries.forEach { length ->
                    FilterChip(
                        selected = state.sessionLength == length,
                        onClick = {
                            onAction(WritingPracticeAction.SessionLengthSelected(length))
                        },
                        label = { Text(length.label()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

        item {
            when {
                !state.hasCompleteScopeSelection -> Text(
                    "연습할 범위를 선택하세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.isLoadingEligibility -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("연습 가능한 단어를 확인하는 중입니다.")
                }
                state.errorMessage != null -> Text(
                    state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                )
                state.eligibleCount == 0 -> Text(
                    "이 범위에는 쓰기 연습에 사용할 수 있는 단어가 없습니다.",
                    modifier = Modifier.testTag("practice_empty_scope"),
                )
                else -> {
                    Text(
                        "대상 단어 ${state.eligibleCount}개",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("practice_eligible_count"),
                    )
                    if (state.excludedCount > 0) {
                        Text(
                            "안전한 힌트가 없는 ${state.excludedCount}개 항목은 제외됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { onAction(WritingPracticeAction.Start) },
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("start_writing_practice"),
            ) { Text("연습 시작") }
        }
    }
}

@Composable
private fun PracticeQuestionContent(
    state: WritingPracticeUiState,
    onAction: (WritingPracticeAction) -> Unit,
    handwritingState: HandwritingInputUiState,
    onHandwritingAction: (HandwritingInputAction) -> Unit,
    onHandwritingCandidateSelected: (String) -> Unit,
    modifier: Modifier,
) {
    val question = state.currentQuestion ?: return
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("practice_question"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "${state.currentIndex + 1} / ${state.sessionTotal}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("practice_progress"),
            )
        }
        item {
            HintText(question.primaryHint, primary = true)
            question.secondaryHints.forEach { hint -> HintText(hint, primary = false) }
            if (question.expandedHints.isNotEmpty()) {
                TextButton(
                    onClick = { onAction(WritingPracticeAction.ToggleExpandedHints) },
                    modifier = Modifier.testTag("practice_more_hints"),
                ) {
                    Text(if (state.showExpandedHints) "힌트 접기" else "힌트 더 보기")
                }
            }
            if (state.showExpandedHints) {
                question.expandedHints.forEach { hint -> HintText(hint, primary = false) }
            }
        }
        item {
            Text("답 입력", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                PracticeInputMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.inputMode == mode,
                        enabled = state.feedback == null,
                        onClick = {
                            onAction(WritingPracticeAction.InputModeSelected(mode))
                        },
                        label = { Text(if (mode == PracticeInputMode.HANDWRITING) "손글씨" else "키보드") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        if (state.inputMode == PracticeInputMode.HANDWRITING && state.feedback == null) {
            item(key = "handwriting-${state.handwritingSessionKey}") {
                InlineHandwritingInput(
                    state = handwritingState,
                    onAction = onHandwritingAction,
                    onCandidateSelected = onHandwritingCandidateSelected,
                    modifier = Modifier.testTag("practice_handwriting_input"),
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.answer,
                onValueChange = { onAction(WritingPracticeAction.AnswerChanged(it)) },
                enabled = state.feedback == null,
                readOnly = state.inputMode == PracticeInputMode.HANDWRITING,
                label = { Text("내 답") },
                supportingText = state.answerError?.let { message ->
                    { Text(message) }
                },
                isError = state.answerError != null,
                singleLine = false,
                modifier = Modifier.fillMaxWidth().testTag("practice_answer"),
            )
        }
        if (state.feedback == null) {
            item {
                Button(
                    onClick = { onAction(WritingPracticeAction.Submit) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("submit_practice_answer"),
                ) { Text("제출") }
            }
        } else {
            item { PracticeFeedback(state = state, onAction = onAction) }
        }
    }
}

@Composable
private fun PracticeFeedback(
    state: WritingPracticeUiState,
    onAction: (WritingPracticeAction) -> Unit,
) {
    val feedback = state.feedback ?: return
    HorizontalDivider()
    Text(
        if (feedback.isCorrect) "✓ 정답" else "다시 확인해 보세요",
        style = MaterialTheme.typography.titleMedium,
        color = if (feedback.isCorrect) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        modifier = Modifier.padding(top = 16.dp).testTag("practice_feedback"),
    )
    Text("정답", style = MaterialTheme.typography.labelMedium)
    Text(
        feedback.expectedHeadword,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag("practice_expected_answer"),
    )
    Text("내 답", style = MaterialTheme.typography.labelMedium)
    Text(feedback.submittedAnswer, style = MaterialTheme.typography.bodyLarge)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!feedback.isCorrect) {
            TextButton(
                onClick = { onAction(WritingPracticeAction.Retry) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .testTag("retry_practice_question"),
            ) { Text("다시 쓰기") }
        }
        Button(
            onClick = { onAction(WritingPracticeAction.Next) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .testTag("next_practice_question"),
        ) { Text(if (state.currentIndex + 1 == state.sessionTotal) "결과 보기" else "다음") }
    }
}

@Composable
private fun PracticeSummary(
    state: WritingPracticeUiState,
    onAction: (WritingPracticeAction) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).testTag("practice_summary"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("${state.sessionTotal}문제 완료", style = MaterialTheme.typography.headlineMedium)
        Text("첫 시도 정답 ${state.firstAttemptCorrect}", style = MaterialTheme.typography.bodyLarge)
        Text("첫 시도 오답 ${state.firstAttemptIncorrect}", style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = { onAction(WritingPracticeAction.Restart) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("다시 연습") }
        TextButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("종료") }
        TextButton(
            onClick = { onAction(WritingPracticeAction.ReturnToSetup) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("범위 바꾸기") }
    }
}

@Composable
private fun HintText(hint: PracticeHint, primary: Boolean) {
    Text(
        hint.kind.label(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = if (primary) 0.dp else 12.dp),
    )
    Text(
        hint.text,
        style = if (primary) MaterialTheme.typography.headlineSmall else {
            MaterialTheme.typography.bodyLarge
        },
        modifier = Modifier.testTag("practice_hint_${hint.kind.name.lowercase()}"),
    )
}

@Composable
private fun ScopeOptionsHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

private fun PracticeScopeType.label(): String = when (this) {
    PracticeScopeType.ALL -> "전체"
    PracticeScopeType.LANGUAGE -> "언어"
    PracticeScopeType.WORDBOOK -> "단어장"
    PracticeScopeType.TAG -> "태그"
}

private fun PracticeSessionLength.label(): String = when (this) {
    PracticeSessionLength.TEN -> "10"
    PracticeSessionLength.TWENTY -> "20"
    PracticeSessionLength.ALL -> "전체"
}

private fun PracticeHintKind.label(): String = when (this) {
    PracticeHintKind.MEANING -> "뜻"
    PracticeHintKind.READING -> "읽기"
    PracticeHintKind.PRONUNCIATION -> "발음"
    PracticeHintKind.GRAMMAR -> "품사 · 문법"
    PracticeHintKind.EXAMPLE -> "예문"
}
