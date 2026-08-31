package com.example.localvocabulary.dictionary.catalog

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryCatalogStorageTest {
    @Test
    fun `catalog replacement keeps only the new validated value`() = withStorage { directory, storage ->
        storage.writeCachedCatalog("first")
        storage.writeCachedCatalog("second")

        assertEquals("second", storage.readCachedCatalog())
        assertFalse(File(directory, "dictionary-catalog-v1.json.bak").exists())
        assertFalse(File(directory, "dictionary-catalog-v1.json.tmp").exists())
    }

    @Test
    fun `startup cleanup recovers backup and removes partial download`() = withStorage { directory, storage ->
        val backup = File(directory, "dictionary-catalog-v1.json.bak")
        directory.mkdirs()
        backup.writeText("last-known")
        val partial = storage.createDownloadFile("kaikki.de-en")
        partial.writeText("partial")

        assertEquals("last-known", storage.readCachedCatalog())
        storage.cleanupPartialDownloads()

        assertEquals("last-known", storage.readCachedCatalog())
        assertTrue(File(directory, "dictionary-catalog-v1.json").isFile)
        assertFalse(backup.exists())
        assertFalse(partial.exists())
    }

    private fun withStorage(
        block: (directory: File, storage: FileDictionaryCatalogStorage) -> Unit,
    ) {
        val directory = Files.createTempDirectory("dictionary-catalog-test").toFile()
        try {
            block(directory, FileDictionaryCatalogStorage(directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}
