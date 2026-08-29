package com.example.localvocabulary.handwriting.data

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.handwriting.domain.HandwritingLanguageResolution
import com.example.localvocabulary.handwriting.domain.HandwritingLanguageResolver
import com.example.localvocabulary.handwriting.domain.HandwritingModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitDigitalInkLanguageResolver @Inject constructor() : HandwritingLanguageResolver {
    override fun resolve(languageTag: String): HandwritingLanguageResolution {
        val canonical = Bcp47LanguageTag.parse(languageTag)
            ?: return HandwritingLanguageResolution.InvalidLanguageTag
        val identifier = runCatching {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(canonical.value)
        }.getOrNull() ?: return HandwritingLanguageResolution.Unsupported

        return HandwritingLanguageResolution.Supported(
            HandwritingModel(
                requestedLanguageTag = canonical.value,
                modelLanguageTag = identifier.languageTag,
            ),
        )
    }
}
