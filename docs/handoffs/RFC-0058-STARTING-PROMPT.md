# DocPilot RFC-0058 — Documentation Profiles and Document Contracts

이 대화는 DocPilot RFC-0058 구현 전용 대화입니다.

## 기준선

RFC-0057에서 확정한 canonical baseline을 기준으로 작업합니다.

- DIR Builder output: 0.3
- manual `ProjectSpecification` default: 0.2
- Specification Snapshot: format 1, DIR 0.3 only
- Review Bundle: format 1
- Relationship Projection Report: format 1
- Evolution Report: format 1
- RFC-0054: proposed, not completed
- RFC-0056: `IMPLEMENTATION_COMPLETED_WITH_VERIFICATION_LIMITATION`
- public v1.0: `PRODUCT_VALIDATION_FAIL` / `NOT_APPROVED`
- PV-009: `PENDING`

RFC-0058에서 위 상태를 임의로 승격하거나 변경하지 않습니다.

## 목적

Documentation Profile과 Document Contract를 정의하여, 향후 Feature, Scenario, Contract, Diagram 문서가 어떤 목적·구조·Evidence·소유권·출력 경로·갱신 규칙을 가져야 하는지 결정론적으로 기술할 기반을 만듭니다.

RFC-0058은 DIR 0.4 또는 Feature/Scenario production model을 구현하기 전에 문서 계약을 먼저 고정하는 RFC입니다.

## 필수 설계 대상

- `DocumentationProfileId`와 Stable ID 규칙
- profile version과 compatibility policy
- Document type / purpose / audience
- required and optional sections
- section-level Evidence requirements
- artifact descriptor and output path ownership
- renderer capability requirements
- completeness and unsupported/unknown handling
- RFC-0052 Artifact Plan integration
- Review Bundle and lifecycle integration
- RFC-0055 ownership/reconciliation integration
- RFC-0056 Evolution impact integration
- legacy document coexistence and migration rules
- deterministic validation and semantic identity

## 범위 밖

- DIR 0.4 implementation
- Feature/EntryPoint/Scenario production models
- runtime call-path extraction
- Diagram IR or Mermaid renderer
- structured AI enrichment
- new CLI command
- MCP extension
- provider changes
- public v1.0 approval or PV-009 completion

## 작업 절차

1. 최신 RFC-0057 완료 ZIP과 handoff를 검사합니다.
2. 기존 renderer, Artifact Catalog/Plan, Review, Reconciliation, Evolution 계약을 분석합니다.
3. 최소 2개의 설계 후보를 비교합니다.
4. production code, tests, documents, compatibility, migration, and risks를 제시합니다.
5. 코드를 수정하기 전에 사용자 승인을 받습니다.

## 시작 지시

먼저 Source and Contract Inspection을 수행하고 다음을 제시하십시오.

1. 현재 artifact descriptor와 renderer 계약
2. 기존 문서 유형 및 출력 경로 inventory
3. profile/contract를 넣을 가장 작은 architecture boundary
4. 후보안 2개 이상과 비교
5. 권고안
6. 예상 production/test/document 변경 파일
7. DIR/Snapshot/Review/Reconciliation/Evolution 호환성 영향
8. 검증 계획과 완료 조건

아직 구현하지 말고 검토 결과와 설계 후보를 먼저 제시하여 사용자 승인을 받으십시오.
