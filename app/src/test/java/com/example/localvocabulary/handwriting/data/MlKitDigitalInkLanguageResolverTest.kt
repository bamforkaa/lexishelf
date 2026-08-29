package com.example.localvocabulary.handwriting.data

import com.example.localvocabulary.handwriting.domain.HandwritingLanguageResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitDigitalInkLanguageResolverTest {
    private val resolver = MlKitDigitalInkLanguageResolver()

    @Test
    fun `official model catalog resolves major scripts and preserves Chinese model semantics`() {
        assertModel("ja", "ja")
        assertModel("ko", "ko")
        assertModel("en", "en")
        assertModel("zh-Hans", "zh-Hani")
        assertModel("zh-Hant", "zh-Hani")
    }

    @Test
    fun `official region model is used when present`() {
        assertModel("pt-BR", "pt-BR")
        assertModel("en-GB", "en-GB")
        assertModel("sr-Latn", "sr-Latn-RS")
    }

    @Test
    fun `invalid and unsupported tags do not fall back to an unrelated Latin model`() {
        assertEquals(
            HandwritingLanguageResolution.InvalidLanguageTag,
            resolver.resolve("not_a_tag"),
        )
        assertEquals(
            HandwritingLanguageResolution.Unsupported,
            resolver.resolve("qaa"),
        )
    }

    private fun assertModel(requested: String, expectedModel: String) {
        val resolution = resolver.resolve(requested)
        assertTrue(resolution is HandwritingLanguageResolution.Supported)
        assertEquals(
            expectedModel,
            (resolution as HandwritingLanguageResolution.Supported).model.modelLanguageTag,
        )
    }
}
