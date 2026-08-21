package com.example.localvocabulary.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.backup.domain.BackupConflictPolicy
import com.example.localvocabulary.backup.domain.BackupDecodeResult
import com.example.localvocabulary.backup.domain.BackupFileStore
import com.example.localvocabulary.backup.domain.BackupImportPreview
import com.example.localvocabulary.backup.domain.BackupReadError
import com.example.localvocabulary.backup.domain.BackupSerializer
import com.example.localvocabulary.backup.domain.ValidatedBackup
import com.example.localvocabulary.backup.domain.VocabularyBackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val isBusy: Boolean = false,
    val conflictPolicy: BackupConflictPolicy = BackupConflictPolicy.MERGE_BY_STABLE_ID,
    val preview: BackupImportPreview? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

sealed interface BackupAction {
    data class ExportDestinationSelected(val uri: String?) : BackupAction
    data class ImportFileSelected(val uri: String?) : BackupAction
    data class ConflictPolicySelected(val policy: BackupConflictPolicy) : BackupAction
    data object ConfirmImport : BackupAction
    data object CancelImport : BackupAction
    data object DismissMessage : BackupAction
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: VocabularyBackupRepository,
    private val serializer: BackupSerializer,
    private val fileStore: BackupFileStore,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = mutableUiState.asStateFlow()

    private var pendingBackup: ValidatedBackup? = null

    fun onAction(action: BackupAction) {
        when (action) {
            is BackupAction.ExportDestinationSelected -> action.uri?.let(::exportTo)
            is BackupAction.ImportFileSelected -> action.uri?.let(::readImport)
            is BackupAction.ConflictPolicySelected -> selectPolicy(action.policy)
            BackupAction.ConfirmImport -> confirmImport()
            BackupAction.CancelImport -> {
                pendingBackup = null
                mutableUiState.update { it.copy(preview = null, errorMessage = null) }
            }
            BackupAction.DismissMessage -> mutableUiState.update {
                it.copy(statusMessage = null, errorMessage = null)
            }
        }
    }

    private fun exportTo(uri: String) {
        if (mutableUiState.value.isBusy) return
        viewModelScope.launch {
            startWork()
            try {
                val json = serializer.encode(backupRepository.createBackup())
                fileStore.write(uri, json)
                mutableUiState.update {
                    it.copy(isBusy = false, statusMessage = "백업 파일을 저장했습니다.")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                failWork("백업 파일을 저장하지 못했습니다. 선택한 위치를 확인하세요.")
            }
        }
    }

    private fun readImport(uri: String) {
        if (mutableUiState.value.isBusy) return
        viewModelScope.launch {
            startWork()
            try {
                when (val decoded = serializer.decode(fileStore.read(uri))) {
                    is BackupDecodeResult.Success -> {
                        pendingBackup = decoded.backup
                        updatePreview(decoded.backup, mutableUiState.value.conflictPolicy)
                    }
                    is BackupDecodeResult.Failure -> {
                        pendingBackup = null
                        failWork(decoded.error.toMessage())
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                pendingBackup = null
                failWork("백업 파일을 읽지 못했습니다. 파일 형식과 접근 권한을 확인하세요.")
            }
        }
    }

    private fun selectPolicy(policy: BackupConflictPolicy) {
        if (mutableUiState.value.isBusy || policy == mutableUiState.value.conflictPolicy) return
        mutableUiState.update { it.copy(conflictPolicy = policy) }
        val backup = pendingBackup ?: return
        viewModelScope.launch {
            startWork(clearPreview = false)
            try {
                updatePreview(backup, policy)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                failWork("가져오기 미리보기를 계산하지 못했습니다.")
            }
        }
    }

    private fun confirmImport() {
        if (mutableUiState.value.isBusy) return
        val backup = pendingBackup ?: return
        val policy = mutableUiState.value.conflictPolicy
        viewModelScope.launch {
            startWork(clearPreview = false)
            try {
                val result = backupRepository.importBackup(backup, policy)
                pendingBackup = null
                mutableUiState.update {
                    it.copy(
                        isBusy = false,
                        preview = null,
                        statusMessage = "복원 완료: 추가 ${result.createdEntryCount}, " +
                            "갱신 ${result.updatedEntryCount}, 제거 ${result.removedEntryCount}",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                failWork("복원 transaction이 실패하여 기존 데이터를 그대로 유지했습니다.")
            }
        }
    }

    private suspend fun updatePreview(backup: ValidatedBackup, policy: BackupConflictPolicy) {
        val preview = backupRepository.previewImport(backup, policy)
        mutableUiState.update { it.copy(isBusy = false, preview = preview) }
    }

    private fun startWork(clearPreview: Boolean = true) {
        mutableUiState.update {
            it.copy(
                isBusy = true,
                preview = if (clearPreview) null else it.preview,
                statusMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun failWork(message: String) {
        mutableUiState.update { it.copy(isBusy = false, errorMessage = message) }
    }
}

private fun BackupReadError.toMessage(): String = when (this) {
    BackupReadError.MalformedJson -> "올바른 JSON 백업 파일이 아닙니다."
    BackupReadError.MissingRequiredField -> "백업에 필수 필드가 없습니다."
    is BackupReadError.UnsupportedSchemaVersion -> "지원하지 않는 백업 schema version입니다: $version"
    is BackupReadError.InvalidData -> "백업 데이터가 올바르지 않습니다: $path ($reason)"
}
