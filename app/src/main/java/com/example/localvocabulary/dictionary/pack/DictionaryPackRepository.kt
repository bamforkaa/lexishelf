package com.example.localvocabulary.dictionary.pack

import kotlinx.coroutines.flow.StateFlow

interface DictionaryPackRepository {
    val installedPacks: StateFlow<List<InstalledDictionaryPack>>

    suspend fun installFromUri(uri: String): DictionaryPackInstallResult
    suspend fun delete(packId: String): Boolean
    suspend fun rollback(packId: String): Boolean
    fun refresh()
}

