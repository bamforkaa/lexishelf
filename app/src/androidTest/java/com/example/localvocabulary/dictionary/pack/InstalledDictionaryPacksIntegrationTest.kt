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
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyQuery
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyResult
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictDataSource
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictHeadwordForm
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictLookupResult
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictPackSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictDataSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictIndexSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictLookupResult
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryDataSource
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryIndexSource
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryLookupResult
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
    fun bundledKoreanBasicPackReportsActualMultiWordExactCoverage() = runBlocking {
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
        assumeTrue(
            "The optional Korean Basic Dictionary debug pack is not configured for this build",
            repository.activePack(DictionaryProviderId("korean-basic-dictionary")) != null,
        )
        val lookup = KoreanBasicDictionaryDataSource(KoreanBasicDictionaryIndexSource(repository))

        listOf(
            "take care of",
            "look forward to",
            "by the way",
            "kick the bucket",
        ).forEach { phrase ->
            assertTrue(
                "$phrase should be an exact English reverse key in the pinned dataset",
                lookup.exactLookup(phrase, "en", "ko") is KoreanBasicDictionaryLookupResult.Matches,
            )
        }
        assertTrue(
            lookup.exactLookup("make a decision", "en", "ko") is
                KoreanBasicDictionaryLookupResult.NoMatch,
        )
    }

    @Test
    fun bundledKaikkiPacksResolveByLanguagePairAndPerformRealExactLookups() = runBlocking {
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
        val vietnamesePack = repository.activePack(providerId, pair("vi"))
        val englishMorphologyPack = repository.activePack(providerId, englishMorphologyPair())
        assumeTrue(
            "The optional de/vi Kaikki debug packs are not configured for this build",
            germanPack != null && vietnamesePack != null && englishMorphologyPack != null,
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

        val sehen = lookup.exactLookup("sehen", "de", 20) as KaikkiLookupResult.Matches
        val sehenLabels = sehen.records.flatMap { record ->
            record.entry.senses.flatMap { it.usageLabels }
        }
        assertTrue("transitive" in sehenLabels)
        assertTrue("intransitive" in sehenLabels)

        val wissenschaft = lookup.exactLookup(
            "Wissenschaft",
            "de",
            20,
        ) as KaikkiLookupResult.Matches
        val countabilityLabels = wissenschaft.records.flatMap { record ->
            record.entry.senses.flatMap { it.usageLabels }
        }
        assertTrue("countable" in countabilityLabels)
        assertTrue("uncountable" in countabilityLabels)

        val vietnamese = lookup.exactLookup("ăn", "vi", 20)
        assertTrue(vietnamese is KaikkiLookupResult.Matches)
        assertEquals(
            "ăn",
            (vietnamese as KaikkiLookupResult.Matches).records.first().entry.headword,
        )

        lateinit var germanMorphology: DictionaryMorphologyResult.Resolved
        lateinit var germanLemmaEntry: KaikkiLookupResult
        val morphologyAndLemmaLookupMillis = measureTimeMillis {
            germanMorphology = lookup.resolve(
                DictionaryMorphologyQuery(
                    surface = "Häuser",
                    sourceLanguage = Bcp47LanguageTag.requireValid("de"),
                ),
            ) as DictionaryMorphologyResult.Resolved
            germanLemmaEntry = lookup.exactLookup(germanMorphology.candidates.first().lemma, "de", 20)
        }
        assertEquals("Haus", germanMorphology.candidates.first().lemma)
        assertTrue(germanLemmaEntry is KaikkiLookupResult.Matches)
        Log.i(
            "DictionaryPackBenchmark",
            "kaikki-de-morphology-plus-lemma=$morphologyAndLemmaLookupMillis ms",
        )
        val ging = lookup.resolve(
            DictionaryMorphologyQuery(
                surface = "ging",
                sourceLanguage = Bcp47LanguageTag.requireValid("de"),
            ),
        ) as DictionaryMorphologyResult.Resolved
        assertEquals("gehen", ging.candidates.first().lemma)

        val englishQueries = mapOf(
            "is" to listOf("be"),
            "are" to listOf("be"),
            "was" to listOf("be"),
            "were" to listOf("be"),
            "been" to listOf("be"),
            "being" to listOf("be"),
            "goes" to listOf("go"),
            "went" to listOf("go", "gan"),
            "gone" to listOf("go"),
            "eats" to listOf("eat"),
            "ate" to listOf("eat"),
            "eaten" to listOf("eat"),
            "children" to listOf("child", "childer"),
            "mice" to listOf("mouse"),
        )
        englishQueries.forEach { (surface, expectedLemmas) ->
            lateinit var morphology: DictionaryMorphologyResult
            val lookupMillis = measureTimeMillis {
                morphology = lookup.resolve(
                    DictionaryMorphologyQuery(
                        surface = surface,
                        sourceLanguage = Bcp47LanguageTag.requireValid("en"),
                    ),
                )
            }
            assertTrue("$surface: $morphology", morphology is DictionaryMorphologyResult.Resolved)
            assertEquals(
                expectedLemmas,
                (morphology as DictionaryMorphologyResult.Resolved).candidates.map { it.lemma },
            )
            Log.i("DictionaryPackBenchmark", "kaikki-en-morphology-$surface=${lookupMillis}ms")
        }
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
        if (repository.activePack(DictionaryProviderId("cc-cedict")) != null) {
            val lookup = CcCedictDataSource(CcCedictPackSource(repository))
            val lookupResult = lookup.exactLookup(
                "你好",
                CcCedictHeadwordForm.SIMPLIFIED,
                20,
            )
            assertTrue(lookupResult is CcCedictLookupResult.Matches)
        }
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
        if (repository.activePack(DictionaryProviderId("kaikki"), pair("tr")) != null) {
            val lookup = KaikkiDataSource(KaikkiIndexSource(repository))
            val morphology = lookup.resolve(
                DictionaryMorphologyQuery(
                    surface = "ingilizceleştirir",
                    sourceLanguage = Bcp47LanguageTag.requireValid("tr"),
                ),
            ) as DictionaryMorphologyResult.Resolved
            assertEquals("İngilizceleştirmek", morphology.candidates.first().lemma)
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

    private fun englishMorphologyPair() = DictionaryLanguagePair(
        sourceLanguage = Bcp47LanguageTag.requireValid("en"),
        resultLanguage = Bcp47LanguageTag.requireValid("en"),
        resultKind = DictionaryResultKind.MONOLINGUAL_DEFINITION,
    )
}
