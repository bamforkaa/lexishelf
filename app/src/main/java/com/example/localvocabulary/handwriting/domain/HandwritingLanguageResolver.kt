package com.example.localvocabulary.handwriting.domain

sealed interface HandwritingLanguageResolution {
    data class Supported(val model: HandwritingModel) : HandwritingLanguageResolution
    data object Unsupported : HandwritingLanguageResolution
    data object InvalidLanguageTag : HandwritingLanguageResolution
}

interface HandwritingLanguageResolver {
    fun resolve(languageTag: String): HandwritingLanguageResolution
}
