package com.example.localvocabulary.feature.settings

import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogRepository
import com.example.localvocabulary.dictionary.catalog.DictionaryCatalogState
import com.example.localvocabulary.dictionary.catalog.DictionaryPackDownloadState
import com.example.localvocabulary.dictionary.catalog.catalog
import com.example.localvocabulary.dictionary.catalog.pack
import com.example.localvocabulary.dictionary.pack.DICTIONARY_PACK_FORMAT
import com.example.localvocabulary.dictionary.pack.DictionaryPackInstallExpectation
import com.example.localvocabulary.dictionary.pack.DictionaryPackInstallResult
import com.example.localvocabulary.dictionary.pack.DictionaryPackLanguagePair
import com.example.localvocabulary.dictionary.pack.DictionaryPackLicense
import com.example.localvocabulary.dictionary.pack.DictionaryPackManifest
import com.example.localvocabulary.dictionary.pack.DictionaryPackPayload
import com.example.localvocabulary.dictionary.pack.DictionaryPackRepository
import com.example.localvocabulary.dictionary.pack.InstalledDictionaryPack
import com.example.localvocabulary.dictionary.registry.DefaultDictionaryProviderRegistry
import com.example.localvocabulary.feature.wordlist.MainDispatcherRule
import com.example.localvocabulary.settings.AppSettings
import com.example.localvocabulary.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `catalog item reflects installed same and new version states`() = runTest {
        val catalogRepository = FakeCatalogRepository()
        val packRepository = FakePackRepository()
        packRepository.installed.value = listOf(installedPack("2026-08-22T08:27:42Z"))
        val viewModel = viewModel(packRepository, catalogRepository)
        advanceUntilIdle()

        assertEquals(
            DictionaryCatalogPackInstallStatus.INSTALLED,
            viewModel.uiState.value.catalogPacks.single().installStatus,
        )

        catalogRepository.catalog.value = DictionaryCatalogState.Available(
            catalog(pack().copy(datasetVersion = "2026-09-01")),
            isCached = false,
        )
        advanceUntilIdle()

        assertEquals(
            DictionaryCatalogPackInstallStatus.UPDATE_AVAILABLE,
            viewModel.uiState.value.catalogPacks.single().installStatus,
        )
    }

    @Test
    fun `catalog unavailable does not block settings or installed packs`() = runTest {
        val catalogRepository = FakeCatalogRepository(
            refreshState = DictionaryCatalogState.Unavailable("offline"),
        )
        val packRepository = FakePackRepository()
        packRepository.installed.value = listOf(installedPack("2026-08-22T08:27:42Z"))
        val viewModel = viewModel(packRepository, catalogRepository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.catalogStatus is DictionaryCatalogStatusUiState.Unavailable)
        assertEquals(1, viewModel.uiState.value.installedPacks.size)
        assertEquals("en", viewModel.uiState.value.defaultLanguageTag)
    }

    private fun viewModel(
        packRepository: FakePackRepository,
        catalogRepository: FakeCatalogRepository,
    ) = SettingsViewModel(
        settingsRepository = FakeSettingsRepository(),
        providerRegistry = DefaultDictionaryProviderRegistry(emptyList()),
        dictionaryPackRepository = packRepository,
        dictionaryCatalogRepository = catalogRepository,
    )

    private fun installedPack(version: String): InstalledDictionaryPack {
        val catalogPack = pack()
        return InstalledDictionaryPack(
            manifest = DictionaryPackManifest(
                format = DICTIONARY_PACK_FORMAT,
                manifestSchemaVersion = 1,
                packId = catalogPack.packId,
                providerId = catalogPack.providerId,
                datasetVersion = version,
                datasetSchemaVersion = catalogPack.datasetSchemaVersion,
                supportedLanguagePairs = listOf(
                    DictionaryPackLanguagePair("zh-Hans", "en", "TRANSLATION"),
                ),
                payload = DictionaryPackPayload(
                    "cedict.gz",
                    catalogPack.installedSizeBytes,
                    catalogPack.payloadSha256,
                ),
                license = DictionaryPackLicense("CC-BY-SA-4.0", "attribution"),
                createdAt = "2026-08-31T00:00:00Z",
            ),
            canRollback = false,
        )
    }
}

private class FakeSettingsRepository : SettingsRepository {
    private val mutableSettings = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = mutableSettings

    override suspend fun setDefaultLanguageTag(languageTag: String) {
        mutableSettings.value = mutableSettings.value.copy(defaultLanguageTag = languageTag)
    }

    override suspend fun addUserLanguageTag(languageTag: String) {
        mutableSettings.value = mutableSettings.value.copy(
            userLanguageTags = mutableSettings.value.userLanguageTags + languageTag,
        )
    }
}

private class FakePackRepository : DictionaryPackRepository {
    val installed = MutableStateFlow<List<InstalledDictionaryPack>>(emptyList())
    override val installedPacks = installed

    override suspend fun installFromUri(
        uri: String,
        expectation: DictionaryPackInstallExpectation?,
    ): DictionaryPackInstallResult = error("Not used")

    override suspend fun delete(packId: String): Boolean {
        installed.value = installed.value.filterNot { it.manifest.packId == packId }
        return true
    }

    override suspend fun rollback(packId: String): Boolean = false
    override fun refresh() = Unit
}

private class FakeCatalogRepository(
    private val refreshState: DictionaryCatalogState = DictionaryCatalogState.Available(
        catalog(),
        isCached = false,
    ),
) : DictionaryCatalogRepository {
    val catalog = MutableStateFlow<DictionaryCatalogState>(DictionaryCatalogState.NotLoaded)
    override val catalogState = catalog
    override val downloadStates =
        MutableStateFlow<Map<String, DictionaryPackDownloadState>>(emptyMap())

    override suspend fun refreshCatalog() {
        catalog.value = refreshState
    }

    override suspend fun downloadAndInstall(packId: String) {
        downloadStates.value = downloadStates.value + (packId to DictionaryPackDownloadState.Installed)
    }
}
