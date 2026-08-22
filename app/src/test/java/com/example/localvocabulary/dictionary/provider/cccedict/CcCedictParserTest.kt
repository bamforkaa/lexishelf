package com.example.localvocabulary.dictionary.provider.cccedict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CcCedictParserTest {
    @Test
    fun `parser preserves headword pinyin sense and gloss order for v1 and v2`() {
        val report = CcCedictParser().parse(fixtureReader())

        val china = report.records.first()
        assertEquals("中國", china.traditional)
        assertEquals("中国", china.simplified)
        assertEquals("Zhong1 guo2", china.pinyin)
        assertEquals(listOf(listOf("China"), listOf("Middle Kingdom")), china.senses)
        assertEquals(CcCedictFormat.V1, china.format)

        val dictionary = report.records[1]
        assertEquals("ci2dian3", dictionary.pinyin)
        assertEquals(listOf(listOf("dictionary", "lexicon")), dictionary.senses)
        assertEquals(CcCedictFormat.V2, dictionary.format)
    }

    @Test
    fun `parser preserves Unicode and unusual valid definition text`() {
        val report = CcCedictParser().parse(fixtureReader())

        assertEquals(
            listOf("coffee", "café (loanword)"),
            report.records.single { it.simplified == "咖啡" }.senses.single(),
        )
        assertEquals(
            "see also 程式|程序[cheng2 shi4]",
            report.records.single { it.simplified == "编程" }.senses.last().single(),
        )
        assertEquals(
            "العربية",
            report.records.single { it.simplified == "阿拉伯语" }.senses.single().last(),
        )
    }

    @Test
    fun `comments blank lines and malformed records are reported without losing valid records`() {
        val report = CcCedictParser().parse(fixtureReader())

        assertEquals(7, report.records.size)
        assertEquals(3, report.commentLineCount)
        assertEquals(1, report.blankLineCount)
        assertEquals(1, report.issues.size)
        assertTrue(report.issues.single().line.contains("malformed"))
        assertTrue(report.issues.single().reason.isNotBlank())
    }

    @Test
    fun `duplicate headwords remain distinct records`() {
        val records = CcCedictParser().parse(fixtureReader()).records.filter { it.simplified == "行" }

        assertEquals(listOf("hang2", "xing2"), records.map { it.pinyin })
        assertEquals(2, records.map { it.stableSourceId }.distinct().size)
    }

    private fun fixtureReader() = requireNotNull(
        javaClass.getResourceAsStream("/cccedict/cccedict_fixture.u8"),
    ).bufferedReader(Charsets.UTF_8)
}
