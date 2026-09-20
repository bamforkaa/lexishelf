package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION

import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupDecodeResult
import com.example.localvocabulary.backup.domain.BackupEntryV6
import com.example.localvocabulary.backup.domain.BackupExampleV6
import com.example.localvocabulary.backup.domain.BackupSenseV6
import com.example.localvocabulary.backup.domain.VocabularyBackupV8
import com.example.localvocabulary.vocabulary.domain.ExampleOrigin
import org.junit.Assert.*
import org.junit.Test

class ContextBackupTest {
    private val serializer = KotlinxBackupSerializer()

    @Test
    fun `all five legacy versions become unknown contexts without invented metadata`() {
        for (version in 1..5) {
            val json = """{
                "format":"$BACKUP_FORMAT_ID","schemaVersion":$version,"exportedAtEpochMillis":20,
                "tags":[],"entries":[{
                    "stableId":"entry","headword":"look forward to","languageTag":"en",
                    "senses":[{"meaning":"기대하다","partOfSpeech":"","examples":["I look forward to it.","Next time!"]}],
                    "notes":"","tagStableIds":[],"createdAtEpochMillis":10,"modifiedAtEpochMillis":20
                }]
            }"""
            val result = serializer.decode(json) as BackupDecodeResult.Success
            assertEquals(version, result.backup.sourceSchemaVersion)
            val sense = result.backup.document.entries.single().senses.single()
            assertTrue(sense.stableId.isNotBlank())
            assertEquals(2, sense.examples.map { it.stableId }.distinct().size)
            sense.examples.forEach {
                assertEquals(ExampleOrigin.UNKNOWN, it.origin)
                assertEquals("", it.meaning)
                assertNull(it.sourceTitle)
                assertNull(it.sourceUrl)
                assertNull(it.sourceLocator)
                assertNull(it.capturedAt)
            }
        }
    }

    @Test
    fun `v6 preserves identity order long expression and every context field`() {
        val original = backup()
        val decoded = serializer.decode(serializer.encode(original)) as BackupDecodeResult.Success
        assertEquals(original, decoded.backup.document)
        val again = serializer.decode(serializer.encode(decoded.backup.document)) as BackupDecodeResult.Success
        assertEquals(original, again.backup.document)
    }

    @Test
    fun `duplicate child identities and malformed metadata are rejected before import`() {
        val original = backup()
        val entry = original.entries.single()
        val sense = entry.senses.single()
        val example = sense.examples.first()
        for (badSense in listOf(
            sense.copy(examples = listOf(example, example.copy(text = "different"))),
            sense.copy(examples = listOf(example.copy(capturedAt = -1))),
            sense.copy(examples = listOf(example.copy(stableId = ""))),
            sense.copy(examples = listOf(example.copy(text = " "))),
        )) {
            assertTrue(serializer.decode(serializer.encode(original.copy(
                entries = listOf(entry.copy(senses = listOf(badSense))),
            ))) is BackupDecodeResult.Failure)
        }
        assertTrue(serializer.decode(serializer.encode(original.copy(
            entries = listOf(entry.copy(senses = listOf(sense, sense))),
        ))) is BackupDecodeResult.Failure)
        assertTrue(serializer.decode(serializer.encode(original).replace("CAPTURED", "INVENTED")) is BackupDecodeResult.Failure)
    }

    private fun backup() = VocabularyBackupV8(
        format = BACKUP_FORMAT_ID, schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION, exportedAtEpochMillis = 20,
        tags = emptyList(),
        entries = listOf(BackupEntryV6(
            stableId = "entry", headword = "I am looking forward to working with you on this project.",
            languageTag = "en", notes = "사용자 메모", tagStableIds = emptyList(),
            createdAtEpochMillis = 10, modifiedAtEpochMillis = 20,
            senses = listOf(BackupSenseV6(
                stableId = "sense", meaning = "함께 일하기를 기대하다", partOfSpeech = "",
                examples = listOf(
                    BackupExampleV6("example-1", "I look forward to it.", "기대됩니다.",
                        ExampleOrigin.CAPTURED, "Podcast", "https://example.com/episode", "12:35", 15),
                    BackupExampleV6("example-2", "I look forward to our meeting.", origin = ExampleOrigin.USER),
                ),
            )),
        )),
    )
}
