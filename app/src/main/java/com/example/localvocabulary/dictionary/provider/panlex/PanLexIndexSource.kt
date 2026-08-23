package com.example.localvocabulary.dictionary.provider.panlex

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PanLexIndexSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PanLexDatabaseSource {
    override fun open(): PanLexIndexOpenResult {
        val assetNames = try {
            context.assets.list(PANLEX_ASSET_DIRECTORY).orEmpty()
        } catch (error: IOException) {
            return PanLexIndexOpenResult.Failed(error.message)
        }
        if (PANLEX_ARTIFACT_NAME !in assetNames) return PanLexIndexOpenResult.Missing

        return try {
            val copiedIndex = copyIndexOnce()
            val database = SQLiteDatabase.openDatabase(
                copiedIndex.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            PanLexIndexOpenResult.Opened(database)
        } catch (error: IOException) {
            PanLexIndexOpenResult.Failed(error.message)
        } catch (error: SQLiteException) {
            PanLexIndexOpenResult.Failed(error.message)
        } catch (error: SecurityException) {
            PanLexIndexOpenResult.Failed(error.message)
        }
    }

    private fun copyIndexOnce(): File {
        val directory = File(context.noBackupFilesDir, "dictionary/panlex/$PANLEX_RELEASE_ID")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create PanLex index directory")
        }
        val destination = File(directory, PANLEX_ARTIFACT_NAME)
        if (destination.isFile && destination.length() > 0L) return destination

        val temporary = File(directory, "$PANLEX_ARTIFACT_NAME.tmp")
        context.assets.open("$PANLEX_ASSET_DIRECTORY/$PANLEX_ARTIFACT_NAME").use { source ->
            temporary.outputStream().buffered().use { target ->
                source.copyTo(target, INDEX_COPY_BUFFER_SIZE_BYTES)
            }
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            if (!destination.isFile || destination.length() == 0L) {
                throw IOException("Unable to install PanLex index")
            }
        }
        return destination
    }
}

internal sealed interface PanLexIndexOpenResult {
    data class Opened(val database: SQLiteDatabase) : PanLexIndexOpenResult
    data object Missing : PanLexIndexOpenResult
    data class Failed(val detail: String?) : PanLexIndexOpenResult
}

internal fun interface PanLexDatabaseSource {
    fun open(): PanLexIndexOpenResult
}

internal const val PANLEX_ASSET_DIRECTORY = "dictionary/panlex"
internal const val PANLEX_ARTIFACT_NAME = "panlex_korean_fallback.db"
internal const val PANLEX_RELEASE_ID = "2019-09-01"
internal const val PANLEX_INDEX_SCHEMA_VERSION = 1
private const val INDEX_COPY_BUFFER_SIZE_BYTES = 1024 * 1024
