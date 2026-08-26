package com.example.localvocabulary.vocabulary.domain

import java.util.Locale

data class NormalizedWordbookName(
    val displayName: String,
    val identity: String,
)

fun normalizeWordbookName(rawName: String): NormalizedWordbookName? {
    val displayName = rawName.trim().replace(WORD_BOOK_WHITESPACE, " ")
    if (displayName.isEmpty()) return null
    return NormalizedWordbookName(
        displayName = displayName,
        identity = displayName.lowercase(Locale.ROOT),
    )
}

private val WORD_BOOK_WHITESPACE = Regex("\\s+")
