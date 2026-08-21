package com.example.localvocabulary.dictionary.domain

interface DictionaryProvider {
    val descriptor: DictionaryProviderDescriptor

    suspend fun search(query: DictionaryQuery): DictionarySearchResult

    suspend fun exactLookup(query: DictionaryQuery): DictionarySearchResult? = null

    suspend fun checkAvailability(): ProviderAvailability
}
