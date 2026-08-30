package com.example.localvocabulary.dictionary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DictionarySenseLabelsTest {
    @Test
    fun `labels deduplicate and sort by register temporal grammar and region`() {
        val labels = listOf(
            DictionarySenseLabel(DictionarySenseLabelType.REGIONAL),
            DictionarySenseLabel(DictionarySenseLabelType.TRANSITIVE),
            DictionarySenseLabel(DictionarySenseLabelType.ARCHAIC),
            DictionarySenseLabel(DictionarySenseLabelType.INFORMAL),
            DictionarySenseLabel(DictionarySenseLabelType.TRANSITIVE),
        ).normalizedSenseLabels()

        assertEquals(
            listOf(
                DictionarySenseLabelType.INFORMAL,
                DictionarySenseLabelType.ARCHAIC,
                DictionarySenseLabelType.TRANSITIVE,
                DictionarySenseLabelType.REGIONAL,
            ),
            labels.map(DictionarySenseLabel::type),
        )
    }

    @Test
    fun `only regional category accepts source detail`() {
        assertThrows(IllegalArgumentException::class.java) {
            DictionarySenseLabel(DictionarySenseLabelType.INFORMAL, "Southern Germany")
        }
        assertEquals(
            "Southern Germany",
            DictionarySenseLabel(
                DictionarySenseLabelType.REGIONAL,
                "Southern Germany",
            ).regionalDetail,
        )
    }
}
