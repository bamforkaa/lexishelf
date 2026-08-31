package com.example.localvocabulary.dictionary.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryCatalogCodecTest {
    private val codec = DictionaryCatalogCodec()

    @Test
    fun `valid catalog round trips`() {
        val catalog = catalog()

        val decoded = codec.decode(codec.encode(catalog)).getOrThrow()

        assertEquals(catalog, decoded)
    }

    @Test
    fun `committed public catalog is accepted by the app codec and excludes jmdict`() {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val repositoryRoot = sequenceOf(workingDirectory, workingDirectory.parentFile)
            .filterNotNull()
            .first { File(it, "distribution/dictionary-catalog-v1.json").isFile }
        val decoded = codec.decode(
            File(repositoryRoot, "distribution/dictionary-catalog-v1.json").readText(),
        ).getOrThrow()

        assertEquals(16, decoded.packs.size)
        assertFalse(decoded.packs.any { it.providerId == "jmdict" })
    }

    @Test
    fun `unsupported catalog schema is rejected`() {
        assertRejected(catalog().copy(schemaVersion = 2), "Unsupported dictionary catalog schema")
    }

    @Test
    fun `invalid UTC generation time is rejected on every supported Android version`() {
        assertRejected(catalog().copy(generatedAt = "2026-02-30T00:00:00Z"), "Invalid catalog generation time")
    }

    @Test
    fun `missing required field is rejected`() {
        val json = codec.encode(catalog()).replace("\"displayName\":\"CC-CEDICT\",", "")

        assertTrue(codec.decode(json).isFailure)
    }

    @Test
    fun `invalid checksum is rejected`() {
        assertRejected(catalog(pack().copy(sha256 = "bad")), "Invalid archive checksum")
    }

    @Test
    fun `duplicate pack ID is rejected`() {
        assertRejected(catalog(packs = listOf(pack(), pack())), "Duplicate dictionary catalog pack ID")
    }

    @Test
    fun `insecure or foreign download URL is rejected`() {
        assertRejected(
            catalog(pack().copy(downloadUrl = "http://github.com/Bamfor/lexishelf/releases/download/v0.1.0/a.dictpack")),
            "Only HTTPS URLs",
        )
        assertRejected(
            catalog(pack().copy(downloadUrl = "https://example.com/a.dictpack")),
            "GitHub release host",
        )
    }

    @Test
    fun `unknown provider and jmdict are rejected`() {
        assertRejected(catalog(pack().copy(providerId = "jmdict")), "mismatched dictionary provider")
        assertRejected(
            catalog(pack().copy(packId = "jmdict.ja-en", providerId = "jmdict")),
            "Unknown public dictionary pack",
        )
    }

    @Test
    fun `malformed language tag is rejected`() {
        assertRejected(
            catalog(
                pack().copy(
                    supportedLanguagePairs = listOf(
                        DictionaryCatalogLanguagePair("en_US", "en", "TRANSLATION"),
                    ),
                ),
            ),
            "Invalid source language",
        )
    }

    @Test
    fun `giant aggregate Kaikki pack is outside public allowlist`() {
        assertRejected(
            catalog(pack().copy(packId = "kaikki.all-en", providerId = "kaikki")),
            "Unknown public dictionary pack",
        )
    }

    private fun assertRejected(value: DictionaryCatalog, message: String) {
        val error = codec.decode(rawEncode(value)).exceptionOrNull()
        assertTrue("Expected <$message>, got <${error?.message}>", error?.message.orEmpty().contains(message))
    }

    private fun rawEncode(value: DictionaryCatalog): String =
        kotlinx.serialization.json.Json.encodeToString(DictionaryCatalog.serializer(), value)
}

internal fun catalog(
    pack: DictionaryCatalogPack = pack(),
    packs: List<DictionaryCatalogPack> = listOf(pack),
) = DictionaryCatalog(
    schemaVersion = 1,
    catalogVersion = "v0.1.0",
    generatedAt = "2026-08-31T00:00:00Z",
    packs = packs,
)

internal fun pack(
    archiveBytes: ByteArray = "archive".encodeToByteArray(),
) = DictionaryCatalogPack(
    packId = "cc-cedict.zh-en",
    providerId = "cc-cedict",
    displayName = "CC-CEDICT",
    section = DictionaryCatalogSection.SPECIALIZED,
    description = "중국어 → 영어",
    datasetVersion = "2026-08-22T08:27:42Z",
    manifestSchemaVersion = 1,
    datasetSchemaVersion = 1,
    supportedLanguagePairs = listOf(
        DictionaryCatalogLanguagePair("zh-Hans", "en", "TRANSLATION"),
    ),
    downloadUrl = "https://github.com/Bamfor/lexishelf/releases/download/v0.1.0/cc-cedict.dictpack",
    downloadSizeBytes = archiveBytes.size.toLong(),
    installedSizeBytes = 7,
    sha256 = sha256(archiveBytes),
    payloadSha256 = "1".repeat(64),
    sourceName = "CC-CEDICT / MDBG",
    sourceArtifactId = "cedict.gz",
    sourceArtifactSha256 = "2".repeat(64),
    sourceUrl = "https://cc-cedict.org/editor/editor.php?handler=Download",
    licenseName = "CC BY-SA 4.0",
    licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
    attribution = "CC-CEDICT data from MDBG",
    shareAlike = true,
    transformationNotice = "Repackaged for this application.",
    redistributionAllowed = true,
    publicDistributionDecision = PublicDistributionDecision.PUBLIC_DISTRIBUTION_ALLOWED,
    recommendedFor = listOf("zh-Hans"),
)

internal fun sha256(value: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(value)
    .joinToString("") { "%02x".format(it) }
