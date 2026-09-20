package com.example.localvocabulary.feature.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localvocabulary.review.domain.ReviewMode
import com.example.localvocabulary.review.domain.ReviewRating

fun ReviewMode.reviewLabel(): String = when (this) {
    ReviewMode.MEANING_TO_EXPRESSION -> "의미 → 표현"
    ReviewMode.EXPRESSION_TO_MEANING -> "표현 → 의미"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayReviewScreen(state: TodayReviewUiState, onAction: (TodayReviewAction) -> Unit, onBack: () -> Unit) {
    var writing by rememberSaveable(state.card?.state?.stableId, state.isRetry) { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("오늘 복습") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.isLoading) item { CircularProgressIndicator() }
            state.error?.let { message -> item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { onAction(TodayReviewAction.Refresh) }, enabled = !state.isBusy) { Text("다시 시도") }
            } }
            val card = state.card
            if (!state.isLoading && card == null) {
                item {
                    Text("지금 할 복습이 없습니다", style = MaterialTheme.typography.headlineSmall)
                    Text("새 표현의 첫 뜻은 자동으로 등록됩니다. 상세 화면에서 다른 뜻도 복습에 추가할 수 있습니다. 오늘 한도에 도달한 항목은 다음 날 이어집니다.")
                    if (state.completed > 0) Text("오늘 이 세션에서 ${state.completed}개를 복습했습니다.")
                    if (state.unsupportedCount > 0) Text("다른 버전에서 저장한 복습 ${state.unsupportedCount}개는 지원하는 앱 버전이 필요합니다.")
                    TextButton(onClick = { onAction(TodayReviewAction.Refresh) }, enabled = !state.isBusy) { Text("목록 새로고침") }
                }
            } else if (card != null) {
                item {
                    Text(if (state.isRetry) "한 번 더 떠올리기" else "${card.promptDirection.reviewLabel()} · 남은 정규 복습 ${state.remaining}개",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    val prompt = if (card.promptDirection == ReviewMode.EXPRESSION_TO_MEANING) card.expression
                    else if (card.meaning.contains(card.expression, ignoreCase = true))
                        "뜻에 정답이 포함되어 단서를 숨겼습니다. 표현을 떠올린 뒤 답을 확인하세요."
                    else card.meaning
                    Text(prompt, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("review_prompt"))
                }
                if (!state.revealed) item {
                    Text("머릿속으로 떠올려 보세요. 입력하지 않아도 됩니다.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { onAction(TodayReviewAction.Reveal) }, enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("review_reveal")) { Text("답 보기") }
                } else {
                    item {
                        HorizontalDivider()
                        Text(card.expression, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("review_answer"))
                        Text(card.meaning, style = MaterialTheme.typography.bodyLarge)

                    }
                    card.examples.forEach { example -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(example.text, style = MaterialTheme.typography.bodyLarge)
                            if (example.meaning.isNotBlank()) Text(example.meaning)
                            val source = listOfNotNull(example.sourceTitle, example.sourceLocator, example.sourceUrl)
                                .filter(String::isNotBlank).joinToString(" · ")
                            if (source.isNotBlank()) Text(source, style = MaterialTheme.typography.bodySmall)
                        }
                    } }
                    if (!state.rated) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewRating.entries.forEach { rating ->
                                OutlinedButton(onClick = { onAction(TodayReviewAction.Rate(rating)) }, enabled = !state.isBusy && !state.hasPendingEvaluation,
                                    modifier = Modifier.fillMaxWidth().testTag("review_rate_${rating.name}")) {
                                    Text(when (rating) {
                                        ReviewRating.FORGOT -> "기억 안 남"
                                        ReviewRating.HARD -> "어렵게 기억남"
                                        ReviewRating.REMEMBERED -> "기억남"
                                    })
                                }
                            }
                        }
                    } else item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (state.isRetry) "재시도를 기록했습니다. 다음 복습 일정은 그대로입니다." else "평가와 다음 복습 일정을 저장했습니다.")
                            TextButton(onClick = { writing = true }, enabled = !state.exampleSaved) { Text("이 표현으로 내 문장 만들기") }
                            if (writing) {
                                OutlinedTextField(value = state.exampleText,
                                    onValueChange = { onAction(TodayReviewAction.ExampleChanged(it)) },
                                    label = { Text("내 문장") }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("review_user_example"),
                                    enabled = !state.isBusy && !state.exampleSaved)
                                Button(onClick = { onAction(TodayReviewAction.SaveExample) },
                                    enabled = !state.isBusy && !state.exampleSaved && state.exampleText.isNotBlank()) {
                                    Text(if (state.exampleSaved) "문장을 저장했습니다" else "문장 저장")
                                }
                            }
                            Button(onClick = { onAction(TodayReviewAction.Next) }, enabled = !state.isBusy,
                                modifier = Modifier.fillMaxWidth().testTag("review_next")) { Text("다음") }
                        }
                    }
                }
            }
        }
    }
}
