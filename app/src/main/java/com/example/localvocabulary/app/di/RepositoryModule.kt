package com.example.localvocabulary.app.di

import com.example.localvocabulary.settings.DataStoreSettingsRepository
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.data.RoomTagRepository
import com.example.localvocabulary.vocabulary.data.RoomVocabularyRepository
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindVocabularyRepository(
        implementation: RoomVocabularyRepository,
    ): VocabularyRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(implementation: RoomTagRepository): TagRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: DataStoreSettingsRepository,
    ): SettingsRepository
}
