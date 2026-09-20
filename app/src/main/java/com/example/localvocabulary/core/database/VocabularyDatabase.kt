package com.example.localvocabulary.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.localvocabulary.core.database.dao.ReviewDao
import com.example.localvocabulary.core.database.entity.ReviewStateEntity
import com.example.localvocabulary.core.database.entity.ReviewEventEntity
import com.example.localvocabulary.core.database.dao.TagDao
import com.example.localvocabulary.core.database.dao.VocabularyDao
import com.example.localvocabulary.core.database.dao.WordbookDao
import com.example.localvocabulary.core.database.entity.EntryTagCrossRef
import com.example.localvocabulary.core.database.entity.EntryDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceFieldEntity
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.entity.EntryWordbookCrossRef
import com.example.localvocabulary.core.database.entity.WordbookEntity
import com.example.localvocabulary.core.database.entity.VocabularyPronunciationEntity
import com.example.localvocabulary.core.database.entity.PronunciationDictionaryProvenanceEntity

@Database(
    entities = [
        VocabularyEntryEntity::class,
        SenseEntity::class,
        ExampleEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class,
        SenseDictionaryProvenanceEntity::class,
        SenseDictionaryProvenanceFieldEntity::class,
        EntryDictionaryProvenanceEntity::class,
        WordbookEntity::class,
        EntryWordbookCrossRef::class,
        VocabularyPronunciationEntity::class,
        PronunciationDictionaryProvenanceEntity::class,
        ReviewStateEntity::class,
        ReviewEventEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class VocabularyDatabase : RoomDatabase() {
    abstract fun reviewDao(): ReviewDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun tagDao(): TagDao
    abstract fun wordbookDao(): WordbookDao
}
