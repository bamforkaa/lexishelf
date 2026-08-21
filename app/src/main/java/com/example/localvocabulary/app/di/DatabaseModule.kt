package com.example.localvocabulary.app.di

import android.content.Context
import androidx.room.Room
import com.example.localvocabulary.core.common.SystemTimeProvider
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.dao.TagDao
import com.example.localvocabulary.core.database.dao.VocabularyDao
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
        ).build()

    @Provides
    fun provideVocabularyDao(database: VocabularyDatabase): VocabularyDao = database.vocabularyDao()

    @Provides
    fun provideTagDao(database: VocabularyDatabase): TagDao = database.tagDao()

    @Provides
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider
}
