package com.example.localvocabulary.vocabulary.domain

data class VocabularyEntry(
    val id: Long,
    val backupId: String,
    val headword: String,
    val languageTag: String,
    val senses: List<VocabularySense>,
    val notes: String,
    val tags: List<VocabularyTag>,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
)

data class VocabularySense(
    val id: Long,
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<ExampleSentence>,
)

data class ExampleSentence(
    val id: Long,
    val text: String,
)

data class VocabularyTag(
    val id: Long,
    val backupId: String,
    val name: String,
)

data class VocabularyEntryDraft(
    val id: Long? = null,
    val headword: String,
    val languageTag: String,
    val senses: List<VocabularySenseDraft>,
    val notes: String,
    val tagIds: Set<Long>,
)

data class VocabularySenseDraft(
    val meaning: String,
    val partOfSpeech: String,
    val examples: List<String>,
)
