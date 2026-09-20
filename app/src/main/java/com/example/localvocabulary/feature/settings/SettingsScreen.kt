package com.example.localvocabulary.feature.settings

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogSection
import com.example.localvocabulary.dictionary.catalog.DictionaryPackDownloadState
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
    var showSavedSources by rememberSaveable { mutableStateOf(false) }
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
            val publicPackIds = state.catalogPacks.mapTo(mutableSetOf()) { it.packId }
            val localOnlyPacks = state.installedPacks.filterNot { it.packId in publicPackIds }
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
                        SectionHeader("하루 복습량", supportingText = "등록한 뜻 중 처음 학습할 수와 전체 정규 복습 수입니다. 재시도는 포함하지 않습니다.")
                        OutlinedTextField(value = state.reviewNewLimit,
                            onValueChange = { onAction(SettingsAction.ReviewNewLimitChanged(it)) },
                            label = { Text("신규 학습 (0~100)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = state.reviewTotalLimit,
                            onValueChange = { onAction(SettingsAction.ReviewTotalLimitChanged(it)) },
                            label = { Text("전체 복습 (1~500, 신규 이상)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        Button(onClick = { onAction(SettingsAction.SaveReviewLimits) }) { Text("복습량 저장") }
                        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                item {
                    SettingsSection {
                        SectionHeader(
                            title = "사전 데이터",
                            supportingText = "필요한 데이터만 내려받으세요. 설치된 pack은 오프라인에서 동작합니다.",
                        )
                        when (val status = state.catalogStatus) {
                            DictionaryCatalogStatusUiState.NotLoaded,
                            DictionaryCatalogStatusUiState.Loading,
                            -> MetadataLabel("다운로드 목록을 불러오는 중입니다.")
                            is DictionaryCatalogStatusUiState.Available -> {
                                MetadataLabel(
                                    if (status.isCached) {
                                        "저장된 catalog ${status.catalogVersion}"
                                    } else {
                                        "catalog ${status.catalogVersion}"
                                    },
                                )
                                status.warning?.let { MetadataLabel(it, maxLines = 3) }
                            }
                            is DictionaryCatalogStatusUiState.Unavailable -> {
                                MetadataLabel(status.reason, maxLines = 3)
                                MetadataLabel("단어장과 이미 설치한 사전은 계속 사용할 수 있습니다.", maxLines = 2)
                            }
                        }
                        TextButton(
                            onClick = { onAction(SettingsAction.RefreshDictionaryCatalog) },
                        ) { Text("목록 새로고침") }
                        if (state.catalogPacks.isEmpty() &&
                            state.catalogStatus is DictionaryCatalogStatusUiState.Available
                        ) {
                            MetadataLabel("공개 사전 pack이 없습니다.")
                        }
                    }
                }

                DictionaryCatalogSection.entries.forEach { section ->
                    val packs = state.catalogPacks.filter { it.section == section }
                    if (packs.isNotEmpty()) {
                        item(key = "catalog-section-$section") {
                            SectionHeader(
                                title = section.title(),
                                supportingText = section.supportingText(),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        itemsIndexed(packs, key = { _, pack -> pack.packId }) { index, pack ->
                            DictionaryCatalogPackRow(pack = pack, onAction = onAction)
                            if (index < packs.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }

                item {
                    SettingsSection {
                        SectionHeader(
                            title = "로컬/개발자 pack",
                            supportingText = "직접 만든 .dictpack을 가져오는 개발용 경로입니다.",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Button(
                            onClick = onChooseDictionaryPack,
                            enabled = !state.isInstallingPack,
                        ) {
                            Text(if (state.isInstallingPack) "설치 중…" else "로컬 pack 가져오기")
                        }
                        if (localOnlyPacks.isEmpty()) {
                            MetadataLabel("공개 catalog 밖에서 설치한 pack이 없습니다.", maxLines = 2)
                        }
                    }
                }

                itemsIndexed(localOnlyPacks, key = { _, pack -> "local-${pack.packId}" }) { index, pack ->
                    DictionaryPackRow(pack = pack, onAction = onAction)
                    if (index < localOnlyPacks.lastIndex) {
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
                if (state.savedDictionarySources.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showSavedSources = !showSavedSources },
                            modifier = Modifier.testTag("saved_dictionary_sources")) {
                            Text(if (showSavedSources) "저장된 항목의 출처 접기" else "저장된 항목의 출처 확인")
                        }
                    }
                    if (showSavedSources) {
                        itemsIndexed(state.savedDictionarySources, key = { index, _ -> "saved-source-$index" }) { _, source ->
                            DictionarySourceRow(source)
                        }
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
private fun DictionaryCatalogPackRow(
    pack: DictionaryCatalogPackUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(pack.displayName, style = MaterialTheme.typography.titleMedium)
        if (pack.isRecommended) MetadataLabel("권장 · 현재 사용 언어에 적합")
        Text(pack.description, style = MaterialTheme.typography.bodyMedium)
        MetadataLabel(
            "다운로드 약 ${formatBytes(pack.downloadSizeBytes)} · " +
                "설치 후 약 ${formatBytes(pack.installedSizeBytes)}",
            maxLines = 2,
        )
        MetadataLabel("데이터 ${pack.datasetVersion} · ${pack.licenseName}", maxLines = 2)

        when (val download = pack.downloadState) {
            is DictionaryPackDownloadState.Downloading -> {
                val progress = if (download.totalBytes > 0) {
                    (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                MetadataLabel(
                    "다운로드 중 · ${formatBytes(download.downloadedBytes)} / " +
                        formatBytes(download.totalBytes),
                )
                TextButton(
                    onClick = { onAction(SettingsAction.CancelDictionaryPackDownload(pack.packId)) },
                ) { Text("취소") }
            }
            DictionaryPackDownloadState.Validating -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                MetadataLabel("무결성과 pack 구조를 확인하고 있습니다.")
                TextButton(
                    onClick = { onAction(SettingsAction.CancelDictionaryPackDownload(pack.packId)) },
                ) { Text("취소") }
            }
            DictionaryPackDownloadState.Installing -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                MetadataLabel("검증된 pack을 설치하고 있습니다.")
            }
            is DictionaryPackDownloadState.Failed -> {
                MetadataLabel("실패 · ${download.reason}", maxLines = 3)
                Button(
                    onClick = { onAction(SettingsAction.DownloadDictionaryPack(pack.packId)) },
                ) { Text("다시 시도") }
            }
            DictionaryPackDownloadState.Installed,
            null,
            -> CatalogPackActions(pack, onAction)
        }
    }
}

@Composable
private fun CatalogPackActions(
    pack: DictionaryCatalogPackUiState,
    onAction: (SettingsAction) -> Unit,
) {
    when (pack.installStatus) {
        DictionaryCatalogPackInstallStatus.NOT_INSTALLED -> Button(
            onClick = { onAction(SettingsAction.DownloadDictionaryPack(pack.packId)) },
        ) { Text("다운로드") }
        DictionaryCatalogPackInstallStatus.INSTALLED -> {
            MetadataLabel("설치됨")
            TextButton(
                onClick = { onAction(SettingsAction.DeleteDictionaryPack(pack.packId)) },
            ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
        }
        DictionaryCatalogPackInstallStatus.UPDATE_AVAILABLE -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onAction(SettingsAction.DownloadDictionaryPack(pack.packId)) },
            ) { Text("업데이트") }
            TextButton(
                onClick = { onAction(SettingsAction.DeleteDictionaryPack(pack.packId)) },
            ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun DictionarySourceRow(source: DictionarySourceUiState) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(source.providerName, style = MaterialTheme.typography.titleMedium)
        source.attributionNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        source.licenseName?.let { Text("라이선스 · $it", style = MaterialTheme.typography.bodySmall) }
        (source.installedDatasetVersion ?: source.releaseId)?.let {
            MetadataLabel(
                if (source.installedDatasetVersion != null) "설치 데이터 버전 · $it" else "데이터 버전 · $it",
                maxLines = 2,
            )
        }
        source.savedDatasetVersion?.let { MetadataLabel("저장된 출처 버전 · $it") }
        source.entryCount?.let { MetadataLabel("수록 항목 · $it") }
        source.format?.let { MetadataLabel("형식 · $it") }
        source.artifactName?.let { MetadataLabel("파일 · $it", maxLines = 2) }
        if (source.providerId == "jmdict") {
            MetadataLabel("개발/로컬 통합은 유지되지만 공개 v1 pack은 배포하지 않습니다.", maxLines = 3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            source.sourceUrl?.let { url ->
                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                    Text("원본 사이트")
                }
            }
            source.licenseUrl?.let { url ->
                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                    Text("라이선스")
                }
            }
            source.releasePageUrl?.let { url ->
                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                    Text("릴리스")
                }
            }
        }
    }
}

private fun DictionaryCatalogSection.title(): String = when (this) {
    DictionaryCatalogSection.KOREAN_MEANINGS -> "한국어 뜻"
    DictionaryCatalogSection.ENGLISH_DETAILS -> "상세 영어 뜻"
    DictionaryCatalogSection.SPECIALIZED -> "전문/보조"
}

private fun DictionaryCatalogSection.supportingText(): String = when (this) {
    DictionaryCatalogSection.KOREAN_MEANINGS -> "수록 범위 안에서 한국어 뜻을 찾기 위한 데이터입니다."
    DictionaryCatalogSection.ENGLISH_DETAILS -> "언어별로 품사·발음·활용형·예문이 포함될 수 있습니다."
    DictionaryCatalogSection.SPECIALIZED -> "특정 언어 상세 정보나 형태소 검색을 보완합니다."
}

private fun formatBytes(bytes: Long): String {
    val mebibytes = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mebibytes >= 10) {
        "${mebibytes.toInt()} MiB"
    } else {
        "%.1f MiB".format(mebibytes)
    }
}
