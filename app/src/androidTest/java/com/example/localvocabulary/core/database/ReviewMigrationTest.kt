package com.example.localvocabulary.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReviewMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), VocabularyDatabase::class.java)

    @Test fun v7ToV8PreservesContextIdsAndDoesNotAutoEnroll() {
        helper.createDatabase("review-migration-7", 7).apply {
            execSQL("INSERT INTO vocabulary_entries(id, backup_id, headword, language_tag, notes, created_at_epoch_millis, modified_at_epoch_millis, reading) VALUES(1,'entry','phrase','en','note',10,20,'')")
            execSQL("INSERT INTO senses(id,entry_id,meaning,part_of_speech,sort_order,stable_id) VALUES(10,1,'meaning','',0,'sense')")
            execSQL("INSERT INTO examples(id,sense_id,text,sort_order,meaning,origin,source_title,source_url,source_locator,captured_at,stable_id) VALUES(100,10,'context',0,'translation','CAPTURED','title','https://example.com','p.1',15,'example')")
            close()
        }
        helper.runMigrationsAndValidate("review-migration-7", 8, true, MIGRATION_7_8).use { db ->
            db.query("SELECT id,stable_id,meaning,source_title,captured_at FROM examples").use {
                assertTrue(it.moveToFirst()); assertEquals(100L, it.getLong(0)); assertEquals("example", it.getString(1))
                assertEquals("translation", it.getString(2)); assertEquals("title", it.getString(3)); assertEquals(15L, it.getLong(4))
            }
            db.query("SELECT * FROM review_states").use { assertEquals(0, it.count) }
            db.query("SELECT * FROM review_events").use { assertEquals(0, it.count) }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }

    @Test fun entireMigrationChainReachesV9WithoutLosingRows() {
        helper.createDatabase("review-migration-1", 1).apply {
            execSQL("INSERT INTO vocabulary_entries VALUES(1,'phrase','en','note',10,20)")
            execSQL("INSERT INTO senses VALUES(10,1,'meaning','',0)")
            execSQL("INSERT INTO examples VALUES(100,10,'context',0)")
            close()
        }
        helper.runMigrationsAndValidate("review-migration-1", 9, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        ).use { db ->
            db.query("SELECT id,sense_id,text FROM examples").use {
                assertTrue(it.moveToFirst()); assertEquals(100L, it.getLong(0)); assertEquals(10L, it.getLong(1)); assertEquals("context", it.getString(2))
            }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
    @Test fun v8ToV9MergesBothAndSingleDirectionsWithoutLosingResetHistory() {
        helper.createDatabase("sense-review-migration", 8).apply {
            execSQL("INSERT INTO vocabulary_entries VALUES(1,'entry','phrase','en','note',10,20,'')")
            for (id in 1..4) execSQL("INSERT INTO senses(id,entry_id,meaning,part_of_speech,sort_order,stable_id) VALUES($id,1,'meaning','',$id,'sense-$id')")
            execSQL("INSERT INTO examples(id,sense_id,text,sort_order,meaning,origin,source_title,source_url,source_locator,captured_at,stable_id) VALUES(100,1,'context',0,'translation','CAPTURED','title','https://example.com','p.1',15,'example')")
            execSQL("INSERT INTO review_states VALUES('a','sense-1','MEANING_TO_EXPRESSION',0,4,20,500,'steps-v1',2)")
            execSQL("INSERT INTO review_states VALUES('b','sense-1','EXPRESSION_TO_MEANING',1,1,10,100,'steps-v1',4)")
            execSQL("INSERT INTO review_states VALUES('c','sense-2','MEANING_TO_EXPRESSION',1,-1,NULL,0,'steps-v1',0)")
            execSQL("INSERT INTO review_states VALUES('d','sense-3','EXPRESSION_TO_MEANING',0,2,10,300,'steps-v1',1)")
            execSQL("INSERT INTO review_events VALUES('event','b',10,'FORGOT','SCHEDULED','steps-v1',3,0,'session',NULL)")
            execSQL("INSERT INTO review_events VALUES('retry','b',11,'REMEMBERED','RETRY','steps-v1',3,0,'session','event')")
            execSQL("INSERT INTO review_events VALUES('reset','b',12,NULL,'RESET','steps-v1',4,0,NULL,NULL)")
            execSQL("INSERT INTO review_events VALUES('other','a',15,'REMEMBERED','SCHEDULED','steps-v1',2,0,'session',NULL)")
            close()
        }
        helper.runMigrationsAndValidate("sense-review-migration", 9, true, MIGRATION_8_9).use { db ->
            db.query("SELECT stable_id,enabled,stage,last_reviewed_at,next_review_at,generation FROM review_states ORDER BY stable_id").use {
                assertEquals(3, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("a", it.getString(0)); assertEquals(1, it.getInt(1)); assertEquals(1, it.getInt(2))
                assertEquals(10L, it.getLong(3)); assertEquals(100L, it.getLong(4)); assertEquals(5, it.getInt(5))
                assertTrue(it.moveToNext()); assertEquals("c", it.getString(0)); assertTrue(it.isNull(3))
                assertTrue(it.moveToNext()); assertEquals("d", it.getString(0)); assertEquals(0, it.getInt(1))
            }
            db.query("SELECT stable_id,review_state_id,prompt_direction,legacy_review_state_id,generation,retry_of_event_id FROM review_events ORDER BY stable_id").use {
                assertEquals(4, it.count)
                while (it.moveToNext()) {
                    assertEquals("a", it.getString(1))
                    val other = it.getString(0) == "other"
                    assertEquals(if (other) "MEANING_TO_EXPRESSION" else "EXPRESSION_TO_MEANING", it.getString(2))
                    assertEquals(if (other) "a" else "b", it.getString(3))
                    assertEquals(when (it.getString(0)) { "reset" -> 4; "other" -> 2; else -> 3 }, it.getInt(4))
                    if (it.getString(0) == "retry") assertEquals("event", it.getString(5))
                }
            }
            db.query("SELECT stable_id,meaning,source_title,captured_at FROM examples").use {
                assertTrue(it.moveToFirst()); assertEquals("example", it.getString(0))
                assertEquals("translation", it.getString(1)); assertEquals("title", it.getString(2)); assertEquals(15L, it.getLong(3))
            }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("DELETE FROM senses WHERE stable_id='sense-1'")
            db.query("SELECT * FROM review_events").use { assertEquals(0, it.count) }
            db.query("SELECT * FROM review_states").use { assertEquals(2, it.count) }
        }
    }

}
