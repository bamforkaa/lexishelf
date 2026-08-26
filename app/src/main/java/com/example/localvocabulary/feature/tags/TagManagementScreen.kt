package com.example.localvocabulary.feature.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.core.ui.component.ScreenStatePane
import com.example.localvocabulary.core.ui.component.SectionHeader
import com.example.localvocabulary.vocabulary.domain.VocabularyTagSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    state: TagManagementUiState,
    onAction: (TagManagementAction) -> Unit,
    onBack: () -> Unit,
    onOpenTag: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("태그") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionHeader(
                    title = if (state.editingTagId == null) "새 태그" else "태그 이름 수정",
                    supportingText = "단어 내용을 설명하는 라벨을 관리합니다.",
                )
                OutlinedTextField(
                    value = state.nameInput,
                    onValueChange = { onAction(TagManagementAction.NameChanged(it)) },
                    label = { Text("태그 이름") },
                    singleLine = true,
                    isError = state.errorMessage != null,
                    supportingText = state.errorMessage?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAction(TagManagementAction.Save) },
                        enabled = !state.isSaving,
                    ) { Text(if (state.editingTagId == null) "추가" else "변경 저장") }
                    if (state.editingTagId != null) {
                        TextButton(onClick = { onAction(TagManagementAction.EditCancelled) }) {
                            Text("취소")
                        }
                    }
                }
                SectionHeader(
                    title = "내 태그",
                    supportingText = "태그를 눌러 포함된 단어를 봅니다.",
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (state.tags.isEmpty()) {
                ScreenStatePane(
                    title = "아직 만든 태그가 없습니다",
                    supportingText = "예: 여행, JLPT N2, 회사 영어",
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(state.tags, key = { _, item -> item.tag.id }) { index, item ->
                        TagCollectionRow(
                            summary = item,
                            onOpen = { onOpenTag(item.tag.id) },
                            onEdit = { onAction(TagManagementAction.EditStarted(item.tag)) },
                            onDelete = {
                                onAction(TagManagementAction.DeleteRequested(item.tag))
                            },
                        )
                        if (index < state.tags.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    state.pendingDeleteTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { onAction(TagManagementAction.DeleteCancelled) },
            title = { Text("태그 삭제") },
            text = {
                Text("‘${tag.name}’ 태그를 삭제하시겠습니까? 이 태그에 포함된 단어 자체는 삭제되지 않습니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(TagManagementAction.DeleteConfirmed) },
                    modifier = Modifier.testTag("confirm_delete_tag"),
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(TagManagementAction.DeleteCancelled) }) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun TagCollectionRow(
    summary: VocabularyTagSummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(summary.tag.name, style = MaterialTheme.typography.titleMedium)
            MetadataLabel("${summary.entryCount}개 단어")
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "${summary.tag.name} 태그 수정")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "${summary.tag.name} 태그 삭제",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
