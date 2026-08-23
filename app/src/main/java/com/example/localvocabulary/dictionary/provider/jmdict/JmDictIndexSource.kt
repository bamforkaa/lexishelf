package com.example.localvocabulary.dictionary.provider.jmdict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class JmDictIndexSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : JmDictDatabaseSource {
    override fun open(): JmDictIndexOpenResult {
        val assetNames = try {
            context.assets.list(JMDICT_ASSET_DIRECTORY).orEmpty()
        } catch (error: IOException) {
            return JmDictIndexOpenResult.Failed(error.message)
        }
        if (JMDICT_ARTIFACT_NAME !in assetNames) return JmDictIndexOpenResult.Missing

        return try {
            val database = SQLiteDatabase.openDatabase(
                copyIndexOnce().absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            JmDictIndexOpenResult.Opened(database)
        } catch (error: IOException) {
            JmDictIndexOpenResult.Failed(error.message)
        } catch (error: SQLiteException) {
            JmDictIndexOpenResult.Failed(error.message)
        } catch (error: SecurityException) {
            JmDictIndexOpenResult.Failed(error.message)
        }
    }

    private fun copyIndexOnce(): File {
        val directory = File(context.noBackupFilesDir, "dictionary/jmdict/$JMDICT_RELEASE_ID")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create JMdict index directory")
        }
        val destination = File(directory, JMDICT_ARTIFACT_NAME)
        if (destination.isFile && destination.length() > 0L) return destination

        val temporary = File(directory, "$JMDICT_ARTIFACT_NAME.tmp")
        context.assets.open("$JMDICT_ASSET_DIRECTORY/$JMDICT_ARTIFACT_NAME").use { source ->
            temporary.outputStream().buffered().use { target -> source.copyTo(target, COPY_BUFFER) }
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            if (!destination.isFile || destination.length() == 0L) {
                throw IOException("Unable to install JMdict index")
            }
        }
        return destination
    }
}

internal fun interface JmDictDatabaseSource {
    fun open(): JmDictIndexOpenResult
}

internal sealed interface JmDictIndexOpenResult {
    data class Opened(val database: SQLiteDatabase) : JmDictIndexOpenResult
    data object Missing : JmDictIndexOpenResult
    data class Failed(val detail: String?) : JmDictIndexOpenResult
}

internal const val JMDICT_ASSET_DIRECTORY = "dictionary/jmdict"
internal const val JMDICT_ARTIFACT_NAME = "jmdict.db"
internal const val JMDICT_RELEASE_ID = "2026-08-23"
internal const val JMDICT_INDEX_SCHEMA_VERSION = 1
private const val COPY_BUFFER = 1024 * 1024
