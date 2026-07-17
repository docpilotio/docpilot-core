# RFC-0035: Specification Builder & Intermediate Model

## Status

Implemented — 사용자 승인 및 구현 완료

## 1. 요약

RFC-0035는 RFC-0034에서 강화된 `SourceIndex`와 기존 `KnowledgeGraph`/`EvidenceCollection`을 입력으로 받아, Renderer가 직접 사용할 수 있는 결정론적 DIR(DocPilot Intermediate Representation)인 `ProjectSpecification`을 생성하는 계층을 구현한다.

```text
SourceIndex
    ↓
DefaultKnowledgeGraphBuilder.buildWithEvidence()
    ↓
KnowledgeBuildResult
  ├─ KnowledgeGraph
  └─ EvidenceCollection
    ↓
SpecificationBuilder
    ↓
ProjectSpecification (DIR)
    ↓
RFC-0036 Renderer
```

RFC-0035는 Markdown, 파일 출력, CLI 명령, AI Provider, Prompt, Incremental Planning을 구현하지 않는다.

---

## 2. 최신 코드 기준 현황

### 2.1 이미 존재하는 구성요소

현재 코드에는 다음 구성요소가 이미 존재한다.

- `io.docpilot.core.model.source.SourceIndex`
- `io.docpilot.core.model.source.SourceFile`
- `io.docpilot.core.model.source.SourceSymbol`
- `io.docpilot.core.model.knowledge.KnowledgeGraph`
- `io.docpilot.core.model.knowledge.KnowledgeNode`
- `io.docpilot.core.model.knowledge.KnowledgeEdge`
- `io.docpilot.core.model.evidence.EvidenceCollection`
- `io.docpilot.core.knowledge.DefaultKnowledgeGraphBuilder`
- `io.docpilot.core.model.ProjectSpecification`
- `io.docpilot.core.api.SpecificationRenderer`

따라서 RFC-0035는 중복된 Source Tree나 별도의 Knowledge Graph를 만들지 않는다.

### 2.2 RFC-0034 결과

`SourceSymbol`은 다음 정보를 이미 제공한다.

- deterministic `id`
- `qualifiedName`
- `parentSymbolId`
- nested declaration
- constructor
- visibility
- modifiers
- annotations
- signature
- parameters
- receiver type
- declared type
- type parameters
- super types
- source range

### 2.3 Knowledge Graph 현황

현재 `DefaultKnowledgeGraphBuilder`는 다음을 생성한다.

- FILE, PACKAGE, CLASS, INTERFACE, OBJECT, ENUM_CLASS, FUNCTION, PROPERTY 등 Node
- PACKAGE → FILE `CONTAINS`
- FILE/TYPE → SYMBOL `DECLARES`
- FILE → EXTERNAL_TYPE `IMPORTS`
- Source file/package/import/symbol Evidence

현재 코드에서는 `SourceSymbol.superTypes`, 함수 호출, 반환 타입 등의 의미 정보를 Graph Edge로 변환하지 않는다. 따라서 RFC-0035에서 근거 없이 `EXTENDS`, `IMPLEMENTS`, `CALLS` 관계를 추측해서는 안 된다.

### 2.4 기존 DIR 모델의 한계

현재 `ProjectSpecification`은 v0.2 최소 모델이며 다음만 직접 표현한다.

- project
- modules
- components
- relationships
- evidence
- unresolved

하지만 RFC-0035 목표에 필요한 다음 구조가 부족하다.

- package specification
- type의 visibility/modifiers/signature/qualifiedName
- function/API specification
- property specification
- source model과 DIR 사이의 명시적 Builder
- 구조적 summary 생성 규칙

또한 Evidence 모델이 두 종류 존재한다.

1. `io.docpilot.core.model.Evidence` — DIR용 문자열 기반 모델
2. `io.docpilot.core.model.evidence.Evidence` — 분석 계층의 typed Evidence 모델

RFC-0035에서는 두 모델을 즉시 통합하여 기존 API를 깨지 않고, 명시적인 Evidence Mapper를 통해 분석 Evidence를 DIR Evidence로 변환한다.

---

## 3. 목표

1. `KnowledgeBuildResult`를 `ProjectSpecification`으로 변환한다.
2. Project, Module, Package, Type, Function, Property 구조를 표현한다.
3. Knowledge Graph의 검증된 관계와 Evidence를 DIR에 연결한다.
4. 동일 입력에 대해 동일한 결과를 생성한다.
5. RFC-0036 Renderer가 SourceIndex나 KnowledgeGraph를 다시 해석하지 않도록 한다.
6. 기존 공개 생성자와 기존 테스트의 소스 호환성을 유지한다.

---

## 4. 비목표

- Markdown/JSON/YAML Renderer 구현
- Output Writer 및 파일 저장
- CLI 명령 또는 옵션 변경
- OpenAI/Ollama Provider 변경
- LLM 기반 Summary
- 함수 본문 의미 분석
- 호출 그래프 생성
- semantic type resolution
- Incremental Planning/Cache
- 기존 AI Generation Pipeline 변경

`DefaultGenerationPipeline`은 AI prompt 실행 파이프라인이며 RFC-0035의 deterministic specification pipeline과 별개이므로 수정하지 않는다.

---

## 5. 핵심 설계 결정

### 5.1 입력은 `KnowledgeBuildResult`

공개 Builder의 주 입력은 `SourceIndex`가 아니라 기존 분석 파이프라인의 결과인 `KnowledgeBuildResult`로 한다.

```kotlin
public fun interface SpecificationBuilder {
    public fun build(request: SpecificationBuildRequest): ProjectSpecification
}
```

```kotlin
public data class SpecificationBuildRequest(
    val project: ProjectDescriptor,
    val knowledge: KnowledgeBuildResult,
    val sourceIndex: SourceIndex? = null,
)
```

- Graph와 Evidence는 `KnowledgeBuildResult`에서 받는다.
- `SourceIndex`는 module/package/type의 세부 속성 보완에 사용한다.
- 프로젝트 이름과 ID를 임의 추론하지 않도록 `ProjectDescriptor`를 명시적으로 받는다.

### 5.2 DIR 버전은 0.3으로 확장

기존 모델에 선택적 필드를 추가하고 기본값을 제공하여 소스 호환성을 유지한다.

- 기존 `ProjectSpecification` 생성 코드는 계속 컴파일되어야 한다.
- 새 Builder가 생성하는 결과의 `schemaVersion`은 `0.3`이다.
- 기존 default 값 `0.2`를 바로 변경할지는 기존 테스트 확인 후 결정하며, 기본 방침은 상수 `CURRENT_SCHEMA_VERSION = "0.3"`를 Builder에서 명시하는 것이다.

### 5.3 Source Tree를 복제하지 않는다

DIR은 SourceIndex의 모든 토큰이나 전체 AST를 복사하지 않는다.

- module: `candidateModulePath`
- package: `packageName`
- type: 문서화 대상 component
- function/constructor: component API
- property: component property
- 관계: Graph Edge
- 근거: Evidence reference

### 5.4 Evidence First

- Graph Node/Edge가 보유한 `evidenceRefs`만 DIR 항목에 연결한다.
- Evidence가 없는 의미 관계는 생성하지 않는다.
- 미해결 또는 지원하지 않는 Graph 항목은 `UnresolvedItem`으로 보존한다.

### 5.5 결정론

정렬 규칙은 다음과 같다.

1. modules: `path`, `id`
2. packages: `qualifiedName`, `id`
3. components: `moduleId`, `qualifiedName`, `id`
4. APIs/properties: source line, signature/name, id
5. relationships: `sourceId`, `type`, `targetId`, `id`
6. evidence: `id`
7. unresolved: `id`

입력 List/Map의 삽입 순서에 출력이 의존하지 않아야 한다.

---

## 6. Intermediate Model 확장

### 6.1 ProjectSpecification

기존 필드를 유지하고 package 목록을 추가한다.

```kotlin
public data class ProjectSpecification(
    val schemaVersion: String = "0.2",
    val project: ProjectDescriptor,
    val modules: List<ModuleSpecification> = emptyList(),
    val packages: List<PackageSpecification> = emptyList(),
    val components: List<ComponentSpecification> = emptyList(),
    val relationships: List<RelationshipSpecification> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    val unresolved: List<UnresolvedItem> = emptyList(),
)
```

`packages`는 기본값을 가지므로 기존 호출부와 소스 호환된다.

### 6.2 ModuleSpecification 확장

```kotlin
public data class ModuleSpecification(
    val id: String,
    val name: String,
    val path: String? = null,
    val description: String? = null,
    val sourceSets: Set<String> = emptySet(),
    val evidenceRefs: Set<String> = emptySet(),
)
```

module 정보는 `SourceFile.candidateModulePath`를 기준으로 생성한다. module path가 없는 파일은 root module에 귀속한다.

### 6.3 PackageSpecification 신규

```kotlin
public data class PackageSpecification(
    val id: String,
    val name: String,
    val qualifiedName: String,
    val moduleId: String,
    val description: String? = null,
    val evidenceRefs: Set<String> = emptySet(),
)
```

동일 package가 여러 module에 존재할 수 있으므로 ID는 `moduleId + qualifiedName`을 포함한다.

### 6.4 ComponentSpecification 확장

Type 선언은 기존 `ComponentSpecification`에 매핑한다.

```kotlin
public data class ComponentSpecification(
    val id: String,
    val name: String,
    val moduleId: String,
    val packageId: String? = null,
    val qualifiedName: String? = null,
    val kind: String,
    val role: String,
    val visibility: String? = null,
    val modifiers: Set<String> = emptySet(),
    val annotations: List<String> = emptyList(),
    val typeParameters: List<String> = emptyList(),
    val superTypes: List<String> = emptyList(),
    val responsibilities: List<String> = emptyList(),
    val dependencyIds: Set<String> = emptySet(),
    val apis: List<ApiSpecification> = emptyList(),
    val properties: List<PropertySpecification> = emptyList(),
    val evidenceRefs: Set<String> = emptySet(),
)
```

기존 필드는 유지하고 새 필드는 모두 기본값을 둔다.

### 6.5 ApiSpecification 신규

Function과 Constructor를 표현한다.

```kotlin
public data class ApiSpecification(
    val id: String,
    val name: String,
    val kind: String,
    val signature: String?,
    val visibility: String?,
    val receiverType: String? = null,
    val returnType: String? = null,
    val parameters: List<ParameterSpecification> = emptyList(),
    val modifiers: Set<String> = emptySet(),
    val annotations: List<String> = emptyList(),
    val purpose: String? = null,
    val evidenceRefs: Set<String> = emptySet(),
)
```

### 6.6 ParameterSpecification 신규

```kotlin
public data class ParameterSpecification(
    val name: String,
    val type: String?,
    val hasDefaultValue: Boolean = false,
)
```

### 6.7 PropertySpecification 신규

```kotlin
public data class PropertySpecification(
    val id: String,
    val name: String,
    val type: String?,
    val visibility: String?,
    val mutable: Boolean?,
    val hasInitializer: Boolean?,
    val modifiers: Set<String> = emptySet(),
    val annotations: List<String> = emptyList(),
    val purpose: String? = null,
    val evidenceRefs: Set<String> = emptySet(),
)
```

---

## 7. Mapping 규칙

### 7.1 Project

`SpecificationBuildRequest.project`를 그대로 사용한다.

언어, 플랫폼, build system을 SourceIndex만으로 과도하게 추론하지 않는다. 호출자가 제공한 값과 확실한 SourceIndex 정보만 병합한다.

### 7.2 Module

- `candidateModulePath != null`: 해당 path를 module ID/name으로 정규화
- `candidateModulePath == null`: `root` module
- `sourceSetName`은 module의 `sourceSets`로 집계

### 7.3 Package

- `packageName != null`: 실제 qualified name 사용
- `packageName == null`: 명시적인 default package ID 사용
- 같은 package라도 module이 다르면 별도 PackageSpecification 생성

### 7.4 Type → Component

다음 `SourceSymbolKind`를 Component로 변환한다.

- CLASS
- INTERFACE
- OBJECT
- ENUM_CLASS
- ANNOTATION_CLASS
- TYPE_ALIAS

중첩 타입도 독립 component로 생성하되, DECLARES relationship으로 소유 타입과 연결한다.

`role`은 의미 추론을 하지 않고 다음 우선순위를 사용한다.

1. 명시적 문서/속성이 존재하면 해당 값
2. 결정론적 fallback: `"Declared <kind> <qualifiedName>"`

이름만 보고 Repository, ViewModel 등의 역할을 추측하지 않는다.

### 7.5 Function/Constructor → API

- FUNCTION → `kind = "function"`
- CONSTRUCTOR → `kind = "constructor"`
- signature, visibility, parameters, receiverType, return type, modifiers, annotations는 SourceSymbol에서 복사
- 함수 본문을 분석하여 purpose를 추론하지 않음

### 7.6 Property → PropertySpecification

- PROPERTY만 변환
- declared type, mutable, initializer 여부, visibility, modifiers, annotations 보존
- getter/setter 의미 분석은 제외

### 7.7 Relationship

현재 Graph가 제공하는 관계만 변환한다.

- CONTAINS
- DECLARES
- IMPORTS
- 기타 향후 Graph에 실제로 존재하는 관계

`SourceSymbol.superTypes`만 보고 RFC-0035 Builder가 `EXTENDS`와 `IMPLEMENTS`를 자체 추론하지 않는다. 해당 관계는 Knowledge Graph Builder가 Evidence와 함께 생성하도록 별도 RFC 또는 후속 보완으로 다룬다.

### 7.8 Evidence

분석 Evidence를 기존 DIR Evidence로 변환한다.

```text
model.evidence.Evidence
    ↓ DirEvidenceMapper
model.Evidence
```

Mapping:

- id → id.value
- type → type.name
- location.relativePath → file
- lineStart/lineEnd → lineStart/lineEnd
- attributes["symbolName"] → symbol (존재 시)
- summary → summary
- typed evidence는 deterministic source evidence이므로 confidence = HIGH

### 7.9 Unresolved

- `KnowledgeGraph.unresolved`를 `UnresolvedItem`으로 변환
- `SourceIndex.failures`도 unresolved로 변환
- 지원되지 않는 node kind 또는 orphan symbol은 unresolved로 보존
- 실패 항목은 삭제하거나 조용히 무시하지 않음

---

## 8. Builder 구조

### 8.1 API

```text
io.docpilot.core.api.SpecificationBuilder
```

### 8.2 구현 패키지

```text
io.docpilot.core.specification
├─ DefaultSpecificationBuilder.kt
├─ SpecificationBuildRequest.kt
├─ SpecificationIndex.kt
├─ SourceSpecificationMapper.kt
├─ KnowledgeRelationshipMapper.kt
├─ DirEvidenceMapper.kt
├─ RuleBasedSummaryGenerator.kt
└─ ProjectSpecificationValidator.kt
```

과도한 클래스 분리를 피한다. 단순 Mapper는 구현 중 실제 복잡도에 따라 `DefaultSpecificationBuilder`의 private 함수로 통합할 수 있다.

### 8.3 의존 방향

```text
api/model
   ↑
specification implementation
```

- `core.api`와 `core.model`은 구현 패키지에 의존하지 않는다.
- CLI/Provider는 변경하지 않는다.
- 외부 DI 프레임워크를 추가하지 않는다.

---

## 9. Summary 규칙

RFC-0035에서는 AI Summary를 생성하지 않는다.

### 우선순위

1. 기존 설명 또는 명시적 documentation 정보
2. Evidence summary
3. deterministic structural fallback
4. 정보가 부족하면 `null`

예:

```text
CLASS Sample is declared.
FUNCTION findUser is declared.
PROPERTY state is declared.
```

사용자의 의도나 비즈니스 책임을 이름만으로 추측하지 않는다.

---

## 10. 검증 규칙

### 실패 처리

- blank project ID/name
- 중복 module/package/component/API/property ID
- component의 존재하지 않는 moduleId
- package의 존재하지 않는 moduleId
- internal relationship endpoint 누락
- 존재하지 않는 evidenceRef
- 잘못된 line range

### 허용 및 unresolved 처리

- 빈 프로젝트
- default package
- summary 없음
- external type/import
- unknown node kind
- SourceIndex indexing failure
- Evidence 없는 합성 root module

Builder는 분석 불완전성 때문에 전체 결과를 버리지 않는다. 구조 무결성이 깨지는 경우에만 명확히 실패한다.

---

## 11. 구현 단계

### Step 1 — 기존 DIR 모델의 additive 확장

- `ProjectSpecification.packages`
- `ModuleSpecification.sourceSets/evidenceRefs`
- `ComponentSpecification`의 package/type/API/property 정보
- 신규 `PackageSpecification`
- 신규 `ApiSpecification`
- 신규 `ParameterSpecification`
- 신규 `PropertySpecification`

모든 신규 필드는 기본값을 제공한다.

### Step 2 — SpecificationBuilder API 추가

- `SpecificationBuilder`
- `SpecificationBuildRequest`
- `DefaultSpecificationBuilder`

### Step 3 — 내부 lookup/index 생성

- SourceSymbol ID → SourceSymbol
- Node ID → KnowledgeNode
- Evidence ID → typed Evidence
- File/path → module/package/source set
- Symbol ID → owner component

### Step 4 — 구조 변환

- Project
- Modules
- Packages
- Components
- APIs
- Properties

### Step 5 — 관계 변환

- Graph Edge를 DIR Relationship으로 변환
- Evidence references 유지
- 중복 제거 및 deterministic ordering

### Step 6 — Evidence/Unresolved 변환

- typed Evidence → DIR Evidence
- Knowledge unresolved 변환
- SourceIndexFailure 변환

### Step 7 — Summary 및 validation

- Evidence 기반 fallback summary
- 전체 ID/reference 무결성 검증

### Step 8 — 테스트 및 회귀 확인

- 신규 단위/통합 테스트
- 기존 core 테스트
- CLI 테스트
- provider 모듈 테스트
- 전체 Gradle build

### Step 9 — 산출물

- 프로젝트 루트 구조 ZIP
- 변경 파일 목록
- 적용 방법
- 테스트 방법
- Commit Message
- `DOCPILOT_RFC0035_HANDOFF.md`
- Main Planning 갱신 정보

---

## 12. 테스트 계획

### 모델 호환성

- 기존 v0.2 방식의 `ProjectSpecification` 생성 코드 컴파일/동작
- 신규 필드 기본값 확인

### Builder 구조

- 빈 SourceIndex
- root module
- 멀티 module
- source set 집계
- default package
- 동일 package의 복수 module 분리
- top-level 및 nested type
- function/constructor/property 변환

### 관계

- CONTAINS
- DECLARES
- IMPORTS
- nested declaration
- external import node
- 중복 relation 제거

### Evidence

- Node Evidence 연결
- Edge Evidence 연결
- Evidence location 보존
- 모든 evidenceRefs가 실제 Evidence를 가리키는지 검증

### Unresolved

- Knowledge unresolved 변환
- SourceIndexFailure 변환
- unsupported node kind 보존

### 결정론

동일한 내용을 서로 다른 순서로 넣은 입력에서 완전히 같은 `ProjectSpecification`을 생성해야 한다.

### 회귀

- `DefaultKnowledgeGraphBuilderTest`
- `KnowledgeEvidenceIntegrationTest`
- 기존 `ProjectSpecification` 관련 테스트
- 전체 `./gradlew test`
- 전체 `./gradlew build`

---

## 13. 예상 변경 파일

### 신규

```text
src/main/kotlin/io/docpilot/core/api/SpecificationBuilder.kt
src/main/kotlin/io/docpilot/core/specification/SpecificationBuildRequest.kt
src/main/kotlin/io/docpilot/core/specification/DefaultSpecificationBuilder.kt
src/main/kotlin/io/docpilot/core/specification/DirEvidenceMapper.kt
src/main/kotlin/io/docpilot/core/specification/ProjectSpecificationValidator.kt
```

구현 복잡도에 따라 다음은 별도 파일 또는 Builder 내부 private 구현으로 둔다.

```text
SourceSpecificationMapper.kt
KnowledgeRelationshipMapper.kt
RuleBasedSummaryGenerator.kt
SpecificationIndex.kt
```

### 수정

```text
src/main/kotlin/io/docpilot/core/model/ProjectSpecification.kt
```

### 테스트 신규

```text
src/test/kotlin/io/docpilot/core/specification/DefaultSpecificationBuilderTest.kt
src/test/kotlin/io/docpilot/core/specification/SpecificationEvidenceMappingTest.kt
src/test/kotlin/io/docpilot/core/specification/SpecificationDeterminismTest.kt
src/test/kotlin/io/docpilot/core/specification/ProjectSpecificationCompatibilityTest.kt
```

### 문서

```text
docs/rfc/RFC-0035-Specification-Builder-and-Intermediate-Model.md
DOCPILOT_RFC0035_HANDOFF.md
```

CLI, OpenAI Provider, Ollama Provider, AI Generation Pipeline은 수정하지 않는다.

---

## 14. 완료 조건

- [ ] `KnowledgeBuildResult`와 `SourceIndex`에서 `ProjectSpecification` 생성
- [ ] Project/Module/Package/Type/Function/Constructor/Property 표현
- [ ] 기존 Graph 관계만 Evidence와 함께 변환
- [ ] Evidence reference 무결성 보장
- [ ] SourceIndex failures와 Knowledge unresolved 보존
- [ ] 동일 입력에 동일 출력 보장
- [ ] 기존 ProjectSpecification 호출부 소스 호환
- [ ] Renderer/CLI/Provider/AI Pipeline 변경 없음
- [ ] 신규 테스트 통과
- [ ] 기존 테스트 통과
- [ ] 전체 build 통과
- [ ] 프로젝트 루트 구조 ZIP 제공
- [ ] RFC-0035 HANDOFF 제공
- [ ] Main Planning 상태 갱신 자료 제공

---

## 15. 후속 RFC와의 경계

### RFC-0036

- `ProjectSpecification`을 Markdown 등으로 렌더링
- 구조/관계/Evidence 표시 방식 결정
- SourceIndex/KnowledgeGraph 직접 접근 금지

### 후속 Knowledge Graph 보완 후보

현재 Graph Builder가 생성하지 않는 다음 관계는 Evidence를 포함한 별도 보완이 필요하다.

- EXTENDS
- IMPLEMENTS
- RETURNS
- USES
- CALLS

RFC-0035는 이 관계들을 추측하여 생성하지 않는다.

### Technical Debt 후보

- 분석 Evidence와 DIR Evidence의 중복 모델
- DSD-0001 v0.2 문서와 확장된 Kotlin DIR 모델의 버전 정합성
- semantic relationship extraction 부재
- source documentation/KDoc 전용 필드 부재

---

## 16. 승인 요청

다음 설계로 RFC-0035 구현을 진행한다.

1. 기존 `ProjectSpecification`을 제거하지 않고 additive하게 확장한다.
2. Builder 입력은 `ProjectDescriptor + KnowledgeBuildResult + SourceIndex`로 한다.
3. Type은 Component, Function/Constructor는 API, Property는 Property Specification으로 변환한다.
4. Package는 module별 first-class `PackageSpecification`으로 추가한다.
5. 현재 Graph가 실제로 제공하는 관계만 변환한다.
6. Evidence와 unresolved를 누락 없이 보존한다.
7. CLI, Provider, AI Generation Pipeline, Renderer는 변경하지 않는다.
8. 구현 후 전체 프로젝트 구조 ZIP과 HANDOFF 문서를 제공한다.
