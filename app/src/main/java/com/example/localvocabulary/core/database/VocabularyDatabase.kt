package com.example.localvocabulary.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.localvocabulary.core.database.dao.TagDao
import com.example.localvocabulary.core.database.dao.VocabularyDao
import com.example.localvocabulary.core.database.entity.EntryTagCrossRef
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity

@Database(
    entities = [
        VocabularyEntryEntity::class,
        SenseEntity::class,
        ExampleEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VocabularyDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun tagDao(): TagDao
}
