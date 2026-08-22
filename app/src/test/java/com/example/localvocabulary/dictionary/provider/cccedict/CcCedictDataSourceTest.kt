package com.example.localvocabulary.dictionary.provider.cccedict

import java.io.BufferedReader
import java.io.StringReader
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CcCedictDataSourceTest {
    @Test
    fun `simplified and traditional exact lookup use separate deterministic indexes`() = runTest {
        val dataSource = fixtureDataSource()

        val simplified = dataSource.exactLookup("中国", CcCedictHeadwordForm.SIMPLIFIED, null)
            as CcCedictLookupResult.Matches
        val traditional = dataSource.exactLookup("中國", CcCedictHeadwordForm.TRADITIONAL, null)
            as CcCedictLookupResult.Matches

        assertEquals("Zhong1 guo2", simplified.records.single().pinyin)
        assertEquals(simplified.records, traditional.records)
        assertEquals(1, simplified.skippedMalformedLineCount)
    }

    @Test
    fun `multiple entries for one headword preserve file order and limit reports truncation`() = runTest {
        val result = fixtureDataSource().exactLookup(
            query = "行",
            headwordForm = CcCedictHeadwordForm.SIMPLIFIED,
            resultLimit = 1,
        ) as CcCedictLookupResult.Matches

        assertEquals(listOf("hang2"), result.records.map { it.pinyin })
        assertEquals(2, result.totalMatchCount)
    }

    @Test
    fun `missing query and missing dataset have distinct results`() = runTest {
        val noMatch = fixtureDataSource().exactLookup(
            "不存在",
            CcCedictHeadwordForm.SIMPLIFIED,
            null,
        )
        val unavailable = CcCedictDataSource(
            datasetSource = CcCedictDatasetSource { CcCedictDatasetOpenResult.Missing },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        ).exactLookup("中国", CcCedictHeadwordForm.SIMPLIFIED, null)

        assertTrue(noMatch is CcCedictLookupResult.NoMatch)
        assertTrue(unavailable is CcCedictLookupResult.DatasetUnavailable)
    }

    @Test
    fun `dataset with no valid records is malformed rather than silently empty`() = runTest {
        val dataSource = CcCedictDataSource(
            datasetSource = CcCedictDatasetSource {
                CcCedictDatasetOpenResult.Opened(BufferedReader(StringReader("bad line")))
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertTrue(
            dataSource.exactLookup("x", CcCedictHeadwordForm.SIMPLIFIED, null) is
                CcCedictLookupResult.MalformedDataset,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.fixtureDataSource() = CcCedictDataSource(
        datasetSource = CcCedictDatasetSource {
            CcCedictDatasetOpenResult.Opened(
                requireNotNull(
                    javaClass.getResourceAsStream("/cccedict/cccedict_fixture.u8"),
                ).bufferedReader(Charsets.UTF_8),
            )
        },
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )
}
