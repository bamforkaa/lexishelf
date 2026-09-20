package com.example.localvocabulary.feature.backup

import com.example.localvocabulary.backup.domain.BackupExampleV6

import com.example.localvocabulary.backup.data.KotlinxBackupSerializer
import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupConflictPolicy
import com.example.localvocabulary.backup.domain.BackupEntryV6
import com.example.localvocabulary.backup.domain.BackupFileStore
import com.example.localvocabulary.backup.domain.BackupImportPreview
import com.example.localvocabulary.backup.domain.BackupImportResult
import com.example.localvocabulary.backup.domain.BackupSenseV6
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.ValidatedBackup
import com.example.localvocabulary.backup.domain.VocabularyBackupRepository
import com.example.localvocabulary.backup.domain.VocabularyBackupV8
import com.example.localvocabulary.feature.wordlist.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class BackupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val serializer = KotlinxBackupSerializer()

    @Test
    fun `valid file is previewed and database is not changed before confirmation`() = runTest {
        val repository = FakeBackupRepository()
        val fileStore = FakeBackupFileStore(readContent = serializer.encode(sampleBackup()))
        val viewModel = BackupViewModel(repository, serializer, fileStore)

        viewModel.onAction(BackupAction.ImportFileSelected("content://backup/input.json"))

        assertNotNull(viewModel.uiState.value.preview)
        assertEquals(1, repository.previewCalls)
        assertEquals(0, repository.importCalls)

        viewModel.onAction(BackupAction.ConfirmImport)

        assertEquals(1, repository.importCalls)
        assertNull(viewModel.uiState.value.preview)
        assertNotNull(viewModel.uiState.value.statusMessage)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `malformed file stops before preview and import`() = runTest {
        val repository = FakeBackupRepository()
        val viewModel = BackupViewModel(
            repository,
            serializer,
            FakeBackupFileStore(readContent = "{not-json"),
        )

        viewModel.onAction(BackupAction.ImportFileSelected("content://backup/broken.json"))

        assertEquals(0, repository.previewCalls)
        assertEquals(0, repository.importCalls)
        assertNull(viewModel.uiState.value.preview)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `changing conflict policy recalculates preview without importing`() = runTest {
        val repository = FakeBackupRepository()
        val viewModel = BackupViewModel(
            repository,
            serializer,
            FakeBackupFileStore(readContent = serializer.encode(sampleBackup())),
        )
        viewModel.onAction(BackupAction.ImportFileSelected("content://backup/input.json"))

        viewModel.onAction(
            BackupAction.ConflictPolicySelected(BackupConflictPolicy.REPLACE_ALL),
        )

        assertEquals(2, repository.previewCalls)
        assertEquals(BackupConflictPolicy.REPLACE_ALL, repository.lastPreviewPolicy)
        assertEquals(0, repository.importCalls)
        assertEquals(3, viewModel.uiState.value.preview?.existingEntryRemovalCount)
    }

    @Test
    fun `export writes serialized user vocabulary to selected uri`() = runTest {
        val repository = FakeBackupRepository()
        val fileStore = FakeBackupFileStore()
        val viewModel = BackupViewModel(repository, serializer, fileStore)

        viewModel.onAction(BackupAction.ExportDestinationSelected("content://backup/output.json"))

        assertEquals("content://backup/output.json", fileStore.writtenUri)
        assertNotNull(fileStore.writtenContent)
        assertNotNull(viewModel.uiState.value.statusMessage)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    private fun sampleBackup() = VocabularyBackupV8(
        format = BACKUP_FORMAT_ID,
        schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
        exportedAtEpochMillis = 1_000,
        tags = emptyList(),
        entries = listOf(
            BackupEntryV6(
                stableId = "entry-one",
                headword = "word",
                languageTag = "en",
                senses = listOf(BackupSenseV6(
                    meaning = "meaning",
                    partOfSpeech = "noun",
                    examples = listOf("example").map { BackupExampleV6(stableId = java.util.UUID.randomUUID().toString(), text = it) },
                    stableId = java.util.UUID.randomUUID().toString(),
                )),
                notes = "note",
                tagStableIds = emptyList(),
                createdAtEpochMillis = 100,
                modifiedAtEpochMillis = 200,
            ),
        ),
    )
}

private class FakeBackupRepository : VocabularyBackupRepository {
    var previewCalls = 0
    var importCalls = 0
    var lastPreviewPolicy: BackupConflictPolicy? = null

    override suspend fun createBackup(): VocabularyBackupV8 = VocabularyBackupV8(
        format = BACKUP_FORMAT_ID,
        schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
        exportedAtEpochMillis = 1_000,
        tags = emptyList(),
        entries = emptyList(),
    )

    override suspend fun previewImport(
        backup: ValidatedBackup,
        policy: BackupConflictPolicy,
    ): BackupImportPreview {
        previewCalls++
        lastPreviewPolicy = policy
        return BackupImportPreview(
            entryCount = 1,
            senseCount = 1,
            exampleCount = 1,
            tagCount = 0,
            conflictCount = 1,
            newEntryCount = 0,
            updatedEntryCount = if (policy == BackupConflictPolicy.MERGE_BY_STABLE_ID) 1 else 0,
            skippedEntryCount = 0,
            existingEntryRemovalCount = if (policy == BackupConflictPolicy.REPLACE_ALL) 3 else 0,
        )
    }

    override suspend fun importBackup(
        backup: ValidatedBackup,
        policy: BackupConflictPolicy,
    ): BackupImportResult {
        importCalls++
        return BackupImportResult(
            createdEntryCount = 0,
            updatedEntryCount = 1,
            removedEntryCount = 0,
        )
    }
}

private class FakeBackupFileStore(
    private val readContent: String = "",
) : BackupFileStore {
    var writtenUri: String? = null
    var writtenContent: String? = null

    override suspend fun read(uri: String): String = readContent

    override suspend fun write(uri: String, content: String) {
        writtenUri = uri
        writtenContent = content
    }
}
