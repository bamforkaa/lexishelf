package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.BackupEntryV6
import com.example.localvocabulary.backup.domain.BackupExampleV6
import com.example.localvocabulary.backup.domain.BackupSenseV6
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.VocabularyBackupV2
import com.example.localvocabulary.backup.domain.VocabularyBackupV8
import com.example.localvocabulary.vocabulary.domain.VocabularyExampleDraft
import java.util.UUID

// Legacy files did not carry child identity. Do not match by mutable text or list position.
internal fun VocabularyBackupV2.toCurrent() = VocabularyBackupV8(
    format, CURRENT_BACKUP_SCHEMA_VERSION, exportedAtEpochMillis, tags,
    entries.map { entry ->
        BackupEntryV6(
            stableId = entry.stableId, headword = entry.headword, languageTag = entry.languageTag,
            senses = entry.senses.map { sense ->
                BackupSenseV6(
                    stableId = UUID.randomUUID().toString(), meaning = sense.meaning,
                    partOfSpeech = sense.partOfSpeech,
                    examples = sense.examples.map(String::trim).filter(String::isNotEmpty).map {
                        BackupExampleV6(UUID.randomUUID().toString(), it)
                    },
                    provenance = sense.provenance, grammaticalGender = sense.grammaticalGender,
                )
            },
            notes = entry.notes, tagStableIds = entry.tagStableIds,
            createdAtEpochMillis = entry.createdAtEpochMillis,
            modifiedAtEpochMillis = entry.modifiedAtEpochMillis,
            reading = entry.reading, readingProvenance = entry.readingProvenance,
            wordbookStableIds = entry.wordbookStableIds, pronunciations = entry.pronunciations,
        )
    },
    wordbooks,
)

internal fun BackupExampleV6.toDraft() = VocabularyExampleDraft(
    text, stableId, meaning, origin, sourceTitle, sourceUrl, sourceLocator, capturedAt,
)

internal fun VocabularyExampleDraft.toBackupV6() = BackupExampleV6(
    requireNotNull(stableId), text, meaning, origin, sourceTitle, sourceUrl, sourceLocator, capturedAt,
)
