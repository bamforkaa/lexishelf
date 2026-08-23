package com.example.localvocabulary.dictionary.provider.koreanbasic

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class KoreanBasicDictionaryIndexSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : KoreanBasicDictionaryDatabaseSource {
    override fun open(): KoreanBasicDictionaryIndexOpenResult {
        val assetNames = try {
            context.assets.list(KOREAN_BASIC_DICTIONARY_ASSET_DIRECTORY).orEmpty()
        } catch (error: IOException) {
            return KoreanBasicDictionaryIndexOpenResult.Failed(error.message)
        }
        if (KOREAN_BASIC_DICTIONARY_ARTIFACT_NAME !in assetNames) {
            return KoreanBasicDictionaryIndexOpenResult.Missing
        }

        return try {
            val copiedIndex = copyIndexOnce()
            val database = SQLiteDatabase.openDatabase(
                copiedIndex.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            )
            KoreanBasicDictionaryIndexOpenResult.Opened(database)
        } catch (error: IOException) {
            KoreanBasicDictionaryIndexOpenResult.Failed(error.message)
        } catch (error: SQLiteException) {
            KoreanBasicDictionaryIndexOpenResult.Failed(error.message)
        } catch (error: SecurityException) {
            KoreanBasicDictionaryIndexOpenResult.Failed(error.message)
        }
    }

    private fun copyIndexOnce(): File {
        val directory = File(
            context.noBackupFilesDir,
            "dictionary/koreanbasic/$KOREAN_BASIC_DICTIONARY_RELEASE_ID",
        )
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create Korean Basic Dictionary index directory")
        }
        val destination = File(directory, KOREAN_BASIC_DICTIONARY_ARTIFACT_NAME)
        if (destination.isFile && destination.length() > 0L) return destination

        val temporary = File(directory, "$KOREAN_BASIC_DICTIONARY_ARTIFACT_NAME.tmp")
        context.assets.open(
            "$KOREAN_BASIC_DICTIONARY_ASSET_DIRECTORY/$KOREAN_BASIC_DICTIONARY_ARTIFACT_NAME",
        ).use { source ->
            temporary.outputStream().buffered().use { destination ->
                source.copyTo(destination, INDEX_COPY_BUFFER_SIZE_BYTES)
            }
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            if (!destination.isFile || destination.length() == 0L) {
                throw IOException("Unable to install Korean Basic Dictionary index")
            }
        }
        return destination
    }
}

internal sealed interface KoreanBasicDictionaryIndexOpenResult {
    data class Opened(val database: SQLiteDatabase) : KoreanBasicDictionaryIndexOpenResult
    data object Missing : KoreanBasicDictionaryIndexOpenResult
    data class Failed(val detail: String?) : KoreanBasicDictionaryIndexOpenResult
}

internal fun interface KoreanBasicDictionaryDatabaseSource {
    fun open(): KoreanBasicDictionaryIndexOpenResult
}

internal const val KOREAN_BASIC_DICTIONARY_ASSET_DIRECTORY =
    "dictionary/koreanbasic"
internal const val KOREAN_BASIC_DICTIONARY_ARTIFACT_NAME =
    "korean_basic_dictionary.db"
internal const val KOREAN_BASIC_DICTIONARY_RELEASE_ID = "2026-08-19"
internal const val KOREAN_BASIC_DICTIONARY_INDEX_SCHEMA_VERSION = 1
private const val INDEX_COPY_BUFFER_SIZE_BYTES = 1024 * 1024
