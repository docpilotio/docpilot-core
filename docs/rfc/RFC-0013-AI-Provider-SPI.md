# RFC-0013 — AI Provider SPI

Status: Accepted  
Version: 1.0  
Target: Phase 2

## Summary

Define a provider-neutral AI service-provider interface for DocPilot.

RFC-0013 adds domain models, provider contracts, deterministic registration,
type-safe selection, safe execution, and a bridge to the RFC-0012 plugin
platform. It does not perform network calls and does not implement OpenAI,
Ollama, or Gemini.

## Goals

- Keep Core independent from vendor SDKs and HTTP APIs.
- Use one request and response model across providers.
- Reuse the RFC-0012 type-safe selection policy.
- Support explicit provider selection without silent fallback.
- Support local-first priority configuration.
- Convert provider exceptions into structured errors.
- Allow AI providers to be exposed through DocPilot plugins.

## Provider Direction

Planned implementations:

```text
Ollama  — local, private, cost-free development provider
OpenAI  — primary high-quality cloud provider
Gemini  — optional provider, including free-tier use when available
```

Provider availability, model names, pricing, and rate limits belong to provider
RFCs and configuration, not this Core SPI.

## Core Flow

```text
AiRequest
    ↓
AiProviderRegistry
    ↓
SelectionPolicy<AiProvider, AiProviderId>
    ↓
AiProvider
    ↓
AiGenerationResult
```

## Selection Policy

The selection order reuses RFC-0012:

1. explicit provider,
2. ordered preference,
3. numeric priority,
4. deterministic provider-ID fallback.

A missing explicitly requested provider is a failure. DocPilot must not silently
send project information to another provider.

Example local-first configuration:

```text
ollama = 100
openai = 90
gemini = 80
```

## Main Contracts

```kotlin
interface AiProvider {
    val descriptor: AiProviderDescriptor

    fun generate(
        request: AiRequest,
    ): AiGenerationResult
}
```

```kotlin
interface AiRuntime {
    fun generate(
        request: AiRequest,
        selectionContext: SelectionContext<AiProviderId>,
    ): AiGenerationResult
}
```

## Domain Model

RFC-0013 introduces:

- `AiProviderId`
- `AiModelId`
- `AiProviderDescriptor`
- `AiCapability`
- `AiExecutionLocation`
- `AiRequest`
- `AiMessage`
- `AiResponse`
- `AiUsage`
- `AiFinishReason`
- `AiGenerationResult`
- `AiError`
- `AiErrorCode`

## Plugin Bridge

An AI provider may be exposed through the existing plugin system:

```kotlin
interface AiProviderPlugin : DocPilotPlugin {
    val provider: AiProvider
}
```

`PluginBackedAiProviderRegistry` extracts providers from registered AI-provider
plugins. Provider implementations remain separate from Core.

## Error Policy

Provider errors are represented as data rather than uncontrolled exceptions.

Initial error codes include:

- provider not found,
- model not supported,
- invalid request,
- authentication,
- rate limited,
- unavailable,
- timeout,
- provider failure,
- unknown.

`DefaultAiRuntime` catches unexpected provider exceptions and returns a
structured `PROVIDER_FAILURE`.

## Security and Privacy

- Core stores no API keys.
- Prompt Packages must not contain credentials.
- Remote provider selection must be explicit or configured.
- Local Ollama execution remains distinguishable from remote execution through
  `AiExecutionLocation`.
- Provider modules are responsible for secure secret loading.

## Non-Goals

This RFC does not:

- call an HTTP endpoint,
- choose concrete model names,
- implement streaming transport,
- implement tool execution,
- manage API keys,
- implement retry or backoff,
- or implement OpenAI, Ollama, or Gemini.

## Acceptance Criteria

- all existing tests continue to pass,
- AI domain validation tests pass,
- provider registry ordering is deterministic,
- explicit selection overrides priority,
- missing explicit selection does not fallback,
- local-first priority can select Ollama,
- provider exceptions become structured errors,
- and `./gradlew clean test` reports `BUILD SUCCESSFUL`.
