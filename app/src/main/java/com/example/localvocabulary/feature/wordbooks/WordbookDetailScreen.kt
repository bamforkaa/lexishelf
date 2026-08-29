package com.example.localvocabulary.feature.wordbooks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.model.LanguageDisplayNameResolver
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.core.ui.component.ScreenStatePane
import com.example.localvocabulary.feature.wordlist.VocabularyListItem
import com.example.localvocabulary.feature.wordlist.VocabularySearchField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordbookDetailScreen(
    state: WordbookDetailUiState,
    onAction: (WordbookDetailAction) -> Unit,
    onBack: () -> Unit,
    onOpenWord: (Long) -> Unit,
    onStartWritingPractice: () -> Unit = {},
) {
    val isSelecting = state.mode != WordbookSelectionMode.BROWSE
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelecting) {
                            "${state.selectedEntryIds.size}개 선택됨"
                        } else {
                            state.wordbook?.name ?: "단어장"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelecting) {
                                onAction(WordbookDetailAction.CancelSelection)
                            } else {
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSelecting) "선택 취소" else "뒤로",
                        )
                    }
                },
                actions = {
                    if (!isSelecting && state.wordbook != null) {
                        IconButton(
                            onClick = onStartWritingPractice,
                            enabled = state.membershipEntryIds.isNotEmpty(),
                            modifier = Modifier.testTag("practice_wordbook"),
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "이 단어장 쓰기 연습",
                            )
                        }
                        TextButton(onClick = { onAction(WordbookDetailAction.StartAdding) }) {
                            Text("단어 추가")
                        }
                        TextButton(
                            onClick = { onAction(WordbookDetailAction.StartRemoving) },
                            enabled = state.membershipEntryIds.isNotEmpty(),
                        ) { Text("선택") }
                    }
                },
            )
        },
        bottomBar = {
            if (isSelecting) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Button(
                        onClick = {
                            if (state.mode == WordbookSelectionMode.ADD) {
                                onAction(WordbookDetailAction.AddSelected)
                            } else {
                                onAction(WordbookDetailAction.RemoveRequested)
                            }
                        },
                        enabled = state.selectedEntryIds.isNotEmpty() && !state.isUpdating,
                        modifier = Modifier.fillMaxWidth().testTag("wordbook_batch_action"),
                    ) {
                        Text(
                            if (state.mode == WordbookSelectionMode.ADD) {
                                "${state.selectedEntryIds.size}개 추가"
                            } else {
                                "단어장에서 제거"
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            VocabularySearchField(
                query = state.query,
                onQueryChanged = { onAction(WordbookDetailAction.QueryChanged(it)) },
                onClear = { onAction(WordbookDetailAction.QueryChanged("")) },
            )
            if (isSelecting) {
                WordbookSelectionFilters(state = state, onAction = onAction)
            }
            MetadataLabel(
                text = if (state.mode == WordbookSelectionMode.BROWSE) {
                    "${state.membershipEntryIds.size}개 단어"
                } else {
                    "${state.entries.size}개 검색 결과"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.message?.let {
                MetadataLabel(
                    text = it,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            when {
                state.isLoading -> ScreenStatePane(
                    title = "단어장을 불러오는 중입니다",
                    isLoading = true,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                state.errorMessage != null -> ScreenStatePane(
                    title = "단어장을 불러오지 못했습니다",
                    supportingText = state.errorMessage,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                state.entries.isEmpty() -> ScreenStatePane(
                    title = when (state.mode) {
                        WordbookSelectionMode.BROWSE -> "아직 포함된 단어가 없습니다"
                        WordbookSelectionMode.ADD -> "추가할 수 있는 단어가 없습니다"
                        WordbookSelectionMode.REMOVE -> "조건에 맞는 단어가 없습니다"
                    },
                    supportingText = when (state.mode) {
                        WordbookSelectionMode.BROWSE -> "단어 추가로 기존 단어를 이 단어장에 넣어 보세요."
                        else -> "검색어나 필터를 바꿔 보세요."
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                else -> WordbookEntryList(
                    state = state,
                    onAction = onAction,
                    onOpenWord = onOpenWord,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (state.isRemoveConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { onAction(WordbookDetailAction.RemoveCancelled) },
            title = { Text("단어장에서 제거") },
            text = {
                Text(
                    "선택한 ${state.selectedEntryIds.size}개 단어를 " +
                        "‘${state.wordbook?.name.orEmpty()}’에서 제거하시겠습니까?\n" +
                        "저장된 단어 자체는 삭제되지 않습니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(WordbookDetailAction.RemoveConfirmed) }) {
                    Text("단어장에서 제거", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(WordbookDetailAction.RemoveCancelled) }) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun WordbookSelectionFilters(
    state: WordbookDetailUiState,
    onAction: (WordbookDetailAction) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(WordbookSelectionFilter.entries) { filter ->
            FilterChip(
                selected = state.filterCategory == filter,
                onClick = { onAction(WordbookDetailAction.FilterSelected(filter)) },
                label = {
                    Text(
                        when (filter) {
                            WordbookSelectionFilter.ALL -> "전체"
                            WordbookSelectionFilter.LANGUAGE -> "언어"
                            WordbookSelectionFilter.TAG -> "태그"
                        },
                    )
                },
            )
        }
    }
    if (state.filterCategory != WordbookSelectionFilter.ALL) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedLanguageTag == null && state.selectedTagId == null,
                    onClick = {
                        if (state.filterCategory == WordbookSelectionFilter.LANGUAGE) {
                            onAction(WordbookDetailAction.LanguageSelected(null))
                        } else {
                            onAction(WordbookDetailAction.TagSelected(null))
                        }
                    },
                    label = { Text("모두") },
                )
            }
            when (state.filterCategory) {
                WordbookSelectionFilter.ALL -> Unit
                WordbookSelectionFilter.LANGUAGE -> items(
                    state.languages,
                    key = { "language-$it" },
                ) { tag ->
                    FilterChip(
                        selected = state.selectedLanguageTag == tag,
                        onClick = { onAction(WordbookDetailAction.LanguageSelected(tag)) },
                        label = { Text(LanguageDisplayNameResolver.resolve(tag).name) },
                    )
                }
                WordbookSelectionFilter.TAG -> items(
                    state.tags,
                    key = { "tag-${it.id}" },
                ) { tag ->
                    FilterChip(
                        selected = state.selectedTagId == tag.id,
                        onClick = { onAction(WordbookDetailAction.TagSelected(tag.id)) },
                        label = { Text(tag.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WordbookEntryList(
    state: WordbookDetailUiState,
    onAction: (WordbookDetailAction) -> Unit,
    onOpenWord: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("wordbook_entry_list"),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        itemsIndexed(state.entries, key = { _, entry -> entry.id }) { index, entry ->
            val existingMembership = state.mode == WordbookSelectionMode.ADD &&
                entry.id in state.membershipEntryIds
            val selected = existingMembership || entry.id in state.selectedEntryIds
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.mode != WordbookSelectionMode.BROWSE) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = if (existingMembership) {
                            { }
                        } else {
                            { onAction(WordbookDetailAction.EntryToggled(entry.id)) }
                        },
                        enabled = !existingMembership,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("wordbook_entry_checkbox_${entry.id}")
                            .semantics {
                                stateDescription = if (existingMembership) {
                                    "이미 이 단어장에 포함됨"
                                } else if (selected) {
                                    "선택됨"
                                } else {
                                    "선택되지 않음"
                                }
                            },
                    )
                }
                Column(Modifier.weight(1f)) {
                    VocabularyListItem(
                        entry = entry,
                        enabled = !existingMembership,
                        onClick = {
                            if (state.mode == WordbookSelectionMode.BROWSE) {
                                onOpenWord(entry.id)
                            } else {
                                onAction(WordbookDetailAction.EntryToggled(entry.id))
                            }
                        },
                    )
                    if (existingMembership) {
                        MetadataLabel(
                            "이미 포함됨",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (index < state.entries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
