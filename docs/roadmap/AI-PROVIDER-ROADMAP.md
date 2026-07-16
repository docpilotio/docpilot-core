# AI Provider Roadmap

## RFC-0013 — AI Provider SPI

Provider-neutral contracts, registry, selection, runtime, errors, and plugin
bridge.

## RFC-0014 — Ollama Provider

First implementation because it supports local, private, cost-free iteration.

Planned configuration:

```text
DOCPILOT_AI_PROVIDER=ollama
DOCPILOT_OLLAMA_BASE_URL=http://localhost:11434
DOCPILOT_OLLAMA_MODEL=<configured-local-model>
```

## RFC-0015 — OpenAI Provider

Primary high-quality cloud provider with structured-output support.

Credentials are loaded from environment or a secure external configuration.

## RFC-0016 — Gemini Provider

Optional provider for additional comparison and free-tier usage where available.

## Routing Principles

1. explicit provider selection,
2. configured default or preference,
3. local-first priority where appropriate,
4. no silent remote fallback,
5. clear provider and model attribution in every response.
