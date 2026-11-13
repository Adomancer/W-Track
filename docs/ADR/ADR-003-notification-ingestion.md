# ADR-003: Notification ingestion, Android SDK, target devices, and Gate G1 thresholds

## Status

Accepted for the project foundation. Owner-specific decisions (test devices, final minSdk and targetSdk, and the exact Gate G1 thresholds) remain open and are marked as such. They must be filled in by the owner before the measured capture window of the ingestion proof of concept begins (PRD section 17).

## Context

The product ingests transaction events through Android notifications using NotificationListenerService (ING-01). Listener behavior differs across Android versions and OEM skins, and the proof of concept must be measured on real devices before the provider parsers are built. The PRD (section 5) requires minSdk and targetSdk to be fixed from the owner's own devices, and the ADR-003 entry in section 20 lists the two cases to verify on the target devices (Android 15 content redaction for one-time-code notifications; the restricted-settings step for sideloaded installs).

## Decision

For the foundation build the following are adopted as initial, owner-confirmable values. The four decisions marked (OPEN) are resolved by the owner before the measured capture window.

- Android platform: API 37 (compileSdk). The newest AndroidX libraries (for example core-ktx:1.19.1) require compileSdk 37 or later.
- minSdk: 26, a reasonable floor for the lifecycle features used. (OPEN) final value from the owner's devices.
- targetSdk: 36, pending the owner's device decisions. (OPEN) final value from the owner's devices.
- Test devices: none specified yet. (OPEN) the owner lists each device (model, OEM skin, Android version) before the measured capture begins.
- Gate G1 thresholds (PRD section 17, proposed): coverage at least 90 percent, completeness at least 95 percent, and no unexplained wrong amount in the sample, measured over 7 to 14 days with at least 30 transactions per provider. (OPEN) the owner sets the final numbers here before the measurement window starts; they are written here before any measured capture, not after the data is seen.

## Consequences

- The build target (API 37) exceeds what earlier AGP versions support, which is why the project uses AGP 9.5.x and a matching Gradle.
- Because the app may run on a sideloaded build, onboarding must include the restricted-settings step before Notification Access can be enabled (ING-10).
- The Android 15 and later behavior of redacting notification content that looks like a one-time code must be verified on the target devices and routed to Needs Review without guessing (ING-06, PAR-12).

## Alternatives considered

- Lower compileSdk to 36 to match the previously installed platform: rejected because the newest supported libraries require API 37, and the PRD asks for a maintained current stack.
- A lower minSdk: rejected for the MVP because notification-listener behavior and redaction handling benefit from the higher floor; the final value is the owner's call.

## Date

2026-11-14