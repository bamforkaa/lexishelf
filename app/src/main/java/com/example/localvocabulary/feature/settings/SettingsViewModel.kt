package com.example.localvocabulary.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogPack
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogRepository
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogSection
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogState
import com.example.localvocabulary.dictionary.catalog.DictionaryPackDownloadState
import com.example.localvocabulary.dictionary.registry.DictionaryProviderRegistry
import com.example.localvocabulary.dictionary.pack.DictionaryPackInstallResult
import com.example.localvocabulary.dictionary.pack.DictionaryPackRepository
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val defaultLanguageTag: String = "en",
    val userLanguageTags: Set<String> = emptySet(),
    val message: String? = null,
    val dictionarySources: List<DictionarySourceUiState> = emptyList(),
    val installedPacks: List<DictionaryPackUiState> = emptyList(),
    val catalogStatus: DictionaryCatalogStatusUiState = DictionaryCatalogStatusUiState.NotLoaded,
    val catalogPacks: List<DictionaryCatalogPackUiState> = emptyList(),
    val isInstallingPack: Boolean = false,
)

sealed interface DictionaryCatalogStatusUiState {
    data object NotLoaded : DictionaryCatalogStatusUiState
    data object Loading : DictionaryCatalogStatusUiState
    data class Available(
        val catalogVersion: String,
        val isCached: Boolean,
        val warning: String?,
    ) : DictionaryCatalogStatusUiState
    data class Unavailable(val reason: String) : DictionaryCatalogStatusUiState
}

enum class DictionaryCatalogPackInstallStatus {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE,
}

data class DictionaryCatalogPackUiState(
    val packId: String,
    val providerId: String,
    val displayName: String,
    val section: DictionaryCatalogSection,
    val description: String,
    val datasetVersion: String,
    val downloadSizeBytes: Long,
    val installedSizeBytes: Long,
    val licenseName: String,
    val installStatus: DictionaryCatalogPackInstallStatus,
    val downloadState: DictionaryPackDownloadState?,
    val isRecommended: Boolean,
)

data class DictionarySourceUiState(
    val providerId: String,
    val providerName: String,
    val sourceUrl: String?,
    val licenseName: String?,
    val licenseUrl: String?,
    val attributionNotice: String?,
    val artifactName: String?,
    val releaseId: String?,
    val releasePageUrl: String?,
    val entryCount: Long?,
    val format: String?,
    val installedDatasetVersion: String? = null,
)

data class DictionaryPackUiState(
    val packId: String,
    val providerId: String,
    val providerName: String,
    val datasetVersion: String,
    val sizeBytes: Long,
    val canRollback: Boolean,
    val payloadSha256: String = "",
)

sealed interface SettingsAction {
    data class DefaultLanguageChanged(val value: String) : SettingsAction
    data class UserLanguageAdded(val languageTag: String) : SettingsAction
    data object Save : SettingsAction
    data class InstallDictionaryPack(val uri: String) : SettingsAction
    data class DeleteDictionaryPack(val packId: String) : SettingsAction
    data class RollbackDictionaryPack(val packId: String) : SettingsAction
    data object RefreshDictionaryCatalog : SettingsAction
    data class DownloadDictionaryPack(val packId: String) : SettingsAction
    data class CancelDictionaryPackDownload(val packId: String) : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    providerRegistry: DictionaryProviderRegistry,
    private val dictionaryPackRepository: DictionaryPackRepository,
    private val dictionaryCatalogRepository: DictionaryCatalogRepository,
) : ViewModel() {
    private val baseDictionarySources = providerRegistry.descriptors().map { descriptor ->
        DictionarySourceUiState(
            providerId = descriptor.id.value,
            providerName = descriptor.displayName,
            sourceUrl = descriptor.attribution.sourceUrl,
            licenseName = descriptor.attribution.licenseName,
            licenseUrl = descriptor.attribution.licenseUrl,
            attributionNotice = descriptor.attribution.attributionNotice,
            artifactName = descriptor.dataset?.artifactName,
            releaseId = descriptor.dataset?.releaseId,
            releasePageUrl = descriptor.dataset?.releasePageUrl,
            entryCount = descriptor.dataset?.entryCount,
            format = descriptor.dataset?.format,
        )
    }
    private val mutableUiState = MutableStateFlow(
        SettingsUiState(dictionarySources = baseDictionarySources),
    )
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            dictionaryPackRepository.installedPacks.collect { packs ->
                mutableUiState.update { state ->
                    state.copy(
                        installedPacks = packs.map { pack ->
                            DictionaryPackUiState(
                                packId = pack.manifest.packId,
                                providerId = pack.manifest.providerId,
                                providerName = baseDictionarySources.firstOrNull {
                                    it.providerId == pack.manifest.providerId
                                }?.providerName ?: pack.manifest.providerId,
                                datasetVersion = pack.manifest.datasetVersion,
                                sizeBytes = pack.manifest.payload.sizeBytes,
                                canRollback = pack.canRollback,
                                payloadSha256 = pack.manifest.payload.sha256,
                            )
                        },
                        dictionarySources = baseDictionarySources.map { source ->
                            source.copy(
                                installedDatasetVersion = packs
                                    .filter { it.manifest.providerId == source.providerId }
                                    .map { it.manifest.datasetVersion }
                                    .distinct()
                                    .sorted()
                                    .joinToString()
                                    .takeIf(String::isNotEmpty),
                            )
                        },
                    )
                    .withCatalogState(
                        dictionaryCatalogRepository.catalogState.value,
                        dictionaryCatalogRepository.downloadStates.value,
                    )
                }
            }
        }
        viewModelScope.launch {
            dictionaryCatalogRepository.catalogState.collect { catalogState ->
                mutableUiState.update {
                    it.withCatalogState(catalogState, dictionaryCatalogRepository.downloadStates.value)
                }
            }
        }
        viewModelScope.launch {
            dictionaryCatalogRepository.downloadStates.collect { downloadStates ->
                mutableUiState.update {
                    it.withCatalogState(dictionaryCatalogRepository.catalogState.value, downloadStates)
                }
            }
        }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    defaultLanguageTag = settings.defaultLanguageTag,
                    userLanguageTags = settings.userLanguageTags,
                    dictionarySources = mutableUiState.value.dictionarySources,
                )
            }
        }
        refreshDictionaryCatalog()
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.DefaultLanguageChanged -> mutableUiState.update {
                it.copy(defaultLanguageTag = action.value, message = null)
            }
            is SettingsAction.UserLanguageAdded -> addUserLanguage(action.languageTag)
            SettingsAction.Save -> save()
            is SettingsAction.InstallDictionaryPack -> installPack(action.uri)
            is SettingsAction.DeleteDictionaryPack -> deletePack(action.packId)
            is SettingsAction.RollbackDictionaryPack -> rollbackPack(action.packId)
            SettingsAction.RefreshDictionaryCatalog -> refreshDictionaryCatalog()
            is SettingsAction.DownloadDictionaryPack -> downloadPack(action.packId)
            is SettingsAction.CancelDictionaryPackDownload -> cancelDownload(action.packId)
        }
    }

    private fun addUserLanguage(languageTag: String) {
        viewModelScope.launch {
            try {
                settingsRepository.addUserLanguageTag(languageTag)
                val storedTags = settingsRepository.settings.first().userLanguageTags
                mutableUiState.update { it.copy(userLanguageTags = storedTags) }
            } catch (exception: IllegalArgumentException) {
                mutableUiState.update { it.copy(message = exception.message) }
            }
        }
    }

    private fun save() {
        val normalized = VocabularyEntryValidator.normalizeLanguageTag(
            mutableUiState.value.defaultLanguageTag,
        )
        if (normalized == null) {
            mutableUiState.update { it.copy(message = "유효한 BCP 47 언어 태그를 입력하세요.") }
            return
        }
        viewModelScope.launch {
            settingsRepository.setDefaultLanguageTag(normalized)
            val storedTags = settingsRepository.settings.first().userLanguageTags
            mutableUiState.update {
                it.copy(
                    defaultLanguageTag = normalized,
                    userLanguageTags = storedTags,
                    message = "저장했습니다.",
                )
            }
        }
    }

    private fun installPack(uri: String) {
        if (mutableUiState.value.isInstallingPack) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isInstallingPack = true, message = null) }
            val result = dictionaryPackRepository.installFromUri(uri)
            mutableUiState.update {
                it.copy(
                    isInstallingPack = false,
                    message = when (result) {
                        is DictionaryPackInstallResult.Installed ->
                            "${result.pack.manifest.packId} pack을 설치했습니다."
                        is DictionaryPackInstallResult.Rejected -> result.reason
                    },
                )
            }
        }
    }

    private fun deletePack(packId: String) {
        viewModelScope.launch {
            val deleted = dictionaryPackRepository.delete(packId)
            mutableUiState.update {
                it.copy(message = if (deleted) "$packId pack을 삭제했습니다." else "pack을 찾지 못했습니다.")
            }
        }
    }

    private fun rollbackPack(packId: String) {
        viewModelScope.launch {
            val rolledBack = dictionaryPackRepository.rollback(packId)
            mutableUiState.update {
                it.copy(message = if (rolledBack) "$packId pack을 되돌렸습니다." else "되돌릴 버전이 없습니다.")
            }
        }
    }

    private fun refreshDictionaryCatalog() {
        viewModelScope.launch { dictionaryCatalogRepository.refreshCatalog() }
    }

    private fun downloadPack(packId: String) {
        if (downloadJobs.values.any { it.isActive }) return
        downloadJobs[packId] = viewModelScope.launch {
            try {
                dictionaryCatalogRepository.downloadAndInstall(packId)
            } finally {
                downloadJobs.remove(packId)
            }
        }
    }

    private fun cancelDownload(packId: String) {
        downloadJobs.remove(packId)?.cancel()
    }

    private fun SettingsUiState.withCatalogState(
        catalogState: DictionaryCatalogState,
        downloadStates: Map<String, DictionaryPackDownloadState>,
    ): SettingsUiState {
        val catalog = (catalogState as? DictionaryCatalogState.Available)?.catalog
        val installedById = installedPacks.associateBy(DictionaryPackUiState::packId)
        return copy(
            catalogStatus = when (catalogState) {
                DictionaryCatalogState.NotLoaded -> DictionaryCatalogStatusUiState.NotLoaded
                DictionaryCatalogState.Loading -> DictionaryCatalogStatusUiState.Loading
                is DictionaryCatalogState.Available -> DictionaryCatalogStatusUiState.Available(
                    catalogVersion = catalogState.catalog.catalogVersion,
                    isCached = catalogState.isCached,
                    warning = catalogState.warning,
                )
                is DictionaryCatalogState.Unavailable ->
                    DictionaryCatalogStatusUiState.Unavailable(catalogState.reason)
            },
            catalogPacks = catalog?.packs.orEmpty().map { pack ->
                pack.toUiState(installedById[pack.packId], downloadStates[pack.packId])
            },
        )
    }

    private fun DictionaryCatalogPack.toUiState(
        installed: DictionaryPackUiState?,
        downloadState: DictionaryPackDownloadState?,
    ) = DictionaryCatalogPackUiState(
        packId = packId,
        providerId = providerId,
        displayName = displayName,
        section = section,
        description = description,
        datasetVersion = datasetVersion,
        downloadSizeBytes = downloadSizeBytes,
        installedSizeBytes = installedSizeBytes,
        licenseName = licenseName,
        installStatus = when {
            installed == null -> DictionaryCatalogPackInstallStatus.NOT_INSTALLED
            installed.datasetVersion == datasetVersion &&
                installed.sizeBytes == installedSizeBytes &&
                installed.payloadSha256 == payloadSha256 ->
                DictionaryCatalogPackInstallStatus.INSTALLED
            else -> DictionaryCatalogPackInstallStatus.UPDATE_AVAILABLE
        },
        downloadState = downloadState,
        isRecommended = recommendedFor.any { it in mutableUiState.value.userLanguageTags } ||
            mutableUiState.value.defaultLanguageTag in recommendedFor,
    )
}
