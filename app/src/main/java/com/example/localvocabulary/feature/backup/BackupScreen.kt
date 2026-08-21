package com.example.localvocabulary.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.backup.domain.BackupConflictPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onAction: (BackupAction) -> Unit,
    onBack: () -> Unit,
) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> onAction(BackupAction.ExportDestinationSelected(uri?.toString())) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> onAction(BackupAction.ImportFileSelected(uri?.toString())) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("백업 및 복원") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("JSON 백업", style = MaterialTheme.typography.titleLarge)
            Text("단어, 뜻, 예문, 메모, 태그와 관계만 포함합니다. 앱 설정이나 자격 증명은 포함하지 않습니다.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { exportLauncher.launch("local-vocabulary-backup.json") },
                    enabled = !state.isBusy,
                ) { Text("내보내기") }
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    enabled = !state.isBusy,
                ) { Text("가져오기") }
            }

            if (state.isBusy) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("처리 중…")
                }
            }

            state.preview?.let { preview ->
                ImportPreviewCard(state, onAction)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("가져오기 미리보기", style = MaterialTheme.typography.titleMedium)
                        Text("단어 ${preview.entryCount}, 뜻 ${preview.senseCount}, 예문 ${preview.exampleCount}")
                        Text("태그 ${preview.tagCount}, 충돌 ${preview.conflictCount}")
                        Text("새로 생성 ${preview.newEntryCount}, 갱신 ${preview.updatedEntryCount}, 건너뜀 ${preview.skippedEntryCount}")
                        if (preview.existingEntryRemovalCount > 0) {
                            Text(
                                "기존 단어 ${preview.existingEntryRemovalCount}개가 제거됩니다.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onAction(BackupAction.ConfirmImport) },
                        enabled = !state.isBusy,
                    ) { Text("확인 후 복원") }
                    TextButton(
                        onClick = { onAction(BackupAction.CancelImport) },
                        enabled = !state.isBusy,
                    ) { Text("취소") }
                }
            }

            state.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ImportPreviewCard(
    state: BackupUiState,
    onAction: (BackupAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("충돌 정책", style = MaterialTheme.typography.titleMedium)
        PolicyRow(
            selected = state.conflictPolicy == BackupConflictPolicy.MERGE_BY_STABLE_ID,
            title = "기존 데이터 유지 + 병합 (기본)",
            description = "같은 stable ID만 전체 갱신하고, 나머지 기존 데이터는 유지합니다.",
            onClick = {
                onAction(BackupAction.ConflictPolicySelected(BackupConflictPolicy.MERGE_BY_STABLE_ID))
            },
        )
        PolicyRow(
            selected = state.conflictPolicy == BackupConflictPolicy.REPLACE_ALL,
            title = "기존 데이터 전체 교체",
            description = "현재 단어와 태그를 모두 지운 뒤 백업 내용으로 교체합니다.",
            onClick = {
                onAction(BackupAction.ConflictPolicySelected(BackupConflictPolicy.REPLACE_ALL))
            },
        )
    }
}

@Composable
private fun PolicyRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
