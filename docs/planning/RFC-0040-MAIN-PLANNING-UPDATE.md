# DocPilot Main Planning Sync — RFC-0040 완료

## Project Status

```text
Current Phase
  Phase 2 — Post-MVP Evolution

Current Release
  v0.5 MVP

Release Tag
  release/v0.5.0

Completed RFC
  RFC-0001 ~ RFC-0040

Current RFC
  RFC-0041 — Incremental CLI Workflow
```

## Planned RFC

```text
RFC-0042 — AI Incremental Generation
RFC-0043 — Documentation Diff and Review
RFC-0044 — Relationship Semantics
RFC-0045 — Knowledge Relationship Enrichment
RFC-0046 — CI Release Integration
```

## RFC-0040 Summary

`ProjectSpecification`을 프로세스 간에 안전하게 복원할 수 있는 정식 Specification Snapshot Persistence 계층을 구현했다. RFC-0028의 소스 fingerprint Snapshot은 변경하지 않고 별도 계약으로 유지했다.

주요 결과:

- Snapshot format version `1`
- DIR schema version `0.3` 검증
- 프로젝트 identity로 `ProjectSpecification.project.id` 사용
- canonical payload SHA-256 integrity 검증
- deterministic JSON round-trip
- UTF-8 temporary write 후 검증 및 atomic replacement
- 명시적인 load/validation 결과
- RFC-0039 Executor 계약을 유지하는 상위 lifecycle coordinator
- 성공한 실행 이후에만 Snapshot 저장
- `NO_CHANGES`에서 불필요한 rewrite 생략
- future unsupported version의 destructive overwrite 방지

Breaking Change는 없다. `ProjectSpecificationValidator`는 Snapshot 복원 검증 재사용을 위해 public object/function으로 공개되었다.

## Implementation

신규 주요 타입:

```text
SpecificationSnapshotFormat
SnapshotProjectIdentity
SnapshotIntegrity
StoredSpecificationSnapshot
SnapshotValidationFailure
SpecificationSnapshotLoadResult
SpecificationSnapshotRepository
JsonSpecificationSnapshotCodec
FileSpecificationSnapshotRepository
SpecificationSnapshotExecutionCoordinator
SpecificationSnapshotExecutionResult
SnapshotExecutionFailureStage
```

수정 타입:

```text
ProjectSpecificationValidator
  internal → public
```

삭제 타입은 없다.

## Architecture Update

```text
Snapshot File
    ↓
JSON decode
    ↓
format / DIR / identity validation
    ↓
ProjectSpecification structural validation
    ↓
canonical payload SHA-256 validation
    ↓
Previous ProjectSpecification
    ↓
IncrementalDocumentationEngine
    ↓
RFC-0039 IncrementalDocumentationExecutor
    ↓
성공 후 Snapshot save
```

## Snapshot Contract

```text
snapshotFormatVersion: 1
dirSchemaVersion: 0.3
projectIdentity.projectId: ProjectSpecification.project.id
integrity.algorithm: SHA-256
integrity.payloadSha256: canonical ProjectSpecification payload hash
path: .docpilot/snapshots/specification.json
encoding: UTF-8
replacement: validated temporary file + atomic move, fallback replace
```

Unknown field는 parser 단계에서 허용되지만 필수 필드와 타입은 엄격하게 검증한다. Optional model field는 JSON `null`로 정규화한다. Set과 stable-id entity collection은 canonical serialization을 위해 정렬한다.

## Validation and Failure Policy

```text
NOT_FOUND
  → Full Regeneration
  → 성공 후 Snapshot 생성

VALID
  → Incremental analysis

CORRUPTED / INTEGRITY_MISMATCH / INVALID_SPECIFICATION
  → Incremental 입력으로 사용하지 않음
  → Full Regeneration 가능
  → 성공 후 정상 Snapshot 교체

SCHEMA_MISMATCH / PROJECT_MISMATCH
  → Incremental 입력으로 사용하지 않음
  → Full Regeneration 가능

UNSUPPORTED_VERSION
  → Explicit Failure at SNAPSHOT_LOAD
  → 자동 덮어쓰기 금지
```

## Test Result

```text
Relevant source compile       PASS (local kotlinc)
Snapshot round-trip smoke     PASS
Integrity tampering smoke     PASS
Gradle clean test             NOT RUN
```

`Gradle clean test` 미실행 사유: 실행 환경에서 Gradle 9.3.0 distribution 다운로드를 위한 외부 네트워크 접근이 차단됨.

사용자 로컬 검증:

```powershell
.\gradlew.bat clean test
```

## Public API

신규 public API가 추가되었다. 기존 RFC-0039 Executor, Builder, Renderer, Source Snapshot API는 유지된다. Breaking Change는 없다.

`ProjectSpecificationValidator`의 visibility가 public으로 확장되었으며 기존 호출 호환성에는 영향이 없다.

## ADR Candidates

```text
ADR — Source Snapshot과 Specification Snapshot 계약을 분리한다
ADR — Snapshot Format Version과 DIR Schema Version을 분리한다
ADR — Specification Snapshot payload에 canonical SHA-256 integrity를 적용한다
ADR — Snapshot은 검증된 temporary file 이후에 교체한다
ADR — 실행 성공 이후에만 Specification Snapshot을 갱신한다
ADR — Future unsupported Snapshot은 자동 재생성으로 덮어쓰지 않는다
```

## Technical Debt

RFC-0041:

- CLI incremental command 및 option 연결
- Snapshot 상태 출력
- validation failure 사용자 메시지
- exit code 정의

향후:

- Snapshot migration registry/pipeline의 실제 migration 구현
- Unknown field strictness 정책 검토
- JSON serialization library 도입 여부 ADR
- Project identity collision 완화
- Snapshot history 및 review UI

## Next RFC Input

RFC-0041은 다음 API를 연결하면 된다.

```text
SpecificationSnapshotRepository
FileSpecificationSnapshotRepository
SpecificationSnapshotExecutionCoordinator
SpecificationSnapshotExecutionResult
SpecificationSnapshotLoadResult
SnapshotValidationFailure
SnapshotExecutionFailureStage
```

기본 Snapshot 경로:

```text
.docpilot/snapshots/specification.json
```

CLI가 표시할 핵심 상태:

```text
load status
execution mode
fallback reason
snapshot saved
failure stage
artifact operations
```

## Commit

Branch:

```text
feature/rfc-0040-specification-snapshot-persistence
```

Commit title:

```text
feat(snapshot): persist versioned specification snapshots
```

Commit body:

```text
- add a versioned ProjectSpecification snapshot contract
- serialize DIR 0.3 specifications deterministically
- validate project identity, schema and SHA-256 integrity
- persist snapshots through validated temporary-file replacement
- coordinate snapshot lifecycle around RFC-0039 execution
- preserve existing source snapshot and executor contracts
- add codec, repository and lifecycle tests
- document RFC-0040 and Main Planning transition
```

제품 Release Tag는 추가하지 않는다. `release/v0.5.0`을 유지한다.
