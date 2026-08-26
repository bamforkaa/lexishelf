package com.example.localvocabulary.dictionary.pack

import android.content.Context
import android.net.Uri
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class AndroidDictionaryPackRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val payloadValidator: DictionaryPackPayloadValidator,
    private val manifestCodec: DictionaryPackManifestCodec,
) : DictionaryPackRepository, DictionaryPackResolver {
    private val root = File(context.noBackupFilesDir, "dictionary-packs")
    private val mutableInstalledPacks = MutableStateFlow(readInstalledPacks())
    override val installedPacks: StateFlow<List<InstalledDictionaryPack>> =
        mutableInstalledPacks.asStateFlow()

    init {
        cleanupOrphans()
    }

    override fun activePack(providerId: DictionaryProviderId): ResolvedDictionaryPack? =
        activePacks(providerId).firstOrNull()

    override fun activePack(
        providerId: DictionaryProviderId,
        languagePair: DictionaryLanguagePair,
    ): ResolvedDictionaryPack? = activePacks(providerId).firstOrNull { pack ->
        pack.manifest.supportedLanguagePairs.any { manifestPair ->
            manifestPair.sourceLanguage == languagePair.sourceLanguage.value &&
                manifestPair.resultLanguage == languagePair.resultLanguage.value &&
                manifestPair.resultKind == languagePair.resultKind.name
        }
    }

    private fun activePacks(providerId: DictionaryProviderId): List<ResolvedDictionaryPack> =
        providerDirectory(providerId.value)
            .takeIf(File::isDirectory)
            ?.listFiles()
            .orEmpty()
            .mapNotNull(::readActivePack)
            .sortedByDescending { it.manifest.datasetVersion }
            .toList()

    override suspend fun installFromUri(uri: String): DictionaryPackInstallResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uri)
                require(parsed.scheme == "content" || parsed.scheme == "file") {
                    "Only user-selected local files are supported"
                }
                context.contentResolver.openInputStream(parsed)?.use { input ->
                    install(input = input)
                } ?: error("Unable to open selected pack")
            }.getOrElse { error ->
                DictionaryPackInstallResult.Rejected(error.message ?: "Dictionary pack install failed")
            }.also { refresh() }
        }

    internal fun install(input: java.io.InputStream): DictionaryPackInstallResult {
        ensureDirectory(root)
        val incoming = File(root, ".incoming-${UUID.randomUUID()}")
        ensureDirectory(incoming)
        return try {
            val extracted = extractAndVerify(input, incoming)
            payloadValidator.validate(extracted.manifest, extracted.payload).getOrThrow()
            activate(extracted.manifest, extracted.payload, incoming)
        } catch (error: Exception) {
            incoming.deleteRecursively()
            DictionaryPackInstallResult.Rejected(error.message ?: "Dictionary pack validation failed")
        }
    }

    override suspend fun delete(packId: String): Boolean = withContext(Dispatchers.IO) {
        val manifest = mutableInstalledPacks.value.firstOrNull { it.manifest.packId == packId }?.manifest
            ?: return@withContext false
        val directory = packDirectory(manifest.providerId, manifest.packId)
        val deleted = directory.deleteRecursively()
        providerDirectory(manifest.providerId).takeIf { it.list().isNullOrEmpty() }?.delete()
        refresh()
        deleted
    }

    override suspend fun rollback(packId: String): Boolean = withContext(Dispatchers.IO) {
        val installed = mutableInstalledPacks.value.firstOrNull { it.manifest.packId == packId }
            ?: return@withContext false
        val packDirectory = packDirectory(installed.manifest.providerId, packId)
        val activation = readActivation(packDirectory) ?: return@withContext false
        val previous = activation.previous ?: return@withContext false
        val previousDirectory = File(File(packDirectory, VERSIONS_DIRECTORY), previous)
        if (!previousDirectory.isDirectory) return@withContext false
        writeActivation(packDirectory, Activation(previous, activation.active))
        refresh()
        true
    }

    override fun refresh() {
        mutableInstalledPacks.value = readInstalledPacks()
    }

    private fun extractAndVerify(
        input: java.io.InputStream,
        incoming: File,
    ): ExtractedPack {
        var manifest: DictionaryPackManifest? = null
        var payload: File? = null
        var payloadDigest: String? = null
        var payloadBytes = 0L
        var entryCount = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(!entry.isDirectory) { "Pack directories are not allowed" }
                require(entry.name == File(entry.name).name) { "Unsafe pack entry name" }
                when (entry.name) {
                    DICTIONARY_PACK_MANIFEST_FILE -> {
                        require(manifest == null) { "Duplicate manifest" }
                        val text = zip.readBytesLimited(MAX_MANIFEST_BYTES).decodeToString()
                        manifest = manifestCodec.decode(text).getOrThrow()
                    }
                    else -> {
                        val current = requireNotNull(manifest) { "Manifest must be the first pack entry" }
                        require(entry.name == current.payload.fileName) { "Unexpected pack entry" }
                        require(payload == null) { "Duplicate payload" }
                        val target = File(incoming, entry.name)
                        val digest = MessageDigest.getInstance("SHA-256")
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                payloadBytes += count
                                require(payloadBytes <= current.payload.sizeBytes) {
                                    "Payload exceeds declared size"
                                }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                        payload = target
                        payloadDigest = digest.digest().toHex()
                    }
                }
                zip.closeEntry()
            }
        }
        val parsedManifest = requireNotNull(manifest) { "Pack manifest is missing" }
        val extractedPayload = requireNotNull(payload) { "Pack payload is missing" }
        require(entryCount == 2) { "Pack must contain exactly a manifest and one payload" }
        require(payloadBytes == parsedManifest.payload.sizeBytes) { "Payload size mismatch" }
        require(payloadDigest == parsedManifest.payload.sha256) { "Payload checksum mismatch" }
        return ExtractedPack(parsedManifest, extractedPayload)
    }

    private fun activate(
        manifest: DictionaryPackManifest,
        payload: File,
        incoming: File,
    ): DictionaryPackInstallResult {
        val packDirectory = packDirectory(manifest.providerId, manifest.packId)
        val versionsDirectory = File(packDirectory, VERSIONS_DIRECTORY)
        ensureDirectory(versionsDirectory)
        val versionName = safeVersionName(manifest)
        val candidate = File(versionsDirectory, "$versionName.candidate")
        candidate.deleteRecursively()
        ensureDirectory(candidate)
        val installedPayload = File(candidate, manifest.payload.fileName)
        if (!payload.renameTo(installedPayload)) {
            payload.copyTo(installedPayload, overwrite = false)
        }
        File(candidate, DICTIONARY_PACK_MANIFEST_FILE).writeText(manifestCodec.encode(manifest))
        val finalVersion = File(versionsDirectory, versionName)
        if (finalVersion.isDirectory) {
            candidate.deleteRecursively()
        } else {
            check(candidate.renameTo(finalVersion)) { "Unable to activate validated pack version" }
        }

        val oldActivation = readActivation(packDirectory)
        writeActivation(packDirectory, Activation(active = versionName, previous = oldActivation?.active))
        cleanupVersions(versionsDirectory, setOfNotNull(versionName, oldActivation?.active))
        incoming.deleteRecursively()
        return DictionaryPackInstallResult.Installed(
            InstalledDictionaryPack(manifest, canRollback = oldActivation?.active != null),
        )
    }

    private fun readInstalledPacks(): List<InstalledDictionaryPack> = root.listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .flatMap { provider -> provider.listFiles().orEmpty().filter(File::isDirectory) }
        .mapNotNull { pack ->
            val active = readActivePack(pack) ?: return@mapNotNull null
            InstalledDictionaryPack(active.manifest, readActivation(pack)?.previous != null)
        }
        .sortedWith(compareBy({ it.manifest.providerId }, { it.manifest.packId }))

    private fun readActivePack(packDirectory: File): ResolvedDictionaryPack? {
        val activation = readActivation(packDirectory) ?: return null
        val version = File(File(packDirectory, VERSIONS_DIRECTORY), activation.active)
        val manifestFile = File(version, DICTIONARY_PACK_MANIFEST_FILE)
        if (!manifestFile.isFile) return null
        val manifest = manifestCodec.decode(manifestFile.readText()).getOrNull() ?: return null
        val payload = File(version, manifest.payload.fileName)
        if (!payload.isFile || payload.length() != manifest.payload.sizeBytes) return null
        return ResolvedDictionaryPack(manifest, payload)
    }

    private fun writeActivation(packDirectory: File, activation: Activation) {
        ensureDirectory(packDirectory)
        val temporary = File(packDirectory, "$ACTIVATION_FILE.tmp")
        temporary.writeText(listOf(activation.active, activation.previous.orEmpty()).joinToString("\n"))
        val target = File(packDirectory, ACTIVATION_FILE)
        val backup = File(packDirectory, "$ACTIVATION_FILE.bak")
        if (target.isFile) target.copyTo(backup, overwrite = true)
        if (target.exists() && !target.delete()) throw IOException("Unable to replace pack activation")
        if (!temporary.renameTo(target)) {
            if (backup.isFile) backup.copyTo(target, overwrite = true)
            throw IOException("Unable to commit pack activation")
        }
        backup.delete()
    }

    private fun readActivation(packDirectory: File): Activation? {
        val file = File(packDirectory, ACTIVATION_FILE)
            .takeIf(File::isFile)
            ?: File(packDirectory, "$ACTIVATION_FILE.bak").takeIf(File::isFile)
            ?: return null
        val lines = file.readLines()
        val active = lines.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
        return Activation(active, lines.getOrNull(1)?.takeIf(String::isNotBlank))
    }

    private fun providerDirectory(providerId: String) = File(root, providerId)
    private fun packDirectory(providerId: String, packId: String) =
        File(providerDirectory(providerId), packId)

    private fun safeVersionName(manifest: DictionaryPackManifest): String =
        "${manifest.datasetVersion.replace(UNSAFE_PATH, "_")}-${manifest.payload.sha256.take(12)}"

    private fun cleanupVersions(directory: File, keep: Set<String>) {
        directory.listFiles().orEmpty().forEach { child ->
            if (child.name !in keep) child.deleteRecursively()
        }
    }

    private fun cleanupOrphans() {
        root.listFiles().orEmpty()
            .filter { it.name.startsWith(".incoming-") }
            .forEach(File::deleteRecursively)
        root.listFiles().orEmpty().filter(File::isDirectory).forEach { provider ->
            provider.listFiles().orEmpty().filter(File::isDirectory).forEach { pack ->
                val activation = readActivation(pack) ?: return@forEach
                cleanupVersions(
                    File(pack, VERSIONS_DIRECTORY),
                    setOfNotNull(activation.active, activation.previous),
                )
            }
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create ${directory.absolutePath}")
        }
    }

    private data class ExtractedPack(
        val manifest: DictionaryPackManifest,
        val payload: File,
    )

    private data class Activation(val active: String, val previous: String?)

    private companion object {
        const val ACTIVATION_FILE = "activation"
        const val VERSIONS_DIRECTORY = "versions"
        const val MAX_MANIFEST_BYTES = 256 * 1024
        const val BUFFER_SIZE = 1024 * 1024
        val UNSAFE_PATH = Regex("[^A-Za-z0-9._-]")
    }
}

internal fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "Pack manifest is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
