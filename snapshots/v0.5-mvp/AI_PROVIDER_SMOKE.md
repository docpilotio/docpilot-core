# AI Provider Smoke Evidence

## Successful Ollama generation

Command:

```powershell
./gradlew :docpilot-cli:run --args="generate architecture --project C:\WorkSpace\architecture-samples --provider ollama --model qwen3:8b --output C:\WorkSpace\architecture-samples\docs\ai-architecture.md"
```

Observed evidence:

```text
[DEBUG] Ollama model: qwen3:8b
[DEBUG] Request chars: 20911
[DEBUG] Timeout: PT5M
[DEBUG] think=false
[DEBUG] HTTP status: 200
[DEBUG] Response chars: 3982
[OK] Generated C:\WorkSpace\architecture-samples\docs\ai-architecture.md

BUILD SUCCESSFUL in 3m 46s
```

Status: PASS

## Invalid-provider handling

Command:

```powershell
./gradlew :docpilot-cli:run --args="generate architecture --project C:\WorkSpace\architecture-samples --provider invalid-provider --model test-model"
```

Observed evidence:

```text
[ERROR] AI provider 'invalid-provider' was not found. Available providers: ollama, openai
Process finished with non-zero exit value 1
```

Status: PASS — expected failure was explicit and returned a non-zero exit code.

## Scope

- Ollama runtime validation: required and complete
- OpenAI real API invocation: out of scope for v0.5 MVP validation
