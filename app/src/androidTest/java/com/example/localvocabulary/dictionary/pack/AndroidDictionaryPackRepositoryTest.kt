package com.example.localvocabulary.dictionary.pack

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.app.DictionaryApplication
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDictionaryPackRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val codec = DictionaryPackManifestCodec()
    private val repository = AndroidDictionaryPackRepository(
        context,
        DictionaryPackPayloadValidator { _, _ -> Result.success(Unit) },
        codec,
        DictionaryPackStorageSpace { Long.MAX_VALUE },
    )
    private val installedPackIds = mutableSetOf<String>()

    @Before
    fun awaitApplicationPackBootstrap() = runTest {
        val application = context.applicationContext as DictionaryApplication
        application.bundledDictionaryPackBootstrapper.state
            .filterIsInstance<BundledDictionaryPackBootstrapState.Complete>()
            .first()
    }

    @After
    fun removeTestPacks() = runTest {
        repository.refresh()
        installedPackIds.forEach { repository.delete(it) }
    }

    @Test
    fun validChecksumInstallsAtomicallyAndInvalidUpdatePreservesActiveVersion() {
        val first = manifest("test-pack-atomic", "test-provider-atomic", "1", "old".encodeToByteArray())
        installedPackIds += first.packId
        assertTrue(repository.install(pack(first, "old".encodeToByteArray())) is DictionaryPackInstallResult.Installed)
        val resolved = repository.activePack(DictionaryProviderId(first.providerId))!!
        assertEquals("1", resolved.manifest.datasetVersion)
        assertEquals("old", resolved.payloadFile.readText())

        val invalid = manifest(first.packId, first.providerId, "2", "new".encodeToByteArray())
            .copy(payload = first.payload.copy(fileName = "fixture.db", sha256 = "0".repeat(64), sizeBytes = 3))
        assertTrue(repository.install(pack(invalid, "new".encodeToByteArray())) is DictionaryPackInstallResult.Rejected)
        assertEquals("1", repository.activePack(DictionaryProviderId(first.providerId))!!.manifest.datasetVersion)
        assertFalse(packRoot().listFiles().orEmpty().any { it.name.startsWith(".incoming-") })
    }

    @Test
    fun catalogLanguagePairMismatchIsRejectedBeforeActivationAndPreservesActiveVersion() {
        val firstPayload = "old".encodeToByteArray()
        val first = manifest("test-pack-catalog", "test-provider-catalog", "1", firstPayload)
        installedPackIds += first.packId
        assertTrue(repository.install(pack(first, firstPayload)) is DictionaryPackInstallResult.Installed)

        val updatePayload = "new".encodeToByteArray()
        val update = manifest(first.packId, first.providerId, "2", updatePayload)
        val expectation = DictionaryPackInstallExpectation(
            packId = update.packId,
            providerId = update.providerId,
            datasetVersion = update.datasetVersion,
            manifestSchemaVersion = update.manifestSchemaVersion,
            datasetSchemaVersion = update.datasetSchemaVersion,
            supportedLanguagePairs = emptyList(),
            payloadSizeBytes = update.payload.sizeBytes,
            payloadSha256 = update.payload.sha256,
        )

        assertTrue(
            repository.install(pack(update, updatePayload), expectation) is
                DictionaryPackInstallResult.Rejected,
        )
        assertEquals("1", repository.activePack(DictionaryProviderId(first.providerId))!!.manifest.datasetVersion)
    }

    @Test
    fun oversizedPayloadIsRejectedBeforeExtractionAndPreservesActiveVersion() {
        val oldPayload = "old".encodeToByteArray()
        val first = manifest("test-pack-size-limit", "test-provider-size-limit", "1", oldPayload)
        installedPackIds += first.packId
        assertTrue(repository.install(pack(first, oldPayload)) is DictionaryPackInstallResult.Installed)

        val newPayload = "new".encodeToByteArray()
        val oversized = manifest(first.packId, first.providerId, "2", newPayload).copy(
            payload = DictionaryPackPayload(
                fileName = "fixture.db",
                sizeBytes = MAX_DICTIONARY_PACK_PAYLOAD_BYTES + 1,
                sha256 = sha256(newPayload),
            ),
        )

        val result = repository.install(pack(oversized, newPayload))

        assertTrue(result is DictionaryPackInstallResult.Rejected)
        assertTrue((result as DictionaryPackInstallResult.Rejected).reason.contains("512 MiB"))
        assertEquals("1", repository.activePack(DictionaryProviderId(first.providerId))!!.manifest.datasetVersion)
        assertFalse(packRoot().listFiles().orEmpty().any { it.name.startsWith(".incoming-") })
    }

    @Test
    fun insufficientStorageIsRejectedBeforeExtractionAndPreservesActiveVersion() {
        val oldPayload = "old".encodeToByteArray()
        val first = manifest("test-pack-storage-limit", "test-provider-storage-limit", "1", oldPayload)
        installedPackIds += first.packId
        assertTrue(repository.install(pack(first, oldPayload)) is DictionaryPackInstallResult.Installed)

        val limitedRepository = AndroidDictionaryPackRepository(
            context,
            DictionaryPackPayloadValidator { _, _ -> Result.success(Unit) },
            codec,
            DictionaryPackStorageSpace { MIN_FREE_STORAGE_BYTES_AFTER_INSTALL },
        )
        val newPayload = "new".encodeToByteArray()
        val update = manifest(first.packId, first.providerId, "2", newPayload)

        val result = limitedRepository.install(pack(update, newPayload))

        assertTrue(result is DictionaryPackInstallResult.Rejected)
        assertTrue((result as DictionaryPackInstallResult.Rejected).reason.contains("Not enough storage"))
        assertEquals(
            "1",
            limitedRepository.activePack(DictionaryProviderId(first.providerId))!!.manifest.datasetVersion,
        )
        assertFalse(packRoot().listFiles().orEmpty().any { it.name.startsWith(".incoming-") })
    }

    @Test
    fun updateSupportsRollbackAndCleansObsoleteThirdVersion() = runTest {
        val packId = "test-pack-rollback"
        val providerId = "test-provider-rollback"
        installedPackIds += packId
        for (version in listOf("1", "2")) {
            val payload = version.encodeToByteArray()
            val manifest = manifest(packId, providerId, version, payload)
            assertTrue(repository.install(pack(manifest, payload)) is DictionaryPackInstallResult.Installed)
        }
        repository.refresh()
        assertEquals("2", repository.activePack(DictionaryProviderId(providerId))!!.manifest.datasetVersion)
        assertTrue(repository.installedPacks.value.single { it.manifest.packId == packId }.canRollback)
        assertTrue(repository.rollback(packId))
        assertEquals("1", repository.activePack(DictionaryProviderId(providerId))!!.manifest.datasetVersion)

        val thirdPayload = "3".encodeToByteArray()
        val third = manifest(packId, providerId, "3", thirdPayload)
        repository.install(pack(third, thirdPayload))
        val versions = File(packRoot(), "$providerId/$packId/versions").listFiles().orEmpty()
        assertEquals(2, versions.count(File::isDirectory))
    }

    @Test
    fun incompletePackIsRejectedAndMultipleProvidersCoexist() {
        val first = manifest("test-pack-one", "test-provider-one", "1", byteArrayOf(1))
        val second = manifest("test-pack-two", "test-provider-two", "1", byteArrayOf(2))
        installedPackIds += setOf(first.packId, second.packId)
        assertTrue(repository.install(pack(first, byteArrayOf(1))) is DictionaryPackInstallResult.Installed)
        assertTrue(repository.install(pack(second, byteArrayOf(2))) is DictionaryPackInstallResult.Installed)
        assertNotNull(repository.activePack(DictionaryProviderId(first.providerId)))
        assertNotNull(repository.activePack(DictionaryProviderId(second.providerId)))

        val incomplete = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(DICTIONARY_PACK_MANIFEST_FILE))
                zip.write(codec.encode(first.copy(packId = "test-incomplete")).encodeToByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        assertTrue(repository.install(ByteArrayInputStream(incomplete)) is DictionaryPackInstallResult.Rejected)
    }

    @Test
    fun multiplePacksForOneProviderResolveByLanguagePair() {
        val providerId = "test-provider-multipack"
        val dePayload = "de".encodeToByteArray()
        val nlPayload = "nl".encodeToByteArray()
        val de = manifest("test-pack-de", providerId, "1", dePayload).copy(
            supportedLanguagePairs = listOf(DictionaryPackLanguagePair("de", "en", "TRANSLATION")),
        )
        val nl = manifest("test-pack-nl", providerId, "1", nlPayload).copy(
            supportedLanguagePairs = listOf(DictionaryPackLanguagePair("nl", "en", "TRANSLATION")),
        )
        installedPackIds += setOf(de.packId, nl.packId)

        assertTrue(repository.install(pack(de, dePayload)) is DictionaryPackInstallResult.Installed)
        assertTrue(repository.install(pack(nl, nlPayload)) is DictionaryPackInstallResult.Installed)

        assertEquals(
            "test-pack-de",
            repository.activePack(DictionaryProviderId(providerId), languagePair("de"))
                ?.manifest
                ?.packId,
        )
        assertEquals(
            "test-pack-nl",
            repository.activePack(DictionaryProviderId(providerId), languagePair("nl"))
                ?.manifest
                ?.packId,
        )
        assertNull(repository.activePack(DictionaryProviderId(providerId), languagePair("pt")))
    }

    @Test
    fun deletingPackDoesNotDeleteUserVocabulary() = runTest {
        val database = Room.inMemoryDatabaseBuilder(context, VocabularyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val entryId = database.vocabularyDao().insertEntry(
                VocabularyEntryEntity(
                    backupId = "pack-delete-regression",
                    headword = "owned",
                    languageTag = "en",
                    notes = "user data",
                    createdAtEpochMillis = 1,
                    modifiedAtEpochMillis = 1,
                ),
            )
            val payload = byteArrayOf(7)
            val manifest = manifest("test-pack-delete", "test-provider-delete", "1", payload)
            installedPackIds += manifest.packId
            repository.install(pack(manifest, payload))
            repository.refresh()

            assertTrue(repository.delete(manifest.packId))
            assertNull(repository.activePack(DictionaryProviderId(manifest.providerId)))
            assertEquals("owned", database.vocabularyDao().findEntryEntity(entryId)?.headword)
        } finally {
            database.close()
        }
    }

    @Test
    fun obsoletePrePackDatasetDirectoryIsRemovedWithoutTouchingPackStorage() = runTest {
        val legacyDirectory = File(context.noBackupFilesDir, "dictionary")
        val legacyPayload = File(legacyDirectory, "jmdict/old/jmdict.db")
        legacyPayload.parentFile?.mkdirs()
        legacyPayload.writeText("obsolete provider dataset")
        val activePackMarker = File(packRoot(), "storage-cleanup-regression.marker")
        activePackMarker.writeText("keep")
        try {
            repository.performStartupMaintenance()

            assertFalse(legacyDirectory.exists())
            assertEquals("keep", activePackMarker.readText())
        } finally {
            activePackMarker.delete()
        }
    }

    private fun manifest(
        packId: String,
        providerId: String,
        version: String,
        payload: ByteArray,
    ) = DictionaryPackManifest(
        format = DICTIONARY_PACK_FORMAT,
        manifestSchemaVersion = DICTIONARY_PACK_MANIFEST_SCHEMA_VERSION,
        packId = packId,
        providerId = providerId,
        datasetVersion = version,
        datasetSchemaVersion = 1,
        supportedLanguagePairs = listOf(DictionaryPackLanguagePair("ja", "en", "TRANSLATION")),
        payload = DictionaryPackPayload("fixture.db", payload.size.toLong(), sha256(payload)),
        license = DictionaryPackLicense("test-license", "test attribution"),
        createdAt = "2026-08-23T00:00:00Z",
    )

    private fun pack(manifest: DictionaryPackManifest, payload: ByteArray): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(DICTIONARY_PACK_MANIFEST_FILE))
            zip.write(codec.encode(manifest).encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(manifest.payload.fileName))
            zip.write(payload)
            zip.closeEntry()
        }
        return ByteArrayInputStream(output.toByteArray())
    }

    private fun sha256(payload: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(payload)
        .joinToString("") { "%02x".format(it) }

    private fun languagePair(sourceLanguage: String) = DictionaryLanguagePair(
        sourceLanguage = Bcp47LanguageTag.requireValid(sourceLanguage),
        resultLanguage = Bcp47LanguageTag.requireValid("en"),
        resultKind = DictionaryResultKind.TRANSLATION,
    )

    private fun packRoot() = File(context.noBackupFilesDir, "dictionary-packs")
}
