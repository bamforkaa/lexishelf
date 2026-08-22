package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupDecodeResult
import com.example.localvocabulary.backup.domain.BackupDictionaryProvenanceV2
import com.example.localvocabulary.backup.domain.BackupEntryV2
import com.example.localvocabulary.backup.domain.BackupImportedFieldV2
import com.example.localvocabulary.backup.domain.BackupReadError
import com.example.localvocabulary.backup.domain.BackupSenseV2
import com.example.localvocabulary.backup.domain.BackupTagV1
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.VocabularyBackupV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `provider provenance survives current schema round trip`() {
        val provenance = provenance(modified = true)
        val backup = backup(
            entries = listOf(
                entry(
                    senses = listOf(
                        BackupSenseV2("hello; hi", "", emptyList(), provenance),
                        BackupSenseV2("사용자 뜻", "", emptyList()),
                    ),
                ),
            ),
        )

        val decoded = serializer.decode(serializer.encode(backup)) as BackupDecodeResult.Success

        assertEquals(provenance, decoded.backup.document.entries.single().senses.first().provenance)
        assertNull(decoded.backup.document.entries.single().senses.last().provenance)
    }

    @Test
    fun `schema v1 imports as current schema without provenance`() {
        val v1Json =
            """
            {
              "format": "$BACKUP_FORMAT_ID",
              "schemaVersion": 1,
              "exportedAtEpochMillis": 500,
              "tags": [{"stableId":"tag-shared","name":"Shared"}],
              "entries": [{
                "stableId": "entry-1",
                "headword": "word",
                "languageTag": "en",
                "senses": [{"meaning":"meaning","partOfSpeech":"noun","examples":["example"]}],
                "notes": "note",
                "tagStableIds": ["tag-shared"],
                "createdAtEpochMillis": 100,
                "modifiedAtEpochMillis": 200
              }]
            }
            """.trimIndent()

        val decoded = serializer.decode(v1Json) as BackupDecodeResult.Success

        assertEquals(CURRENT_BACKUP_SCHEMA_VERSION, decoded.backup.document.schemaVersion)
        assertNull(decoded.backup.document.entries.single().senses.single().provenance)
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
            """{"format":"$BACKUP_FORMAT_ID","schemaVersion":2,"exportedAtEpochMillis":0,"tags":[]}""",
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

        assertTrue(serializer.decode(json) is BackupDecodeResult.Failure)
    }

    @Test
    fun `blank required vocabulary text is rejected`() {
        val json = serializer.encode(
            backup(entries = listOf(entry(headword = "  "))),
        )

        assertTrue(serializer.decode(json) is BackupDecodeResult.Failure)
    }

    @Test
    fun `invalid provenance is rejected`() {
        val json = serializer.encode(
            backup(
                entries = listOf(
                    entry(
                        senses = listOf(
                            BackupSenseV2(
                                "meaning",
                                "",
                                emptyList(),
                                provenance().copy(importedFields = emptyList()),
                            ),
                        ),
                    ),
                ),
            ),
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
        entries: List<BackupEntryV2>,
        tags: List<BackupTagV1> = listOf(BackupTagV1("tag-shared", "Shared")),
    ) = VocabularyBackupV2(
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
        senses: List<BackupSenseV2> = listOf(BackupSenseV2(meaning, "noun", examples)),
    ) = BackupEntryV2(
        stableId = "entry-1",
        headword = headword,
        languageTag = languageTag,
        senses = senses,
        notes = "note",
        tagStableIds = listOf("tag-shared"),
        createdAtEpochMillis = 100,
        modifiedAtEpochMillis = 200,
    )

    private fun provenance(modified: Boolean = false) = BackupDictionaryProvenanceV2(
        providerId = "cc-cedict",
        sourceEntryId = "你好|你好|ni3 hao3",
        sourceSenseId = "0",
        sourceName = "CC-CEDICT",
        sourceUrl = "https://cc-cedict.org/editor/editor.php?handler=Download",
        licenseName = "Creative Commons Attribution-ShareAlike 4.0 International",
        licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
        datasetVersion = "2026-08-22T08:27:42Z",
        importedFields = listOf(BackupImportedFieldV2.MEANING),
        importedAtEpochMillis = 150,
        modifiedAfterImport = modified,
    )
}
