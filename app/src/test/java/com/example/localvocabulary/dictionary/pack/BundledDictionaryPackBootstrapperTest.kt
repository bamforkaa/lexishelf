package com.example.localvocabulary.dictionary.pack

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledDictionaryPackBootstrapperTest {
    @Test
    fun `same active payload is not reinstalled`() {
        val manifest = manifest("same-hash")

        assertFalse(
            shouldInstallBundledPack(
                listOf(InstalledDictionaryPack(manifest, canRollback = false)),
                manifest,
            ),
        )
    }

    @Test
    fun `changed bundled payload is installed`() {
        val installed = manifest("old-hash")
        val bundled = manifest("new-hash")

        assertTrue(
            shouldInstallBundledPack(
                listOf(InstalledDictionaryPack(installed, canRollback = false)),
                bundled,
            ),
        )
    }

    private fun manifest(hashSeed: String) = DictionaryPackManifest(
        format = DICTIONARY_PACK_FORMAT,
        manifestSchemaVersion = DICTIONARY_PACK_MANIFEST_SCHEMA_VERSION,
        packId = "fixture.pack",
        providerId = "fixture-provider",
        datasetVersion = "1",
        datasetSchemaVersion = 1,
        supportedLanguagePairs = listOf(
            DictionaryPackLanguagePair("ja", "en", "TRANSLATION"),
        ),
        payload = DictionaryPackPayload(
            fileName = "fixture.db",
            sizeBytes = 1,
            sha256 = hashSeed.padEnd(64, '0'),
        ),
        license = DictionaryPackLicense("test", "test attribution"),
        createdAt = "2026-08-25T00:00:00Z",
    )
}
