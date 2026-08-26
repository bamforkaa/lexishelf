package com.example.localvocabulary.dictionary.pack

import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictDatasetOpenResult
import com.example.localvocabulary.dictionary.provider.cccedict.CcCedictPackSource
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictIndexOpenResult
import com.example.localvocabulary.dictionary.provider.jmdict.JmDictIndexSource
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryIndexOpenResult
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryIndexSource
import com.example.localvocabulary.dictionary.provider.panlex.PanLexIndexOpenResult
import com.example.localvocabulary.dictionary.provider.panlex.PanLexIndexSource
import com.example.localvocabulary.dictionary.provider.kaikki.KaikkiIndexOpenResult
import com.example.localvocabulary.dictionary.provider.kaikki.KaikkiIndexSource
import org.junit.Assert.assertSame
import org.junit.Test

class PackBackedProviderSourcesTest {
    private val missingResolver = object : DictionaryPackResolver {
        override fun activePack(providerId: DictionaryProviderId): ResolvedDictionaryPack? = null
    }

    @Test
    fun `all current provider sources report missing when no pack is active`() {
        assertSame(CcCedictDatasetOpenResult.Missing, CcCedictPackSource(missingResolver).open())
        assertSame(JmDictIndexOpenResult.Missing, JmDictIndexSource(missingResolver).open())
        assertSame(
            KoreanBasicDictionaryIndexOpenResult.Missing,
            KoreanBasicDictionaryIndexSource(missingResolver).open(),
        )
        assertSame(PanLexIndexOpenResult.Missing, PanLexIndexSource(missingResolver).open())
        assertSame(KaikkiIndexOpenResult.Missing, KaikkiIndexSource(missingResolver).open("de"))
    }
}
