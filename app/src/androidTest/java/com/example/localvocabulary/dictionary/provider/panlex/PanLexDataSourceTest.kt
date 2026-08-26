package com.example.localvocabulary.dictionary.provider.panlex

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PanLexDataSourceTest {
    private lateinit var databaseFile: File

    @Before
    fun createFixture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseFile = File(context.cacheDir, "panlex-fixture.db")
        databaseFile.delete()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL("PRAGMA user_version = $PANLEX_INDEX_SCHEMA_VERSION")
            database.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            database.execSQL("INSERT INTO metadata VALUES('release_id', ?)", arrayOf(PANLEX_RELEASE_ID))
            database.execSQL(
                "INSERT INTO metadata VALUES('supported_language_tags', ?)",
                arrayOf(PANLEX_FOREIGN_LANGUAGE_TAGS_METADATA),
            )
            database.execSQL(
                """
                CREATE TABLE relations(
                    foreign_language_tag TEXT NOT NULL,
                    foreign_expr_id INTEGER NOT NULL,
                    foreign_text TEXT NOT NULL,
                    normalized_foreign TEXT NOT NULL,
                    korean_expr_id INTEGER NOT NULL,
                    korean_text TEXT NOT NULL,
                    normalized_korean TEXT NOT NULL,
                    representative_meaning_id INTEGER NOT NULL,
                    representative_source_id INTEGER NOT NULL,
                    source_attestation_count INTEGER NOT NULL,
                    source_group_count INTEGER NOT NULL,
                    translation_quality INTEGER NOT NULL,
                    PRIMARY KEY(foreign_language_tag, foreign_expr_id, korean_expr_id)
                )
                """.trimIndent(),
            )
            insertRelation(database, 1, "Wasser", "wasser", 10, "물", "물", 101, 201, 2, 2, 15)
            insertRelation(database, 1, "Wasser", "wasser", 11, "수", "수", 102, 202, 1, 1, 8)
            insertRelation(database, 2, "Cafe\u0301", "café", 12, "카페", "카페", 103, 203, 1, 1, 7)
            insertRelation(
                database, 3, "पानी", "पानी", 13, "물", "물", 104, 204, 1, 1, 7, "hi",
            )
            insertRelation(
                database, 4, "książka", "książka", 14, "책", "책", 105, 205, 1, 1, 7, "pl",
            )
            insertRelation(
                database, 5, "aqua", "aqua", 15, "물", "물", 106, 206, 1, 1, 7, "la",
            )
            listOf(
                Triple("nl", "water", "water"),
                Triple("pt", "água", "água"),
                Triple("it", "acqua", "acqua"),
                Triple("tr", "su", "su"),
                Triple("cs", "voda", "voda"),
                Triple("sv", "vatten", "vatten"),
                Triple("fi", "vesi", "vesi"),
                Triple("uk", "вода", "вода"),
            ).forEachIndexed { index, (language, text, normalized) ->
                val identifier = 20L + index
                insertRelation(
                    database = database,
                    foreignExpressionId = identifier,
                    foreignText = text,
                    normalizedForeign = normalized,
                    koreanExpressionId = 30L + index,
                    koreanText = "물",
                    normalizedKorean = "물",
                    meaningId = 300L + index,
                    sourceId = 400L + index,
                    sourceCount = 1,
                    sourceGroupCount = 1,
                    quality = 7,
                    languageTag = language,
                )
            }
        }
    }

    @After
    fun deleteFixture() {
        databaseFile.delete()
    }

    @Test
    fun foreignAndKoreanExactQueriesNormalizeAndPreserveRanking() = runBlocking {
        val source = dataSource()

        val foreign = source.exactLookup("  WASSER ", "de", "ko", 10)
            as PanLexLookupResult.Matches
        val korean = source.exactLookup(" 물 ", "ko", "de", 10)
            as PanLexLookupResult.Matches
        val unicode = source.exactLookup("CAFÉ", "de", "ko", 10)
            as PanLexLookupResult.Matches

        assertEquals(listOf("물", "수"), foreign.records.map { it.targetText })
        assertEquals("물", korean.records.single().sourceText)
        assertEquals("Wasser", korean.records.single().targetText)
        assertEquals("Cafe\u0301", unicode.records.single().sourceText)
        assertFalse(foreign.isTruncated)
    }

    @Test
    fun limitReportsTruncationWithoutChangingTopRankedResult() = runBlocking {
        val result = dataSource().exactLookup("Wasser", "de", "ko", 1)
            as PanLexLookupResult.Matches

        assertEquals("물", result.records.single().targetText)
        assertTrue(result.isTruncated)
    }

    @Test
    fun allReviewedLanguagesSupportForeignAndKoreanDirections() = runBlocking {
        val source = dataSource()
        val cases = listOf(
            Triple("de", "Wasser", "물"),
            Triple("hi", "पानी", "물"),
            Triple("pl", "książka", "책"),
            Triple("la", "aqua", "물"),
            Triple("nl", "water", "물"),
            Triple("pt", "água", "물"),
            Triple("it", "acqua", "물"),
            Triple("tr", "su", "물"),
            Triple("cs", "voda", "물"),
            Triple("sv", "vatten", "물"),
            Triple("fi", "vesi", "물"),
            Triple("uk", "вода", "물"),
        )

        cases.forEach { (language, foreign, korean) ->
            val forward = source.exactLookup(foreign, language, "ko", 10)
                as PanLexLookupResult.Matches
            val reverse = source.exactLookup(korean, "ko", language, 10)
                as PanLexLookupResult.Matches

            assertTrue(forward.records.any { it.targetText == korean })
            assertTrue(reverse.records.any { it.targetText == foreign })
        }
    }

    @Test
    fun malformedIndexVersionIsRejectedWithoutFallbackData() = runBlocking {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { it.execSQL("PRAGMA user_version = 99") }

        val result = dataSource().exactLookup("Wasser", "de", "ko", 10)

        assertTrue(result is PanLexLookupResult.MalformedDataset)
    }

    private fun dataSource() = PanLexDataSource(
        PanLexDatabaseSource {
            PanLexIndexOpenResult.Opened(
                SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ),
            )
        },
    )

    private fun insertRelation(
        database: SQLiteDatabase,
        foreignExpressionId: Long,
        foreignText: String,
        normalizedForeign: String,
        koreanExpressionId: Long,
        koreanText: String,
        normalizedKorean: String,
        meaningId: Long,
        sourceId: Long,
        sourceCount: Int,
        sourceGroupCount: Int,
        quality: Int,
        languageTag: String = "de",
    ) {
        database.execSQL(
            "INSERT INTO relations VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(
                languageTag,
                foreignExpressionId,
                foreignText,
                normalizedForeign,
                koreanExpressionId,
                koreanText,
                normalizedKorean,
                meaningId,
                sourceId,
                sourceCount,
                sourceGroupCount,
                quality,
            ),
        )
    }
}
