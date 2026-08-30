package com.example.localvocabulary.dictionary.reference

import android.content.Intent
import androidx.core.net.toUri

object ExternalDictionaryIntentFactory {
    fun create(uri: String): Intent? {
        val parsed = runCatching { uri.toUri() }.getOrNull() ?: return null
        if (parsed.scheme != "https" && parsed.scheme != "http") return null
        if (parsed.host.isNullOrBlank()) return null
        return Intent(Intent.ACTION_VIEW, parsed)
    }
}
