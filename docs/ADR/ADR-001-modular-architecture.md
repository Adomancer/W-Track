# ADR-001: Modular architecture

## Status

Accepted for the project foundation.

## Context

The product must stay modular so providers and capabilities can be added later (PRD product goal 6, section 1.3). The PRD (section 12) prescribes three Gradle modules for the MVP: the Android app module, the pure-Kotlin integration API, and the integration providers. Inside the app module, the layers from section 6.1 are packages (feature, domain, data, core) and are extracted into modules only when a boundary is repeatedly violated or build times justify it.

## Decision

- Three modules: app (Android app), integration:api (pure-Kotlin parser contracts), and integration:providers (provider parsers and fixtures).
- Dependency direction: app depends on integration:api and integration:providers; integration:providers depends only on integration:api.
- Inside app, the domain package must not import UI, Room, Android framework, or provider-specific code. This is enforced in code review and may be automated with an architecture test later.
- The parser boundary (integration:api) is the one that pays off immediately: parsers are pure Kotlin, testable on the JVM with fixtures, and isolated per provider.

## Consequences

- The parser contract in integration:api is a clean seam that both the app and the providers rely on.
- No empty modules are created just for the architecture diagram; domain and data stay as packages inside app until a boundary is repeatedly violated.

## Date

2026-11-14
