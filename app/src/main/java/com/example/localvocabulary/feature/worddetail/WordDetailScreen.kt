package com.example.localvocabulary.feature.worddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    state: WordDetailUiState,
    onAction: (WordDetailAction) -> Unit,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val entryId = (state as? WordDetailUiState.Content)?.entry?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("단어 상세") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
                actions = {
                    if (entryId != null) {
                        TextButton(onClick = { onEdit(entryId) }) { Text("수정") }
                        TextButton(onClick = { showDeleteConfirmation = true }) { Text("삭제") }
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            WordDetailUiState.Loading -> CenteredDetail { CircularProgressIndicator() }
            WordDetailUiState.NotFound -> CenteredDetail { Text("단어를 찾을 수 없습니다.") }
            is WordDetailUiState.Error -> CenteredDetail { Text(state.message) }
            is WordDetailUiState.Content -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(state.entry.headword, style = MaterialTheme.typography.headlineMedium)
                        Text("언어: ${state.entry.languageTag}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.entry.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text(tag.name) }) }
                        }
                    }
                }
                itemsIndexed(state.entry.senses, key = { _, sense -> sense.id }) { index, sense ->
                    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("뜻 ${index + 1}", style = MaterialTheme.typography.labelLarge)
                            Text(sense.meaning, style = MaterialTheme.typography.titleMedium)
                            sense.provenance?.let { provenance ->
                                Text(
                                    text = buildString {
                                        append(provenance.sourceName)
                                        append(" 기반")
                                        if (provenance.modifiedAfterImport) append(" · 수정됨")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("sense_provenance"),
                                )
                            }
                            if (sense.partOfSpeech.isNotBlank()) Text("품사: ${sense.partOfSpeech}")
                            sense.examples.forEach { example -> Text("예: ${example.text}") }
                        }
                    }
                }
                if (state.entry.notes.isNotBlank()) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("메모", style = MaterialTheme.typography.titleMedium)
                            Text(state.entry.notes)
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
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun CenteredDetail(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
