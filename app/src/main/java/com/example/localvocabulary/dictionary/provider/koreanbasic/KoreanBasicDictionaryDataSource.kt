package com.example.localvocabulary.dictionary.provider.koreanbasic

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class KoreanBasicDictionaryDataSource(
    private val indexSource: KoreanBasicDictionaryDatabaseSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KoreanBasicDictionaryLookup {
    private val loadMutex = Mutex()

    @Volatile
    private var cachedLoad: IndexLoad? = null

    override suspend fun exactLookup(
        query: String,
        sourceLanguageTag: String,
        resultLanguageTag: String,
    ): KoreanBasicDictionaryLookupResult = withContext(ioDispatcher) {
        when (val load = loadIndex()) {
            is IndexLoad.Ready -> queryIndex(
                database = load.database,
                query = normalizeExactKey(query),
                sourceLanguageTag = sourceLanguageTag,
                resultLanguageTag = resultLanguageTag,
            )
            IndexLoad.Unavailable -> KoreanBasicDictionaryLookupResult.DatasetUnavailable
            is IndexLoad.Invalid -> KoreanBasicDictionaryLookupResult.MalformedDataset(load.detail)
        }
    }

    override suspend fun availability(): KoreanBasicDictionaryDatasetAvailability =
        withContext(ioDispatcher) {
            when (val load = loadIndex()) {
                is IndexLoad.Ready -> KoreanBasicDictionaryDatasetAvailability.Available
                IndexLoad.Unavailable -> KoreanBasicDictionaryDatasetAvailability.Unavailable
                is IndexLoad.Invalid -> KoreanBasicDictionaryDatasetAvailability.Invalid(load.detail)
            }
        }

    private suspend fun loadIndex(): IndexLoad = cachedLoad ?: loadMutex.withLock {
        cachedLoad ?: openAndValidateIndex().also { cachedLoad = it }
    }

    private fun openAndValidateIndex(): IndexLoad = when (val opened = indexSource.open()) {
        KoreanBasicDictionaryIndexOpenResult.Missing -> IndexLoad.Unavailable
        is KoreanBasicDictionaryIndexOpenResult.Failed -> IndexLoad.Invalid(opened.detail)
        is KoreanBasicDictionaryIndexOpenResult.Opened -> try {
            validateIndex(opened.database)
            IndexLoad.Ready(opened.database)
        } catch (error: SQLiteException) {
            opened.database.close()
            IndexLoad.Invalid(error.message)
        } catch (error: IllegalStateException) {
            opened.database.close()
            IndexLoad.Invalid(error.message)
        }
    }

    private fun validateIndex(database: SQLiteDatabase) {
        val schemaVersion = database.rawQuery("PRAGMA user_version", null).use { cursor ->
            check(cursor.moveToFirst()) { "Dictionary index has no schema version" }
            cursor.getInt(0)
        }
        check(schemaVersion == KOREAN_BASIC_DICTIONARY_INDEX_SCHEMA_VERSION) {
            "Unsupported Korean Basic Dictionary index schema: $schemaVersion"
        }
        val releaseId = database.rawQuery(
            "SELECT value FROM metadata WHERE key = 'release_id'",
            null,
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Dictionary index has no release metadata" }
            cursor.getString(0)
        }
        check(releaseId == KOREAN_BASIC_DICTIONARY_RELEASE_ID) {
            "Unexpected Korean Basic Dictionary release: $releaseId"
        }
    }

    private fun queryIndex(
        database: SQLiteDatabase,
        query: String,
        sourceLanguageTag: String,
        resultLanguageTag: String,
    ): KoreanBasicDictionaryLookupResult = try {
        val records = if (sourceLanguageTag == KOREAN_LANGUAGE_TAG) {
            database.queryForward(query, resultLanguageTag)
        } else {
            database.queryReverse(sourceLanguageTag, query)
        }
        if (records.isEmpty()) {
            KoreanBasicDictionaryLookupResult.NoMatch
        } else {
            KoreanBasicDictionaryLookupResult.Matches(records)
        }
    } catch (error: SQLiteException) {
        KoreanBasicDictionaryLookupResult.MalformedDataset(error.message)
    }

    private fun SQLiteDatabase.queryForward(
        normalizedHeadword: String,
        resultLanguageTag: String,
    ): List<KoreanBasicDictionaryRecord> = rawQuery(
        FORWARD_QUERY,
        arrayOf(normalizedHeadword, resultLanguageTag),
    ).use(::readRecords)

    private fun SQLiteDatabase.queryReverse(
        sourceLanguageTag: String,
        normalizedTerm: String,
    ): List<KoreanBasicDictionaryRecord> = rawQuery(
        REVERSE_QUERY,
        arrayOf(sourceLanguageTag, normalizedTerm),
    ).use(::readRecords)

    private fun readRecords(cursor: Cursor): List<KoreanBasicDictionaryRecord> = buildList {
        while (cursor.moveToNext()) {
            add(
                KoreanBasicDictionaryRecord(
                    officialEntryId = cursor.getString(0),
                    officialSenseId = cursor.getString(1),
                    koreanHeadword = cursor.getString(2),
                    partOfSpeech = cursor.getString(3),
                    translationTerm = cursor.getString(4),
                    entryOrder = cursor.getLong(5),
                    senseOrder = cursor.getInt(6),
                    translationOrder = cursor.getInt(7),
                ),
            )
        }
    }

    private sealed interface IndexLoad {
        data class Ready(val database: SQLiteDatabase) : IndexLoad
        data object Unavailable : IndexLoad
        data class Invalid(val detail: String?) : IndexLoad
    }

    private companion object {
        const val KOREAN_LANGUAGE_TAG = "ko"
        val WHITESPACE = Regex("\\s+")

        val FORWARD_QUERY =
            """
            SELECT e.official_entry_id, s.official_sense_id, e.headword,
                   e.part_of_speech, t.display_term, e.entry_pk,
                   s.sense_order, t.translation_order
            FROM entries e
            JOIN senses s USING(entry_pk)
            JOIN translations t USING(sense_pk)
            WHERE e.normalized_headword = ? AND t.language_tag = ?
            ORDER BY e.entry_pk, s.sense_order, t.translation_order
            """.trimIndent()

        val REVERSE_QUERY =
            """
            SELECT e.official_entry_id, s.official_sense_id, e.headword,
                   e.part_of_speech, t.display_term, e.entry_pk,
                   s.sense_order, t.translation_order
            FROM reverse_keys r
            JOIN translations t USING(translation_pk)
            JOIN senses s USING(sense_pk)
            JOIN entries e USING(entry_pk)
            WHERE r.language_tag = ? AND r.normalized_term = ?
            ORDER BY e.entry_pk, s.sense_order, t.translation_order
            """.trimIndent()

        fun normalizeExactKey(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFC)
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)
    }
}
