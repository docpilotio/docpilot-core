# v0.5 MVP Snapshot Checklist

## Build and tests

- [x] `./gradlew clean build`
- [x] `./gradlew test`
- [x] JDK 21 / Gradle 9.3 target environment

## Core CLI

- [x] `architecture-samples` path accepted
- [x] Project summary generated
- [x] Source index generated
- [x] Knowledge graph generated
- [x] Prompt package generated
- [x] Exit code 0 / `BUILD SUCCESSFUL`

## AI Provider

- [x] Ollama provider selected
- [x] `qwen3:8b` invoked
- [x] HTTP 200 received
- [x] AI architecture Markdown generated
- [x] Invalid provider rejected with available-provider guidance
- [x] OpenAI real invocation marked out of scope

## Release metadata

- [x] RFC-0038 documentation synchronized
- [x] Main Planning advanced to RFC-0039
- [x] Release evidence snapshot recorded
- [ ] Artifact-version policy resolved
