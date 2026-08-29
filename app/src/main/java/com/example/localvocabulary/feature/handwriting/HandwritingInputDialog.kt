package com.example.localvocabulary.feature.handwriting

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.localvocabulary.core.model.LanguageDisplayNameResolver
import com.example.localvocabulary.feature.language.LanguagePickerDialog
import com.example.localvocabulary.handwriting.domain.HandwritingPoint
import com.example.localvocabulary.handwriting.domain.HandwritingStroke

@Composable
fun HandwritingInputDialog(
    state: HandwritingInputUiState,
    onAction: (HandwritingInputAction) -> Unit,
    onCandidateSelected: (String) -> Unit,
) {
    if (!state.isOpen) return

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    Dialog(onDismissRequest = { onAction(HandwritingInputAction.Close) }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .testTag("handwriting_dialog"),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("손글씨 입력", style = MaterialTheme.typography.headlineSmall)

                // This surface stays above every state-dependent section. Together with the
                // fixed dialog height, candidate/model recomposition cannot move its bounds.
                HandwritingCanvas(state = state, onAction = onAction)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { onAction(HandwritingInputAction.UndoLastStroke) },
                        enabled = !state.ink.isEmpty,
                        modifier = Modifier.testTag("handwriting_undo"),
                    ) { Text("한 획 취소") }
                    TextButton(
                        onClick = { onAction(HandwritingInputAction.Clear) },
                        enabled = !state.ink.isEmpty || state.candidates.isNotEmpty(),
                        modifier = Modifier.testTag("handwriting_clear"),
                    ) { Text("전체 지우기") }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .testTag("handwriting_results_panel"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HandwritingLanguageSelector(state = state, onAction = onAction)
                    HandwritingStatus(state = state, onAction = onAction)
                    HandwritingCandidates(
                        state = state,
                        onAction = onAction,
                        onCandidateSelected = onCandidateSelected,
                    )
                }

                TextButton(
                    onClick = { onAction(HandwritingInputAction.Close) },
                    modifier = Modifier.align(Alignment.End).testTag("close_handwriting"),
                ) { Text("닫기") }
            }
        }
    }
}

@Composable
private fun HandwritingLanguageSelector(
    state: HandwritingInputUiState,
    onAction: (HandwritingInputAction) -> Unit,
) {
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    val visibleOptions = buildList {
        addAll(state.languageOptions.take(MAX_VISIBLE_LANGUAGE_CHIPS))
        state.selectedLanguageTag?.takeIf { it !in this }?.let(::add)
    }

    Text("인식 언어", style = MaterialTheme.typography.titleMedium)
    Text(
        "필기 언어를 자동 판정하지 않습니다. 같은 필기를 다른 언어 모델로 다시 시도할 수 있습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleOptions.forEach { languageTag ->
            val display = LanguageDisplayNameResolver.resolve(languageTag)
            val isSelected = state.selectedLanguageTag == languageTag
            FilterChip(
                selected = isSelected,
                onClick = { onAction(HandwritingInputAction.LanguageSelected(languageTag)) },
                label = { Text(display.name) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("handwriting_language_$languageTag")
                    .semantics {
                        selected = isSelected
                        stateDescription = if (isSelected) "선택됨" else "선택 안 됨"
                    },
            )
        }
        FilterChip(
            selected = false,
            onClick = { pickerVisible = true },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text("다른 언어") },
            modifier = Modifier.heightIn(min = 48.dp).testTag("handwriting_more_languages"),
        )
    }
    state.selectedLanguageTag?.let { selected ->
        val display = LanguageDisplayNameResolver.resolve(selected)
        Text(
            buildString {
                append(display.name)
                append(" · ")
                append(display.languageTag)
                state.modelLanguageTag?.takeIf { it != display.languageTag }?.let {
                    append(" · 모델 ")
                    append(it)
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (pickerVisible) {
        LanguagePickerDialog(
            currentTag = state.selectedLanguageTag.orEmpty(),
            userLanguageTags = state.languageOptions.toSet(),
            onSelected = {
                onAction(HandwritingInputAction.LanguageSelected(it))
                pickerVisible = false
            },
            onUserLanguageAdded = {},
            onDismiss = { pickerVisible = false },
        )
    }
}

@Composable
private fun HandwritingCanvas(
    state: HandwritingInputUiState,
    onAction: (HandwritingInputAction) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(HANDWRITING_CANVAS_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, outlineColor, RoundedCornerShape(12.dp))
            .testTag("handwriting_canvas")
            .semantics { contentDescription = "손글씨 입력 영역" }
            .pointerInput(Unit) {
                onAction(
                    HandwritingInputAction.WritingAreaChanged(
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                    ),
                )
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastPosition = down.position
                    onAction(
                        HandwritingInputAction.StrokeStarted(
                            HandwritingPoint(
                                x = lastPosition.x,
                                y = lastPosition.y,
                                timestampMillis = SystemClock.uptimeMillis(),
                            ),
                        ),
                    )
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            onAction(HandwritingInputAction.StrokeEnded())
                            break
                        }
                        val point = HandwritingPoint(
                            x = change.position.x,
                            y = change.position.y,
                            timestampMillis = SystemClock.uptimeMillis(),
                        )
                        if (!change.pressed) {
                            onAction(HandwritingInputAction.StrokeEnded(point))
                            change.consume()
                            break
                        }
                        if (change.position != lastPosition) {
                            onAction(HandwritingInputAction.StrokeContinued(point))
                            lastPosition = change.position
                        }
                        change.consume()
                    }
                }
            },
    ) {
        state.ink.strokes.forEach { drawStroke(it, lineColor) }
        state.ink.activePoints.takeIf { it.isNotEmpty() }?.let { points ->
            drawStroke(HandwritingStroke(points), lineColor)
        }
    }
}

@Composable
private fun HandwritingStatus(
    state: HandwritingInputUiState,
    onAction: (HandwritingInputAction) -> Unit,
) {
    when (state.modelState) {
        HandwritingModelUiState.NoLanguageSelected ->
            StatusText("필기를 유지한 채 인식 언어를 선택하세요.")
        HandwritingModelUiState.Resolving -> StatusProgress("손글씨 모델을 확인하는 중입니다.")
        HandwritingModelUiState.UnsupportedLanguage ->
            StatusText("이 언어의 손글씨 인식은 지원되지 않습니다.")
        HandwritingModelUiState.InvalidLanguageTag ->
            StatusText("올바른 BCP 47 언어 태그를 선택하세요.")
        HandwritingModelUiState.ModelMissing -> {
            StatusText("이 언어의 손글씨 모델이 설치되어 있지 않습니다. (약 20MB)")
            Button(
                onClick = { onAction(HandwritingInputAction.DownloadModel) },
                modifier = Modifier.testTag("download_handwriting_model"),
            ) { Text("모델 다운로드") }
        }
        HandwritingModelUiState.Downloading -> StatusProgress("손글씨 모델을 다운로드하는 중입니다.")
        HandwritingModelUiState.Ready -> when {
            state.isRecognizing -> StatusProgress("손글씨를 인식하는 중입니다.")
            state.recognitionFailed -> StatusText("손글씨를 인식하지 못했습니다. 다시 써 보세요.")
            state.ink.isEmpty -> StatusText("글씨를 쓴 뒤 현재 언어 모델로 인식합니다.")
            state.candidates.isEmpty() -> StatusText("획 입력이 끝나면 자동으로 인식합니다.")
            else -> StatusText("후보를 선택해야 입력에 반영됩니다.")
        }
        HandwritingModelUiState.ModelOperationFailed -> {
            StatusText("모델을 확인하거나 다운로드하지 못했습니다. 네트워크를 확인하세요.")
            Button(
                onClick = { onAction(HandwritingInputAction.DownloadModel) },
                modifier = Modifier.testTag("retry_handwriting_model"),
            ) { Text("다시 시도") }
        }
    }
}

@Composable
private fun HandwritingCandidates(
    state: HandwritingInputUiState,
    onAction: (HandwritingInputAction) -> Unit,
    onCandidateSelected: (String) -> Unit,
) {
    Text("인식 후보", style = MaterialTheme.typography.titleMedium)
    if (state.candidates.isEmpty()) {
        Text(
            "아직 표시할 후보가 없습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("handwriting_candidates_empty"),
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.candidates.forEach { candidate ->
            val isSelected = candidate.text == state.selectedCandidate
            FilterChip(
                selected = isSelected,
                onClick = {
                    onCandidateSelected(candidate.text)
                    onAction(HandwritingInputAction.CandidateAccepted(candidate.text))
                },
                label = { Text(candidate.text) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("handwriting_candidate_${candidate.text}")
                    .semantics {
                        selected = isSelected
                        stateDescription = if (isSelected) "선택됨" else "선택 안 됨"
                    },
            )
        }
    }
}

@Composable
private fun StatusProgress(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        StatusText(text)
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("handwriting_status"),
    )
}

private fun DrawScope.drawStroke(stroke: HandwritingStroke, color: androidx.compose.ui.graphics.Color) {
    if (stroke.points.size == 1) {
        drawCircle(color = color, radius = 3.dp.toPx(), center = stroke.points.first().toOffset())
        return
    }
    val path = Path().apply {
        val first = stroke.points.first()
        moveTo(first.x, first.y)
        stroke.points.drop(1).forEach { point -> lineTo(point.x, point.y) }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
    )
}

private fun HandwritingPoint.toOffset() = androidx.compose.ui.geometry.Offset(x, y)

private val HANDWRITING_CANVAS_HEIGHT = 240.dp
private const val MAX_VISIBLE_LANGUAGE_CHIPS = 5
