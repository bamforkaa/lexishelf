package com.example.localvocabulary.dictionary.pack

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.GZIPInputStream
import javax.inject.Inject

fun interface DictionaryPackPayloadValidator {
    fun validate(manifest: DictionaryPackManifest, payload: File): Result<Unit>
}

class AndroidDictionaryPackPayloadValidator @Inject constructor() : DictionaryPackPayloadValidator {
    override fun validate(manifest: DictionaryPackManifest, payload: File): Result<Unit> = runCatching {
        when (payload.extension.lowercase()) {
            "db", "sqlite", "sqlite3" -> validateSqlite(manifest, payload)
            "gz" -> validateGzip(payload)
            else -> require(payload.length() > 0L) { "Dictionary payload is empty" }
        }
    }

    private fun validateSqlite(manifest: DictionaryPackManifest, payload: File) {
        val database = SQLiteDatabase.openDatabase(payload.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        database.use {
            val schema = it.rawQuery("PRAGMA user_version", null).use { cursor ->
                check(cursor.moveToFirst()) { "Dictionary database has no schema version" }
                cursor.getInt(0)
            }
            require(schema == manifest.datasetSchemaVersion) {
                "Dictionary schema $schema does not match manifest ${manifest.datasetSchemaVersion}"
            }
            val integrity = it.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                check(cursor.moveToFirst()) { "Dictionary database integrity check failed" }
                cursor.getString(0)
            }
            require(integrity == "ok") { "Dictionary database is corrupt: $integrity" }
        }
    }

    private fun validateGzip(payload: File) {
        var bytesRead = 0L
        GZIPInputStream(payload.inputStream().buffered()).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                bytesRead += count
            }
        }
        require(bytesRead > 0L) { "Compressed dictionary payload is empty" }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}

