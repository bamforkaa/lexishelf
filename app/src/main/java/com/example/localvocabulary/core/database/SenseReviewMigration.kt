package com.example.localvocabulary.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.localvocabulary.review.domain.DirectionalReviewMigration
import com.example.localvocabulary.review.domain.LegacyDirectionalReviewState
import com.example.localvocabulary.review.domain.ReviewEvent
import com.example.localvocabulary.review.domain.ReviewEventKind
import com.example.localvocabulary.review.domain.ReviewMode
import com.example.localvocabulary.review.domain.ReviewRating
import com.example.localvocabulary.review.domain.ReviewState

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val states = db.query("""
            SELECT stable_id,sense_stable_id,mode,enabled,stage,last_reviewed_at,
                next_review_at,scheduler_version,generation FROM review_states
        """.trimIndent()).use { rows -> buildList {
            while (rows.moveToNext()) add(LegacyDirectionalReviewState(
                ReviewState(rows.getString(0), rows.getString(1), rows.getInt(3) != 0,
                    rows.getInt(4), if (rows.isNull(5)) null else rows.getLong(5), rows.getLong(6),
                    rows.getString(7), rows.getInt(8)), ReviewMode.valueOf(rows.getString(2)),
            ))
        } }
        val events = db.query("""
            SELECT stable_id,review_state_id,reviewed_at,rating,kind,scheduler_version,generation,
                was_new,session_id,retry_of_event_id FROM review_events
        """.trimIndent()).use { rows -> buildList {
            while (rows.moveToNext()) add(ReviewEvent(
                rows.getString(0), rows.getString(1), rows.getLong(2),
                if (rows.isNull(3)) null else ReviewRating.valueOf(rows.getString(3)),
                ReviewEventKind.valueOf(rows.getString(4)), rows.getString(5), rows.getInt(6),
                rows.getInt(7) != 0, if (rows.isNull(8)) null else rows.getString(8),
                if (rows.isNull(9)) null else rows.getString(9),
            ))
        } }
        val migrated = DirectionalReviewMigration.merge(states, events)
        // Room runs this rebuild and data restoration in the upgrade transaction.
        // Final table names avoid SQLite-version-dependent foreign key rewrites on rename.
        db.execSQL("DROP TABLE review_events")
        db.execSQL("DROP TABLE review_states")
        db.execSQL("""
            CREATE TABLE review_states (
                stable_id TEXT NOT NULL PRIMARY KEY, sense_stable_id TEXT NOT NULL,
                enabled INTEGER NOT NULL, stage INTEGER NOT NULL, last_reviewed_at INTEGER,
                next_review_at INTEGER NOT NULL, scheduler_version TEXT NOT NULL, generation INTEGER NOT NULL,
                FOREIGN KEY(sense_stable_id) REFERENCES senses(stable_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE review_events (
                stable_id TEXT NOT NULL PRIMARY KEY, review_state_id TEXT NOT NULL,
                reviewed_at INTEGER NOT NULL, rating TEXT, kind TEXT NOT NULL,
                scheduler_version TEXT NOT NULL, generation INTEGER NOT NULL,
                was_new INTEGER NOT NULL, session_id TEXT, retry_of_event_id TEXT,
                prompt_direction TEXT, legacy_review_state_id TEXT,
                FOREIGN KEY(review_state_id) REFERENCES review_states(stable_id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        migrated.states.forEach { state -> db.execSQL(
            "INSERT INTO review_states VALUES(?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(state.stableId, state.senseStableId, if (state.enabled) 1 else 0, state.stage,
                state.lastReviewedAt, state.nextReviewAt, state.schedulerVersion, state.generation),
        ) }
        migrated.events.forEach { event -> db.execSQL(
            "INSERT INTO review_events VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(event.stableId, event.reviewStateId, event.reviewedAt, event.rating?.name, event.kind.name,
                event.schedulerVersion, event.generation, if (event.wasNew) 1 else 0, event.sessionId,
                event.retryOfEventId, event.promptDirection?.name, event.legacyReviewStateId),
        ) }
        db.execSQL("CREATE UNIQUE INDEX index_review_states_sense_stable_id ON review_states(sense_stable_id)")
        db.execSQL("CREATE INDEX index_review_states_next_review_at ON review_states(next_review_at)")
        db.execSQL("CREATE INDEX index_review_events_review_state_id ON review_events(review_state_id)")
        db.execSQL("CREATE INDEX index_review_events_reviewed_at ON review_events(reviewed_at)")
        db.execSQL("CREATE UNIQUE INDEX index_review_events_retry_of_event_id ON review_events(retry_of_event_id)")
    }
}
