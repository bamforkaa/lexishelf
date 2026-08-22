package com.example.localvocabulary.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.dictionary.registry.DictionaryProviderRegistry
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

sealed interface SettingsAction {
    data class DefaultLanguageChanged(val value: String) : SettingsAction
    data object Save : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    providerRegistry: DictionaryProviderRegistry,
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
            val settings = settingsRepository.settings.first()
            mutableUiState.value = SettingsUiState(
                isLoading = false,
                defaultLanguageTag = settings.defaultLanguageTag,
                dictionarySources = dictionarySources,
            )
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.DefaultLanguageChanged -> mutableUiState.update {
                it.copy(defaultLanguageTag = action.value, message = null)
            }
            SettingsAction.Save -> save()
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
}
