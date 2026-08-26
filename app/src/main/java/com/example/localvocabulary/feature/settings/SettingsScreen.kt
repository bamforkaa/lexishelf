package com.example.localvocabulary.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.core.ui.component.ScreenStatePane
import com.example.localvocabulary.core.ui.component.SectionHeader
import com.example.localvocabulary.feature.language.LanguagePickerField

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            ScreenStatePane(
                title = "설정을 불러오는 중입니다",
                isLoading = true,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SettingsSection {
                        SectionHeader(
                            title = "기본 설정",
                            supportingText = "새 단어를 만들 때 사용할 원문 언어입니다.",
                        )
                        LanguagePickerField(
                            value = state.defaultLanguageTag,
                            onValueChange = {
                                onAction(SettingsAction.DefaultLanguageChanged(it))
                            },
                            label = "기본 언어",
                            userLanguageTags = state.userLanguageTags,
                            onUserLanguageAdded = {
                                onAction(SettingsAction.UserLanguageAdded(it))
                            },
                            supportingText = "목록에서 선택하거나 BCP 47 태그를 직접 입력할 수 있습니다.",
                            testTag = "settings_language",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = { onAction(SettingsAction.Save) }) { Text("저장") }
                        state.message?.let { MetadataLabel(it, maxLines = 3) }
                    }
                }

                item {
                    SettingsSection {
                        SectionHeader(
                            title = "사전 데이터",
                            supportingText = "설치한 로컬 사전 pack은 오프라인 검색에 사용됩니다.",
                        )
                        Button(
                            onClick = onChooseDictionaryPack,
                            enabled = !state.isInstallingPack,
                        ) {
                            Text(if (state.isInstallingPack) "설치 중…" else "로컬 pack 설치")
                        }
                        if (state.installedPacks.isEmpty()) {
                            MetadataLabel("설치된 사전 pack이 없습니다.", maxLines = 2)
                        }
                    }
                }

                itemsIndexed(state.installedPacks, key = { _, pack -> pack.packId }) { index, pack ->
                    DictionaryPackRow(pack = pack, onAction = onAction)
                    if (index < state.installedPacks.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                item {
                    SectionHeader(
                        title = "사전 출처",
                        supportingText = "데이터셋의 출처와 라이선스 정보입니다.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                itemsIndexed(
                    state.dictionarySources,
                    key = { _, source -> source.providerId },
                ) { index, source ->
                    DictionarySourceRow(source)
                    if (index < state.dictionarySources.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun DictionaryPackRow(
    pack: DictionaryPackUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(pack.providerName, style = MaterialTheme.typography.titleMedium)
        MetadataLabel("설치됨 · ${pack.sizeBytes / (1024 * 1024)} MiB")
        MetadataLabel(pack.datasetVersion)
        MetadataLabel("pack ID · ${pack.packId}", maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pack.canRollback) {
                TextButton(
                    onClick = { onAction(SettingsAction.RollbackDictionaryPack(pack.packId)) },
                ) { Text("이전 버전") }
            }
            TextButton(
                onClick = { onAction(SettingsAction.DeleteDictionaryPack(pack.packId)) },
            ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun DictionarySourceRow(source: DictionarySourceUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(source.providerName, style = MaterialTheme.typography.titleMedium)
        source.attributionNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        source.licenseName?.let { MetadataLabel("라이선스 · $it", maxLines = 3) }
        source.releaseId?.let { MetadataLabel("데이터 버전 · $it", maxLines = 2) }
        source.entryCount?.let { MetadataLabel("수록 항목 · $it") }
        source.format?.let { MetadataLabel("형식 · $it") }
        source.artifactName?.let { MetadataLabel("파일 · $it", maxLines = 2) }
        source.sourceUrl?.let { MetadataLabel("출처 · $it", maxLines = 3) }
        source.licenseUrl?.let { MetadataLabel(it, maxLines = 3) }
        source.releasePageUrl?.let { MetadataLabel("릴리스 · $it", maxLines = 3) }
    }
}
