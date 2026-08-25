package com.example.localvocabulary.dictionary.provider.cccedict

import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.pack.DictionaryPackResolver
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CcCedictPackSource @Inject constructor(
    private val packResolver: DictionaryPackResolver,
) : CcCedictDatasetSource {
    override fun open(): CcCedictDatasetOpenResult {
        val pack = packResolver.activePack(DictionaryProviderId(CcCedictProvider.STABLE_PROVIDER_ID))
            ?: return CcCedictDatasetOpenResult.Missing

        return try {
            val stream = pack.payloadFile.inputStream()
            val content = if (pack.payloadFile.name.endsWith(".gz")) GZIPInputStream(stream) else stream
            CcCedictDatasetOpenResult.Opened(
                BufferedReader(InputStreamReader(content, Charsets.UTF_8)),
            )
        } catch (error: IOException) {
            CcCedictDatasetOpenResult.Failed(error.message)
        }
    }

    override fun activeIdentity(): String? = packResolver
        .activePack(DictionaryProviderId(CcCedictProvider.STABLE_PROVIDER_ID))
        ?.activationIdentity

    override fun datasetVersion(): String? = packResolver
        .activePack(DictionaryProviderId(CcCedictProvider.STABLE_PROVIDER_ID))
        ?.manifest
        ?.datasetVersion
}

internal const val CC_CEDICT_ARTIFACT_NAME = "cedict_1_0_ts_utf-8_mdbg.txt.gz"
