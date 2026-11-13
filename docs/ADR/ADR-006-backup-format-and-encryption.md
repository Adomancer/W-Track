# ADR-006: Backup format and encryption

## Status

Accepted for the project foundation as the proposed direction in the PRD (sections 4.7, 9.4). The exact container details are decided before the backup task EXP-001 is implemented (PRD section 19).

## Context

A full backup (DATA-05) must contain everything needed to restore the user's data, and restore (DATA-09) must be atomic and refuse backups from a newer format version than the app supports. Every full backup must be encrypted with a key derived from a user-chosen passphrase (DATA-10); no plaintext full backup is offered, and a lost passphrase cannot be recovered. Automatic Android backup and device transfer are disabled (DATA-08), so the encrypted full backup is the supported recovery path.

## Decision

- Format: a versioned, encrypted container (not plaintext JSON). The container carries a format version, the key-derivation parameters (salt, iteration or memory cost), and a nonce.
- Encryption: a memory-hard key-derivation function (Argon2id if a maintained library is available, otherwise PBKDF2-HMAC-SHA256 with a high iteration count) and AES-256-GCM authenticated encryption.
- Contents: accounts, account-to-source mappings with their account hints, transactions (including voided ones, adjustments, and their original captured values), categories, merchants, rules, tags, transfer pairs, budgets, capture gaps, event identity records, the source allowlist, the base currency, and app settings. It excludes raw notification payloads and Needs Review items.
- Restore is atomic: a failed or wrong-passphrase restore leaves no partial data, refuses a backup from a newer format version, and migrates older versions.
- The passphrase is independent of the database key (ADR-005), which is bound to the device. Losing the passphrase makes the backup unreadable, by design; the app states this before the first backup.

## Consequences

- The backup is the only recovery path for a lost or replaced device, so reminders (DATA-12) and verification (DATA-13) are important.
- The container versioning lets older app versions refuse newer backups.
- No raw payloads or review items travel in a backup, consistent with the data-minimization rules (DATA-07, section 9.3).

## Alternatives considered

- A plaintext JSON export as the backup format: rejected because DATA-10 requires passphrase encryption and no plaintext full backup.
- A raw copy of the encrypted database: rejected because the database key is device-bound, so the copy would be unreadable on another device.

## Date

2026-11-14
