# RFC-0024: CLI and DocPilot Facade

- Status: Accepted
- Scope: Product interface

## Summary

RFC-0024 introduces a stable `DocPilot` facade and a dedicated `docpilot-cli` application module. The CLI exposes architecture and ADR generation without requiring command handlers to assemble the generation pipeline directly.

## Architecture

```text
CLI command
  -> CliBootstrap
  -> DocPilot facade
  -> specialized generator
  -> DocumentService
  -> GenerationPipeline
  -> AI provider
```

The CLI distribution includes the Ollama and OpenAI provider modules at runtime. Provider selection remains explicit through `--provider` and model selection through `--model`.

## Commands

```text
docpilot generate architecture --project <path> --provider <id> --model <model>
docpilot generate adr --project <path> --provider <id> --model <model> \
  --title <title> --context <text> --decision <text> --consequences <text>
```

Both commands write rendered Markdown to standard output by default. `--output <file>` writes UTF-8 content to a file and creates parent directories when necessary.

## Decisions

1. The CLI is a separate Gradle module to avoid a dependency cycle between the core and provider modules.
2. The core facade accepts typed generation requests and is reusable by future REST, IDE, and Gradle integrations.
3. Argument parsing has no external library dependency in this RFC.
4. Project knowledge is built deterministically before invoking the selected AI provider.
5. Provider discovery uses the existing plugin service-loader mechanism.

## Excluded

- Interactive prompts
- Configuration files
- Progress UI
- Batch generation
- Automatic ADR numbering
- Remote plugin installation

## Completion criteria

- Core facade and factory
- Dedicated application module
- Architecture and ADR commands
- Provider and model selection
- Standard-output and file output
- Usage and argument validation
- Tests and RFC documentation
