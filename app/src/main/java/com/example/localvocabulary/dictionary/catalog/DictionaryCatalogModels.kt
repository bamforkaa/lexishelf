package com.example.localvocabulary.dictionary.catalog

import kotlinx.serialization.Serializable

const val DICTIONARY_CATALOG_SCHEMA_VERSION = 1

@Serializable
data class DictionaryCatalog(
    val schemaVersion: Int,
    val catalogVersion: String,
    val generatedAt: String,
    val packs: List<DictionaryCatalogPack>,
)

@Serializable
data class DictionaryCatalogPack(
    val packId: String,
    val providerId: String,
    val displayName: String,
    val section: DictionaryCatalogSection,
    val description: String,
    val datasetVersion: String,
    val manifestSchemaVersion: Int,
    val datasetSchemaVersion: Int,
    val supportedLanguagePairs: List<DictionaryCatalogLanguagePair>,
    val downloadUrl: String,
    val downloadSizeBytes: Long,
    val installedSizeBytes: Long,
    val sha256: String,
    val payloadSha256: String,
    val sourceName: String,
    val sourceArtifactId: String,
    val sourceArtifactSha256: String,
    val sourceUrl: String,
    val licenseName: String,
    val licenseUrl: String,
    val attribution: String,
    val shareAlike: Boolean,
    val transformationNotice: String,
    val redistributionAllowed: Boolean,
    val publicDistributionDecision: PublicDistributionDecision,
    val recommendedFor: List<String> = emptyList(),
)

@Serializable
data class DictionaryCatalogLanguagePair(
    val sourceLanguage: String,
    val resultLanguage: String,
    val resultKind: String,
)

@Serializable
enum class DictionaryCatalogSection {
    KOREAN_MEANINGS,
    ENGLISH_DETAILS,
    SPECIALIZED,
}

@Serializable
enum class PublicDistributionDecision {
    PUBLIC_DISTRIBUTION_ALLOWED,
}

sealed interface DictionaryCatalogState {
    data object NotLoaded : DictionaryCatalogState
    data object Loading : DictionaryCatalogState

    data class Available(
        val catalog: DictionaryCatalog,
        val isCached: Boolean,
        val warning: String? = null,
    ) : DictionaryCatalogState

    data class Unavailable(val reason: String) : DictionaryCatalogState
}

sealed interface DictionaryPackDownloadState {
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) :
        DictionaryPackDownloadState

    data object Validating : DictionaryPackDownloadState
    data object Installing : DictionaryPackDownloadState
    data object Installed : DictionaryPackDownloadState
    data class Failed(val reason: String) : DictionaryPackDownloadState
}
