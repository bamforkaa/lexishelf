package com.example.localvocabulary.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance

/** Editing context only. Full dictionary attribution belongs to Settings. */
@Composable
fun DictionaryProvenanceLabel(provenance: DictionaryProvenance, modifier: Modifier = Modifier) {
    MetadataLabel(
        provenance.sourceName.substringBefore(" · ") +
            if (provenance.modifiedAfterImport) " · 수정됨" else "",
        modifier = modifier,
    )
}
