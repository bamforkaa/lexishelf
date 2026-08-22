package com.example.localvocabulary.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.localvocabulary.core.database.dao.TagDao
import com.example.localvocabulary.core.database.dao.VocabularyDao
import com.example.localvocabulary.core.database.entity.EntryTagCrossRef
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceFieldEntity
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity

@Database(
    entities = [
        VocabularyEntryEntity::class,
        SenseEntity::class,
        ExampleEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class,
        SenseDictionaryProvenanceEntity::class,
        SenseDictionaryProvenanceFieldEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class VocabularyDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun tagDao(): TagDao
}
