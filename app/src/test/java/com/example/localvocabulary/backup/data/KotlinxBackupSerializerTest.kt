package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupDecodeResult
import com.example.localvocabulary.backup.domain.BackupDictionaryProvenanceV2
import com.example.localvocabulary.backup.domain.BackupEntryV2
import com.example.localvocabulary.backup.domain.BackupImportedFieldV2
import com.example.localvocabulary.backup.domain.BackupReadError
import com.example.localvocabulary.backup.domain.BackupSenseV2
import com.example.localvocabulary.backup.domain.BackupTagV1
import com.example.localvocabulary.backup.domain.BackupWordbookV4
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
        val provenance = provenance(modified = true).copy(
            providerId = "kaikki",
            sourceEntryId = "enw-de-entry",
            sourceSenseId = "en-Wasser-de-noun-1",
            sourceName = "English Wiktionary via Kaikki/Wiktextract",
            importedFields = listOf(
                BackupImportedFieldV2.EXAMPLES,
                BackupImportedFieldV2.MEANING,
            ),
        )
        val backup = backup(
            entries = listOf(
                entry(
                    senses = listOf(
                        BackupSenseV2(
                            "water",
                            "noun",
                            listOf("Das Wasser ist kalt."),
                            provenance,
                        ),
                        BackupSenseV2("사용자 뜻", "", emptyList()),
                    ),
                ),
            ),
        )

        val decoded = serializer.decode(serializer.encode(backup)) as BackupDecodeResult.Success

        assertEquals(provenance, decoded.backup.document.entries.single().senses.first().provenance)
        assertEquals(
            listOf("Das Wasser ist kalt."),
            decoded.backup.document.entries.single().senses.first().examples,
        )
        assertNull(decoded.backup.document.entries.single().senses.last().provenance)
    }

    @Test
    fun `reading and reading provenance survive current schema round trip`() {
        val readingProvenance = provenance().copy(
            providerId = "jmdict",
            sourceEntryId = "1358280",
            sourceSenseId = "1358280:1",
            sourceName = "JMdict",
            datasetVersion = "2026-08-23",
            importedFields = listOf(BackupImportedFieldV2.READING),
        )
        val original = backup(
            entries = listOf(
                entry(
                    headword = "食べる",
                    languageTag = "ja",
                    reading = "たべる",
                    readingProvenance = readingProvenance,
                    senses = listOf(
                        BackupSenseV2("to eat", "Ichidan verb", emptyList()),
                    ),
                ),
            ),
        )

        val decoded = serializer.decode(serializer.encode(original)) as BackupDecodeResult.Success

        assertEquals("たべる", decoded.backup.document.entries.single().reading)
        assertEquals(readingProvenance, decoded.backup.document.entries.single().readingProvenance)
    }

    @Test
    fun `schema v2 imports with empty reading without changing old semantics`() {
        val v2Json =
            """
            {
              "format":"$BACKUP_FORMAT_ID","schemaVersion":2,"exportedAtEpochMillis":500,
              "tags":[],"entries":[{
                "stableId":"old-entry","headword":"word","languageTag":"en",
                "senses":[{"meaning":"meaning","partOfSpeech":"noun","examples":[],"provenance":null}],
                "notes":"note","tagStableIds":[],"createdAtEpochMillis":100,"modifiedAtEpochMillis":200
              }]
            }
            """.trimIndent()

        val decoded = serializer.decode(v2Json) as BackupDecodeResult.Success

        assertEquals(CURRENT_BACKUP_SCHEMA_VERSION, decoded.backup.document.schemaVersion)
        assertEquals("", decoded.backup.document.entries.single().reading)
    }

    @Test
    fun `mixed CC CEDICT Korean Basic Dictionary PanLex and user senses round trip`() {
        val ccCedict = provenance()
        val koreanBasic = provenance().copy(
            providerId = "korean-basic-dictionary",
            sourceEntryId = "100:먹다",
            sourceSenseId = "1",
            sourceName = "한국어기초사전 - 국립국어원 제공",
            sourceUrl = "https://krdict.korean.go.kr/",
            licenseName = "Creative Commons Attribution-ShareAlike 2.0 Korea",
            licenseUrl = "https://creativecommons.org/licenses/by-sa/2.0/kr/",
            datasetVersion = "2026-08-19",
        )
        val panLex = provenance().copy(
            providerId = "panlex",
            sourceEntryId = "ex:11->ex:22",
            sourceSenseId = "mn:33:src:44",
            sourceName = "PanLex",
            sourceUrl = "https://panlex.org/",
            licenseName = "CC0 1.0 Universal",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
            datasetVersion = "2019-09-01",
        )
        val backup = backup(
            entries = listOf(
                entry(
                    senses = listOf(
                        BackupSenseV2("hello", "", emptyList(), ccCedict),
                        BackupSenseV2("먹다", "동사", emptyList(), koreanBasic),
                        BackupSenseV2("물", "", emptyList(), panLex),
                        BackupSenseV2("내가 쓴 뜻", "", emptyList()),
                    ),
                ),
            ),
        )

        val decoded = serializer.decode(serializer.encode(backup)) as BackupDecodeResult.Success
        val senses = decoded.backup.document.entries.single().senses

        assertEquals(ccCedict, senses[0].provenance)
        assertEquals(koreanBasic, senses[1].provenance)
        assertEquals(panLex, senses[2].provenance)
        assertNull(senses[3].provenance)
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
    fun `wordbooks and tags remain distinct in current schema round trip`() {
        val document = VocabularyBackupV2(
            format = BACKUP_FORMAT_ID,
            schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
            exportedAtEpochMillis = 10,
            tags = listOf(BackupTagV1("tag-food", "음식")),
            wordbooks = listOf(BackupWordbookV4("wordbook-jlpt", "JLPT N2")),
            entries = listOf(
                entry(headword = "食べる", languageTag = "ja").copy(
                    tagStableIds = listOf("tag-food"),
                    wordbookStableIds = listOf("wordbook-jlpt"),
                ),
            ),
        )

        val decoded = serializer.decode(serializer.encode(document)) as BackupDecodeResult.Success

        assertEquals(listOf("음식"), decoded.backup.document.tags.map { it.name })
        assertEquals(listOf("JLPT N2"), decoded.backup.document.wordbooks.map { it.name })
        assertEquals(
            listOf("wordbook-jlpt"),
            decoded.backup.document.entries.single().wordbookStableIds,
        )
    }

    @Test
    fun `schema v3 imports with empty wordbooks`() {
        val oldJson = serializer.encode(backup(entries = listOf(entry(headword = "old"))))
            .replace("\"schemaVersion\": 4", "\"schemaVersion\": 3")
            .replace(",\n  \"wordbooks\": []", "")
            .replace(",\n      \"wordbookStableIds\": []", "")

        val decoded = serializer.decode(oldJson) as BackupDecodeResult.Success

        assertEquals(CURRENT_BACKUP_SCHEMA_VERSION, decoded.backup.document.schemaVersion)
        assertTrue(decoded.backup.document.wordbooks.isEmpty())
        assertTrue(decoded.backup.document.entries.single().wordbookStableIds.isEmpty())
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
        reading: String = "",
        readingProvenance: BackupDictionaryProvenanceV2? = null,
    ) = BackupEntryV2(
        stableId = "entry-1",
        headword = headword,
        languageTag = languageTag,
        senses = senses,
        notes = "note",
        tagStableIds = listOf("tag-shared"),
        createdAtEpochMillis = 100,
        modifiedAtEpochMillis = 200,
        reading = reading,
        readingProvenance = readingProvenance,
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
