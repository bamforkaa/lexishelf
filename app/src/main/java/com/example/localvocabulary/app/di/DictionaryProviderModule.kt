package com.example.localvocabulary.app.di

import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictAssetSource
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictDataSource
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictProvider
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryDataSource
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryIndexSource
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryLookup
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryProvider
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictDataSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictIndexSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictLookup
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictProvider
import com.example.localvocabulary.dictionary.provider.panlex.PanLexDataSource
import com.example.localvocabulary.dictionary.provider.panlex.PanLexIndexSource
import com.example.localvocabulary.dictionary.provider.panlex.PanLexLookup
import com.example.localvocabulary.dictionary.provider.panlex.PanLexProvider
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
        internal fun provideKoreanBasicDictionaryLookup(
            indexSource: KoreanBasicDictionaryIndexSource,
        ): KoreanBasicDictionaryLookup = KoreanBasicDictionaryDataSource(indexSource)

        @Provides
        @IntoSet
        fun provideKoreanBasicDictionaryProvider(
            provider: KoreanBasicDictionaryProvider,
        ): DictionaryProvider = provider

        @Provides
        @Singleton
        internal fun provideJmDictLookup(indexSource: JmDictIndexSource): JmDictLookup =
            JmDictDataSource(indexSource)

        @Provides
        @IntoSet
        fun provideJmDictProvider(provider: JmDictProvider): DictionaryProvider = provider

        @Provides
        @Singleton
        internal fun providePanLexLookup(
            indexSource: PanLexIndexSource,
        ): PanLexLookup = PanLexDataSource(indexSource)

        @Provides
        @IntoSet
        fun providePanLexProvider(provider: PanLexProvider): DictionaryProvider = provider

        @Provides
        @Singleton
        fun provideDictionaryProviderRegistry(
            providers: Set<@JvmSuppressWildcards DictionaryProvider>,
        ): DictionaryProviderRegistry = DefaultDictionaryProviderRegistry(providers)
    }
}
