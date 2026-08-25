package com.example.localvocabulary.dictionary.pack

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BundledDictionaryPackBootstrapState {
    data object NotStarted : BundledDictionaryPackBootstrapState
    data object Installing : BundledDictionaryPackBootstrapState
    data class Complete(val failures: List<String>) : BundledDictionaryPackBootstrapState
}

@Singleton
class BundledDictionaryPackBootstrapper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: AndroidDictionaryPackRepository,
    private val manifestCodec: DictionaryPackManifestCodec,
) {
    private val started = AtomicBoolean(false)
    private val mutableState =
        MutableStateFlow<BundledDictionaryPackBootstrapState>(
            BundledDictionaryPackBootstrapState.NotStarted,
        )
    val state: StateFlow<BundledDictionaryPackBootstrapState> = mutableState.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        mutableState.value = BundledDictionaryPackBootstrapState.Installing
        scope.launch {
            val failures = runCatching(::installChangedPacks).getOrElse { error ->
                listOf(error.message ?: "Bundled dictionary pack preparation failed")
            }
            failures.forEach { Log.e(LOG_TAG, it) }
            mutableState.value = BundledDictionaryPackBootstrapState.Complete(failures)
        }
    }

    private fun installChangedPacks(): List<String> = buildList {
        val assetNames = context.assets.list(ASSET_DIRECTORY)
            .orEmpty()
            .filter { it.endsWith(DICTIONARY_PACK_EXTENSION) }
            .sorted()
        assetNames.forEach { assetName ->
            val assetPath = "$ASSET_DIRECTORY/$assetName"
            val manifest = readManifest(assetPath).getOrElse { error ->
                add("$assetName: ${error.message ?: "invalid bundled manifest"}")
                return@forEach
            }
            if (!shouldInstallBundledPack(repository.installedPacks.value, manifest)) {
                return@forEach
            }
            val result = context.assets.open(assetPath).use(repository::install)
            if (result is DictionaryPackInstallResult.Rejected) {
                add("$assetName: ${result.reason}")
            }
        }
        repository.refresh()
    }

    private fun readManifest(assetPath: String): Result<DictionaryPackManifest> = runCatching {
        ZipInputStream(context.assets.open(assetPath).buffered()).use { zip ->
            val first = requireNotNull(zip.nextEntry) { "Bundled pack is empty" }
            require(first.name == DICTIONARY_PACK_MANIFEST_FILE) {
                "Manifest must be the first pack entry"
            }
            manifestCodec.decode(
                zip.readBytesLimited(MAX_MANIFEST_BYTES).decodeToString(),
            ).getOrThrow()
        }
    }

    private companion object {
        const val ASSET_DIRECTORY = "bundled-dictionary-packs"
        const val DICTIONARY_PACK_EXTENSION = ".dictpack"
        const val MAX_MANIFEST_BYTES = 256 * 1024
        const val LOG_TAG = "BundledDictionaryPacks"
    }
}

internal fun shouldInstallBundledPack(
    installedPacks: List<InstalledDictionaryPack>,
    bundledManifest: DictionaryPackManifest,
): Boolean = installedPacks.none { installed ->
    installed.manifest.packId == bundledManifest.packId &&
        installed.manifest.providerId == bundledManifest.providerId &&
        installed.manifest.payload.sha256 == bundledManifest.payload.sha256
}
