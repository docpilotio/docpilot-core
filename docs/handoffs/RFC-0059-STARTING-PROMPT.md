# DocPilot RFC-0059 — Feature, Entry Point, and Scenario Specification Foundation

이 대화는 DocPilot RFC-0059 구현 전용 대화입니다.

RFC-0058이 반영된 최신 DocPilot Core 전체 소스와 RFC-0058 Completion Handoff를 기준으로 작업합니다.

## 목적

RFC-0058의 `kotlin-android@1` Profile은 Feature Catalog와 Feature Specification을 정의하지만, DIR 0.3에는 Feature, Entry Point, Scenario production model이 없기 때문에 해당 문서를 `DEFERRED`로 처리합니다.

RFC-0059의 목적은 AI가 Feature를 임의로 만들지 않고 Source Evidence와 기존 Specification 관계를 기반으로 다음을 결정론적으로 표현할 수 있는 최소 production foundation을 정의하는 것입니다.

```text
FeatureSpecification
EntryPointSpecification
ScenarioSpecification
ScenarioStepSpecification
```

## Canonical baseline

```text
RFC-0058:
IMPLEMENTED_WITH_ENVIRONMENT_VERIFICATION_LIMITATION

Built-in Profile:
kotlin-android@1

DIR Builder output:
0.3

Manual ProjectSpecification default:
0.2

Specification Snapshot:
format 1 / DIR 0.3

Review Bundle:
format 1

Reconciliation / Ownership:
format 1

Evolution Report:
format 1

Public v1.0:
PRODUCT_VALIDATION_FAIL / NOT_APPROVED

PV-009:
PENDING

v1.1 Release Candidate:
NOT_DECLARED
```

위 상태를 임의로 승격하지 않습니다.

## 운영 원칙

- Clean Architecture
- Evidence First
- Deterministic
- Stable ID
- Incremental compatibility
- AI Provider Independent
- ambiguous Feature 후보를 임의 선택하지 않음
- 존재하지 않는 Feature, Entry Point, Scenario를 AI로 생성하지 않음
- Renderer는 SourceIndex 또는 Knowledge Graph를 직접 해석하지 않음
- RFC-0058 Profile 계약을 중복 생성하지 않음
- 사용자 승인 없이 commit, merge, push, tag, release 금지

## 우선 검토할 핵심 문제

1. Feature의 semantic identity와 display name 구분
2. Feature owner와 참여 Module/Package/Component 관계
3. Entry Point 후보 종류
   - Android Activity/Fragment/Service/Receiver/Provider
   - Compose navigation destination
   - public API 또는 CLI entry
   - background worker/scheduler
4. Scenario와 Scenario Step의 최소 계약
5. ordered step의 Evidence 요건
6. branch, error, async, callback, state transition의 표현 범위
7. unresolved/ambiguous candidate 처리
8. DIR 0.4 additive model과 runtime-only projection 비교
9. Snapshot format 1 유지 또는 format 2 도입 조건
10. RFC-0037 Stable-ID diff 영향
11. RFC-0045 relationship impact 연결
12. RFC-0052 Artifact Plan 연결
13. RFC-0056 Evolution change 연결
14. RFC-0058 Profile Resolution이 DEFERRED 문서를 READY/PARTIAL로 전환하는 조건

## 설계 후보

최소 다음 두 후보를 실제 코드 기준으로 비교합니다.

### 후보 A — DIR 0.4 additive production model

`ProjectSpecification`에 Feature, Entry Point, Scenario를 추가하고 명시적 Snapshot migration을 도입합니다.

### 후보 B — DIR 0.3 beside runtime projection

기존 DIR을 변경하지 않고 별도 deterministic Feature Projection을 생성하며 persistence는 후속 RFC로 미룹니다.

각 후보에 대해 architecture boundary, persisted format, migration, diff, review, evolution, profile resolution, compatibility, 회귀 위험을 제시합니다.

## 범위 밖

- runtime telemetry
- whole-program dynamic call graph
- AI Feature invention
- Diagram IR
- Mermaid Renderer
- data contract extraction
- test traceability 완성
- Profile-aware Markdown 생성
- 새 AI Provider
- 새 CLI/MCP command
- RFC-0054 완료
- public v1.0 승인
- PV-009 완료
- v1.1 RC 선언

## 작업 절차

### Phase 1 — Source and Contract Inspection

아직 코드를 수정하지 않습니다.

다음을 확인합니다.

- RFC-0058 실제 반영 상태
- ProjectSpecification, Snapshot, validator, differ 구조
- Android entry-point observation이 현재 Source/Knowledge 모델에 존재하는 범위
- relationship semantics와 call/dependency Evidence
- unresolved/ambiguity 모델
- Profile Resolution의 Feature model gate
- 가장 작은 architecture boundary
- DIR/Snapshot migration 필요 여부

### Phase 2 — Design Candidates

최소 두 후보를 비교하고 권고안을 제시합니다.

### Phase 3 — User Approval

현재 구조 분석, 후보 비교, 권고안, 파일별 변경 계획, Stable ID, Evidence, migration, 테스트 계획을 제시하여 승인받습니다.

### Phase 4 이후

승인 후에만 RFC 명세 확정, 구현, 검증, 문서 동기화, Handoff를 수행합니다.

## 시작 지시

RFC-0058 완료 소스를 분석하고 Phase 1과 Phase 2까지만 수행하십시오.

아직 코드를 수정하지 마십시오.

검토 결과와 권고 설계안을 제시하고 사용자 승인을 받으십시오.
