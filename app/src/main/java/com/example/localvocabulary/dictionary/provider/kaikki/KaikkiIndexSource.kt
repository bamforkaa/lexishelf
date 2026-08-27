package com.example.localvocabulary.dictionary.provider.kaikki

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.pack.DictionaryPackResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class KaikkiIndexSource @Inject constructor(
    private val packResolver: DictionaryPackResolver,
) : KaikkiDatabaseSource {
    override fun open(sourceLanguageTag: String): KaikkiIndexOpenResult {
        val pair = languagePair(sourceLanguageTag)
        val pack = packResolver.activePack(
            DictionaryProviderId(KaikkiProvider.STABLE_PROVIDER_ID),
            pair,
        ) ?: return KaikkiIndexOpenResult.Missing
        return try {
            val database = SQLiteDatabase.openDatabase(
                pack.payloadFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            KaikkiIndexOpenResult.Opened(
                database = database,
                datasetVersion = pack.manifest.datasetVersion,
                expectedLanguageTag = sourceLanguageTag,
            )
        } catch (error: SQLiteException) {
            KaikkiIndexOpenResult.Failed(error.message)
        } catch (error: SecurityException) {
            KaikkiIndexOpenResult.Failed(error.message)
        }
    }

    override fun activeIdentity(sourceLanguageTag: String): String? = packResolver.activePack(
        DictionaryProviderId(KaikkiProvider.STABLE_PROVIDER_ID),
        languagePair(sourceLanguageTag),
    )?.activationIdentity

    override fun hasAnyActivePack(): Boolean =
        packResolver.activePack(DictionaryProviderId(KaikkiProvider.STABLE_PROVIDER_ID)) != null

    private fun languagePair(sourceLanguageTag: String) = DictionaryLanguagePair(
        sourceLanguage = Bcp47LanguageTag.requireValid(sourceLanguageTag),
        resultLanguage = KAIKKI_ENGLISH_LANGUAGE,
        resultKind = DictionaryResultKind.TRANSLATION,
    )
}

internal sealed interface KaikkiIndexOpenResult {
    data class Opened(
        val database: SQLiteDatabase,
        val datasetVersion: String,
        val expectedLanguageTag: String,
    ) : KaikkiIndexOpenResult
    data object Missing : KaikkiIndexOpenResult
    data class Failed(val detail: String?) : KaikkiIndexOpenResult
}

internal interface KaikkiDatabaseSource {
    fun open(sourceLanguageTag: String): KaikkiIndexOpenResult
    fun activeIdentity(sourceLanguageTag: String): String? = null
    fun hasAnyActivePack(): Boolean = false
}

internal const val KAIKKI_RELEASE_ID = "enwiktionary-2026-08-05"
internal const val KAIKKI_EXTRACTION_DATE = "2026-08-23"
internal const val KAIKKI_INDEX_SCHEMA_VERSION = 3
internal const val KAIKKI_LEGACY_INDEX_SCHEMA_VERSION = 2
