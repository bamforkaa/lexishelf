package com.example.localvocabulary.feature.wordeditor

import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Only editable user data. Search results, provider objects and transient selection never enter saved state. */
@Serializable
internal data class EditorDraftSnapshot(
    val entryId: Long?,
    val headword: String,
    val languageTag: String,
    val reading: String,
    val readingProvenance: DictionaryProvenance?,
    val pronunciations: List<EditablePronunciation>,
    val senses: List<EditableSense>,
    val notes: String,
    val tagIds: Set<Long>,
    val wordbookIds: Set<Long>,
    val version: Int = 1,
) {
    fun restoreInto(state: WordEditorUiState) = state.copy(
        isLoading = false,
        headword = headword,
        languageTag = languageTag,
        reading = reading,
        readingProvenance = readingProvenance,
        isReadingUserEdited = true,
        pronunciations = pronunciations.map { it.copy(isUserEdited = true) },
        senses = senses,
        notes = notes,
        selectedTagIds = tagIds,
        selectedWordbookIds = wordbookIds,
    )

    fun nextLocalKey(): Long = (senses.map { it.key } +
        senses.flatMap { it.examples }.map { it.key } + pronunciations.map { it.key } + -10L)
        .min() - 1L

    fun encodeOrNull(): String? = json.encodeToString(this).takeIf { it.length <= MAX_CHARACTERS }

    companion object {
        const val KEY = "editor_user_draft_v1"
        // Bound the UTF-16 String well below Android's shared saved-state transaction limit.
        private const val MAX_CHARACTERS = 64 * 1024
        private val json = Json { encodeDefaults = false }

        fun from(state: WordEditorUiState) = EditorDraftSnapshot(
            state.entryId, state.headword, state.languageTag, state.reading, state.readingProvenance,
            state.pronunciations.map {
                it.copy(suggestionKeys = emptySet(), sessionSuggestionKeys = emptySet(), importedValue = null)
            },
            state.senses.map { it.copy(importSuggestionKey = null, sessionContribution = null) },
            state.notes, state.selectedTagIds, state.selectedWordbookIds,
        )

        fun decode(value: String?): EditorDraftSnapshot? {
            if (value == null || value.length > MAX_CHARACTERS) return null
            return try {
                json.decodeFromString<EditorDraftSnapshot>(value).takeIf { it.version == 1 }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
