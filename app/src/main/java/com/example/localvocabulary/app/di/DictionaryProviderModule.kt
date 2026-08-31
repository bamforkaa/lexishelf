package com.example.localvocabulary.app.di

import android.content.Context
import com.example.localvocabulary.BuildConfig
import com.example.localvocabulary.dictionary.catalog.DefaultDictionaryCatalogRepository
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogEndpoint
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogRepository
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogStorage
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogTransport
import com.example.localvocabulary.dictionary.catalog.DownloadedDictionaryPackInstaller
import com.example.localvocabulary.dictionary.catalog.FileDictionaryCatalogStorage
import com.example.localvocabulary.dictionary.catalog.OkHttpDictionaryCatalogTransport
import com.example.localvocabulary.dictionary.catalog.RepositoryDownloadedDictionaryPackInstaller
import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyResolver
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictPackSource
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
import com.example.localvocabulary.dictionary.provider.kaikki.KaikkiDataSource
import com.example.localvocabulary.dictionary.provider.kaikki.KaikkiIndexSource
import com.example.localvocabulary.dictionary.provider.kaikki.KaikkiLookup
import com.example.localvocabulary.dictionary.provider.kaikki.KaikkiProvider
import com.example.localvocabulary.dictionary.registry.DefaultDictionaryProviderRegistry
import com.example.localvocabulary.dictionary.registry.DictionaryProviderRegistry
import com.example.localvocabulary.dictionary.reference.ExternalDictionaryReferenceProvider
import com.example.localvocabulary.dictionary.reference.NaverDictionaryLinkProvider
import com.example.localvocabulary.dictionary.pack.AndroidDictionaryPackPayloadValidator
import com.example.localvocabulary.dictionary.pack.AndroidDictionaryPackRepository
import com.example.localvocabulary.dictionary.pack.DictionaryPackPayloadValidator
import com.example.localvocabulary.dictionary.pack.DictionaryPackRepository
import com.example.localvocabulary.dictionary.pack.DictionaryPackResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DictionaryProviderModule {
    @Multibinds
    abstract fun dictionaryProviders(): Set<DictionaryProvider>

    companion object {
        @Provides
        @Singleton
        fun provideDictionaryPackPayloadValidator(
            validator: AndroidDictionaryPackPayloadValidator,
        ): DictionaryPackPayloadValidator = validator

        @Provides
        @Singleton
        fun provideDictionaryPackRepository(
            repository: AndroidDictionaryPackRepository,
        ): DictionaryPackRepository = repository

        @Provides
        @Singleton
        fun provideDictionaryPackResolver(
            repository: AndroidDictionaryPackRepository,
        ): DictionaryPackResolver = repository

        @Provides
        @Singleton
        fun provideDictionaryCatalogEndpoint(): DictionaryCatalogEndpoint =
            DictionaryCatalogEndpoint(BuildConfig.DICTIONARY_CATALOG_URL)

        @Provides
        @Singleton
        fun provideDictionaryCatalogTransport(
            transport: OkHttpDictionaryCatalogTransport,
        ): DictionaryCatalogTransport = transport

        @Provides
        @Singleton
        fun provideDictionaryCatalogStorage(
            @ApplicationContext context: Context,
        ): DictionaryCatalogStorage = FileDictionaryCatalogStorage(
            context.filesDir.resolve("dictionary-catalog"),
        )

        @Provides
        @Singleton
        fun provideDownloadedDictionaryPackInstaller(
            installer: RepositoryDownloadedDictionaryPackInstaller,
        ): DownloadedDictionaryPackInstaller = installer

        @Provides
        @Singleton
        fun provideDictionaryCatalogRepository(
            repository: DefaultDictionaryCatalogRepository,
        ): DictionaryCatalogRepository = repository

        @Provides
        @Singleton
        fun provideExternalDictionaryReferenceProvider(): ExternalDictionaryReferenceProvider =
            NaverDictionaryLinkProvider()

        @Provides
        @Singleton
        internal fun provideCcCedictDataSource(
            packSource: CcCedictPackSource,
        ): CcCedictDataSource = CcCedictDataSource(packSource)

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
        internal fun provideKaikkiDataSource(indexSource: KaikkiIndexSource): KaikkiDataSource =
            KaikkiDataSource(indexSource)

        @Provides
        @Singleton
        internal fun provideKaikkiLookup(dataSource: KaikkiDataSource): KaikkiLookup = dataSource

        @Provides
        @Singleton
        internal fun provideDictionaryMorphologyResolver(
            dataSource: KaikkiDataSource,
        ): DictionaryMorphologyResolver = dataSource

        @Provides
        @IntoSet
        fun provideKaikkiProvider(provider: KaikkiProvider): DictionaryProvider = provider

        @Provides
        @Singleton
        fun provideDictionaryProviderRegistry(
            providers: Set<@JvmSuppressWildcards DictionaryProvider>,
        ): DictionaryProviderRegistry = DefaultDictionaryProviderRegistry(providers)
    }
}
