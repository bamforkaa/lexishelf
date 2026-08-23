package com.example.localvocabulary.dictionary.provider.panlex

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

internal class PanLexDataSource(
    private val indexSource: PanLexDatabaseSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PanLexLookup {
    private val loadMutex = Mutex()

    @Volatile
    private var cachedLoad: IndexLoad? = null

    override suspend fun exactLookup(
        query: String,
        sourceLanguageTag: String,
        resultLanguageTag: String,
        resultLimit: Int,
    ): PanLexLookupResult = withContext(ioDispatcher) {
        when (val load = loadIndex()) {
            is IndexLoad.Ready -> queryIndex(
                database = load.database,
                normalizedQuery = normalizeExactKey(query),
                sourceLanguageTag = sourceLanguageTag,
                resultLanguageTag = resultLanguageTag,
                resultLimit = resultLimit,
            )
            IndexLoad.Unavailable -> PanLexLookupResult.DatasetUnavailable
            is IndexLoad.Invalid -> PanLexLookupResult.MalformedDataset(load.detail)
        }
    }

    override suspend fun availability(): PanLexDatasetAvailability = withContext(ioDispatcher) {
        when (val load = loadIndex()) {
            is IndexLoad.Ready -> PanLexDatasetAvailability.Available
            IndexLoad.Unavailable -> PanLexDatasetAvailability.Unavailable
            is IndexLoad.Invalid -> PanLexDatasetAvailability.Invalid(load.detail)
        }
    }

    private suspend fun loadIndex(): IndexLoad = cachedLoad ?: loadMutex.withLock {
        cachedLoad ?: openAndValidateIndex().also { cachedLoad = it }
    }

    private fun openAndValidateIndex(): IndexLoad = when (val opened = indexSource.open()) {
        PanLexIndexOpenResult.Missing -> IndexLoad.Unavailable
        is PanLexIndexOpenResult.Failed -> IndexLoad.Invalid(opened.detail)
        is PanLexIndexOpenResult.Opened -> try {
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
            check(cursor.moveToFirst()) { "PanLex index has no schema version" }
            cursor.getInt(0)
        }
        check(schemaVersion == PANLEX_INDEX_SCHEMA_VERSION) {
            "Unsupported PanLex index schema: $schemaVersion"
        }
        val releaseId = database.metadata("release_id")
        check(releaseId == PANLEX_RELEASE_ID) {
            "Unexpected PanLex release: $releaseId"
        }
        check(database.metadata("supported_language_tags") == PANLEX_FOREIGN_LANGUAGE_TAGS) {
            "Unexpected PanLex language-variety mapping"
        }
    }

    private fun SQLiteDatabase.metadata(key: String): String = rawQuery(
        "SELECT value FROM metadata WHERE key = ?",
        arrayOf(key),
    ).use { cursor ->
        check(cursor.moveToFirst()) { "PanLex index is missing $key metadata" }
        cursor.getString(0)
    }

    private fun queryIndex(
        database: SQLiteDatabase,
        normalizedQuery: String,
        sourceLanguageTag: String,
        resultLanguageTag: String,
        resultLimit: Int,
    ): PanLexLookupResult = try {
        val rows = if (sourceLanguageTag == KOREAN_LANGUAGE_TAG) {
            database.queryKoreanToForeign(normalizedQuery, resultLanguageTag, resultLimit + 1)
        } else {
            database.queryForeignToKorean(sourceLanguageTag, normalizedQuery, resultLimit + 1)
        }
        if (rows.isEmpty()) {
            PanLexLookupResult.NoMatch
        } else {
            PanLexLookupResult.Matches(
                records = rows.take(resultLimit),
                isTruncated = rows.size > resultLimit,
            )
        }
    } catch (error: SQLiteException) {
        PanLexLookupResult.MalformedDataset(error.message)
    }

    private fun SQLiteDatabase.queryForeignToKorean(
        sourceLanguageTag: String,
        normalizedQuery: String,
        limit: Int,
    ): List<PanLexRelationRecord> = rawQuery(
        FOREIGN_TO_KOREAN_QUERY,
        arrayOf(sourceLanguageTag, normalizedQuery, limit.toString()),
    ).use { cursor -> readRecords(cursor, sourceIsKorean = false) }

    private fun SQLiteDatabase.queryKoreanToForeign(
        normalizedQuery: String,
        resultLanguageTag: String,
        limit: Int,
    ): List<PanLexRelationRecord> = rawQuery(
        KOREAN_TO_FOREIGN_QUERY,
        arrayOf(normalizedQuery, resultLanguageTag, limit.toString()),
    ).use { cursor -> readRecords(cursor, sourceIsKorean = true) }

    private fun readRecords(cursor: Cursor, sourceIsKorean: Boolean): List<PanLexRelationRecord> =
        buildList {
            while (cursor.moveToNext()) {
                val foreignExpressionId = cursor.getLong(0)
                val foreignText = cursor.getString(1)
                val koreanExpressionId = cursor.getLong(2)
                val koreanText = cursor.getString(3)
                add(
                    PanLexRelationRecord(
                        sourceExpressionId = if (sourceIsKorean) koreanExpressionId else foreignExpressionId,
                        sourceText = if (sourceIsKorean) koreanText else foreignText,
                        targetExpressionId = if (sourceIsKorean) foreignExpressionId else koreanExpressionId,
                        targetText = if (sourceIsKorean) foreignText else koreanText,
                        representativeMeaningId = cursor.getLong(4),
                        representativeSourceId = cursor.getLong(5),
                        sourceAttestationCount = cursor.getInt(6),
                        sourceGroupCount = cursor.getInt(7),
                        translationQuality = cursor.getInt(8),
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
        const val PANLEX_FOREIGN_LANGUAGE_TAGS = "de,hi,pl,la"
        val WHITESPACE = Regex("\\s+")

        val FOREIGN_TO_KOREAN_QUERY =
            """
            SELECT foreign_expr_id, foreign_text, korean_expr_id, korean_text,
                   representative_meaning_id, representative_source_id,
                   source_attestation_count, source_group_count, translation_quality
            FROM relations
            WHERE foreign_language_tag = ? AND normalized_foreign = ?
            ORDER BY translation_quality DESC, source_group_count DESC,
                     source_attestation_count DESC, korean_text, korean_expr_id
            LIMIT ?
            """.trimIndent()

        val KOREAN_TO_FOREIGN_QUERY =
            """
            SELECT foreign_expr_id, foreign_text, korean_expr_id, korean_text,
                   representative_meaning_id, representative_source_id,
                   source_attestation_count, source_group_count, translation_quality
            FROM relations
            WHERE normalized_korean = ? AND foreign_language_tag = ?
            ORDER BY translation_quality DESC, source_group_count DESC,
                     source_attestation_count DESC, foreign_text, foreign_expr_id
            LIMIT ?
            """.trimIndent()

        fun normalizeExactKey(value: String): String = Normalizer
            .normalize(value, Normalizer.Form.NFC)
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)
    }
}
