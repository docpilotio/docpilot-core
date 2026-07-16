# Ollama Provider

## Prerequisites

Install Ollama and start the local server:

```powershell
ollama serve
```

Pull a model:

```powershell
ollama pull qwen3:8b
```

## Environment

```powershell
$env:DOCPILOT_OLLAMA_BASE_URL="http://localhost:11434"
$env:DOCPILOT_OLLAMA_MODEL="qwen3:8b"
$env:DOCPILOT_OLLAMA_TIMEOUT_SECONDS="120"
```

## Build and test

```powershell
.\gradlew clean test
```

The automated tests use a local mock HTTP server and do not require Ollama.

## Plugin discovery

The provider module includes:

```text
META-INF/services/io.docpilot.core.api.DocPilotPlugin
```

with:

```text
io.docpilot.provider.ollama.OllamaProviderPlugin
```

When the provider module is present on the application runtime classpath,
DocPilot's existing ServiceLoader plugin discovery can find it.
