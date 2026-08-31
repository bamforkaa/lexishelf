package com.example.localvocabulary.dictionary.pack

import kotlinx.coroutines.flow.StateFlow

interface DictionaryPackRepository {
    val installedPacks: StateFlow<List<InstalledDictionaryPack>>

    suspend fun installFromUri(
        uri: String,
        expectation: DictionaryPackInstallExpectation? = null,
    ): DictionaryPackInstallResult
    suspend fun delete(packId: String): Boolean
    suspend fun rollback(packId: String): Boolean
    fun refresh()
}
