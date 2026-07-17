# Core CLI Smoke Evidence

Target:

```text
C:\WorkSpace\architecture-samples
```

Command:

```powershell
./gradlew :run --args="analyze C:\WorkSpace\architecture-samples"
```

Observed generated files:

```text
C:\WorkSpace\architecture-samples\docs\project-summary.md
C:\WorkSpace\architecture-samples\docs\source-index.md
C:\WorkSpace\architecture-samples\docs\knowledge-graph.json
C:\WorkSpace\architecture-samples\prompt-package\overview.md
C:\WorkSpace\architecture-samples\prompt-package\knowledge-graph.json
C:\WorkSpace\architecture-samples\prompt-package\evidence.json
C:\WorkSpace\architecture-samples\prompt-package\instructions.md
```

Observed result:

```text
BUILD SUCCESSFUL in 1s
2 actionable tasks: 1 executed, 1 up-to-date
```

Status: PASS

Operational note: use the fully qualified root task `:run`; unqualified `run` can also select `:docpilot-cli:run` in this multi-project build.
