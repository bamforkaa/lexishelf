package com.example.localvocabulary.feature.wordeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DictionarySuggestionSection(
    state: WordEditorUiState,
    onAction: (WordEditorAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("dictionary_suggestions"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("사전 제안", style = MaterialTheme.typography.titleMedium)
        if (state.dictionaryLanguageOptions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.dictionaryLanguageOptions.forEach { option ->
                    FilterChip(
                        selected = state.selectedDictionaryLanguageOptionKey == option.key,
                        onClick = {
                            onAction(WordEditorAction.DictionaryLanguagePairSelected(option.key))
                        },
                        label = { Text(option.label()) },
                    )
                }
            }
        }
        state.dictionarySuggestionMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
        if (state.isDictionarySearchInProgress) {
            CircularProgressIndicator(modifier = Modifier.testTag("dictionary_suggestions_loading"))
        }
        state.dictionarySuggestionGroups.forEach { group ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val license = group.entries.firstOrNull()
                        ?.attribution?.licenseShortName
                    Text(
                        listOfNotNull(group.providerName, license).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    group.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    group.entries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        DictionarySuggestionEntry(entry = entry) { selected ->
                            onAction(WordEditorAction.DictionarySuggestionSelected(selected))
                        }
                    }
                }
            }
        }
        state.dictionaryReference?.let { reference ->
            SelectedDictionaryReference(reference)
        }
    }
}

@Composable
private fun DictionarySuggestionEntry(
    entry: ExternalDictionaryEntry,
    onUse: (ExternalDictionaryEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(entry.headword, style = MaterialTheme.typography.titleMedium)
        if (entry.alternateWrittenForms.isNotEmpty()) {
            Text(entry.alternateWrittenForms.joinToString { "${it.text} (${it.language.value})" })
        }
        val readings = listOfNotNull(entry.linguisticFeatures.reading) +
            entry.linguisticFeatures.alternativeReadings
        if (readings.isNotEmpty()) Text(readings.joinToString { it.text })
        entry.senses.forEachIndexed { senseIndex, sense ->
            Text(sense.meanings.joinToString(separator = "; ") { it.text })
            sense.partOfSpeech?.takeIf(String::isNotBlank)?.let { Text(it) }
            if (sense.writtenFormRestrictions.isNotEmpty() || sense.readingRestrictions.isNotEmpty()) {
                Text(
                    listOfNotNull(
                        sense.writtenFormRestrictions.takeIf { it.isNotEmpty() }
                            ?.joinToString(prefix = "writing: "),
                        sense.readingRestrictions.takeIf { it.isNotEmpty() }
                            ?.joinToString(prefix = "reading: "),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = { onUse(entry.copy(senses = listOf(sense))) },
                modifier = Modifier.testTag(
                    if (senseIndex == 0) "dictionary_suggestion_use" else {
                        "dictionary_suggestion_use_$senseIndex"
                    },
                ),
            ) { Text("Use this sense") }
        }
        if (entry.senses.isEmpty()) {
            TextButton(
                onClick = { onUse(entry) },
                modifier = Modifier.testTag("dictionary_suggestion_use"),
            ) { Text("사용") }
        }
    }
}

@Composable
private fun SelectedDictionaryReference(entry: ExternalDictionaryEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dictionary_reference"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("선택한 사전 참고 자료", style = MaterialTheme.typography.titleSmall)
            Text(entry.headword, style = MaterialTheme.typography.titleMedium)
            val readings = listOfNotNull(entry.linguisticFeatures.reading) +
                entry.linguisticFeatures.alternativeReadings
            if (readings.isNotEmpty()) Text(readings.joinToString { it.text })
            entry.senses.forEach { sense ->
                Text(sense.meanings.joinToString(separator = "; ") { it.text })
            }
            Text(
                listOfNotNull(
                    entry.attribution.sourceName,
                    entry.attribution.licenseShortName,
                ).joinToString(" · "),
            )
            Text(
                "참고 자료 자체는 사용자 입력란과 분리됩니다.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun DictionaryLanguagePairOption.label(): String {
    val kind = when (languagePair.resultKind) {
        DictionaryResultKind.MONOLINGUAL_DEFINITION -> "정의"
        DictionaryResultKind.TRANSLATION -> "번역"
    }
    return "${languagePair.sourceLanguage.value} → ${languagePair.resultLanguage.value} · $kind"
}
