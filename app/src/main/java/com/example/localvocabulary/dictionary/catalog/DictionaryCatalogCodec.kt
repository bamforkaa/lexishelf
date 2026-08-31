package com.example.localvocabulary.dictionary.catalog

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.pack.DICTIONARY_PACK_MANIFEST_SCHEMA_VERSION
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DictionaryCatalogCodec @Inject constructor() {
    private val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun decode(value: String): Result<DictionaryCatalog> = runCatching {
        require(value.encodeToByteArray().size <= MAX_CATALOG_BYTES) { "Dictionary catalog is too large" }
        json.decodeFromString<DictionaryCatalog>(value).also(::validate)
    }

    fun encode(catalog: DictionaryCatalog): String {
        validate(catalog)
        return json.encodeToString(DictionaryCatalog.serializer(), catalog)
    }

    fun validate(catalog: DictionaryCatalog) {
        require(catalog.schemaVersion == DICTIONARY_CATALOG_SCHEMA_VERSION) {
            "Unsupported dictionary catalog schema: ${catalog.schemaVersion}"
        }
        require(catalog.catalogVersion.isNotBlank()) { "Catalog version is required" }
        require(isValidUtcInstant(catalog.generatedAt)) { "Invalid catalog generation time" }
        require(catalog.packs.isNotEmpty()) { "Dictionary catalog has no packs" }
        require(catalog.packs.size <= MAX_PACK_COUNT) { "Dictionary catalog has too many packs" }
        require(catalog.packs.map { it.packId }.distinct().size == catalog.packs.size) {
            "Duplicate dictionary catalog pack ID"
        }
        catalog.packs.forEach(::validatePack)
    }

    private fun validatePack(pack: DictionaryCatalogPack) {
        require(pack.packId in PUBLIC_PACK_IDS) { "Unknown public dictionary pack: ${pack.packId}" }
        require(pack.providerId == EXPECTED_PROVIDER_BY_PACK.getValue(pack.packId)) {
            "Unknown or mismatched dictionary provider"
        }
        require(pack.displayName.isNotBlank()) { "Pack display name is required" }
        require(pack.description.isNotBlank()) { "Pack description is required" }
        require(pack.datasetVersion.isNotBlank()) { "Dataset version is required" }
        require(pack.manifestSchemaVersion == DICTIONARY_PACK_MANIFEST_SCHEMA_VERSION) {
            "Unsupported pack manifest schema"
        }
        val expectedDatasetSchema = if (pack.providerId == "kaikki") 3 else 1
        require(pack.datasetSchemaVersion == expectedDatasetSchema) {
            "Unsupported dataset schema for ${pack.packId}"
        }
        require(pack.supportedLanguagePairs.isNotEmpty()) { "Pack language pairs are required" }
        require(pack.supportedLanguagePairs.distinct().size == pack.supportedLanguagePairs.size) {
            "Duplicate pack language pair"
        }
        pack.supportedLanguagePairs.forEach { pair ->
            requireNotNull(Bcp47LanguageTag.parse(pair.sourceLanguage)) { "Invalid source language" }
            requireNotNull(Bcp47LanguageTag.parse(pair.resultLanguage)) { "Invalid result language" }
            runCatching { DictionaryResultKind.valueOf(pair.resultKind) }
                .getOrElse { throw IllegalArgumentException("Invalid dictionary result kind") }
        }
        pack.recommendedFor.forEach {
            requireNotNull(Bcp47LanguageTag.parse(it)) { "Invalid recommended language" }
        }
        require(pack.recommendedFor.distinct().size == pack.recommendedFor.size) {
            "Duplicate recommended language"
        }
        GitHubDistributionUrlPolicy.requirePackDownload(pack.downloadUrl)
        require(pack.downloadSizeBytes in 1..MAX_DOWNLOAD_BYTES) { "Invalid pack download size" }
        require(pack.installedSizeBytes in 1..MAX_INSTALLED_BYTES) { "Invalid installed pack size" }
        require(SHA_256.matches(pack.sha256)) { "Invalid archive checksum" }
        require(SHA_256.matches(pack.payloadSha256)) { "Invalid payload checksum" }
        require(pack.sourceName.isNotBlank()) { "Source name is required" }
        require(pack.sourceArtifactId.isNotBlank()) { "Source artifact ID is required" }
        require(SHA_256.matches(pack.sourceArtifactSha256)) { "Invalid source artifact checksum" }
        requireHttps(pack.sourceUrl, "source")
        require(pack.licenseName.isNotBlank()) { "License name is required" }
        requireHttps(pack.licenseUrl, "license")
        require(pack.attribution.isNotBlank()) { "Attribution is required" }
        require(pack.transformationNotice.isNotBlank()) { "Transformation notice is required" }
        require(pack.redistributionAllowed) { "Blocked artifacts cannot enter the public catalog" }
    }

    private fun requireHttps(value: String, label: String) {
        val uri = runCatching { URI(value) }.getOrElse {
            throw IllegalArgumentException("Invalid $label URL")
        }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "Invalid $label URL"
        }
    }

    private fun isValidUtcInstant(value: String): Boolean {
        if (!UTC_INSTANT.matches(value)) return false
        val withoutFraction = value.replace(UTC_FRACTION, "Z")
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return runCatching { parser.parse(withoutFraction) }.getOrNull() != null
    }

    private companion object {
        const val MAX_CATALOG_BYTES = 1024 * 1024
        const val MAX_PACK_COUNT = 100
        const val MAX_DOWNLOAD_BYTES = 1024L * 1024L * 1024L
        const val MAX_INSTALLED_BYTES = 2L * 1024L * 1024L * 1024L
        val SHA_256 = Regex("[a-f0-9]{64}")
        val UTC_INSTANT = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z")
        val UTC_FRACTION = Regex("\\.\\d{1,9}Z$")
        val PUBLIC_PACK_IDS = setOf(
            "cc-cedict.zh-en",
            "korean-basic.multilingual",
            "panlex.ko-fallback",
            "kaikki.de-en",
            "kaikki.hi-en",
            "kaikki.pl-en",
            "kaikki.nl-en",
            "kaikki.pt-en",
            "kaikki.tr-en",
            "kaikki.cs-en",
            "kaikki.sv-en",
            "kaikki.uk-en",
            "kaikki.vi-en",
            "kaikki.th-en",
            "kaikki.id-en",
            "kaikki.en-morphology",
        )
        val EXPECTED_PROVIDER_BY_PACK = PUBLIC_PACK_IDS.associateWith { packId ->
            when {
                packId.startsWith("cc-cedict.") -> "cc-cedict"
                packId.startsWith("korean-basic.") -> "korean-basic-dictionary"
                packId.startsWith("panlex.") -> "panlex"
                else -> "kaikki"
            }
        }
    }
}
