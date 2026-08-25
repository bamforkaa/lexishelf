package com.example.localvocabulary.dictionary.pack

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DictionaryPackManifestCodec @Inject constructor() {
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }
    fun decode(value: String): Result<DictionaryPackManifest> = runCatching {
        val manifest = json.decodeFromString<DictionaryPackManifest>(value)
        validate(manifest)
        manifest
    }

    fun encode(manifest: DictionaryPackManifest): String {
        validate(manifest)
        return json.encodeToString(DictionaryPackManifest.serializer(), manifest)
    }

    fun validate(manifest: DictionaryPackManifest) {
        require(manifest.format == DICTIONARY_PACK_FORMAT) { "Unsupported dictionary pack format" }
        require(manifest.manifestSchemaVersion == DICTIONARY_PACK_MANIFEST_SCHEMA_VERSION) {
            "Unsupported dictionary pack manifest schema: ${manifest.manifestSchemaVersion}"
        }
        require(PACK_ID.matches(manifest.packId)) { "Invalid pack ID" }
        DictionaryProviderId(manifest.providerId)
        require(manifest.datasetVersion.isNotBlank()) { "Dataset version is required" }
        require(manifest.datasetSchemaVersion > 0) { "Dataset schema version must be positive" }
        require(manifest.supportedLanguagePairs.isNotEmpty()) { "Language pairs are required" }
        manifest.supportedLanguagePairs.forEach { pair ->
            requireNotNull(Bcp47LanguageTag.parse(pair.sourceLanguage)) { "Invalid source language" }
            requireNotNull(Bcp47LanguageTag.parse(pair.resultLanguage)) { "Invalid result language" }
            try {
                DictionaryResultKind.valueOf(pair.resultKind)
            } catch (error: IllegalArgumentException) {
                throw SerializationException("Invalid result kind: ${pair.resultKind}", error)
            }
        }
        require(FILE_NAME.matches(manifest.payload.fileName)) { "Payload must be a plain file name" }
        require(manifest.payload.sizeBytes > 0) { "Payload size must be positive" }
        require(SHA_256.matches(manifest.payload.sha256)) { "Invalid SHA-256" }
        require(manifest.license.licenseId.isNotBlank()) { "License ID is required" }
        require(manifest.license.attribution.isNotBlank()) { "Attribution is required" }
        require(manifest.createdAt.isNotBlank()) { "Build date is required" }
    }

    private companion object {
        val PACK_ID = Regex("[a-z0-9][a-z0-9._-]{0,127}")
        val FILE_NAME = Regex("[^/\\\\]+")
        val SHA_256 = Regex("[a-f0-9]{64}")
    }
}
