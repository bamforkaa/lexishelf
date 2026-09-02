package com.example.localvocabulary.dictionary.catalog

import com.example.localvocabulary.dictionary.pack.DICTIONARY_PACK_FORMAT
import com.example.localvocabulary.dictionary.pack.DictionaryPackInstallExpectation
import com.example.localvocabulary.dictionary.pack.DictionaryPackInstallResult
import com.example.localvocabulary.dictionary.pack.DictionaryPackLanguagePair
import com.example.localvocabulary.dictionary.pack.DictionaryPackLicense
import com.example.localvocabulary.dictionary.pack.DictionaryPackManifest
import com.example.localvocabulary.dictionary.pack.DictionaryPackPayload
import com.example.localvocabulary.dictionary.pack.InstalledDictionaryPack
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictionaryCatalogRepositoryTest {
    private lateinit var temporaryDirectory: File
    private lateinit var storage: FileDictionaryCatalogStorage
    private lateinit var transport: FakeCatalogTransport
    private lateinit var installer: FakeDownloadedPackInstaller
    private lateinit var repository: DefaultDictionaryCatalogRepository
    private val codec = DictionaryCatalogCodec()

    @Before
    fun setUp() {
        temporaryDirectory = Files.createTempDirectory("catalog-test").toFile()
        storage = FileDictionaryCatalogStorage(temporaryDirectory)
        transport = FakeCatalogTransport()
        installer = FakeDownloadedPackInstaller()
        repository = newRepository()
    }

    @After
    fun tearDown() {
        temporaryDirectory.deleteRecursively()
    }

    @Test
    fun `successful download reports progress validates identity and installs`() = runTest {
        repository.refreshCatalog()

        repository.downloadAndInstall(pack().packId)

        assertTrue(transport.progressEvents.first() > 0)
        assertEquals(transport.archive.size.toLong(), transport.progressEvents.last())
        assertEquals(pack().packId, installer.lastExpectation?.packId)
        assertEquals(
            DictionaryPackDownloadState.Installed,
            repository.downloadStates.value.getValue(pack().packId),
        )
        assertFalse(temporaryDirectory.listFiles().orEmpty().any { it.name.endsWith(".part") })
    }

    @Test
    fun `archive checksum mismatch never reaches installer`() = runTest {
        transport.catalog = catalog(pack().copy(sha256 = "0".repeat(64)))
        repository.refreshCatalog()

        repository.downloadAndInstall(pack().packId)

        assertEquals(0, installer.installCount)
        assertTrue(repository.downloadStates.value.getValue(pack().packId) is DictionaryPackDownloadState.Failed)
    }

    @Test
    fun `network failure can be retried`() = runTest {
        repository.refreshCatalog()
        transport.downloadFailure = IOException("offline")
        repository.downloadAndInstall(pack().packId)
        assertTrue(repository.downloadStates.value.getValue(pack().packId) is DictionaryPackDownloadState.Failed)

        transport.downloadFailure = null
        repository.downloadAndInstall(pack().packId)

        assertEquals(DictionaryPackDownloadState.Installed, repository.downloadStates.value.getValue(pack().packId))
        assertEquals(1, installer.installCount)
    }

    @Test
    fun `invalid dictpack installation failure preserves existing active state`() = runTest {
        installer.activeVersion = "old"
        installer.result = DictionaryPackInstallResult.Rejected("invalid dictpack")
        repository.refreshCatalog()

        repository.downloadAndInstall(pack().packId)

        assertEquals("old", installer.activeVersion)
        assertTrue(repository.downloadStates.value.getValue(pack().packId) is DictionaryPackDownloadState.Failed)
    }

    @Test
    fun `cancel removes partial download and allows retry`() = runTest {
        repository.refreshCatalog()
        transport.blockDownload = true
        val job = launch { repository.downloadAndInstall(pack().packId) }
        runCurrent()
        assertTrue(temporaryDirectory.listFiles().orEmpty().any { it.name.endsWith(".part") })

        job.cancelAndJoin()

        assertFalse(temporaryDirectory.listFiles().orEmpty().any { it.name.endsWith(".part") })
        transport.blockDownload = false
        repository.downloadAndInstall(pack().packId)
        assertEquals(DictionaryPackDownloadState.Installed, repository.downloadStates.value.getValue(pack().packId))
    }

    @Test
    fun `catalog network failure falls back to last known valid cache`() = runTest {
        repository.refreshCatalog()
        transport.fetchFailure = IOException("offline")

        repository.refreshCatalog()

        val state = repository.catalogState.value as DictionaryCatalogState.Available
        assertTrue(state.isCached)
        assertTrue(state.warning.orEmpty().contains("저장된 목록"))
    }

    @Test
    fun `catalog failure without cache is non fatal unavailable state`() = runTest {
        transport.fetchFailure = IOException("offline")

        repository.refreshCatalog()

        assertTrue(repository.catalogState.value is DictionaryCatalogState.Unavailable)
    }

    private fun newRepository() = DefaultDictionaryCatalogRepository(
        endpoint = DictionaryCatalogEndpoint(
            "https://github.com/bamforkaa/lexishelf/releases/latest/download/dictionary-catalog-v1.json",
        ),
        transport = transport,
        codec = codec,
        storage = storage,
        installer = installer,
    )

    private inner class FakeCatalogTransport : DictionaryCatalogTransport {
        val archive = "archive".encodeToByteArray()
        var catalog: DictionaryCatalog = catalog(pack(archive))
        var fetchFailure: Exception? = null
        var downloadFailure: Exception? = null
        var blockDownload = false
        val progressEvents = mutableListOf<Long>()

        override suspend fun fetchText(url: String): String {
            fetchFailure?.let { throw it }
            return codec.encode(catalog)
        }

        override suspend fun download(
            url: String,
            target: File,
            expectedBytes: Long,
            onProgress: (downloadedBytes: Long) -> Unit,
        ): DictionaryDownloadResult {
            downloadFailure?.let { throw it }
            target.writeBytes(archive.copyOfRange(0, 2))
            progressEvents += 2
            onProgress(2)
            if (blockDownload) awaitCancellation()
            target.writeBytes(archive)
            progressEvents += archive.size.toLong()
            onProgress(archive.size.toLong())
            return DictionaryDownloadResult(archive.size.toLong(), sha256(archive))
        }
    }
}

private class FakeDownloadedPackInstaller : DownloadedDictionaryPackInstaller {
    var installCount = 0
    var lastExpectation: DictionaryPackInstallExpectation? = null
    var activeVersion: String? = null
    var result: DictionaryPackInstallResult? = null

    override suspend fun install(
        file: File,
        expectation: DictionaryPackInstallExpectation,
    ): DictionaryPackInstallResult {
        installCount++
        lastExpectation = expectation
        result?.let { return it }
        activeVersion = expectation.datasetVersion
        return DictionaryPackInstallResult.Installed(
            InstalledDictionaryPack(
                manifest = DictionaryPackManifest(
                    format = DICTIONARY_PACK_FORMAT,
                    manifestSchemaVersion = 1,
                    packId = expectation.packId,
                    providerId = expectation.providerId,
                    datasetVersion = expectation.datasetVersion,
                    datasetSchemaVersion = expectation.datasetSchemaVersion,
                    supportedLanguagePairs = listOf(
                        DictionaryPackLanguagePair("zh-Hans", "en", "TRANSLATION"),
                    ),
                    payload = DictionaryPackPayload(
                        "fixture.gz",
                        expectation.payloadSizeBytes,
                        expectation.payloadSha256,
                    ),
                    license = DictionaryPackLicense("test", "test attribution"),
                    createdAt = "2026-08-31T00:00:00Z",
                ),
                canRollback = false,
            ),
        )
    }
}
