# RFC-0014 — Ollama Local Provider

Status: Accepted
Version: 1.0

## Summary

RFC-0014 adds a local Ollama implementation of the provider-neutral AI SPI
defined by RFC-0013.

The provider is delivered as a separate Gradle module:

```text
docpilot-provider-ollama
```

## Goals

- Keep Ollama dependencies outside DocPilot Core.
- Use the JDK HTTP client without third-party runtime libraries.
- Support text and JSON responses.
- Preserve system, user, assistant, and tool messages.
- Convert transport and provider failures into `AiError`.
- Register the provider through the existing plugin ServiceLoader contract.
- Test without requiring an installed Ollama server.

## Configuration

```text
DOCPILOT_OLLAMA_BASE_URL
DOCPILOT_OLLAMA_MODEL
DOCPILOT_OLLAMA_TIMEOUT_SECONDS
```

Defaults:

```text
Base URL: http://localhost:11434
Model:    qwen3:8b
Timeout:  120 seconds
```

## API

The provider uses:

```text
POST /api/chat
```

with non-streaming requests.

## Error Mapping

```text
Connection refused  → UNAVAILABLE
Timeout             → TIMEOUT
Missing model       → MODEL_NOT_SUPPORTED
Invalid response    → PROVIDER_FAILURE
HTTP 5xx            → retryable PROVIDER_FAILURE
```

## Security

Ollama runs locally by default. No API key is required. The base URL remains
configurable for controlled remote Ollama deployments.

## Module Boundary

```text
docpilot-core
    ↑
docpilot-provider-ollama
```

Core contains only the AI SPI. Ollama-specific HTTP, JSON, configuration, and
plugin behavior remain in the provider module.
