package com.example.localvocabulary.feature.wordlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.core.ui.component.ScreenStatePane
import com.example.localvocabulary.core.model.LanguageDisplayNameResolver
import com.example.localvocabulary.feature.handwriting.HandwritingInputAction
import com.example.localvocabulary.feature.handwriting.HandwritingInputDialog
import com.example.localvocabulary.feature.handwriting.HandwritingInputUiState
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    state: WordListUiState,
    onAction: (WordListAction) -> Unit,
    onAddWord: () -> Unit,
    onOpenWord: (Long) -> Unit,
    onManageTags: () -> Unit,
    onManageWordbooks: () -> Unit = {},
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWritingPractice: () -> Unit = {},
    onOpenTodayReview: () -> Unit = {},
    reviewQueue: com.example.localvocabulary.review.domain.ReviewQueue? = null,
    reviewError: String? = null,
    onBack: (() -> Unit)? = null,
    handwritingState: HandwritingInputUiState = HandwritingInputUiState(),
    onHandwritingAction: (HandwritingInputAction) -> Unit = {},
    onHandwritingCandidateSelected: (String) -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val title = if (state.isCollectionView) {
        state.selectedWordbookName ?: state.selectedTagName ?: "내 단어"
    } else {
        "내 단어"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (state.isCollectionView && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onManageWordbooks) { Text("단어장") }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "더보기")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("쓰기 연습") },
                            onClick = {
                                menuExpanded = false
                                onOpenWritingPractice()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("태그 관리") },
                            onClick = {
                                menuExpanded = false
                                onManageTags()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("백업 및 복원") },
                            onClick = {
                                menuExpanded = false
                                onOpenBackup()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("설정") },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddWord) {
                Icon(Icons.Default.Add, contentDescription = "단어 추가")
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            if (!state.isCollectionView) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = onOpenTodayReview,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column {
                        Text("오늘 복습", style = MaterialTheme.typography.titleMedium)
                        reviewQueue?.let {
                            Text("남은 ${it.cards.size}개 · 신규 ${it.newAvailable}개 포함", style = MaterialTheme.typography.bodySmall)
                        }
                        if (reviewError != null) Text("복습 수를 불러오지 못했습니다. 열어서 다시 시도하세요.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            VocabularySearchField(
                query = state.query,
                onQueryChanged = { onAction(WordListAction.QueryChanged(it)) },
                onClear = { onAction(WordListAction.QueryChanged("")) },
                onHandwriting = {
                    onHandwritingAction(
                        HandwritingInputAction.Open(
                            vocabularyLanguageTags = state.languages,
                        ),
                    )
                },
            )
            if (!state.isCollectionView) {
                FilterCategoryRow(state = state, onAction = onAction)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                item {
                    if (state.filterCategory != VocabularyFilterCategory.ALL) {
                        FilterChip(
                            selected = state.selectedTagId == null &&
                                state.selectedWordbookId == null &&
                                state.selectedLanguageTag == null,
                            onClick = {
                                when (state.filterCategory) {
                                    VocabularyFilterCategory.LANGUAGE ->
                                        onAction(WordListAction.LanguageSelected(null))
                                    VocabularyFilterCategory.WORDBOOK ->
                                        onAction(WordListAction.WordbookSelected(null))
                                    VocabularyFilterCategory.TAG ->
                                        onAction(WordListAction.TagSelected(null))
                                    VocabularyFilterCategory.ALL -> Unit
                                }
                            },
                            label = { Text("모두") },
                        )
                    }
                }
                when (state.filterCategory) {
                    VocabularyFilterCategory.ALL -> Unit
                    VocabularyFilterCategory.LANGUAGE -> items(
                        state.languages,
                        key = { "language-$it" },
                    ) { languageTag ->
                        val display = LanguageDisplayNameResolver.resolve(languageTag)
                        FilterChip(
                            selected = state.selectedLanguageTag == languageTag,
                            onClick = { onAction(WordListAction.LanguageSelected(languageTag)) },
                            label = { Text(display.name) },
                        )
                    }
                    VocabularyFilterCategory.WORDBOOK -> items(
                        state.wordbooks,
                        key = { "wordbook-${it.id}" },
                    ) { wordbook ->
                        FilterChip(
                            selected = state.selectedWordbookId == wordbook.id,
                            onClick = { onAction(WordListAction.WordbookSelected(wordbook.id)) },
                            label = { Text(wordbook.name) },
                        )
                    }
                    VocabularyFilterCategory.TAG -> items(
                        state.tags,
                        key = { "tag-${it.id}" },
                    ) { tag ->
                        FilterChip(
                            selected = state.selectedTagId == tag.id,
                            onClick = { onAction(WordListAction.TagSelected(tag.id)) },
                            label = { Text(tag.name) },
                        )
                    }
                }
                }
            }
            Text(
                "${state.entries.size}개 단어",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                state.isLoading -> ScreenStatePane(
                    title = "단어를 불러오는 중입니다",
                    isLoading = true,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                state.errorMessage != null -> ScreenStatePane(
                    title = "단어를 불러오지 못했습니다",
                    supportingText = state.errorMessage,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                state.entries.isEmpty() -> ScreenStatePane(
                    title = if (state.query.isBlank() && state.selectedTagId == null &&
                        state.selectedWordbookId == null && state.selectedLanguageTag == null
                    ) {
                        "아직 저장된 단어가 없습니다"
                    } else {
                        "조건에 맞는 단어가 없습니다"
                    },
                    supportingText = if (state.query.isBlank() && state.selectedTagId == null &&
                        state.selectedWordbookId == null && state.selectedLanguageTag == null
                    ) {
                        "추가 버튼으로 첫 단어를 저장해 보세요."
                    } else {
                        "검색어나 선택한 태그를 바꿔 보세요."
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                else -> VocabularyEntryList(
                    entries = state.entries,
                    onOpenWord = onOpenWord,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    HandwritingInputDialog(
        state = handwritingState,
        onAction = onHandwritingAction,
        onCandidateSelected = onHandwritingCandidateSelected,
    )
}

@Composable
private fun FilterCategoryRow(
    state: WordListUiState,
    onAction: (WordListAction) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(VocabularyFilterCategory.entries) { category ->
            FilterChip(
                selected = state.filterCategory == category,
                onClick = { onAction(WordListAction.FilterCategorySelected(category)) },
                label = {
                    Text(
                        when (category) {
                            VocabularyFilterCategory.ALL -> "전체"
                            VocabularyFilterCategory.LANGUAGE -> "언어"
                            VocabularyFilterCategory.WORDBOOK -> "단어장"
                            VocabularyFilterCategory.TAG -> "태그"
                        },
                    )
                },
            )
        }
    }
}

@Composable
internal fun VocabularySearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
    onHandwriting: (() -> Unit)? = null,
) {
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text("단어 또는 뜻 검색") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "검색어 지우기")
                    }
                }
                onHandwriting?.let { openHandwriting ->
                    IconButton(
                        onClick = openHandwriting,
                        modifier = Modifier.testTag("open_search_handwriting"),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "손글씨로 검색")
                    }
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun VocabularyEntryList(
    entries: List<VocabularyEntry>,
    onOpenWord: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            VocabularyListItem(entry = entry, onClick = { onOpenWord(entry.id) })
            if (index < entries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
internal fun VocabularyListItem(
    entry: VocabularyEntry,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val firstSense = entry.senses.firstOrNull()
    val visibleTags = entry.tags.take(2)
    val hiddenTagCount = entry.tags.size - visibleTags.size
    val visibleMetadata = buildList {
        firstSense?.partOfSpeech?.takeIf(String::isNotBlank)?.let(::add)
        addAll(entry.wordbooks.take(1).map { it.name })
        addAll(visibleTags.map { it.name })
        if (hiddenTagCount > 0) add("+$hiddenTagCount")
    }.joinToString(" · ")
    val fullMetadataDescription = buildList {
        firstSense?.partOfSpeech?.takeIf(String::isNotBlank)?.let { add("품사 $it") }
        if (entry.tags.isNotEmpty()) add("태그 ${entry.tags.joinToString { it.name }}")
        if (entry.wordbooks.isNotEmpty()) {
            add("단어장 ${entry.wordbooks.joinToString { it.name }}")
        }
    }.joinToString(", ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isPressed) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                entry.headword,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            val language = LanguageDisplayNameResolver.resolve(entry.languageTag)
            Column(horizontalAlignment = Alignment.End) {
                MetadataLabel(language.name)
                if (language.name != language.languageTag) MetadataLabel(language.languageTag)
            }
        }
        if (entry.reading.isNotBlank()) {
            Text(
                entry.reading,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        firstSense?.meaning?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (visibleMetadata.isNotBlank()) {
            MetadataLabel(
                text = visibleMetadata,
                modifier = Modifier.semantics {
                    if (fullMetadataDescription.isNotBlank()) {
                        contentDescription = fullMetadataDescription
                    }
                },
            )
        }
    }
}
