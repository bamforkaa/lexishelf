package com.example.localvocabulary.dictionary.provider.panlex

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.pack.DictionaryPackResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PanLexIndexSource @Inject constructor(
    private val packResolver: DictionaryPackResolver,
) : PanLexDatabaseSource {
    override fun open(): PanLexIndexOpenResult {
        val pack = packResolver.activePack(DictionaryProviderId(PanLexProvider.STABLE_PROVIDER_ID))
            ?: return PanLexIndexOpenResult.Missing

        return try {
            val database = SQLiteDatabase.openDatabase(
                pack.payloadFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            PanLexIndexOpenResult.Opened(database, pack.manifest.datasetVersion)
        } catch (error: SQLiteException) {
            PanLexIndexOpenResult.Failed(error.message)
        } catch (error: SecurityException) {
            PanLexIndexOpenResult.Failed(error.message)
        }
    }

    override fun activeIdentity(): String? = packResolver
        .activePack(DictionaryProviderId(PanLexProvider.STABLE_PROVIDER_ID))
        ?.activationIdentity
}

internal sealed interface PanLexIndexOpenResult {
    data class Opened(
        val database: SQLiteDatabase,
        val datasetVersion: String = PANLEX_RELEASE_ID,
    ) : PanLexIndexOpenResult
    data object Missing : PanLexIndexOpenResult
    data class Failed(val detail: String?) : PanLexIndexOpenResult
}

internal fun interface PanLexDatabaseSource {
    fun open(): PanLexIndexOpenResult

    fun activeIdentity(): String? = null
}

internal const val PANLEX_ARTIFACT_NAME = "panlex_korean_fallback.db"
internal const val PANLEX_RELEASE_ID = "2019-09-01"
internal const val PANLEX_INDEX_SCHEMA_VERSION = 1
