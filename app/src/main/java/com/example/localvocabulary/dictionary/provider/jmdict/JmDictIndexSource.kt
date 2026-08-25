package com.example.localvocabulary.dictionary.provider.jmdict

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.pack.DictionaryPackResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class JmDictIndexSource @Inject constructor(
    private val packResolver: DictionaryPackResolver,
) : JmDictDatabaseSource {
    override fun open(): JmDictIndexOpenResult {
        val pack = packResolver.activePack(DictionaryProviderId(JmDictProvider.STABLE_PROVIDER_ID))
            ?: return JmDictIndexOpenResult.Missing

        return try {
            val database = SQLiteDatabase.openDatabase(
                pack.payloadFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            JmDictIndexOpenResult.Opened(database, pack.manifest.datasetVersion)
        } catch (error: SQLiteException) {
            JmDictIndexOpenResult.Failed(error.message)
        } catch (error: SecurityException) {
            JmDictIndexOpenResult.Failed(error.message)
        }
    }

    override fun activeIdentity(): String? = packResolver
        .activePack(DictionaryProviderId(JmDictProvider.STABLE_PROVIDER_ID))
        ?.activationIdentity
}

internal fun interface JmDictDatabaseSource {
    fun open(): JmDictIndexOpenResult

    fun activeIdentity(): String? = null
}

internal sealed interface JmDictIndexOpenResult {
    data class Opened(
        val database: SQLiteDatabase,
        val datasetVersion: String = JMDICT_RELEASE_ID,
    ) : JmDictIndexOpenResult
    data object Missing : JmDictIndexOpenResult
    data class Failed(val detail: String?) : JmDictIndexOpenResult
}

internal const val JMDICT_ARTIFACT_NAME = "jmdict.db"
internal const val JMDICT_RELEASE_ID = "2026-08-23"
internal const val JMDICT_INDEX_SCHEMA_VERSION = 1
