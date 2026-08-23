package com.example.localvocabulary.dictionary.provider.koreanbasic

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KoreanBasicDictionaryDataSourceTest {
    private lateinit var databaseFile: File

    @Before
    fun createFixture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseFile = File(context.cacheDir, "korean-basic-dictionary-fixture.db")
        databaseFile.delete()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL("PRAGMA user_version = 1")
            database.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            database.execSQL(
                "INSERT INTO metadata(key, value) VALUES('release_id', ?)",
                arrayOf(KOREAN_BASIC_DICTIONARY_RELEASE_ID),
            )
            database.execSQL(
                """
                CREATE TABLE entries(
                    entry_pk INTEGER PRIMARY KEY,
                    official_entry_id TEXT NOT NULL,
                    headword TEXT NOT NULL,
                    normalized_headword TEXT NOT NULL,
                    part_of_speech TEXT
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE senses(
                    sense_pk INTEGER PRIMARY KEY,
                    entry_pk INTEGER NOT NULL,
                    official_sense_id TEXT NOT NULL,
                    sense_order INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE translations(
                    translation_pk INTEGER PRIMARY KEY,
                    sense_pk INTEGER NOT NULL,
                    language_tag TEXT NOT NULL,
                    translation_order INTEGER NOT NULL,
                    display_term TEXT NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE reverse_keys(
                    language_tag TEXT NOT NULL,
                    normalized_term TEXT NOT NULL,
                    translation_pk INTEGER NOT NULL,
                    PRIMARY KEY(language_tag, normalized_term, translation_pk)
                )
                """.trimIndent(),
            )
            database.execSQL(
                "INSERT INTO entries VALUES(1, '100', '먹다', '먹다', '동사')",
            )
            database.execSQL("INSERT INTO senses VALUES(1, 1, '7', 0)")
            database.execSQL("INSERT INTO translations VALUES(1, 1, 'en', 0, 'eat; consume')")
            database.execSQL("INSERT INTO reverse_keys VALUES('en', 'eat', 1)")
        }
    }

    @After
    fun deleteFixture() {
        databaseFile.delete()
    }

    @Test
    fun forwardAndReverseExactQueriesReadTheSeparateIndex() = runBlocking {
        val source = KoreanBasicDictionaryDataSource(
            KoreanBasicDictionaryDatabaseSource {
                KoreanBasicDictionaryIndexOpenResult.Opened(
                    SQLiteDatabase.openDatabase(
                        databaseFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                    ),
                )
            },
        )

        val forward = source.exactLookup(" 먹다 ", "ko", "en")
            as KoreanBasicDictionaryLookupResult.Matches
        val reverse = source.exactLookup(" EAT ", "en", "ko")
            as KoreanBasicDictionaryLookupResult.Matches

        assertEquals("eat; consume", forward.records.single().translationTerm)
        assertEquals("먹다", reverse.records.single().koreanHeadword)
        assertEquals("100", reverse.records.single().officialEntryId)
        assertEquals("7", reverse.records.single().officialSenseId)
    }

    @Test
    fun malformedIndexVersionIsRejectedWithoutFallbackData() = runBlocking {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { it.execSQL("PRAGMA user_version = 99") }
        val source = KoreanBasicDictionaryDataSource(
            KoreanBasicDictionaryDatabaseSource {
                KoreanBasicDictionaryIndexOpenResult.Opened(
                    SQLiteDatabase.openDatabase(
                        databaseFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                    ),
                )
            },
        )

        val result = source.exactLookup("eat", "en", "ko")

        assertTrue(result is KoreanBasicDictionaryLookupResult.MalformedDataset)
    }
}
