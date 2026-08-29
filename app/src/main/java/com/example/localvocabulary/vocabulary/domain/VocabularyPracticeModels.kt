package com.example.localvocabulary.vocabulary.domain

/** Compact read model used to prepare an in-memory writing-practice session. */
data class VocabularyPracticeItem(
    val entryId: Long,
    val headword: String,
    val languageTag: String,
    val representativeMeaning: String,
    val reading: String,
    val pronunciation: String,
    val partOfSpeech: String,
    val grammaticalGender: String,
    val example: String,
)

data class VocabularyPracticeFilter(
    val languageTag: String? = null,
    val wordbookId: Long? = null,
    val tagId: Long? = null,
)
