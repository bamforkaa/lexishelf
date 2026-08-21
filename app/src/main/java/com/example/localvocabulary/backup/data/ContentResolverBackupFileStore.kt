package com.example.localvocabulary.backup.data

import android.content.Context
import androidx.core.net.toUri
import com.example.localvocabulary.backup.domain.BackupFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentResolverBackupFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BackupFileStore {
    override suspend fun read(uri: String): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri.toUri())?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BACKUP_BYTES) { "백업 파일이 25 MiB 제한을 초과합니다." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("선택한 백업 파일을 열 수 없습니다.")

        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    override suspend fun write(uri: String, content: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri.toUri(), "wt")?.use { output ->
            output.writer(Charsets.UTF_8).use { writer -> writer.write(content) }
        } ?: error("선택한 위치에 백업 파일을 만들 수 없습니다.")
    }

    private companion object {
        const val MAX_BACKUP_BYTES = 25 * 1024 * 1024
    }
}
