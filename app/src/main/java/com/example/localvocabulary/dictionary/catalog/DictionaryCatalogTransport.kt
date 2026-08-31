package com.example.localvocabulary.dictionary.catalog

import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class DictionaryDownloadResult(
    val bytesWritten: Long,
    val sha256: String,
)

interface DictionaryCatalogTransport {
    suspend fun fetchText(url: String): String

    suspend fun download(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (downloadedBytes: Long) -> Unit,
    ): DictionaryDownloadResult
}

@Singleton
class OkHttpDictionaryCatalogTransport @Inject constructor() : DictionaryCatalogTransport {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.MINUTES)
        .build()

    override suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        executeFollowingTrustedRedirects(url).use { response ->
            requireSuccessful(response)
            val input = requireNotNull(response.body) { "Catalog response has no body" }.byteStream()
            input.use { it.readBytesLimited(MAX_CATALOG_BYTES).decodeToString() }
        }
    }

    override suspend fun download(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (downloadedBytes: Long) -> Unit,
    ): DictionaryDownloadResult = withContext(Dispatchers.IO) {
        require(expectedBytes in 1..MAX_DOWNLOAD_BYTES) { "Invalid expected download size" }
        target.parentFile?.mkdirs()
        executeFollowingTrustedRedirects(url).use { response ->
            requireSuccessful(response)
            val body = requireNotNull(response.body) { "Download response has no body" }
            val contentLength = body.contentLength()
            require(contentLength == -1L || contentLength == expectedBytes) {
                "Download size does not match the catalog"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            body.byteStream().buffered().use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= expectedBytes) { "Download exceeds the catalog size" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(written)
                    }
                }
            }
            DictionaryDownloadResult(written, digest.digest().toHex())
        }
    }

    private fun executeFollowingTrustedRedirects(initialUrl: String): Response {
        var current = GitHubDistributionUrlPolicy.requireTrustedNetworkUrl(initialUrl)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val request = Request.Builder().url(current.toString()).get().build()
            val response = client.newCall(request).execute()
            if (response.code !in REDIRECT_CODES) return response
            val location = response.header("Location")
            response.close()
            require(redirectCount < MAX_REDIRECTS) { "Too many GitHub download redirects" }
            require(!location.isNullOrBlank()) { "GitHub redirect has no destination" }
            current = GitHubDistributionUrlPolicy.requireTrustedNetworkUrl(
                current.resolve(URI(location)).toString(),
            )
        }
        error("Too many GitHub download redirects")
    }

    private fun requireSuccessful(response: Response) {
        if (!response.isSuccessful) throw IOException("GitHub download failed with HTTP ${response.code}")
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_CATALOG_BYTES = 1024 * 1024
        const val MAX_DOWNLOAD_BYTES = 1024L * 1024L * 1024L
        const val BUFFER_SIZE = 1024 * 1024
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "Dictionary catalog is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
