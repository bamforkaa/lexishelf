package com.example.localvocabulary.dictionary.provider.jmdict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JmDictDataSourceTest {
    private lateinit var databaseFile: File

    @Before
    fun createFixture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseFile = File(context.cacheDir, "jmdict-fixture.db")
        databaseFile.delete()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL("PRAGMA user_version = 1")
            database.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            database.execSQL("INSERT INTO metadata VALUES('release_id', ?)", arrayOf(JMDICT_RELEASE_ID))
            database.execSQL(
                "CREATE TABLE entries(ent_seq INTEGER PRIMARY KEY, entry_order INTEGER, payload BLOB)",
            )
            database.execSQL(
                """
                CREATE TABLE lookup_keys(
                    normalized_key TEXT, ent_seq INTEGER, match_kind INTEGER,
                    element_order INTEGER, has_priority INTEGER
                )
                """.trimIndent(),
            )
            insert(database, 100, 0, entry("100", "食べる", "たべる", "to eat"), priority = 0)
            insert(database, 200, 1, entry("200", "食べる", "くべる", "to feed"), priority = 1)
        }
    }

    @After
    fun deleteFixture() { databaseFile.delete() }

    @Test
    fun exactKanjiAndKanaLookupPreserveHomographsAndOfficialPriorityOrdering() = runBlocking {
        val source = dataSource()

        val kanji = source.exactLookup(" 食べる ", 20) as JmDictLookupResult.Matches
        val kana = source.exactLookup("たべる", 20) as JmDictLookupResult.Matches

        assertEquals(listOf("200", "100"), kanji.records.map { it.entry.entrySequence })
        assertEquals(JmDictMatchKind.WRITTEN_FORM, kanji.records.first().matchKind)
        assertEquals("100", kana.records.single().entry.entrySequence)
        assertEquals(JmDictMatchKind.READING, kana.records.single().matchKind)
        assertTrue(source.exactLookup("missing", 20) is JmDictLookupResult.NoMatch)
    }

    @Test
    fun invalidSchemaIsRejected() = runBlocking {
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("PRAGMA user_version = 99")
        }
        assertTrue(dataSource().exactLookup("食べる", 20) is JmDictLookupResult.MalformedDataset)
    }

    private fun dataSource() = JmDictDataSource(
        JmDictDatabaseSource {
            JmDictIndexOpenResult.Opened(
                SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY),
            )
        },
    )

    private fun insert(
        database: SQLiteDatabase,
        sequence: Long,
        order: Int,
        entry: JmDictEntryRecord,
        priority: Int,
    ) {
        val payload = Json.encodeToString(entry).encodeToByteArray()
        database.execSQL("INSERT INTO entries VALUES(?, ?, ?)", arrayOf(sequence, order, payload))
        database.execSQL(
            "INSERT INTO lookup_keys VALUES(?, ?, 0, 0, ?)",
            arrayOf<Any>("食べる", sequence, priority),
        )
        database.execSQL(
            "INSERT INTO lookup_keys VALUES(?, ?, 1, 0, ?)",
            arrayOf<Any>(entry.readings.single().text, sequence, priority),
        )
    }

    private fun entry(sequence: String, writing: String, reading: String, gloss: String) =
        JmDictEntryRecord(
            entrySequence = sequence,
            writtenForms = listOf(JmDictWrittenForm(writing)),
            readings = listOf(JmDictReading(reading)),
            senses = listOf(JmDictSense(0, glosses = listOf(JmDictGloss(gloss)))),
        )
}
