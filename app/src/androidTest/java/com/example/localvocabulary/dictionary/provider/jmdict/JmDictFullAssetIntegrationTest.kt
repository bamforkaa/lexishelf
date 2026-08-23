package com.example.localvocabulary.dictionary.provider.jmdict

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JmDictFullAssetIntegrationTest {
    @Test
    fun bundledOfficialAssetCopiesAndFindsKanjiAndKana() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            assumeTrue(
                "Full JMdict asset is optional",
                JMDICT_ARTIFACT_NAME in context.assets.list(JMDICT_ASSET_DIRECTORY).orEmpty(),
            )
            val installedDirectory = File(
                context.noBackupFilesDir,
                "dictionary/jmdict/$JMDICT_RELEASE_ID",
            )
            installedDirectory.deleteRecursively()

            val source = JmDictIndexSource(context)
            lateinit var opened: JmDictIndexOpenResult
            val firstCopyMillis = measureTimeMillis { opened = source.open() }
            (opened as JmDictIndexOpenResult.Opened).database.close()

            val lookup = JmDictDataSource(JmDictIndexSource(context))
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
                "release=$JMDICT_RELEASE_ID firstCopyMs=$firstCopyMillis " +
                    "firstOpenAndQueryMs=$firstQueryMillis",
            )
        }
    }
}
