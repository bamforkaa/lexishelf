package com.example.localvocabulary.core.database.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.localvocabulary.core.database.entity.EntryTagCrossRef
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity

data class SenseWithExamples(
    @Embedded
    val sense: SenseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sense_id",
    )
    val examples: List<ExampleEntity>,
)

data class VocabularyEntryWithDetails(
    @Embedded
    val entry: VocabularyEntryEntity,
    @Relation(
        entity = SenseEntity::class,
        parentColumn = "id",
        entityColumn = "entry_id",
    )
    val senses: List<SenseWithExamples>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryTagCrossRef::class,
            parentColumn = "entry_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)
