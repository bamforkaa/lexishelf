package com.example.localvocabulary.dictionary.provider.jmdict

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.pack.AndroidDictionaryPackPayloadValidator
import com.example.localvocabulary.dictionary.pack.AndroidDictionaryPackRepository
import com.example.localvocabulary.dictionary.pack.AndroidDictionaryPackStorageSpace
import com.example.localvocabulary.dictionary.pack.DictionaryPackManifestCodec
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JmDictInstalledPackIntegrationTest {
    @Test
    fun installedOfficialPackFindsKanjiAndKana() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val repository = AndroidDictionaryPackRepository(
                context,
                AndroidDictionaryPackPayloadValidator(),
                DictionaryPackManifestCodec(),
                AndroidDictionaryPackStorageSpace(context),
            )
            assumeTrue(
                "Full JMdict pack is optional",
                repository.activePack(DictionaryProviderId(JmDictProvider.STABLE_PROVIDER_ID)) != null,
            )
            val source = JmDictIndexSource(repository)
            lateinit var opened: JmDictIndexOpenResult
            val firstOpenMillis = measureTimeMillis { opened = source.open() }
            (opened as JmDictIndexOpenResult.Opened).database.close()

            val lookup = JmDictDataSource(JmDictIndexSource(repository))
            lateinit var kanji: JmDictLookupResult
            val firstQueryMillis = measureTimeMillis {
                kanji = lookup.exactLookup("食べる", 20)
            }
            val reading = lookup.exactLookup("たべる", 20) as JmDictLookupResult.Matches
            val kanjiMatches = kanji as JmDictLookupResult.Matches

            assertEquals("1358280", kanjiMatches.records.single().entry.entrySequence)
            assertEquals("1358280", reading.records.single().entry.entrySequence)
            Log.i(
                "JmDictBenchmark",
                "release=$JMDICT_RELEASE_ID firstOpenMs=$firstOpenMillis " +
                    "firstOpenAndQueryMs=$firstQueryMillis",
            )
        }
    }
}
