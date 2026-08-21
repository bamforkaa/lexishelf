package com.example.localvocabulary.feature.wordlist

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    state: WordListUiState,
    onAction: (WordListAction) -> Unit,
    onAddWord: () -> Unit,
    onOpenWord: (Long) -> Unit,
    onManageTags: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 단어장") },
                actions = {
                    TextButton(onClick = onManageTags) { Text("태그") }
                    TextButton(onClick = onOpenBackup) { Text("백업") }
                    TextButton(onClick = onOpenSettings) { Text("설정") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddWord) { Text("추가") }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(WordListAction.QueryChanged(it)) },
                label = { Text("단어 또는 뜻 검색") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedTagId == null,
                        onClick = { onAction(WordListAction.TagSelected(null)) },
                        label = { Text("전체") },
                    )
                }
                items(state.tags, key = { it.id }) { tag ->
                    FilterChip(
                        selected = state.selectedTagId == tag.id,
                        onClick = { onAction(WordListAction.TagSelected(tag.id)) },
                        label = { Text(tag.name) },
                    )
                }
            }

            when {
                state.isLoading -> CenteredMessage { CircularProgressIndicator() }
                state.errorMessage != null -> CenteredMessage { Text(state.errorMessage) }
                state.entries.isEmpty() -> CenteredMessage {
                    Text(if (state.query.isBlank() && state.selectedTagId == null) "아직 저장된 단어가 없습니다." else "검색 결과가 없습니다.")
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        VocabularyEntryCard(entry = entry, onClick = { onOpenWord(entry.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabularyEntryCard(entry: VocabularyEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(entry.headword, style = MaterialTheme.typography.titleLarge)
                Text(entry.languageTag, style = MaterialTheme.typography.labelMedium)
            }
            entry.senses.firstOrNull()?.let { Text(it.meaning) }
            if (entry.tags.isNotEmpty()) {
                Text(
                    entry.tags.joinToString(separator = " · ") { it.name },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
