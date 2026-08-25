package com.example.localvocabulary.dictionary.provider.koreanbasic

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.pack.DictionaryPackResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class KoreanBasicDictionaryIndexSource @Inject constructor(
    private val packResolver: DictionaryPackResolver,
) : KoreanBasicDictionaryDatabaseSource {
    override fun open(): KoreanBasicDictionaryIndexOpenResult {
        val pack = packResolver.activePack(
            DictionaryProviderId(KoreanBasicDictionaryProvider.STABLE_PROVIDER_ID),
        ) ?: return KoreanBasicDictionaryIndexOpenResult.Missing

        return try {
            val database = SQLiteDatabase.openDatabase(
                pack.payloadFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            KoreanBasicDictionaryIndexOpenResult.Opened(database, pack.manifest.datasetVersion)
        } catch (error: SQLiteException) {
            KoreanBasicDictionaryIndexOpenResult.Failed(error.message)
        } catch (error: SecurityException) {
            KoreanBasicDictionaryIndexOpenResult.Failed(error.message)
        }
    }

    override fun activeIdentity(): String? = packResolver
        .activePack(DictionaryProviderId(KoreanBasicDictionaryProvider.STABLE_PROVIDER_ID))
        ?.activationIdentity
}

internal sealed interface KoreanBasicDictionaryIndexOpenResult {
    data class Opened(
        val database: SQLiteDatabase,
        val datasetVersion: String = KOREAN_BASIC_DICTIONARY_RELEASE_ID,
    ) : KoreanBasicDictionaryIndexOpenResult
    data object Missing : KoreanBasicDictionaryIndexOpenResult
    data class Failed(val detail: String?) : KoreanBasicDictionaryIndexOpenResult
}

internal fun interface KoreanBasicDictionaryDatabaseSource {
    fun open(): KoreanBasicDictionaryIndexOpenResult

    fun activeIdentity(): String? = null
}

internal const val KOREAN_BASIC_DICTIONARY_ARTIFACT_NAME =
    "korean_basic_dictionary.db"
internal const val KOREAN_BASIC_DICTIONARY_RELEASE_ID = "2026-08-19"
internal const val KOREAN_BASIC_DICTIONARY_INDEX_SCHEMA_VERSION = 1
