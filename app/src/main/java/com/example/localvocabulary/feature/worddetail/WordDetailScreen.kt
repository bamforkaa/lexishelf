package com.example.localvocabulary.feature.worddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.core.ui.component.ScreenStatePane
import com.example.localvocabulary.core.ui.component.SectionHeader
import com.example.localvocabulary.core.model.LanguageDisplayNameResolver
import com.example.localvocabulary.vocabulary.domain.PronunciationNotation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordDetailScreen(
    state: WordDetailUiState,
    onAction: (WordDetailAction) -> Unit,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenTag: (Long) -> Unit,
    onOpenWordbook: (Long) -> Unit = {},
) {
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val entryId = (state as? WordDetailUiState.Content)?.entry?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("단어 상세") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (entryId != null) {
                        TextButton(onClick = { onEdit(entryId) }) { Text("수정") }
                        TextButton(onClick = { showDeleteConfirmation = true }) {
                            Text("삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            WordDetailUiState.Loading -> ScreenStatePane(
                title = "단어를 불러오는 중입니다",
                isLoading = true,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            WordDetailUiState.NotFound -> ScreenStatePane(
                title = "단어를 찾을 수 없습니다",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is WordDetailUiState.Error -> ScreenStatePane(
                title = "단어를 불러오지 못했습니다",
                supportingText = state.message,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is WordDetailUiState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(state.entry.headword, style = MaterialTheme.typography.headlineMedium)

                    }
                }

                itemsIndexed(state.entry.senses, key = { _, sense -> sense.id }) { index, sense ->
                    val review = state.reviewStates.firstOrNull { it.senseStableId == sense.stableId }
                    val enabled = review?.enabled == true
                    SenseDetailSection(
                        sense = sense,
                        index = index,
                        reviewEnabled = enabled,
                        reviewError = state.reviewError,
                        onToggleReview = { onAction(WordDetailAction.SetReviewEnabled(sense.stableId, !enabled)) },
                    )
                }
                if (state.entry.notes.isNotBlank()) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SectionHeader("메모")
                            Text(state.entry.notes, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionHeader("언어 정보")
                        if (state.entry.reading.isNotBlank()) {
                            Text(
                                state.entry.reading,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.entry.pronunciations.isNotEmpty()) {
                            SectionHeader("발음")
                            state.entry.pronunciations.forEach { pronunciation ->
                                Text(
                                    "${pronunciation.notation.detailLabel()} · ${pronunciation.value}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        val language = LanguageDisplayNameResolver.resolve(state.entry.languageTag)
                        MetadataLabel("언어 · ${language.name} · ${language.languageTag}")
                    }
                }
                if (state.entry.wordbooks.isNotEmpty() || state.entry.tags.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SectionHeader("정리")
                            if (state.entry.wordbooks.isNotEmpty()) {
                                SectionHeader("단어장")
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    state.entry.wordbooks.forEach { wordbook ->
                                        AssistChip(
                                            onClick = { onOpenWordbook(wordbook.id) },
                                            label = { Text(wordbook.name) },
                                        )
                                    }
                                }
                            }
                            if (state.entry.tags.isNotEmpty()) {
                                SectionHeader("태그")
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    state.entry.tags.forEach { tag ->
                                        AssistChip(
                                            onClick = { onOpenTag(tag.id) },
                                            label = { Text(tag.name) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("단어 삭제") },
            text = { Text("이 단어와 뜻, 예문, 태그 연결을 삭제할까요?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onAction(WordDetailAction.DeleteConfirmed)
                    },
                ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("취소") }
            },
        )
    }
}

private fun PronunciationNotation.detailLabel(): String = when (this) {
    PronunciationNotation.IPA -> "IPA"
    PronunciationNotation.PHONETIC -> "음성 표기"
    PronunciationNotation.ROMANIZATION -> "로마자 표기"
    PronunciationNotation.OTHER -> "기타 발음"
}
