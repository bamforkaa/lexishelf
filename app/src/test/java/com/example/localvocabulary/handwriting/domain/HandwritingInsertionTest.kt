package com.example.localvocabulary.handwriting.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HandwritingInsertionTest {
    @Test
    fun `candidate fills empty headword and appends to existing user text`() {
        assertEquals("食", appendHandwritingCandidate("", "食"))
        assertEquals("食べる", appendHandwritingCandidate("食べ", "る"))
    }

    @Test
    fun `blank candidate never changes user text`() {
        assertEquals("사용자 입력", appendHandwritingCandidate("사용자 입력", "  "))
    }
}
