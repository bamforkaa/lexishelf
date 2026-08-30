package com.example.localvocabulary.feature.wordeditor

import com.example.localvocabulary.dictionary.domain.DictionarySenseLabel
import com.example.localvocabulary.dictionary.domain.DictionarySenseLabelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictionarySenseLabelPresentationTest {
    @Test
    fun `empty label list has no presentation row`() {
        assertNull(emptyList<DictionarySenseLabel>().toCompactSenseLabelText())
    }

    @Test
    fun `visible labels are bounded while accessibility retains every label`() {
        val text = listOf(
            DictionarySenseLabel(DictionarySenseLabelType.REGIONAL, "Southern Germany"),
            DictionarySenseLabel(DictionarySenseLabelType.TRANSITIVE),
            DictionarySenseLabel(DictionarySenseLabelType.ARCHAIC),
            DictionarySenseLabel(DictionarySenseLabelType.INFORMAL),
        ).toCompactSenseLabelText()

        assertEquals("informal · archaic · transitive · +1", text?.visibleText)
        assertEquals(
            "informal · archaic · transitive · regional: Southern Germany",
            text?.accessibilityText,
        )
    }
}
