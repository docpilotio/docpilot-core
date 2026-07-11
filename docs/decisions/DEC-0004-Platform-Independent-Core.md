# DEC-0004 — Platform-Independent Core

Status: Accepted  
Priority: Critical

## Decision

DocPilot Core will remain platform-independent.

Android-specific analysis belongs in DocPilot Droid.

## Consequences

- Core domain entities must not depend on Android SDK types.
- Android metadata is stored through extensions or profiles.
- Additional platform analyzers may be introduced later without redesigning Core.
