package com.example.localvocabulary.feature.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    state: TagManagementUiState,
    onAction: (TagManagementAction) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("태그 관리") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = { onAction(TagManagementAction.NameChanged(it)) },
                label = { Text(if (state.editingTagId == null) "새 태그 이름" else "태그 이름 수정") },
                singleLine = true,
                isError = state.errorMessage != null,
                supportingText = state.errorMessage?.let { message -> { Text(message) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onAction(TagManagementAction.Save) },
                    enabled = !state.isSaving,
                ) { Text(if (state.editingTagId == null) "태그 추가" else "변경 저장") }
                if (state.editingTagId != null) {
                    TextButton(onClick = { onAction(TagManagementAction.EditCancelled) }) {
                        Text("취소")
                    }
                }
            }
            if (state.tags.isEmpty()) {
                Text("아직 태그가 없습니다.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tags, key = { it.id }) { tag ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(tag.name, style = MaterialTheme.typography.titleMedium)
                                Row {
                                    TextButton(
                                        onClick = { onAction(TagManagementAction.EditStarted(tag)) },
                                    ) { Text("수정") }
                                    TextButton(
                                        onClick = { onAction(TagManagementAction.Delete(tag.id)) },
                                    ) { Text("삭제") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
