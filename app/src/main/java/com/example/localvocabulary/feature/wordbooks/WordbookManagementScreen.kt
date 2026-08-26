package com.example.localvocabulary.feature.wordbooks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.ScreenStatePane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordbookManagementScreen(
    state: WordbookManagementUiState,
    onAction: (WordbookManagementAction) -> Unit,
    onBack: () -> Unit,
    onOpenWordbook: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 단어장") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.nameInput,
                    onValueChange = { onAction(WordbookManagementAction.NameChanged(it)) },
                    label = { Text(if (state.editingWordbookId == null) "새 단어장" else "단어장 이름") },
                    supportingText = state.errorMessage?.let { message -> { Text(message) } },
                    isError = state.errorMessage != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAction(WordbookManagementAction.Save) },
                        enabled = !state.isSaving,
                    ) { Text(if (state.editingWordbookId == null) "추가" else "저장") }
                    if (state.editingWordbookId != null) {
                        TextButton(onClick = { onAction(WordbookManagementAction.EditCancelled) }) {
                            Text("취소")
                        }
                    }
                }
            }
            if (state.wordbooks.isEmpty()) {
                ScreenStatePane(
                    title = "아직 단어장이 없습니다",
                    supportingText = "목적에 맞는 단어장을 만들어 보세요.",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.wordbooks, key = { it.wordbook.id }) { summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenWordbook(summary.wordbook.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(summary.wordbook.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${summary.entryCount}개 단어",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                onAction(WordbookManagementAction.EditStarted(summary.wordbook))
                            }) { Text("수정") }
                            TextButton(onClick = {
                                onAction(WordbookManagementAction.DeleteRequested(summary.wordbook))
                            }) { Text("삭제") }
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }

    state.pendingDeleteWordbook?.let { wordbook ->
        AlertDialog(
            onDismissRequest = { onAction(WordbookManagementAction.DeleteCancelled) },
            title = { Text("단어장 삭제") },
            text = {
                Text("‘${wordbook.name}’ 단어장을 삭제하시겠습니까?\n단어장에 포함된 단어 자체는 삭제되지 않습니다.")
            },
            confirmButton = {
                TextButton(onClick = { onAction(WordbookManagementAction.DeleteConfirmed) }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(WordbookManagementAction.DeleteCancelled) }) {
                    Text("취소")
                }
            },
        )
    }
}
