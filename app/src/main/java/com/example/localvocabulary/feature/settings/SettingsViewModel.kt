package com.example.localvocabulary.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.dictionary.registry.DictionaryProviderRegistry
import com.example.localvocabulary.dictionary.pack.DictionaryPackInstallResult
import com.example.localvocabulary.dictionary.pack.DictionaryPackRepository
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val defaultLanguageTag: String = "en",
    val message: String? = null,
    val dictionarySources: List<DictionarySourceUiState> = emptyList(),
    val installedPacks: List<DictionaryPackUiState> = emptyList(),
    val isInstallingPack: Boolean = false,
)

data class DictionarySourceUiState(
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
)

data class DictionaryPackUiState(
    val packId: String,
    val providerId: String,
    val datasetVersion: String,
    val sizeBytes: Long,
    val canRollback: Boolean,
)

sealed interface SettingsAction {
    data class DefaultLanguageChanged(val value: String) : SettingsAction
    data object Save : SettingsAction
    data class InstallDictionaryPack(val uri: String) : SettingsAction
    data class DeleteDictionaryPack(val packId: String) : SettingsAction
    data class RollbackDictionaryPack(val packId: String) : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    providerRegistry: DictionaryProviderRegistry,
    private val dictionaryPackRepository: DictionaryPackRepository,
) : ViewModel() {
    private val dictionarySources = providerRegistry.descriptors().map { descriptor ->
        DictionarySourceUiState(
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
        SettingsUiState(dictionarySources = dictionarySources),
    )
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            dictionaryPackRepository.installedPacks.collect { packs ->
                mutableUiState.update { state ->
                    state.copy(
                        installedPacks = packs.map { pack ->
                            DictionaryPackUiState(
                                packId = pack.manifest.packId,
                                providerId = pack.manifest.providerId,
                                datasetVersion = pack.manifest.datasetVersion,
                                sizeBytes = pack.manifest.payload.sizeBytes,
                                canRollback = pack.canRollback,
                            )
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    defaultLanguageTag = settings.defaultLanguageTag,
                    dictionarySources = dictionarySources,
                )
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.DefaultLanguageChanged -> mutableUiState.update {
                it.copy(defaultLanguageTag = action.value, message = null)
            }
            SettingsAction.Save -> save()
            is SettingsAction.InstallDictionaryPack -> installPack(action.uri)
            is SettingsAction.DeleteDictionaryPack -> deletePack(action.packId)
            is SettingsAction.RollbackDictionaryPack -> rollbackPack(action.packId)
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
            mutableUiState.update {
                it.copy(defaultLanguageTag = normalized, message = "저장했습니다.")
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
}
