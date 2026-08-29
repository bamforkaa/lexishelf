package com.example.localvocabulary.dictionary.provider.kaikki

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyQuery
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyResult
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
class KaikkiDataSourceTest {
    private lateinit var databaseFile: File

    @Before
    fun createFixture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseFile = File(context.cacheDir, "kaikki-de-fixture.db")
        databaseFile.delete()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL("PRAGMA user_version = $KAIKKI_INDEX_SCHEMA_VERSION")
            database.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            mapOf(
                "release_id" to KAIKKI_RELEASE_ID,
                "language_tag" to "de",
                "result_language_tag" to "en",
            ).forEach { (key, value) ->
                database.execSQL("INSERT INTO metadata VALUES(?, ?)", arrayOf(key, value))
            }
            database.execSQL(
                """
                CREATE TABLE entries(
                    entry_id TEXT PRIMARY KEY,
                    normalized_headword TEXT NOT NULL,
                    headword TEXT NOT NULL,
                    entry_order INTEGER NOT NULL,
                    payload BLOB NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE morphology_forms(
                    normalized_form TEXT NOT NULL,
                    form TEXT NOT NULL,
                    lemma TEXT NOT NULL,
                    normalized_lemma TEXT NOT NULL,
                    source_entry_id TEXT NOT NULL,
                    entry_order INTEGER NOT NULL,
                    form_order INTEGER NOT NULL,
                    PRIMARY KEY(normalized_form, normalized_lemma, source_entry_id)
                )
                """.trimIndent(),
            )
            insert(database, "de-water-noun", 0, "Wasser", nounPayload)
            insert(database, "de-water-name", 1, "Wasser", namePayload)
            insert(database, "de-cafe", 2, "Cafe\u0301", cafePayload)
            insertMorphology(database, "Häuser", "Haus", "de-house", 0)
            insertMorphology(database, "Häuser", "Haus", "de-house-homograph", 1)
            insertMorphology(database, "Häuser", "hausen", "de-hausen", 2)
            insertMorphology(database, "Is", "I", "en-i-abbreviation", 3)
            insertMorphology(database, "is", "be", "en-be", 4)
            repeat(6) { index ->
                insertMorphology(
                    database,
                    "shared",
                    "lemma-$index",
                    "shared-$index",
                    10 + index,
                )
            }
        }
    }

    @After
    fun deleteFixture() {
        databaseFile.delete()
    }

    @Test
    fun exactLookupNormalizesUnicodeAndPreservesHomographsAndSenseOrder() = runBlocking {
        val source = dataSource()
        val matches = source.exactLookup(" WASSER ", "de", 20)
            as KaikkiLookupResult.Matches
        val unicode = source.exactLookup("CAFÉ", "de", 20)
            as KaikkiLookupResult.Matches

        assertEquals(listOf("de-water-noun", "de-water-name"), matches.records.map { it.sourceEntryId })
        assertEquals(listOf("water", "body of water"), matches.records.first().entry.senses.map { it.glosses.single() })
        assertEquals(
            listOf("Das Wasser ist kalt."),
            matches.records.first().entry.senses.first().retainedExamples,
        )
        assertEquals("Cafe\u0301", unicode.records.single().entry.headword)
        assertFalse(matches.isTruncated)
    }

    @Test
    fun resultLimitReportsTruncationAndNoResultIsNormal() = runBlocking {
        val limited = dataSource().exactLookup("Wasser", "de", 1)
            as KaikkiLookupResult.Matches
        val missing = dataSource().exactLookup("fehlt", "de", 20)

        assertEquals("de-water-noun", limited.records.single().sourceEntryId)
        assertTrue(limited.isTruncated)
        assertEquals(KaikkiLookupResult.NoMatch, missing)
    }

    @Test
    fun wrongLanguageMetadataIsRejected() = runBlocking {
        val result = dataSource().exactLookup("Wasser", "nl", 20)

        assertTrue(result is KaikkiLookupResult.MalformedDataset)
    }

    @Test
    fun wrongSchemaIsRejected() = runBlocking {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            database.execSQL("PRAGMA user_version = ${KAIKKI_INDEX_SCHEMA_VERSION + 1}")
        }

        val result = dataSource().exactLookup("Wasser", "de", 20)

        assertTrue(result is KaikkiLookupResult.MalformedDataset)
    }

    @Test
    fun morphologyLookupNormalizesUnicodeDeduplicatesLemmaAndKeepsHomographs() = runBlocking {
        val result = dataSource().resolve(
            DictionaryMorphologyQuery(
                surface = " HA\u0308USER ",
                sourceLanguage = Bcp47LanguageTag.requireValid("de"),
                resultLimit = 5,
            ),
        ) as DictionaryMorphologyResult.Resolved

        assertEquals(listOf("Haus", "hausen"), result.candidates.map { it.lemma })
        assertEquals("Häuser", result.candidates.first().surface)
        assertEquals("de-house", result.candidates.first().sourceEntryId)
    }

    @Test
    fun morphologyLookupNoResultIsNormal() = runBlocking {
        val result = dataSource().resolve(
            DictionaryMorphologyQuery(
                surface = "fehlt",
                sourceLanguage = Bcp47LanguageTag.requireValid("de"),
            ),
        )

        assertEquals(DictionaryMorphologyResult.NoResult, result)
    }

    @Test
    fun morphologyLookupRanksExactSourceCaseBeforeStableSourceOrder() = runBlocking {
        val lower = dataSource().resolve(
            DictionaryMorphologyQuery(
                surface = "is",
                sourceLanguage = Bcp47LanguageTag.requireValid("de"),
            ),
        ) as DictionaryMorphologyResult.Resolved
        val title = dataSource().resolve(
            DictionaryMorphologyQuery(
                surface = "Is",
                sourceLanguage = Bcp47LanguageTag.requireValid("de"),
            ),
        ) as DictionaryMorphologyResult.Resolved

        assertEquals(listOf("be", "I"), lower.candidates.map { it.lemma })
        assertEquals(listOf("I", "be"), title.candidates.map { it.lemma })
        assertFalse(lower.isTruncated)
    }

    @Test
    fun morphologyLookupAppliesFinalSafetyCapAndReportsTruncation() = runBlocking {
        val result = dataSource().resolve(
            DictionaryMorphologyQuery(
                surface = "shared",
                sourceLanguage = Bcp47LanguageTag.requireValid("de"),
                resultLimit = 2,
            ),
        ) as DictionaryMorphologyResult.Resolved

        assertEquals(listOf("lemma-0", "lemma-1"), result.candidates.map { it.lemma })
        assertTrue(result.isTruncated)
    }

    @Test
    fun turkishExactKeyUsesDottedAndDotlessICasePairs() {
        assertEquals("ı", KaikkiDataSource.normalizeExactKey("I", "tr"))
        assertEquals("i", KaikkiDataSource.normalizeExactKey("İ", "tr"))
        assertEquals("i", KaikkiDataSource.normalizeExactKey("i", "tr"))
        assertEquals("ı", KaikkiDataSource.normalizeExactKey("ı", "tr"))
        assertEquals("i", KaikkiDataSource.normalizeExactKey("I", "de"))
    }

    private fun dataSource() = KaikkiDataSource(
        object : KaikkiDatabaseSource {
            override fun open(sourceLanguageTag: String) = KaikkiIndexOpenResult.Opened(
                database = SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ),
                datasetVersion = KAIKKI_RELEASE_ID,
                expectedLanguageTag = sourceLanguageTag,
            )

            override fun activeIdentity(sourceLanguageTag: String) = sourceLanguageTag
            override fun hasAnyActivePack() = true
        },
    )

    private fun insert(
        database: SQLiteDatabase,
        id: String,
        order: Int,
        headword: String,
        payload: String,
    ) {
        database.execSQL(
            "INSERT INTO entries VALUES(?, ?, ?, ?, ?)",
            arrayOf<Any>(id, normalize(headword), headword, order, payload.encodeToByteArray()),
        )
    }

    private fun insertMorphology(
        database: SQLiteDatabase,
        form: String,
        lemma: String,
        sourceEntryId: String,
        order: Int,
    ) {
        database.execSQL(
            "INSERT INTO morphology_forms VALUES(?, ?, ?, ?, ?, ?, 0)",
            arrayOf<Any>(normalize(form), form, lemma, normalize(lemma), sourceEntryId, order),
        )
    }

    private fun normalize(value: String) = java.text.Normalizer
        .normalize(value, java.text.Normalizer.Form.NFC)
        .lowercase()

    private companion object {
        const val nounPayload =
            """{"w":"Wasser","p":"noun","n":["/ˈvasɐ/"],"f":[],"fc":2,"s":[{"o":0,"i":"sense-1","g":["water"],"e":["Das Wasser ist kalt."],"x":1,"d":"neuter"},{"o":1,"i":"sense-2","g":["body of water"],"e":[],"x":0,"d":null}]}"""
        const val namePayload =
            """{"w":"Wasser","p":"name","n":[],"f":[],"fc":0,"s":[{"o":0,"i":"sense-name","g":["surname"],"x":0,"d":null}]}"""
        const val cafePayload =
            """{"w":"Café","p":"noun","n":[],"f":[],"fc":0,"s":[{"o":0,"i":"sense-cafe","g":["coffee shop"],"x":0,"d":null}]}"""
    }
}
