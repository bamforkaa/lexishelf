package com.example.localvocabulary.vocabulary.domain

import java.util.Locale

data class NormalizedTagName(
    val displayName: String,
    val identity: String,
)

fun normalizeTagName(rawName: String): NormalizedTagName? {
    val displayName = rawName.trim().replace(TAG_WHITESPACE, " ")
    if (displayName.isEmpty()) return null
    return NormalizedTagName(
        displayName = displayName,
        identity = displayName.lowercase(Locale.ROOT),
    )
}

private val TAG_WHITESPACE = Regex("\\s+")
