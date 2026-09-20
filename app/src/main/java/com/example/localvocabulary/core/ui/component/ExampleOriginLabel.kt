package com.example.localvocabulary.core.ui.component

import com.example.localvocabulary.vocabulary.domain.ExampleOrigin

fun ExampleOrigin.originLabel(): String = when (this) {
    ExampleOrigin.UNKNOWN -> "출처 미상"
    ExampleOrigin.DICTIONARY -> "사전 예문"
    ExampleOrigin.CAPTURED -> "만난 문맥"
    ExampleOrigin.USER -> "내가 만든 문장"
}
