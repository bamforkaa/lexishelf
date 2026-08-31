package com.example.localvocabulary.dictionary.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDistributionUrlPolicyTest {
    @Test
    fun `only the stable catalog endpoint or repository raw catalog is accepted`() {
        GitHubDistributionUrlPolicy.requireCatalogEndpoint(
            "https://github.com/Bamfor/lexishelf/releases/latest/download/dictionary-catalog-v1.json",
        )
        GitHubDistributionUrlPolicy.requireCatalogEndpoint(
            "https://raw.githubusercontent.com/Bamfor/lexishelf/main/distribution/dictionary-catalog-v1.json",
        )

        assertRejected {
            GitHubDistributionUrlPolicy.requireCatalogEndpoint(
                "https://github.com/other/lexishelf/releases/latest/download/dictionary-catalog-v1.json",
            )
        }
        assertRejected {
            GitHubDistributionUrlPolicy.requireCatalogEndpoint(
                "https://github.com/Bamfor/lexishelf/releases/latest/download/dictionary-catalog-v1.json?x=1",
            )
        }
    }

    @Test
    fun `pack URL requires HTTPS immutable tag and one dictpack asset`() {
        GitHubDistributionUrlPolicy.requirePackDownload(
            "https://github.com/Bamfor/lexishelf/releases/download/v0.1.0/kaikki-de.dictpack",
        )

        listOf(
            "http://github.com/Bamfor/lexishelf/releases/download/v0.1.0/kaikki-de.dictpack",
            "https://github.com/Bamfor/lexishelf/releases/download/latest/kaikki-de.dictpack",
            "https://github.com/Bamfor/lexishelf/releases/download/v0.1.0/not-a-pack.zip",
            "https://github.com/Bamfor/lexishelf/releases/download/v0.1.0/sub/kaikki-de.dictpack",
        ).forEach { value ->
            assertRejected { GitHubDistributionUrlPolicy.requirePackDownload(value) }
        }
    }

    @Test
    fun `redirect destinations are limited to approved GitHub hosts`() {
        GitHubDistributionUrlPolicy.requireTrustedNetworkUrl(
            "https://release-assets.githubusercontent.com/github-production-release-asset/file?x=public",
        )
        assertRejected {
            GitHubDistributionUrlPolicy.requireTrustedNetworkUrl("https://example.com/file")
        }
    }

    private fun assertRejected(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }
}
