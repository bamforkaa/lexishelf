package com.example.localvocabulary.backup.data

import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupDecodeResult
import com.example.localvocabulary.backup.domain.BackupDictionaryProvenanceV2
import com.example.localvocabulary.backup.domain.BackupEntryV2
import com.example.localvocabulary.backup.domain.BackupReadError
import com.example.localvocabulary.backup.domain.BackupSenseV2
import com.example.localvocabulary.backup.domain.BackupSerializer
import com.example.localvocabulary.backup.domain.BackupTagV1
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.ValidatedBackup
import com.example.localvocabulary.backup.domain.VocabularyBackupV1
import com.example.localvocabulary.backup.domain.VocabularyBackupV2
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

    override fun encode(backup: VocabularyBackupV2): String = json.encodeToString(backup)

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

        val document = when (version) {
            1 -> decodeDocument<VocabularyBackupV1>(root)?.toCurrent()
            2 -> decodeDocument<VocabularyBackupV2>(root)?.copy(
                schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
            )
            CURRENT_BACKUP_SCHEMA_VERSION -> decodeDocument<VocabularyBackupV2>(root)
            else -> return BackupDecodeResult.Failure(
                BackupReadError.UnsupportedSchemaVersion(version),
            )
        } ?: return decodeFailure(root, version)

        return validate(document)
    }

    private inline fun <reified T> decodeDocument(root: JsonObject): T? = try {
        json.decodeFromJsonElement<T>(root)
    } catch (_: MissingFieldException) {
        null
    } catch (_: SerializationException) {
        null
    }

    private fun decodeFailure(root: JsonObject, version: Int): BackupDecodeResult = try {
        when (version) {
            1 -> json.decodeFromJsonElement<VocabularyBackupV1>(root)
            else -> json.decodeFromJsonElement<VocabularyBackupV2>(root)
        }
        BackupDecodeResult.Failure(BackupReadError.MalformedJson)
    } catch (_: MissingFieldException) {
        BackupDecodeResult.Failure(BackupReadError.MissingRequiredField)
    } catch (_: SerializationException) {
        BackupDecodeResult.Failure(BackupReadError.MalformedJson)
    }

    private fun validate(document: VocabularyBackupV2): BackupDecodeResult {
        if (document.format != BACKUP_FORMAT_ID) {
            return invalid("format", "지원하는 vocabulary backup 파일이 아닙니다.")
        }
        if (document.schemaVersion != CURRENT_BACKUP_SCHEMA_VERSION) {
            return invalid("schemaVersion", "현재 backup schema와 일치하지 않습니다.")
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
        val normalizedEntries = document.entries.mapIndexed { entryIndex, entry ->
            if (!isValidStableId(entry.stableId)) {
                return invalid("entries[$entryIndex].stableId", "유효한 stable ID가 아닙니다.")
            }
            if (!entryIds.add(entry.stableId)) {
                return invalid("entries[$entryIndex].stableId", "stable ID가 중복됩니다.")
            }
            if (
                entry.createdAtEpochMillis < 0 ||
                entry.modifiedAtEpochMillis < entry.createdAtEpochMillis
            ) {
                return invalid("entries[$entryIndex].timestamps", "생성/수정 시간이 올바르지 않습니다.")
            }
            if (entry.tagStableIds.size != entry.tagStableIds.distinct().size) {
                return invalid("entries[$entryIndex].tagStableIds", "태그 참조가 중복됩니다.")
            }
            if (entry.tagStableIds.any { it !in tagIds }) {
                return invalid("entries[$entryIndex].tagStableIds", "존재하지 않는 태그를 참조합니다.")
            }

            val senseDrafts = entry.senses.mapIndexed { senseIndex, sense ->
                val provenance = sense.provenance?.let {
                    validateProvenance(it)
                        ?: return invalid(
                            "entries[$entryIndex].senses[$senseIndex].provenance",
                            "provenance 값이 올바르지 않습니다.",
                        )
                }
                VocabularySenseDraft(
                    meaning = sense.meaning,
                    partOfSpeech = sense.partOfSpeech,
                    examples = sense.examples,
                    provenance = provenance,
                )
            }
            val readingProvenance = entry.readingProvenance?.let {
                validateProvenance(it)
                    ?: return invalid(
                        "entries[$entryIndex].readingProvenance",
                        "Reading provenance is invalid.",
                    )
            }
            if (readingProvenance != null && entry.reading.isBlank()) {
                return invalid(
                    "entries[$entryIndex].readingProvenance",
                    "Reading provenance requires a persisted reading.",
                )
            }
            if (
                readingProvenance != null &&
                readingProvenance.importedFields != setOf(
                    com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField.READING,
                )
            ) {
                return invalid(
                    "entries[$entryIndex].readingProvenance.importedFields",
                    "Entry reading provenance may describe only READING.",
                )
            }
            val validation = VocabularyEntryValidator.validate(
                VocabularyEntryDraft(
                    headword = entry.headword,
                    languageTag = entry.languageTag,
                    senses = senseDrafts,
                    notes = entry.notes,
                    tagIds = emptySet(),
                    reading = entry.reading,
                    readingProvenance = readingProvenance,
                ),
            )
            val draft = when (validation) {
                is VocabularyValidationResult.Valid -> validation.draft
                is VocabularyValidationResult.Invalid -> {
                    return invalid("entries[$entryIndex]", validation.error.toString())
                }
            }
            BackupEntryV2(
                stableId = entry.stableId,
                headword = draft.headword,
                languageTag = draft.languageTag,
                senses = draft.senses.map { sense ->
                    BackupSenseV2(
                        meaning = sense.meaning,
                        partOfSpeech = sense.partOfSpeech,
                        examples = sense.examples,
                        provenance = sense.provenance?.toBackupV2(),
                    )
                },
                notes = draft.notes,
                tagStableIds = entry.tagStableIds,
                createdAtEpochMillis = entry.createdAtEpochMillis,
                modifiedAtEpochMillis = entry.modifiedAtEpochMillis,
                reading = draft.reading,
                readingProvenance = draft.readingProvenance?.toBackupV2(),
            )
        }

        return BackupDecodeResult.Success(
            ValidatedBackup(
                document.copy(tags = normalizedTags, entries = normalizedEntries),
            ),
        )
    }

    private fun validateProvenance(provenance: BackupDictionaryProvenanceV2) = runCatching {
        if (provenance.importedFields.size != provenance.importedFields.distinct().size) {
            error("Imported fields contain duplicates")
        }
        provenance.toDomain()
    }.getOrNull()

    private fun VocabularyBackupV1.toCurrent(): VocabularyBackupV2 = VocabularyBackupV2(
        format = format,
        schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
        exportedAtEpochMillis = exportedAtEpochMillis,
        tags = tags,
        entries = entries.map { entry ->
            BackupEntryV2(
                stableId = entry.stableId,
                headword = entry.headword,
                languageTag = entry.languageTag,
                senses = entry.senses.map { sense ->
                    BackupSenseV2(
                        meaning = sense.meaning,
                        partOfSpeech = sense.partOfSpeech,
                        examples = sense.examples,
                    )
                },
                notes = entry.notes,
                tagStableIds = entry.tagStableIds,
                createdAtEpochMillis = entry.createdAtEpochMillis,
                modifiedAtEpochMillis = entry.modifiedAtEpochMillis,
            )
        },
    )

    private fun invalid(path: String, reason: String) =
        BackupDecodeResult.Failure(BackupReadError.InvalidData(path, reason))

    private fun isValidStableId(value: String): Boolean = STABLE_ID.matches(value)

    private companion object {
        val STABLE_ID = Regex("[A-Za-z0-9._:-]{1,128}")
    }
}
