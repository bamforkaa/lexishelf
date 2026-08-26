package com.example.localvocabulary.dictionary.pack

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localvocabulary.app.DictionaryApplication
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictDataSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictIndexSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictLookupResult
import com.example.localvocabulary.dictionary.provider.kaikki.KaikkiDataSource
import com.example.localvocabulary.dictionary.provider.kaikki.KaikkiIndexSource
import com.example.localvocabulary.dictionary.provider.kaikki.KaikkiLookupResult
import com.example.localvocabulary.dictionary.provider.panlex.PanLexDataSource
import com.example.localvocabulary.dictionary.provider.panlex.PanLexIndexSource
import com.example.localvocabulary.dictionary.provider.panlex.PanLexLookupResult
import java.io.File
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real-pack checks for the configured debug bundle and optional Test-AVD staging flow. */
@RunWith(AndroidJUnit4::class)
class InstalledDictionaryPacksIntegrationTest {
    @Test
    fun bundledKaikkiPackResolvesByLanguagePairAndPerformsRealExactLookup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as DictionaryApplication
        application.bundledDictionaryPackBootstrapper.state
            .filterIsInstance<BundledDictionaryPackBootstrapState.Complete>()
            .first()
        val repository = AndroidDictionaryPackRepository(
            context,
            AndroidDictionaryPackPayloadValidator(),
            DictionaryPackManifestCodec(),
        )
        val providerId = DictionaryProviderId("kaikki")
        val germanPack = repository.activePack(providerId, pair("de"))
        assumeTrue(
            "The optional de Kaikki debug pack is not configured for this build",
            germanPack != null,
        )

        val lookup = KaikkiDataSource(KaikkiIndexSource(repository))
        lateinit var result: KaikkiLookupResult
        val elapsed = measureTimeMillis {
            result = lookup.exactLookup("Wasser", "de", 20)
        }

        assertTrue(result is KaikkiLookupResult.Matches)
        val matches = result as KaikkiLookupResult.Matches
        assertEquals("Wasser", matches.records.first().entry.headword)
        assertTrue(
            matches.records
                .flatMap { it.entry.senses }
                .flatMap { it.retainedExamples }
                .contains("Wasser lassen"),
        )
        Log.i("DictionaryPackBenchmark", "kaikki-de-first-query=${elapsed}ms")
        Unit
    }

    @Test
    fun installsLocallyStagedRealPacks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        val packPaths = InstrumentationRegistry.getArguments()
            .getString(PACK_PATHS_ARGUMENT)
            .orEmpty()
            .split(',')
            .filter { it.startsWith("$STAGING_DIRECTORY/") && it.endsWith(".dictpack") }
            .sorted()
        assumeTrue("No local .dictpack files were staged on this Test AVD", packPaths.isNotEmpty())
        val repository = AndroidDictionaryPackRepository(
            context,
            AndroidDictionaryPackPayloadValidator(),
            DictionaryPackManifestCodec(),
        )
        val report = buildList {
            packPaths.forEach { packPath ->
                lateinit var result: DictionaryPackInstallResult
                val elapsed = measureTimeMillis {
                    ParcelFileDescriptor.AutoCloseInputStream(
                        shell.executeShellCommand("cat $packPath"),
                    ).use { result = repository.install(it) }
                }
                assertTrue("$packPath: $result", result is DictionaryPackInstallResult.Installed)
                val installed = result as DictionaryPackInstallResult.Installed
                assertTrue(
                    repository.activePack(DictionaryProviderId(installed.pack.manifest.providerId)) != null,
                )
                add("${File(packPath).name}\t${elapsed}ms")
            }
        }
        Log.i("DictionaryPackBenchmark", report.joinToString(" | "))
        if (repository.activePack(DictionaryProviderId("jmdict")) != null) {
            val lookup = JmDictDataSource(JmDictIndexSource(repository))
            lateinit var lookupResult: JmDictLookupResult
            val firstQueryMillis = measureTimeMillis {
                lookupResult = lookup.exactLookup("食べる", 20)
            }
            assertTrue(lookupResult is JmDictLookupResult.Matches)
            Log.i("DictionaryPackBenchmark", "jmdict-first-query=${firstQueryMillis}ms")
        }
        if (repository.activePack(DictionaryProviderId("panlex")) != null) {
            val lookup = PanLexDataSource(PanLexIndexSource(repository))
            lateinit var lookupResult: PanLexLookupResult
            val firstQueryMillis = measureTimeMillis {
                lookupResult = lookup.exactLookup("water", "nl", "ko", 20)
            }
            assertTrue(lookupResult is PanLexLookupResult.Matches)
            Log.i("DictionaryPackBenchmark", "panlex-first-query=${firstQueryMillis}ms")
        }
    }

    private companion object {
        const val STAGING_DIRECTORY = "/data/local/tmp/local-vocabulary-packs"
        const val PACK_PATHS_ARGUMENT = "dictionaryPackPaths"
    }

    private fun pair(sourceLanguage: String) = DictionaryLanguagePair(
        sourceLanguage = Bcp47LanguageTag.requireValid(sourceLanguage),
        resultLanguage = Bcp47LanguageTag.requireValid("en"),
        resultKind = DictionaryResultKind.TRANSLATION,
    )
}
