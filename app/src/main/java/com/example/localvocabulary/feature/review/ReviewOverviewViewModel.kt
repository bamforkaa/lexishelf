package com.example.localvocabulary.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.review.domain.ReviewQueue
import com.example.localvocabulary.review.domain.ReviewRepository
import com.example.localvocabulary.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
class ReviewOverviewViewModel @Inject constructor(
    private val repository: ReviewRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val mutableQueue = MutableStateFlow<ReviewQueue?>(null)
    val queue = mutableQueue.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                combine(repository.observeStates(), settingsRepository.settings) { _, _ -> Unit }
                    .collect { refresh() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableError.value = error.message ?: "복습 수를 불러오지 못했습니다." }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                mutableQueue.value = repository.queue()
                mutableError.value = null
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableQueue.value = null
                mutableError.value = error.message ?: "복습 수를 불러오지 못했습니다."
            }
        }
    }
}
