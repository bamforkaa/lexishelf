package com.example.localvocabulary.handwriting.domain

/**
 * The editor currently stores headword text without a persisted cursor selection, so handwriting
 * candidates are appended. This never silently replaces existing user text.
 */
fun appendHandwritingCandidate(headword: String, candidate: String): String =
    if (candidate.isBlank()) headword else headword + candidate
