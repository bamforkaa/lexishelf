package com.example.localvocabulary.dictionary.catalog

import com.example.localvocabulary.dictionary.pack.DictionaryPackInstallExpectation
import com.example.localvocabulary.dictionary.pack.DictionaryPackInstallResult
import com.example.localvocabulary.dictionary.pack.DictionaryPackLanguagePair
import com.example.localvocabulary.dictionary.pack.DictionaryPackRepository
import java.io.File
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

data class DictionaryCatalogEndpoint(val value: String)

interface DownloadedDictionaryPackInstaller {
    suspend fun install(
        file: File,
        expectation: DictionaryPackInstallExpectation,
    ): DictionaryPackInstallResult
}

class RepositoryDownloadedDictionaryPackInstaller @Inject constructor(
    private val repository: DictionaryPackRepository,
) : DownloadedDictionaryPackInstaller {
    override suspend fun install(
        file: File,
        expectation: DictionaryPackInstallExpectation,
    ): DictionaryPackInstallResult = repository.installFromUri(
        uri = file.toURI().toString(),
        expectation = expectation,
    )
}

interface DictionaryCatalogRepository {
    val catalogState: StateFlow<DictionaryCatalogState>
    val downloadStates: StateFlow<Map<String, DictionaryPackDownloadState>>

    suspend fun refreshCatalog()
    suspend fun downloadAndInstall(packId: String)
}

@Singleton
class DefaultDictionaryCatalogRepository @Inject constructor(
    private val endpoint: DictionaryCatalogEndpoint,
    private val transport: DictionaryCatalogTransport,
    private val codec: DictionaryCatalogCodec,
    private val storage: DictionaryCatalogStorage,
    private val installer: DownloadedDictionaryPackInstaller,
) : DictionaryCatalogRepository {
    private val mutableCatalogState = MutableStateFlow<DictionaryCatalogState>(
        DictionaryCatalogState.NotLoaded,
    )
    override val catalogState: StateFlow<DictionaryCatalogState> = mutableCatalogState.asStateFlow()

    private val mutableDownloadStates =
        MutableStateFlow<Map<String, DictionaryPackDownloadState>>(emptyMap())
    override val downloadStates: StateFlow<Map<String, DictionaryPackDownloadState>> =
        mutableDownloadStates.asStateFlow()

    private val downloadMutex = Mutex()

    init {
        GitHubDistributionUrlPolicy.requireCatalogEndpoint(endpoint.value)
        runCatching(storage::cleanupPartialDownloads)
    }

    override suspend fun refreshCatalog() {
        val cachedCatalog = storage.readCachedCatalog()?.let { codec.decode(it).getOrNull() }
        mutableCatalogState.value = cachedCatalog?.let {
            DictionaryCatalogState.Available(
                catalog = it,
                isCached = true,
                warning = "저장된 목록을 표시하며 최신 목록을 확인하고 있습니다.",
            )
        } ?: DictionaryCatalogState.Loading

        val remote = runCatching {
            val text = transport.fetchText(endpoint.value)
            val catalog = codec.decode(text).getOrThrow()
            catalog to text
        }
        remote.onSuccess { (catalog, text) ->
            val cacheFailure = runCatching { storage.writeCachedCatalog(text) }.exceptionOrNull()
            mutableCatalogState.value = DictionaryCatalogState.Available(
                catalog = catalog,
                isCached = false,
                warning = cacheFailure?.let { "목록은 최신이지만 기기에 캐시하지 못했습니다." },
            )
        }.onFailure { error ->
            mutableCatalogState.value = cachedCatalog?.let {
                DictionaryCatalogState.Available(
                    catalog = it,
                    isCached = true,
                    warning = "최신 목록을 불러오지 못해 저장된 목록을 표시합니다.",
                )
            } ?: DictionaryCatalogState.Unavailable(
                error.message ?: "사전 다운로드 목록을 불러오지 못했습니다.",
            )
        }
    }

    override suspend fun downloadAndInstall(packId: String) {
        val catalog = (catalogState.value as? DictionaryCatalogState.Available)?.catalog
        val pack = catalog?.packs?.firstOrNull { it.packId == packId }
        if (pack == null) {
            setDownloadState(packId, DictionaryPackDownloadState.Failed("다운로드 목록에서 pack을 찾지 못했습니다."))
            return
        }
        if (!downloadMutex.tryLock()) {
            setDownloadState(packId, DictionaryPackDownloadState.Failed("다른 사전 pack을 설치하고 있습니다."))
            return
        }
        var temporary: File? = null
        try {
            temporary = storage.createDownloadFile(packId)
            setDownloadState(packId, DictionaryPackDownloadState.Downloading(0, pack.downloadSizeBytes))
            val downloaded = transport.download(
                url = pack.downloadUrl,
                target = temporary,
                expectedBytes = pack.downloadSizeBytes,
            ) { bytes ->
                setDownloadState(
                    packId,
                    DictionaryPackDownloadState.Downloading(bytes, pack.downloadSizeBytes),
                )
            }
            setDownloadState(packId, DictionaryPackDownloadState.Validating)
            require(downloaded.bytesWritten == pack.downloadSizeBytes) {
                "Downloaded archive size does not match the catalog"
            }
            require(downloaded.sha256 == pack.sha256) {
                "Downloaded archive checksum does not match the catalog"
            }
            setDownloadState(packId, DictionaryPackDownloadState.Installing)
            when (
                val result = installer.install(
                    temporary,
                    DictionaryPackInstallExpectation(
                        packId = pack.packId,
                        providerId = pack.providerId,
                        datasetVersion = pack.datasetVersion,
                        manifestSchemaVersion = pack.manifestSchemaVersion,
                        datasetSchemaVersion = pack.datasetSchemaVersion,
                        supportedLanguagePairs = pack.supportedLanguagePairs.map { pair ->
                            DictionaryPackLanguagePair(
                                sourceLanguage = pair.sourceLanguage,
                                resultLanguage = pair.resultLanguage,
                                resultKind = pair.resultKind,
                            )
                        },
                        payloadSizeBytes = pack.installedSizeBytes,
                        payloadSha256 = pack.payloadSha256,
                    ),
                )
            ) {
                is DictionaryPackInstallResult.Installed ->
                    setDownloadState(packId, DictionaryPackDownloadState.Installed)
                is DictionaryPackInstallResult.Rejected ->
                    setDownloadState(packId, DictionaryPackDownloadState.Failed(result.reason))
            }
        } catch (error: CancellationException) {
            setDownloadState(packId, DictionaryPackDownloadState.Failed("다운로드를 취소했습니다. 다시 시도할 수 있습니다."))
            throw error
        } catch (error: Exception) {
            setDownloadState(
                packId,
                DictionaryPackDownloadState.Failed(error.message ?: "사전 pack 다운로드에 실패했습니다."),
            )
        } finally {
            temporary?.delete()
            downloadMutex.unlock()
        }
    }

    private fun setDownloadState(packId: String, state: DictionaryPackDownloadState) {
        mutableDownloadStates.update { it + (packId to state) }
    }
}
