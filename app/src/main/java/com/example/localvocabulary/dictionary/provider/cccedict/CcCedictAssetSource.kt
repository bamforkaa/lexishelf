package com.example.localvocabulary.dictionary.provider.cccedict

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CcCedictAssetSource @Inject constructor(
    @ApplicationContext context: Context,
) : CcCedictDatasetSource {
    private val assets = context.assets

    override fun open(): CcCedictDatasetOpenResult {
        val names = try {
            assets.list(CC_CEDICT_ASSET_DIRECTORY).orEmpty()
        } catch (error: IOException) {
            return CcCedictDatasetOpenResult.Failed(error.message)
        }
        val assetName = when {
            CC_CEDICT_ARTIFACT_NAME in names -> CC_CEDICT_ARTIFACT_NAME
            CC_CEDICT_UNPACKED_ASSET_NAME in names -> CC_CEDICT_UNPACKED_ASSET_NAME
            else -> return CcCedictDatasetOpenResult.Missing
        }

        return try {
            val stream = assets.open("$CC_CEDICT_ASSET_DIRECTORY/$assetName")
            val content = if (assetName.endsWith(".gz")) GZIPInputStream(stream) else stream
            CcCedictDatasetOpenResult.Opened(
                BufferedReader(InputStreamReader(content, Charsets.UTF_8)),
            )
        } catch (error: IOException) {
            CcCedictDatasetOpenResult.Failed(error.message)
        }
    }
}

internal const val CC_CEDICT_ASSET_DIRECTORY = "dictionary/cccedict"
internal const val CC_CEDICT_ARTIFACT_NAME = "cedict_1_0_ts_utf-8_mdbg.txt.gz"
private const val CC_CEDICT_UNPACKED_ASSET_NAME = "cedict_1_0_ts_utf-8_mdbg.txt"
