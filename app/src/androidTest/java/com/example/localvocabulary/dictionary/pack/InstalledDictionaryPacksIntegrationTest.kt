package com.example.localvocabulary.dictionary.pack

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictDataSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictIndexSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictLookupResult
import java.io.File
import kotlin.system.measureTimeMillis
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/** Optional Test-AVD development flow; no pack is bundled into either APK. */
@RunWith(AndroidJUnit4::class)
class InstalledDictionaryPacksIntegrationTest {
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
    }

    private companion object {
        const val STAGING_DIRECTORY = "/data/local/tmp/local-vocabulary-packs"
        const val PACK_PATHS_ARGUMENT = "dictionaryPackPaths"
    }
}
