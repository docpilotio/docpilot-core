# OpenAI Provider

`docpilot-provider-openai` is DocPilot's single OpenAI provider. It calls the OpenAI API Platform Responses API (`POST /v1/responses`); it does not automate the ChatGPT website. A ChatGPT Plus, Pro, or Business subscription does not supply API authentication or API billing. Create and fund an API Platform project separately, then issue an API key for it.

## Security and execution boundary

OpenAI execution is remote and may incur API charges. Project prompts leave the local machine only when `openai` is explicitly selected. Selecting `openai` without valid configuration fails; it never falls back to Ollama. Selecting `ollama` never calls OpenAI.

Keep API keys in environment variables or a secret manager. DocPilot sends the key only in the bearer authorization header, masks it in configuration diagnostics, and does not include prompts, response bodies, or authorization data in provider errors.

## Configuration

```powershell
$env:OPENAI_API_KEY="<openai-api-key>"              # required
$env:DOCPILOT_OPENAI_MODEL="gpt-5.6-terra"          # optional
$env:DOCPILOT_OPENAI_TIMEOUT_SECONDS="120"           # optional, 1..300
$env:DOCPILOT_OPENAI_BASE_URL="https://api.openai.com" # optional
$env:OPENAI_ORGANIZATION_ID="<organization-id>"      # optional
$env:OPENAI_PROJECT_ID="<project-id>"                # optional
```

The base URL must be an absolute HTTP(S) URI without user information, query, or fragment. A custom URL exists for local mock tests and explicitly compatible endpoints. Configuration is validated before a network request.

## Supported behavior

- ordered system, user, and assistant text messages;
- deterministic text requests and `max_output_tokens`/`temperature` when present in the Core SPI;
- text output from every Responses API `output_text` content block;
- JSON mode through `text.format.type=json_object`, with returned JSON-object validation;
- response/model identifiers, token usage, completion status, refusal, and incomplete-response handling;
- optional organization/project headers, explicit request timeout, response-size limit, and typed HTTP/I/O failures;
- ServiceLoader plugin discovery and CLI selection with `--provider openai`.

JSON mode guarantees syntactically valid JSON but not a caller-defined schema because the current provider-neutral `AiRequest` has no JSON Schema field. Streaming, tools, web/file search, images, audio, realtime, retries, failover, and conversation persistence are unsupported.

## Mock tests

Tests use local HTTP servers and never call OpenAI:

```powershell
.\gradlew.bat :docpilot-provider-openai:test
.\gradlew.bat :docpilot-cli:test
```

## Real API smoke test

Real API smoke is not part of the default suite. Run it only after explicit approval to incur API usage, confirming `OPENAI_API_KEY`, billing, and the selected model. Use a minimal synthetic prompt with no project data and record the result separately as `REAL_API_SMOKE: PASS` or `FAIL`. If it was not run, record `REAL_API_SMOKE: NOT_EXECUTED`.

## Troubleshooting

- `OPENAI_API_KEY ... required`: create an API Platform key; a ChatGPT subscription is not a key.
- HTTP 401/403: verify the key and its project permissions.
- HTTP 404/model error: verify the configured model and compatible endpoint.
- HTTP 408/client timeout: increase the timeout within the supported range if appropriate.
- HTTP 429: inspect API Platform rate, credit, spend, and usage limits.
- HTTP 5xx or connection failure: verify service/endpoint availability and retry explicitly at the caller level if appropriate.

Use `--provider ollama` for local execution. There is no automatic provider fallback.
