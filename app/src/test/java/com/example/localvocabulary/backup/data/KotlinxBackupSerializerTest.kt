package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupDecodeResult
import com.example.localvocabulary.backup.domain.BackupEntryV1
import com.example.localvocabulary.backup.domain.BackupReadError
import com.example.localvocabulary.backup.domain.BackupSenseV1
import com.example.localvocabulary.backup.domain.BackupTagV1
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.VocabularyBackupV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinxBackupSerializerTest {
    private val serializer = KotlinxBackupSerializer()

    @Test
    fun `Unicode vocabulary survives JSON serialization and validation`() {
        val backup = backup(
            entries = listOf(
                entry(
                    headword = "辞書 / 詞典 / مُعْجَم / café",
                    languageTag = "ja",
                    meaning = "日本語・简体中文・العربية・français",
                    examples = listOf("彼は辞書を引いた。", "他查了词典。", "فَتَحَ الْمُعْجَمَ.", "un café"),
                ),
            ),
        )

        val decoded = serializer.decode(serializer.encode(backup)) as BackupDecodeResult.Success

        assertEquals(backup, decoded.backup.document)
    }

    @Test
    fun `empty backup round trips`() {
        val backup = backup(entries = emptyList(), tags = emptyList())

        val decoded = serializer.decode(serializer.encode(backup)) as BackupDecodeResult.Success

        assertEquals(backup, decoded.backup.document)
    }

    @Test
    fun `malformed JSON is rejected`() {
        assertEquals(
            BackupDecodeResult.Failure(BackupReadError.MalformedJson),
            serializer.decode("{not-json"),
        )
    }

    @Test
    fun `unknown schema version is rejected before decoding`() {
        val result = serializer.decode(
            """{"format":"$BACKUP_FORMAT_ID","schemaVersion":99}""",
        )

        assertEquals(
            BackupDecodeResult.Failure(BackupReadError.UnsupportedSchemaVersion(99)),
            result,
        )
    }

    @Test
    fun `missing required field is rejected`() {
        val result = serializer.decode(
            """{"format":"$BACKUP_FORMAT_ID","schemaVersion":1,"exportedAtEpochMillis":0,"tags":[]}""",
        )

        assertEquals(
            BackupDecodeResult.Failure(BackupReadError.MissingRequiredField),
            result,
        )
    }

    @Test
    fun `invalid BCP 47 tag is rejected`() {
        val json = serializer.encode(
            backup(entries = listOf(entry(languageTag = "en_US"))),
        )

        val result = serializer.decode(json)

        assertTrue(result is BackupDecodeResult.Failure)
        assertTrue((result as BackupDecodeResult.Failure).error is BackupReadError.InvalidData)
    }

    @Test
    fun `blank required vocabulary text is rejected`() {
        val json = serializer.encode(
            backup(entries = listOf(entry(headword = "  "))),
        )

        assertTrue(serializer.decode(json) is BackupDecodeResult.Failure)
    }

    @Test
    fun `export schema contains no credential or application settings fields`() {
        val json = serializer.encode(backup(entries = listOf(entry())))

        assertFalse(json.contains("credential", ignoreCase = true))
        assertFalse(json.contains("apiKey", ignoreCase = true))
        assertFalse(json.contains("settings", ignoreCase = true))
        assertFalse(json.contains("defaultLanguageTag", ignoreCase = true))
    }

    private fun backup(
        entries: List<BackupEntryV1>,
        tags: List<BackupTagV1> = listOf(BackupTagV1("tag-shared", "Shared")),
    ) = VocabularyBackupV1(
        format = BACKUP_FORMAT_ID,
        schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
        exportedAtEpochMillis = 500,
        tags = tags,
        entries = entries,
    )

    private fun entry(
        headword: String = "word",
        languageTag: String = "en",
        meaning: String = "meaning",
        examples: List<String> = listOf("example"),
    ) = BackupEntryV1(
        stableId = "entry-1",
        headword = headword,
        languageTag = languageTag,
        senses = listOf(BackupSenseV1(meaning, "noun", examples)),
        notes = "note",
        tagStableIds = listOf("tag-shared"),
        createdAtEpochMillis = 100,
        modifiedAtEpochMillis = 200,
    )
}
