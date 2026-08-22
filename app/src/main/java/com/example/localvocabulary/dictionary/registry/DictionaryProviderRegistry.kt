package com.example.localvocabulary.dictionary.registry

import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryQuery

interface DictionaryProviderRegistry {
    fun descriptors(): List<DictionaryProviderDescriptor>
    fun descriptorsSupporting(query: DictionaryQuery): List<DictionaryProviderDescriptor>
    fun find(providerId: DictionaryProviderId): DictionaryProvider?
}

class DefaultDictionaryProviderRegistry(
    providers: Collection<DictionaryProvider>,
) : DictionaryProviderRegistry {
    private val providersById = providers.associateBy { it.descriptor.id }

    init {
        require(providersById.size == providers.size) { "Dictionary provider IDs must be unique" }
    }

    override fun descriptors(): List<DictionaryProviderDescriptor> = providersById.values
        .map(DictionaryProvider::descriptor)
        .sortedBy { it.id.value }

    override fun descriptorsSupporting(
        query: DictionaryQuery,
    ): List<DictionaryProviderDescriptor> = descriptors().filter { it.supports(query) }

    override fun find(providerId: DictionaryProviderId): DictionaryProvider? =
        providersById[providerId]
}
