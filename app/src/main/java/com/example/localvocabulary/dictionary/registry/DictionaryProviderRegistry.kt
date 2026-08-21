package com.example.localvocabulary.dictionary.registry

import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor

interface DictionaryProviderRegistry {
    fun descriptors(): List<DictionaryProviderDescriptor>
    fun find(providerId: String): DictionaryProvider?
}
