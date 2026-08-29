package com.example.localvocabulary.feature.language

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.core.model.AppLanguageCatalog
import com.example.localvocabulary.core.model.LanguageDisplayNameResolver
import com.example.localvocabulary.core.ui.component.MetadataLabel
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator

@Composable
fun LanguagePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    userLanguageTags: Set<String> = emptySet(),
    onUserLanguageAdded: (String) -> Unit = {},
    isError: Boolean = false,
    supportingText: String? = null,
    testTag: String = "language_picker_field",
) {
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    val display = LanguageDisplayNameResolver.resolve(value)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = supportingText?.let { text -> { Text(text) } },
            trailingIcon = {
                IconButton(
                    onClick = { pickerVisible = true },
                    modifier = Modifier.testTag("${testTag}_open"),
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "언어 선택")
                }
            },
            singleLine = true,
            isError = isError,
            modifier = Modifier.fillMaxWidth().testTag(testTag),
        )
        if (value.isNotBlank()) {
            MetadataLabel(
                text = if (display.name == display.languageTag) {
                    display.languageTag
                } else {
                    "${display.name} · ${display.languageTag}"
                },
                maxLines = 2,
                modifier = Modifier.testTag("${testTag}_display"),
            )
        }
    }

    if (pickerVisible) {
        LanguagePickerDialog(
            currentTag = value,
            userLanguageTags = userLanguageTags,
            onSelected = {
                onValueChange(it)
                pickerVisible = false
            },
            onUserLanguageAdded = onUserLanguageAdded,
            onDismiss = { pickerVisible = false },
        )
    }
}

@Composable
internal fun LanguagePickerDialog(
    currentTag: String,
    userLanguageTags: Set<String>,
    onSelected: (String) -> Unit,
    onUserLanguageAdded: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var manualMode by rememberSaveable { mutableStateOf(false) }
    var manualTag by rememberSaveable(currentTag) { mutableStateOf(currentTag) }
    var manualError by rememberSaveable { mutableStateOf<String?>(null) }
    val userEntries = AppLanguageCatalog.userEntries(userLanguageTags)
    val currentEntry = AppLanguageCatalog.currentEntry(currentTag, userEntries)
    val currentResults = AppLanguageCatalog.search(query, listOfNotNull(currentEntry))
    val userResults = AppLanguageCatalog.search(query, userEntries)
    val builtInResults = AppLanguageCatalog.search(query)
    val canonicalCurrentTag = LanguageDisplayNameResolver.resolve(currentTag).languageTag

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("언어 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (manualMode) {
                    OutlinedTextField(
                        value = manualTag,
                        onValueChange = {
                            manualTag = it
                            manualError = null
                        },
                        label = { Text("BCP 47 언어 태그") },
                        supportingText = {
                            Text(manualError ?: "예: en-GB, pt-BR, sr-Latn")
                        },
                        isError = manualError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("manual_language_tag"),
                    )
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("언어 이름 또는 코드 검색") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "언어 검색어 지우기")
                                }
                            }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("language_search"),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .testTag("language_catalog_list"),
                    ) {
                        if (currentResults.isNotEmpty()) {
                            item(key = "current-header") { LanguageSectionLabel("현재 언어") }
                            items(currentResults, key = { "current-${it.languageTag}" }) { language ->
                                LanguageOptionRow(language, canonicalCurrentTag, onSelected)
                            }
                        }
                        if (userResults.isNotEmpty()) {
                            item(key = "user-header") { LanguageSectionLabel("추가한 언어") }
                            items(userResults, key = { "user-${it.languageTag}" }) { language ->
                                LanguageOptionRow(language, canonicalCurrentTag, onSelected)
                            }
                        }
                        if (builtInResults.isNotEmpty()) {
                            item(key = "built-in-header") { LanguageSectionLabel("기본 언어") }
                            items(builtInResults, key = { "built-in-${it.languageTag}" }) { language ->
                                LanguageOptionRow(language, canonicalCurrentTag, onSelected)
                            }
                        }
                        if (
                            currentResults.isEmpty() &&
                            userResults.isEmpty() &&
                            builtInResults.isEmpty()
                        ) {
                            item(key = "empty") {
                                Text(
                                    "검색 결과가 없습니다.",
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (manualMode) {
                TextButton(
                    onClick = {
                        val normalized = VocabularyEntryValidator.normalizeLanguageTag(manualTag)
                        if (normalized == null) {
                            manualError = "유효한 BCP 47 언어 태그를 입력하세요."
                        } else {
                            onUserLanguageAdded(normalized)
                            onSelected(normalized)
                        }
                    },
                    modifier = Modifier.testTag("confirm_manual_language"),
                ) { Text("선택") }
            } else {
                TextButton(onClick = { manualMode = true }) { Text("직접 태그 입력") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (manualMode) {
                        manualMode = false
                        manualError = null
                    } else {
                        onDismiss()
                    }
                },
            ) { Text(if (manualMode) "목록으로" else "취소") }
        },
    )
}

@Composable
private fun LanguageSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun LanguageOptionRow(
    language: com.example.localvocabulary.core.model.LanguageCatalogEntry,
    currentTag: String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onSelected(language.languageTag) }
            .padding(vertical = 10.dp)
            .testTag("language_option_${language.languageTag}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(language.nativeName, style = MaterialTheme.typography.bodyLarge)
            MetadataLabel(
                listOf(language.localizedName, language.englishName, language.languageTag)
                    .distinct().joinToString(" · "),
            )
        }
        if (language.languageTag == currentTag) {
            Icon(Icons.Default.Check, contentDescription = "현재 언어")
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
