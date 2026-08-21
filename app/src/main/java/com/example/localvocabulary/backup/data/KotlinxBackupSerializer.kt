package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupDecodeResult
import com.example.localvocabulary.backup.domain.BackupEntryV1
import com.example.localvocabulary.backup.domain.BackupReadError
import com.example.localvocabulary.backup.domain.BackupSenseV1
import com.example.localvocabulary.backup.domain.BackupSerializer
import com.example.localvocabulary.backup.domain.BackupTagV1
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.ValidatedBackup
import com.example.localvocabulary.backup.domain.VocabularyBackupV1
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import com.example.localvocabulary.vocabulary.domain.VocabularySenseDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationResult
import com.example.localvocabulary.vocabulary.domain.normalizeTagName
import javax.inject.Inject
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

class KotlinxBackupSerializer @Inject constructor() : BackupSerializer {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    override fun encode(backup: VocabularyBackupV1): String = json.encodeToString(backup)

    override fun decode(json: String): BackupDecodeResult {
        val root = try {
            this.json.parseToJsonElement(json) as? JsonObject
                ?: return BackupDecodeResult.Failure(BackupReadError.MalformedJson)
        } catch (_: SerializationException) {
            return BackupDecodeResult.Failure(BackupReadError.MalformedJson)
        }

        val versionElement = root["schemaVersion"]
            ?: return BackupDecodeResult.Failure(BackupReadError.MissingRequiredField)
        val version = (versionElement as? JsonPrimitive)?.intOrNull
            ?: return invalid("schemaVersion", "정수여야 합니다.")
        if (version != CURRENT_BACKUP_SCHEMA_VERSION) {
            return BackupDecodeResult.Failure(BackupReadError.UnsupportedSchemaVersion(version))
        }

        val document = try {
            this.json.decodeFromJsonElement<VocabularyBackupV1>(root)
        } catch (_: MissingFieldException) {
            return BackupDecodeResult.Failure(BackupReadError.MissingRequiredField)
        } catch (_: SerializationException) {
            return BackupDecodeResult.Failure(BackupReadError.MalformedJson)
        }
        return validate(document)
    }

    private fun validate(document: VocabularyBackupV1): BackupDecodeResult {
        if (document.format != BACKUP_FORMAT_ID) {
            return invalid("format", "지원하는 vocabulary backup 파일이 아닙니다.")
        }
        if (document.exportedAtEpochMillis < 0) {
            return invalid("exportedAtEpochMillis", "음수일 수 없습니다.")
        }

        val tagIds = mutableSetOf<String>()
        val normalizedTagIdentities = mutableSetOf<String>()
        val normalizedTags = document.tags.mapIndexed { index, tag ->
            if (!isValidStableId(tag.stableId)) {
                return invalid("tags[$index].stableId", "유효한 stable ID가 아닙니다.")
            }
            if (!tagIds.add(tag.stableId)) {
                return invalid("tags[$index].stableId", "stable ID가 중복됩니다.")
            }
            val normalized = normalizeTagName(tag.name)
                ?: return invalid("tags[$index].name", "태그 이름이 비어 있습니다.")
            if (!normalizedTagIdentities.add(normalized.identity)) {
                return invalid("tags[$index].name", "정규화한 태그 이름이 중복됩니다.")
            }
            BackupTagV1(tag.stableId, normalized.displayName)
        }

        val entryIds = mutableSetOf<String>()
        val normalizedEntries = document.entries.mapIndexed { index, entry ->
            if (!isValidStableId(entry.stableId)) {
                return invalid("entries[$index].stableId", "유효한 stable ID가 아닙니다.")
            }
            if (!entryIds.add(entry.stableId)) {
                return invalid("entries[$index].stableId", "stable ID가 중복됩니다.")
            }
            if (entry.createdAtEpochMillis < 0 || entry.modifiedAtEpochMillis < entry.createdAtEpochMillis) {
                return invalid("entries[$index].timestamps", "생성/수정 시간이 올바르지 않습니다.")
            }
            if (entry.tagStableIds.size != entry.tagStableIds.distinct().size) {
                return invalid("entries[$index].tagStableIds", "태그 참조가 중복됩니다.")
            }
            val missingTag = entry.tagStableIds.firstOrNull { it !in tagIds }
            if (missingTag != null) {
                return invalid("entries[$index].tagStableIds", "존재하지 않는 태그를 참조합니다.")
            }

            val validation = VocabularyEntryValidator.validate(
                VocabularyEntryDraft(
                    headword = entry.headword,
                    languageTag = entry.languageTag,
                    senses = entry.senses.map { sense ->
                        VocabularySenseDraft(sense.meaning, sense.partOfSpeech, sense.examples)
                    },
                    notes = entry.notes,
                    tagIds = emptySet(),
                ),
            )
            val draft = when (validation) {
                is VocabularyValidationResult.Valid -> validation.draft
                is VocabularyValidationResult.Invalid -> {
                    return invalid("entries[$index]", validation.error.toString())
                }
            }
            BackupEntryV1(
                stableId = entry.stableId,
                headword = draft.headword,
                languageTag = draft.languageTag,
                senses = draft.senses.map { sense ->
                    BackupSenseV1(sense.meaning, sense.partOfSpeech, sense.examples)
                },
                notes = draft.notes,
                tagStableIds = entry.tagStableIds,
                createdAtEpochMillis = entry.createdAtEpochMillis,
                modifiedAtEpochMillis = entry.modifiedAtEpochMillis,
            )
        }

        return BackupDecodeResult.Success(
            ValidatedBackup(
                document.copy(tags = normalizedTags, entries = normalizedEntries),
            ),
        )
    }

    private fun invalid(path: String, reason: String) =
        BackupDecodeResult.Failure(BackupReadError.InvalidData(path, reason))

    private fun isValidStableId(value: String): Boolean = STABLE_ID.matches(value)

    private companion object {
        val STABLE_ID = Regex("[A-Za-z0-9._:-]{1,128}")
    }
}
