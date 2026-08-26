package com.example.localvocabulary.feature.wordeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.core.ui.component.SectionHeader

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
        SectionHeader(
            title = "사전 제안",
            supportingText = "뜻을 누를 때만 내 단어에 가져옵니다.",
        )
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
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isDictionarySearchInProgress) {
            CircularProgressIndicator(modifier = Modifier.testTag("dictionary_suggestions_loading"))
        }
        val rows = state.dictionarySuggestionGroups.toRows()
        if (rows.isNotEmpty()) {
            val selectableRowCount = rows.count { it is DictionarySuggestionRow.Entry }
            if (selectableRowCount <= MAX_VISIBLE_SELECTABLE_ROWS) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dictionary_suggestion_content"),
                    verticalArrangement = Arrangement.spacedBy(SUGGESTION_ROW_SPACING),
                ) {
                    rows.forEach { row ->
                        key(row.key) {
                            DictionarySuggestionRowContent(row, state, onAction)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BOUNDED_SUGGESTION_HEIGHT)
                        .testTag("dictionary_suggestion_list"),
                    verticalArrangement = Arrangement.spacedBy(SUGGESTION_ROW_SPACING),
                ) {
                    items(
                        items = rows,
                        key = DictionarySuggestionRow::key,
                    ) { row ->
                        DictionarySuggestionRowContent(row, state, onAction)
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
private fun DictionarySuggestionRowContent(
    row: DictionarySuggestionRow,
    state: WordEditorUiState,
    onAction: (WordEditorAction) -> Unit,
) {
    when (row) {
        is DictionarySuggestionRow.ProviderHeader -> {
            Text(
                row.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }
        is DictionarySuggestionRow.Message -> {
            Text(row.text, style = MaterialTheme.typography.bodySmall)
        }
        is DictionarySuggestionRow.Entry -> {
            DictionarySuggestionEntryRow(
                entry = row.entry,
                isSelected = row.entry.suggestionKey() in state.selectedSuggestionKeys,
                onClick = {
                    onAction(WordEditorAction.DictionarySuggestionSelected(row.entry))
                },
            )
        }
    }
}

@Composable
private fun DictionarySuggestionEntryRow(
    entry: ExternalDictionaryEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val sense = entry.senses.singleOrNull()
    val meanings = sense?.meanings.orEmpty()
    val primaryMeaning = meanings.joinToString(separator = "; ") { it.text }
        .ifBlank { entry.headword }
    val resultLanguages = meanings.map { it.language.value }.distinct().joinToString()
    val readings = listOfNotNull(entry.linguisticFeatures.reading) +
        entry.linguisticFeatures.alternativeReadings
    val pronunciations = entry.linguisticFeatures.pronunciations
        .mapNotNull { it.text?.takeIf(String::isNotBlank) }
    val availableFormCount = entry.linguisticFeatures.totalInflectionCount
    val availableExampleCount = sense?.availableExampleCount ?: 0
    val secondaryText = buildList {
        if (primaryMeaning != entry.headword) add(entry.headword)
        if (readings.isNotEmpty()) add(readings.joinToString { it.text })
        if (pronunciations.isNotEmpty()) add(pronunciations.joinToString())
        sense?.partOfSpeech?.takeIf(String::isNotBlank)?.let(::add)
        sense?.grammaticalGender?.takeIf(String::isNotBlank)?.let(::add)
    }.joinToString(" · ")
    val availabilityText = buildList {
        if (availableFormCount > 0) add("활용형 $availableFormCount")
        if (availableExampleCount > 0) add("예문 $availableExampleCount")
    }.joinToString(" · ")

    Surface(
        onClick = onClick,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RectangleShape,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dictionary_suggestion_row")
            .semantics {
                selected = isSelected
                stateDescription = if (isSelected) "가져옴" else "가져오지 않음"
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(primaryMeaning, style = MaterialTheme.typography.bodyLarge)
                if (secondaryText.isNotBlank()) {
                    Text(
                        secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (availabilityText.isNotBlank()) MetadataLabel(availabilityText)
                if (resultLanguages.isNotBlank()) MetadataLabel(resultLanguages)
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "가져옴",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private sealed interface DictionarySuggestionRow {
    val key: String

    data class ProviderHeader(override val key: String, val label: String) : DictionarySuggestionRow
    data class Message(override val key: String, val text: String) : DictionarySuggestionRow
    data class Entry(override val key: String, val entry: ExternalDictionaryEntry) :
        DictionarySuggestionRow
}

private fun List<DictionarySuggestionGroup>.toRows(): List<DictionarySuggestionRow> {
    val groups = this
    return buildList {
        groups.forEach { group ->
            val license = group.entries.firstOrNull()?.attribution?.licenseShortName
            add(
                DictionarySuggestionRow.ProviderHeader(
                    key = "${group.providerId.value}|header",
                    label = listOfNotNull(group.providerName, license).joinToString(" · "),
                ),
            )
            group.message?.let {
                add(DictionarySuggestionRow.Message("${group.providerId.value}|message", it))
            }
            group.selectableEntries().forEachIndexed { index, entry ->
                add(
                    DictionarySuggestionRow.Entry(
                        key = "${group.providerId.value}|$index|${entry.suggestionKey()}",
                        entry = entry,
                    ),
                )
            }
        }
    }
}

private const val MAX_VISIBLE_SELECTABLE_ROWS = 4
private val BOUNDED_SUGGESTION_HEIGHT = 352.dp
private val SUGGESTION_ROW_SPACING = 6.dp

@Composable
private fun SelectedDictionaryReference(entry: ExternalDictionaryEntry) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RectangleShape,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dictionary_reference"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("선택한 사전 참고 자료 · 저장되지 않음", style = MaterialTheme.typography.titleSmall)
            Text(entry.headword, style = MaterialTheme.typography.titleMedium)
            val readings = listOfNotNull(entry.linguisticFeatures.reading) +
                entry.linguisticFeatures.alternativeReadings
            if (readings.isNotEmpty()) Text(readings.joinToString { it.text })
            entry.senses.forEach { sense ->
                Text(sense.meanings.joinToString(separator = "; ") { it.text })
            }
            MetadataLabel(
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
