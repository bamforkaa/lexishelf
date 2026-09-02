package com.example.localvocabulary.dictionary.catalog

import java.net.URI

object GitHubDistributionUrlPolicy {
    private const val REPOSITORY_PATH = "/bamforkaa/lexishelf/"
    private val githubRedirectHosts = setOf(
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )

    fun requireCatalogEndpoint(value: String): URI {
        val uri = requireSafeHttps(value)
        val valid = when (uri.host.lowercase()) {
            "github.com" -> uri.path == "${REPOSITORY_PATH}releases/latest/download/dictionary-catalog-v1.json"
            "raw.githubusercontent.com" ->
                uri.path.startsWith(REPOSITORY_PATH) &&
                    uri.path.endsWith("/distribution/dictionary-catalog-v1.json")
            else -> false
        }
        require(valid && uri.query == null) { "Catalog endpoint is not an approved LexiShelf GitHub URL" }
        return uri
    }

    fun requirePackDownload(value: String): URI {
        val uri = requireSafeHttps(value)
        require(uri.host.equals("github.com", ignoreCase = true)) {
            "Dictionary packs must use the GitHub release host"
        }
        val releaseAssetPath = uri.path.removePrefix("${REPOSITORY_PATH}releases/download/")
        val segments = releaseAssetPath.split('/')
        require(
            uri.path.startsWith("${REPOSITORY_PATH}releases/download/") &&
                segments.size == 2 &&
                segments.all(String::isNotBlank) &&
                !segments[0].equals("latest", ignoreCase = true) &&
                segments[1].endsWith(".dictpack"),
        ) {
            "Dictionary packs must be versioned LexiShelf release assets"
        }
        require(uri.query == null) { "Catalog pack URLs must not contain a query" }
        return uri
    }

    fun requireTrustedNetworkUrl(value: String): URI {
        val uri = requireSafeHttps(value)
        val host = uri.host.lowercase()
        val valid = when (host) {
            "github.com" -> uri.path.startsWith("${REPOSITORY_PATH}releases/")
            "raw.githubusercontent.com" -> uri.path.startsWith(REPOSITORY_PATH)
            in githubRedirectHosts -> true
            else -> false
        }
        require(valid) { "Network URL is not an approved GitHub distribution URL" }
        return uri
    }

    private fun requireSafeHttps(value: String): URI {
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("Invalid URL") }
        require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS URLs are allowed" }
        require(!uri.host.isNullOrBlank()) { "URL host is required" }
        require(uri.userInfo == null) { "URL credentials are not allowed" }
        require(uri.port == -1 || uri.port == 443) { "Only the default HTTPS port is allowed" }
        require(uri.fragment == null) { "URL fragments are not allowed" }
        require("/../" !in uri.path && !uri.path.endsWith("/..")) { "Unsafe URL path" }
        return uri
    }
}
