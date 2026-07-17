# OpenAI Provider

> v0.5 scope note: the provider is bundled and mock-tested, but real OpenAI API invocation is outside the v0.5 MVP release-validation scope.

## Configuration

```powershell
$env:OPENAI_API_KEY="<your-api-key>"
$env:DOCPILOT_OPENAI_MODEL="gpt-5.6-terra"
$env:DOCPILOT_OPENAI_TIMEOUT_SECONDS="120"
```

Optional:

```powershell
$env:DOCPILOT_OPENAI_BASE_URL="https://api.openai.com"
$env:OPENAI_ORGANIZATION_ID="<organization-id>"
$env:OPENAI_PROJECT_ID="<project-id>"
```

## Test

```powershell
.\gradlew :docpilot-provider-openai:test
.\gradlew clean test
```

Tests use a local mock server and do not call OpenAI.
