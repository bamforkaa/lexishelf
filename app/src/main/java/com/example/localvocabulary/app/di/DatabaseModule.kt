package com.example.localvocabulary.app.di

import android.content.Context
import androidx.room.Room
import com.example.localvocabulary.core.common.SystemTimeProvider
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.core.common.UuidStableIdGenerator
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.MIGRATION_1_2
import com.example.localvocabulary.core.database.MIGRATION_2_3
import com.example.localvocabulary.core.database.MIGRATION_3_4
import com.example.localvocabulary.core.database.MIGRATION_4_5
import com.example.localvocabulary.core.database.MIGRATION_5_6
import com.example.localvocabulary.core.database.MIGRATION_6_7
import com.example.localvocabulary.core.database.MIGRATION_7_8
import com.example.localvocabulary.core.database.MIGRATION_8_9
import com.example.localvocabulary.core.database.dao.TagDao
import com.example.localvocabulary.core.database.dao.VocabularyDao
import com.example.localvocabulary.core.database.dao.WordbookDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VocabularyDatabase =
        Room.databaseBuilder(
            context,
            VocabularyDatabase::class.java,
            "vocabulary.db",
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
        ).build()

    @Provides
    fun provideVocabularyDao(database: VocabularyDatabase): VocabularyDao = database.vocabularyDao()

    @Provides
    fun provideTagDao(database: VocabularyDatabase): TagDao = database.tagDao()

    @Provides
    fun provideWordbookDao(database: VocabularyDatabase): WordbookDao = database.wordbookDao()

    @Provides
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider

    @Provides
    fun provideStableIdGenerator(): StableIdGenerator = UuidStableIdGenerator
}
