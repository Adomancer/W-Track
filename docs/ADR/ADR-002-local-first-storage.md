# ADR-002: Local-first storage and offline capability

## Status

Accepted for the project foundation.

## Context

The product is a personal finance aggregator that processes financial data locally (PRD product principle Local-first, section 2). Core features must work without an account or backend and remain usable offline (DATA-01, DATA-02). There is no mandatory backend for the MVP (PRD section 5).

## Decision

- All core application data is stored locally on the device using Room/SQLite.
- There is no mandatory backend, cloud account, or multi-device synchronization in the MVP.
- Automatic cloud and device-transfer backup of app data is disabled (DATA-08); the encrypted user-initiated full backup is the supported backup and recovery path (ADR-006).
- Notification capture and parsing run entirely on device (ING-05).

## Consequences

- The app never depends on network availability for its core features.
- Recovery depends on the user's own full backup, so backup reminders (DATA-12) and verification (DATA-13) are important.

## Date

2026-11-14
