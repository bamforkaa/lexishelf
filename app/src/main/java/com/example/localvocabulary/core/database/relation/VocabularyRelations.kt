package com.example.localvocabulary.core.database.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.localvocabulary.core.database.entity.EntryTagCrossRef
import com.example.localvocabulary.core.database.entity.EntryDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.ExampleEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceEntity
import com.example.localvocabulary.core.database.entity.SenseDictionaryProvenanceFieldEntity
import com.example.localvocabulary.core.database.entity.SenseEntity
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.entity.EntryWordbookCrossRef
import com.example.localvocabulary.core.database.entity.WordbookEntity

data class SenseWithExamples(
    @Embedded
    val sense: SenseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sense_id",
    )
    val examples: List<ExampleEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "sense_id",
    )
    val provenance: SenseDictionaryProvenanceEntity? = null,
    @Relation(
        parentColumn = "id",
        entityColumn = "sense_id",
    )
    val provenanceFields: List<SenseDictionaryProvenanceFieldEntity> = emptyList(),
)

data class VocabularyEntryWithDetails(
    @Embedded
    val entry: VocabularyEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "entry_id",
    )
    val entryProvenance: List<EntryDictionaryProvenanceEntity> = emptyList(),
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
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryWordbookCrossRef::class,
            parentColumn = "entry_id",
            entityColumn = "wordbook_id",
        ),
    )
    val wordbooks: List<WordbookEntity> = emptyList(),
)
