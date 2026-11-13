# ADR-005: Local database encryption and key management

## Status

Accepted for the project foundation as the proposed design in the PRD (section 9.2). The concrete library choice and key lifecycle details are confirmed before the first persistence step of the proof of concept (PRD section 19), and the key-failure behavior is defined and tested before any real data is stored.

## Context

Financial data must be protected at rest (DATA-03) with key material protected by Android Keystore (DATA-04). Android Keystore does not encrypt a SQLite database by itself, so the database needs a SQLite-compatible encryption solution. The database key is bound to the device Keystore, so an automatically restored copy on another device is unreadable; this is why automatic backup is disabled (DATA-08) and user-initiated backup and restore is the recovery path. Encryption must be enabled from the first schema version so no plaintext-to-encrypted migration is ever needed.

## Decision

- Use SQLCipher for Android via Room. SQLCipher is a maintained SQLite extension and Room supports it through a custom support factory. The library is net.zetetic:android-database-sqlcipher:4.5.4 with androidx.sqlite:sqlite-ktx.
- Key design: a random database passphrase, wrapped by a Keystore-protected AES-GCM key, stored in app-private storage. The database key never appears in plaintext and is recoverable only while the Keystore key is available on the same device.
- Key-failure behavior is defined and tested before real data is stored: the database reopens after a reboot; if the key is missing or invalid (for example after app-data loss), the app shows a clear locked state, never silently creates a new empty database over existing data, and points to restore from a full backup. A lost key is unrecoverable by design.
- Raw notification payloads are protected by this database encryption and are not encrypted a second time (PRD section 9.2). Payloads never leave the database (backups exclude them).
- The app does not claim the database is encrypted merely because Keystore is used; the encryption is verified in tests.

## Consequences

- Room schemas are introduced only after SQLCipher support is wired in, so the first schema is already encrypted.
- A database key lost with the Keystore is unrecoverable; the full backup is the recovery path (DATA-09).
- Tests must cover reboot-reopen and key-missing or key-invalid locked-state behavior (section 14.2).

## Alternatives considered

- Relying on Keystore alone, which does not encrypt SQLite: rejected because it does not protect the database file at rest.
- A separate second encryption layer over payloads: dropped in PRD 1.4 because the database key and a payload key would sit in the same Keystore chain, adding code and key management without new protection.

## Date

2026-11-14
