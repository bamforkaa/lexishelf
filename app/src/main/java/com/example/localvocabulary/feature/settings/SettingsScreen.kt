package com.example.localvocabulary.feature.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
    onChooseDictionaryPack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("새 단어의 기본 언어 태그")
                OutlinedTextField(
                    value = state.defaultLanguageTag,
                    onValueChange = { onAction(SettingsAction.DefaultLanguageChanged(it)) },
                    label = { Text("BCP 47 언어 태그") },
                    supportingText = { Text("예: en, ko, ja, zh-Hant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onAction(SettingsAction.Save) }) { Text("저장") }
                state.message?.let { Text(it) }
                Text("사전 pack")
                Button(
                    onClick = onChooseDictionaryPack,
                    enabled = !state.isInstallingPack,
                ) {
                    Text(if (state.isInstallingPack) "설치 중…" else "로컬 pack 설치")
                }
                if (state.installedPacks.isEmpty()) Text("설치된 사전 pack이 없습니다.")
                state.installedPacks.forEach { pack ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${pack.packId} · ${pack.datasetVersion}")
                        Text("${pack.providerId} · ${pack.sizeBytes / (1024 * 1024)} MiB")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (pack.canRollback) {
                                TextButton(
                                    onClick = {
                                        onAction(SettingsAction.RollbackDictionaryPack(pack.packId))
                                    },
                                ) { Text("이전 버전") }
                            }
                            TextButton(
                                onClick = {
                                    onAction(SettingsAction.DeleteDictionaryPack(pack.packId))
                                },
                            ) { Text("삭제") }
                        }
                    }
                }
                Text("사전 출처")
                state.dictionarySources.forEach { source ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(source.providerName)
                        source.attributionNotice?.let { Text(it) }
                        source.sourceUrl?.let { Text("Source: $it") }
                        source.licenseName?.let { Text("License: $it") }
                        source.licenseUrl?.let { Text(it) }
                        source.releaseId?.let { Text("Expected release: $it") }
                        source.releasePageUrl?.let { Text("Release page: $it") }
                        source.entryCount?.let { Text("Entries: $it") }
                        source.artifactName?.let { Text("Expected artifact: $it") }
                        source.format?.let { Text("Format: $it") }
                    }
                }
            }
        }
    }
}
