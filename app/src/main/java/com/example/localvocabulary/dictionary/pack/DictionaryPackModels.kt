package com.example.localvocabulary.dictionary.pack

import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import java.io.File
import kotlinx.serialization.Serializable

const val DICTIONARY_PACK_FORMAT = "local-vocabulary-dictionary-pack"
const val DICTIONARY_PACK_MANIFEST_SCHEMA_VERSION = 1
const val DICTIONARY_PACK_MANIFEST_FILE = "manifest.json"

@Serializable
data class DictionaryPackManifest(
    val format: String,
    val manifestSchemaVersion: Int,
    val packId: String,
    val providerId: String,
    val datasetVersion: String,
    val datasetSchemaVersion: Int,
    val supportedLanguagePairs: List<DictionaryPackLanguagePair>,
    val payload: DictionaryPackPayload,
    val license: DictionaryPackLicense,
    val createdAt: String,
    val source: DictionaryPackSource? = null,
)

@Serializable
data class DictionaryPackLanguagePair(
    val sourceLanguage: String,
    val resultLanguage: String,
    val resultKind: String,
)

@Serializable
data class DictionaryPackPayload(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
)

@Serializable
data class DictionaryPackLicense(
    val licenseId: String,
    val attribution: String,
)

@Serializable
data class DictionaryPackSource(
    val officialUrl: String? = null,
    val sourceVersion: String? = null,
    val buildTool: String? = null,
)

data class ResolvedDictionaryPack(
    val manifest: DictionaryPackManifest,
    val payloadFile: File,
) {
    val activationIdentity: String =
        "${manifest.packId}:${manifest.datasetVersion}:${manifest.payload.sha256}"
}

data class InstalledDictionaryPack(
    val manifest: DictionaryPackManifest,
    val canRollback: Boolean,
)

data class DictionaryPackInstallExpectation(
    val packId: String,
    val providerId: String,
    val datasetVersion: String,
    val manifestSchemaVersion: Int,
    val datasetSchemaVersion: Int,
    val supportedLanguagePairs: List<DictionaryPackLanguagePair>,
    val payloadSizeBytes: Long,
    val payloadSha256: String,
)

sealed interface DictionaryPackInstallResult {
    data class Installed(val pack: InstalledDictionaryPack) : DictionaryPackInstallResult
    data class Rejected(val reason: String) : DictionaryPackInstallResult
}

interface DictionaryPackResolver {
    fun activePack(providerId: DictionaryProviderId): ResolvedDictionaryPack?

    fun activePack(
        providerId: DictionaryProviderId,
        languagePair: DictionaryLanguagePair,
    ): ResolvedDictionaryPack? = activePack(providerId)?.takeIf { pack ->
        pack.manifest.supportedLanguagePairs.any { manifestPair ->
            manifestPair.sourceLanguage == languagePair.sourceLanguage.value &&
                manifestPair.resultLanguage == languagePair.resultLanguage.value &&
                manifestPair.resultKind == languagePair.resultKind.name
        }
    }
}
