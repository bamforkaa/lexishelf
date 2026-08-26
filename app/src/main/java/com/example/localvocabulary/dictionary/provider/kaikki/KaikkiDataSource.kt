package com.example.localvocabulary.dictionary.provider.kaikki

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class KaikkiDataSource(
    private val indexSource: KaikkiDatabaseSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KaikkiLookup {
    private val loadMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false }
    private val cachedLoads = ConcurrentHashMap<String, IdentifiedLoad>()

    override suspend fun exactLookup(
        query: String,
        sourceLanguageTag: String,
        resultLimit: Int,
    ): KaikkiLookupResult = withContext(ioDispatcher) {
        when (val load = loadIndex(sourceLanguageTag)) {
            is IndexLoad.Ready -> queryIndex(
                database = load.database,
                normalizedQuery = normalizeExactKey(query),
                resultLimit = resultLimit,
                datasetVersion = load.datasetVersion,
            )
            IndexLoad.Unavailable -> KaikkiLookupResult.DatasetUnavailable
            is IndexLoad.Invalid -> KaikkiLookupResult.MalformedDataset(load.detail)
        }
    }

    override suspend fun availability(): KaikkiDatasetAvailability = withContext(ioDispatcher) {
        if (indexSource.hasAnyActivePack()) {
            KaikkiDatasetAvailability.Available
        } else {
            KaikkiDatasetAvailability.Unavailable
        }
    }

    private suspend fun loadIndex(sourceLanguageTag: String): IndexLoad {
        val identity = indexSource.activeIdentity(sourceLanguageTag)
        return cachedLoads[sourceLanguageTag]?.takeIf { it.identity == identity }?.load
            ?: loadMutex.withLock {
                cachedLoads[sourceLanguageTag]?.takeIf { it.identity == identity }?.load
                    ?: run {
                        (cachedLoads.remove(sourceLanguageTag)?.load as? IndexLoad.Ready)
                            ?.database
                            ?.close()
                        openAndValidate(sourceLanguageTag).also { load ->
                            cachedLoads[sourceLanguageTag] = IdentifiedLoad(identity, load)
                        }
                    }
            }
    }

    private fun openAndValidate(sourceLanguageTag: String): IndexLoad =
        when (val opened = indexSource.open(sourceLanguageTag)) {
            KaikkiIndexOpenResult.Missing -> IndexLoad.Unavailable
            is KaikkiIndexOpenResult.Failed -> IndexLoad.Invalid(opened.detail)
            is KaikkiIndexOpenResult.Opened -> try {
                validate(opened.database, opened.datasetVersion, opened.expectedLanguageTag)
                IndexLoad.Ready(opened.database, opened.datasetVersion)
            } catch (error: SQLiteException) {
                opened.database.close()
                IndexLoad.Invalid(error.message)
            } catch (error: IllegalStateException) {
                opened.database.close()
                IndexLoad.Invalid(error.message)
            }
        }

    private fun validate(
        database: SQLiteDatabase,
        expectedDatasetVersion: String,
        expectedLanguageTag: String,
    ) {
        val schema = database.rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst()) { "Kaikki index has no schema version" }
            cursor.getInt(0)
        }
        check(schema == KAIKKI_INDEX_SCHEMA_VERSION) {
            "Unsupported Kaikki index schema: $schema"
        }
        check(database.metadata("release_id") == expectedDatasetVersion) {
            "Unexpected Kaikki release"
        }
        check(database.metadata("language_tag") == expectedLanguageTag) {
            "Unexpected Kaikki language pack"
        }
        check(database.metadata("result_language_tag") == "en") {
            "Unexpected Kaikki result language"
        }
    }

    private fun SQLiteDatabase.metadata(key: String): String = rawQuery(
        "SELECT value FROM metadata WHERE key = ?",
        arrayOf(key),
    ).use { cursor ->
        check(cursor.moveToFirst()) { "Kaikki index is missing $key metadata" }
        cursor.getString(0)
    }

    private fun queryIndex(
        database: SQLiteDatabase,
        normalizedQuery: String,
        resultLimit: Int,
        datasetVersion: String,
    ): KaikkiLookupResult = try {
        val matches = buildList {
            database.rawQuery(
                LOOKUP_QUERY,
                arrayOf(normalizedQuery, (resultLimit + 1).toString()),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    add(
                        KaikkiMatch(
                            sourceEntryId = cursor.getString(0),
                            entry = json.decodeFromString<KaikkiEntryRecord>(
                                cursor.getBlob(1).decodeToString(),
                            ),
                        ),
                    )
                }
            }
        }
        if (matches.isEmpty()) {
            KaikkiLookupResult.NoMatch
        } else {
            KaikkiLookupResult.Matches(
                records = matches.take(resultLimit),
                isTruncated = matches.size > resultLimit,
                datasetVersion = datasetVersion,
            )
        }
    } catch (error: SQLiteException) {
        KaikkiLookupResult.MalformedDataset(error.message)
    } catch (error: SerializationException) {
        KaikkiLookupResult.MalformedDataset(error.message)
    }

    private sealed interface IndexLoad {
        data class Ready(val database: SQLiteDatabase, val datasetVersion: String) : IndexLoad
        data object Unavailable : IndexLoad
        data class Invalid(val detail: String?) : IndexLoad
    }

    private data class IdentifiedLoad(val identity: String?, val load: IndexLoad)

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val LOOKUP_QUERY =
            """
            SELECT entry_id, payload
            FROM entries
            WHERE normalized_headword = ?
            ORDER BY entry_order, entry_id
            LIMIT ?
            """.trimIndent()

        fun normalizeExactKey(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFC)
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)
    }
}

