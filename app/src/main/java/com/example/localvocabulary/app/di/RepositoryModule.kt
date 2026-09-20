package com.example.localvocabulary.app.di

import com.example.localvocabulary.backup.data.ContentResolverBackupFileStore
import com.example.localvocabulary.backup.data.KotlinxBackupSerializer
import com.example.localvocabulary.backup.data.RoomVocabularyBackupRepository
import com.example.localvocabulary.backup.domain.BackupFileStore
import com.example.localvocabulary.backup.domain.BackupSerializer
import com.example.localvocabulary.backup.domain.VocabularyBackupRepository
import com.example.localvocabulary.settings.DataStoreSettingsRepository
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.data.RoomTagRepository
import com.example.localvocabulary.vocabulary.data.RoomVocabularyRepository
import com.example.localvocabulary.vocabulary.data.RoomWordbookRepository
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
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
    abstract fun bindReviewRepository(
        implementation: com.example.localvocabulary.review.data.RoomReviewRepository,
    ): com.example.localvocabulary.review.domain.ReviewRepository

    @Binds
    @Singleton
    abstract fun bindVocabularyBackupRepository(
        implementation: RoomVocabularyBackupRepository,
    ): VocabularyBackupRepository

    @Binds
    @Singleton
    abstract fun bindBackupSerializer(implementation: KotlinxBackupSerializer): BackupSerializer

    @Binds
    @Singleton
    abstract fun bindBackupFileStore(implementation: ContentResolverBackupFileStore): BackupFileStore

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
    abstract fun bindWordbookRepository(
        implementation: RoomWordbookRepository,
    ): WordbookRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: DataStoreSettingsRepository,
    ): SettingsRepository
}
