package com.example.localvocabulary.dictionary.provider.jmdict

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class JmDictDataSource(
    private val indexSource: JmDictDatabaseSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JmDictLookup {
    private val loadMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = false }

    @Volatile private var cachedLoad: IndexLoad? = null

    override suspend fun exactLookup(query: String, resultLimit: Int?): JmDictLookupResult =
        withContext(ioDispatcher) {
            when (val load = loadIndex()) {
                is IndexLoad.Ready -> query(load.database, normalizeExactKey(query), resultLimit)
                IndexLoad.Unavailable -> JmDictLookupResult.DatasetUnavailable
                is IndexLoad.Invalid -> JmDictLookupResult.MalformedDataset(load.detail)
            }
        }

    override suspend fun availability(): JmDictDatasetAvailability = withContext(ioDispatcher) {
        when (val load = loadIndex()) {
            is IndexLoad.Ready -> JmDictDatasetAvailability.Available
            IndexLoad.Unavailable -> JmDictDatasetAvailability.Unavailable
            is IndexLoad.Invalid -> JmDictDatasetAvailability.Invalid(load.detail)
        }
    }

    private suspend fun loadIndex(): IndexLoad = cachedLoad ?: loadMutex.withLock {
        cachedLoad ?: openAndValidate().also { cachedLoad = it }
    }

    private fun openAndValidate(): IndexLoad = when (val opened = indexSource.open()) {
        JmDictIndexOpenResult.Missing -> IndexLoad.Unavailable
        is JmDictIndexOpenResult.Failed -> IndexLoad.Invalid(opened.detail)
        is JmDictIndexOpenResult.Opened -> try {
            validate(opened.database)
            IndexLoad.Ready(opened.database)
        } catch (error: SQLiteException) {
            opened.database.close()
            IndexLoad.Invalid(error.message)
        } catch (error: IllegalStateException) {
            opened.database.close()
            IndexLoad.Invalid(error.message)
        }
    }

    private fun validate(database: SQLiteDatabase) {
        val schema = database.rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst()) { "JMdict index has no schema version" }
            cursor.getInt(0)
        }
        check(schema == JMDICT_INDEX_SCHEMA_VERSION) { "Unsupported JMdict index schema: $schema" }
        val release = database.rawQuery(
            "SELECT value FROM metadata WHERE key = 'release_id'",
            null,
        ).use { cursor ->
            check(cursor.moveToFirst()) { "JMdict index has no release metadata" }
            cursor.getString(0)
        }
        check(release == JMDICT_RELEASE_ID) { "Unexpected JMdict release: $release" }
    }

    private fun query(
        database: SQLiteDatabase,
        normalizedQuery: String,
        resultLimit: Int?,
    ): JmDictLookupResult = try {
        val unique = linkedMapOf<String, JmDictMatch>()
        database.rawQuery(LOOKUP_QUERY, arrayOf(normalizedQuery)).use { cursor ->
            while (cursor.moveToNext() && (resultLimit == null || unique.size < resultLimit)) {
                val entrySequence = cursor.getString(0)
                if (entrySequence in unique) continue
                val payload = cursor.getBlob(3).decodeToString()
                unique[entrySequence] = JmDictMatch(
                    entry = json.decodeFromString<JmDictEntryRecord>(payload),
                    matchKind = if (cursor.getInt(1) == 0) {
                        JmDictMatchKind.WRITTEN_FORM
                    } else {
                        JmDictMatchKind.READING
                    },
                    matchedElementOrder = cursor.getInt(2),
                )
            }
        }
        if (unique.isEmpty()) JmDictLookupResult.NoMatch else JmDictLookupResult.Matches(unique.values.toList())
    } catch (error: SQLiteException) {
        JmDictLookupResult.MalformedDataset(error.message)
    } catch (error: SerializationException) {
        JmDictLookupResult.MalformedDataset(error.message)
    }

    private sealed interface IndexLoad {
        data class Ready(val database: SQLiteDatabase) : IndexLoad
        data object Unavailable : IndexLoad
        data class Invalid(val detail: String?) : IndexLoad
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val LOOKUP_QUERY =
            """
            SELECT e.ent_seq, l.match_kind, l.element_order, e.payload
            FROM lookup_keys l JOIN entries e USING(ent_seq)
            WHERE l.normalized_key = ?
            ORDER BY l.has_priority DESC, e.entry_order ASC,
                     l.match_kind ASC, l.element_order ASC, e.ent_seq ASC
            """.trimIndent()

        fun normalizeExactKey(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFC)
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)
    }
}
