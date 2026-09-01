package com.example.localvocabulary.dictionary.pack

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

fun interface DictionaryPackStorageSpace {
    fun availableBytes(directory: File): Long
}

class AndroidDictionaryPackStorageSpace @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DictionaryPackStorageSpace {
    override fun availableBytes(directory: File): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            allocatableBytes(directory)
        } else {
            directory.usableSpace
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun allocatableBytes(directory: File): Long {
        val storageManager = requireNotNull(context.getSystemService(StorageManager::class.java)) {
            "Storage service is unavailable"
        }
        return storageManager.getAllocatableBytes(storageManager.getUuidForPath(directory))
    }
}
