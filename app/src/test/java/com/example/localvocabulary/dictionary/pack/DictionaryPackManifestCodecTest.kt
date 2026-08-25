package com.example.localvocabulary.dictionary.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryPackManifestCodecTest {
    private val codec = DictionaryPackManifestCodec()

    @Test
    fun `manifest round trip preserves generic pack metadata`() {
        val manifest = manifest()
        assertEquals(manifest, codec.decode(codec.encode(manifest)).getOrThrow())
    }

    @Test
    fun `unknown manifest schema and unsafe payload are rejected`() {
        assertTrue(codec.decode(codec.encode(manifest()).replace(
            "\"manifestSchemaVersion\":1",
            "\"manifestSchemaVersion\":99",
        )).isFailure)
        assertTrue(runCatching {
            codec.encode(manifest().copy(payload = manifest().payload.copy(fileName = "../data.db")))
        }.isFailure)
    }

    private fun manifest() = DictionaryPackManifest(
        format = DICTIONARY_PACK_FORMAT,
        manifestSchemaVersion = DICTIONARY_PACK_MANIFEST_SCHEMA_VERSION,
        packId = "jmdict.ja-en",
        providerId = "jmdict",
        datasetVersion = "2026-08-23",
        datasetSchemaVersion = 1,
        supportedLanguagePairs = listOf(
            DictionaryPackLanguagePair("ja", "en", "TRANSLATION"),
        ),
        payload = DictionaryPackPayload("jmdict.db", 100, "a".repeat(64)),
        license = DictionaryPackLicense("CC-BY-SA-4.0", "JMdict by EDRDG"),
        createdAt = "2026-08-23T00:00:00Z",
    )
}

