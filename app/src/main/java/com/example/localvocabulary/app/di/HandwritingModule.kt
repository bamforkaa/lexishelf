package com.example.localvocabulary.app.di

import com.example.localvocabulary.handwriting.data.DataStoreHandwritingLanguagePreferences
import com.example.localvocabulary.handwriting.data.MlKitDigitalInkLanguageResolver
import com.example.localvocabulary.handwriting.data.MlKitHandwritingRecognitionService
import com.example.localvocabulary.handwriting.domain.HandwritingLanguagePreferences
import com.example.localvocabulary.handwriting.domain.HandwritingLanguageResolver
import com.example.localvocabulary.handwriting.domain.HandwritingRecognitionService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HandwritingModule {
    @Binds
    @Singleton
    abstract fun bindHandwritingLanguagePreferences(
        implementation: DataStoreHandwritingLanguagePreferences,
    ): HandwritingLanguagePreferences

    @Binds
    @Singleton
    abstract fun bindHandwritingLanguageResolver(
        implementation: MlKitDigitalInkLanguageResolver,
    ): HandwritingLanguageResolver

    @Binds
    @Singleton
    abstract fun bindHandwritingRecognitionService(
        implementation: MlKitHandwritingRecognitionService,
    ): HandwritingRecognitionService
}
