package com.example.localvocabulary.app.di

import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictAssetSource
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictDataSource
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictProvider
import com.example.localvocabulary.dictionary.registry.DefaultDictionaryProviderRegistry
import com.example.localvocabulary.dictionary.registry.DictionaryProviderRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DictionaryProviderModule {
    @Multibinds
    abstract fun dictionaryProviders(): Set<DictionaryProvider>

    companion object {
        @Provides
        @Singleton
        internal fun provideCcCedictDataSource(
            assetSource: CcCedictAssetSource,
        ): CcCedictDataSource = CcCedictDataSource(assetSource)

        @Provides
        @IntoSet
        fun provideCcCedictProvider(provider: CcCedictProvider): DictionaryProvider = provider

        @Provides
        @Singleton
        fun provideDictionaryProviderRegistry(
            providers: Set<@JvmSuppressWildcards DictionaryProvider>,
        ): DictionaryProviderRegistry = DefaultDictionaryProviderRegistry(providers)
    }
}
