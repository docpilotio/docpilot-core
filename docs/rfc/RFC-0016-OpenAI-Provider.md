# RFC-0016 — OpenAI Provider

Status: Accepted

## Problem

DocPilot supports local AI through Ollama but does not yet support the planned cloud provider.

## Decision

Add `docpilot-provider-openai` as a separate Gradle module implementing the RFC-0013 AI SPI with the OpenAI Responses API.

## Scope

- Responses API (`POST /v1/responses`)
- Text and JSON-object output
- Environment-based configuration
- Typed error mapping
- ServiceLoader plugin registration
- Mock-server tests

## Out of Scope

- Streaming
- Tool calling
- Images and audio
- Automatic fallback to another provider
