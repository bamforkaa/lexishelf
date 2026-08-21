package com.example.localvocabulary.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

sealed interface SettingsAction {
    data class DefaultLanguageChanged(val value: String) : SettingsAction
    data object Save : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            mutableUiState.value = SettingsUiState(
                isLoading = false,
                defaultLanguageTag = settings.defaultLanguageTag,
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
