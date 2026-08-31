package com.example.localvocabulary.dictionary.catalog

import java.io.File
import java.io.IOException
import java.util.UUID

interface DictionaryCatalogStorage {
    fun readCachedCatalog(): String?
    fun writeCachedCatalog(value: String)
    fun createDownloadFile(packId: String): File
    fun cleanupPartialDownloads()
}

class FileDictionaryCatalogStorage(
    private val directory: File,
) : DictionaryCatalogStorage {
    private val catalogFile = File(directory, CATALOG_FILE)
    private val catalogBackupFile = File(directory, "$CATALOG_FILE.bak")

    override fun readCachedCatalog(): String? = sequenceOf(catalogFile, catalogBackupFile)
        .firstOrNull { it.isFile && it.length() <= MAX_CATALOG_BYTES }
        ?.readText(Charsets.UTF_8)

    override fun writeCachedCatalog(value: String) {
        require(value.encodeToByteArray().size <= MAX_CATALOG_BYTES) { "Dictionary catalog is too large" }
        ensureDirectory()
        val temporary = File(directory, "$CATALOG_FILE.tmp")
        temporary.writeText(value, Charsets.UTF_8)
        if (catalogBackupFile.exists() && !catalogBackupFile.delete()) {
            temporary.delete()
            throw IOException("Unable to clear the previous catalog backup")
        }
        if (catalogFile.exists() && !catalogFile.renameTo(catalogBackupFile)) {
            temporary.delete()
            throw IOException("Unable to preserve the cached dictionary catalog")
        }
        if (!temporary.renameTo(catalogFile)) {
            catalogBackupFile.renameTo(catalogFile)
            temporary.delete()
            throw IOException("Unable to activate the cached dictionary catalog")
        }
        catalogBackupFile.delete()
    }

    override fun createDownloadFile(packId: String): File {
        ensureDirectory()
        val safePackId = packId.replace(UNSAFE_FILE_NAME, "_")
        return File(directory, "$safePackId-${UUID.randomUUID()}.dictpack.part")
    }

    override fun cleanupPartialDownloads() {
        if (!catalogFile.isFile && catalogBackupFile.isFile) {
            catalogBackupFile.renameTo(catalogFile)
        } else if (catalogFile.isFile) {
            catalogBackupFile.delete()
        }
        File(directory, "$CATALOG_FILE.tmp").delete()
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".dictpack.part") }
            .forEach(File::delete)
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create dictionary download storage")
        }
    }

    private companion object {
        const val CATALOG_FILE = "dictionary-catalog-v1.json"
        const val MAX_CATALOG_BYTES = 1024 * 1024L
        val UNSAFE_FILE_NAME = Regex("[^A-Za-z0-9._-]")
    }
}
