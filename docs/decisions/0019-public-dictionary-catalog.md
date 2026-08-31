# ADR-0019: Versioned public dictionary catalog and existing installer reuse

## Status

Accepted for public v1 preparation on 2026-08-31.

## Context

Dictionary packs are too large and too independently licensed to bundle in the base APK. Users
should not need converter tools or local file management. Individual pack URLs must be updateable
without shipping a new APK, but a catalog outage must not break local vocabulary or installed packs.

## Decision

The app knows one stable GitHub catalog endpoint. Schema-v1 catalog entries contain exact pack,
provider, language, dataset/schema, archive/payload checksum, size, license and source metadata.
Only 16 reviewed public-v1 pack IDs are accepted. JMdict and aggregate Kaikki artifacts are rejected.

Catalog and asset requests require HTTPS and the LexiShelf GitHub repository or known GitHub asset
redirect hosts. Redirects are followed manually so every hop is checked. Archive SHA-256 verifies
integrity; it is not publisher-authenticity proof. The existing `.dictpack` manifest, size, payload
SHA-256, SQLite/GZip and atomic activation pipeline remains authoritative and also checks catalog
identity before activation.

A last-known valid catalog is cached. Fetch failure displays an error or cached warning while manual
vocabulary and installed packs remain available. Downloads are user-initiated, sequential,
cancellable foreground Settings operations with partial-file cleanup. Automatic updates and
“install all” are not added.

The catalog is not cryptographically signed in v1. Ed25519 signing is a future hardening option.
APK production signing is separate and mandatory for publication.

## Consequences

- Pack distribution can change through one versioned catalog without hardcoded per-pack app URLs.
- Release publication must keep catalog hashes, asset names and exact files synchronized.
- HTTPS plus checksums detects transport corruption but does not replace a signed catalog trust root.
- Very large downloads stop if Settings/ViewModel is destroyed; retry is safe and active packs remain.
