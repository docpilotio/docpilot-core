# RFC-0032 제안: Incremental Documentation Generator

RFC-0032는 RFC-0030의 생성 계획과 RFC-0031의 프롬프트를 실제 AI Provider에 전달하여, **변경이 필요한 문서 섹션만 생성하고 안전하게 반영하는 실행 엔진**입니다.

핵심 원칙은 다음과 같습니다.

> Planner는 무엇을 생성할지 결정하고, Prompt Builder는 어떻게 요청할지 구성하며, Generator는 실제로 실행하고 결과를 문서에 반영한다.

---

## 전체 흐름

```text
Source changes
    ↓
RFC-0027 Change Detection
    ↓
RFC-0029 Knowledge Impact
    ↓
RFC-0030 Incremental Generation Plan
    ↓
RFC-0031 PromptPlan
    ↓
RFC-0032 Documentation Generator
    ├─ AI Provider 호출
    ├─ 응답 정규화
    ├─ 기본 출력 검증
    ├─ 문서 섹션 교체
    └─ 실행 결과 반환
    ↓
RFC-0033 AI Review & Validation
```

예를 들어 `UserRepository.kt`가 변경되었다면:

```text
RFC-0030

Job 1: PERSISTENCE
Job 2: ARCHITECTURE_SUMMARY
```

RFC-0031은 각 Job의 `PromptPlan`을 만듭니다.

RFC-0032는 다음 순서로 실행합니다.

```text
PERSISTENCE PromptPlan
    ↓
OllamaAiProvider.generate()
    ↓
Persistence Markdown 생성
    ↓
임시 저장

ARCHITECTURE_SUMMARY PromptPlan
    ↓
OllamaAiProvider.generate()
    ↓
Summary Markdown 생성
    ↓
임시 저장

모든 Job 성공
    ↓
문서 파일에 일괄 반영
```

---

# 1. 목표

RFC-0032의 목표는 다음과 같습니다.

1. `IncrementalGenerationPlan`의 Job을 정해진 순서로 실행
2. RFC-0031을 통해 Job별 `PromptPlan` 생성
3. 기존 `AiProvider`를 통해 실제 AI 호출
4. AI 응답을 요청된 문서 섹션으로 정규화
5. 변경되지 않은 섹션은 그대로 유지
6. 전체 작업 성공 후 결과를 원자적으로 반영
7. Job별 성공·실패 정보를 구조화하여 반환

---

# 2. 책임 범위

## RFC-0032가 담당하는 것

```text
GenerationPlan 실행
Job 의존 순서 준수
Prompt Builder 호출
AI Provider 호출
기본 응답 검증
Markdown 섹션 정규화
기존 문서와 병합
원자적 저장
실행 결과 보고
Preview 모드
```

## RFC-0032가 담당하지 않는 것

```text
변경 파일 탐지
Knowledge 영향 분석
생성할 Section 결정
Prompt Context 선택
사실 정확성 검증
Evidence 충실도 평가
AI 결과 품질 점수화
자동 재생성 판단
```

깊은 의미 검증과 재생성 판단은 RFC-0033에서 처리합니다.

---

# 3. 제안 패키지 구조

```text
io.docpilot.core.incremental.generation/

    IncrementalDocumentationGenerator.kt
    DefaultIncrementalDocumentationGenerator.kt

    DocumentationGenerationRequest.kt
    DocumentationGenerationResult.kt
    GenerationJobResult.kt

    GenerationExecutionMode.kt
    GenerationJobStatus.kt
    GenerationFailure.kt

    GeneratedSection.kt
    GeneratedSectionNormalizer.kt
    DefaultGeneratedSectionNormalizer.kt

    DocumentationSectionStore.kt
    FileDocumentationSectionStore.kt

    DocumentationTransaction.kt
    GenerationException.kt
```

테스트 경로:

```text
src/test/kotlin/io/docpilot/core/incremental/generation/
```

현재 프로젝트에 이미 동일한 역할의 Writer나 Markdown 모델이 있다면 새로 중복 구현하지 않고 기존 모델을 재사용합니다.

---

# 4. Generator 인터페이스

```kotlin
interface IncrementalDocumentationGenerator {

    fun generate(
        request: DocumentationGenerationRequest,
    ): DocumentationGenerationResult
}
```

Generator는 RFC-0030의 전체 Plan을 입력받아 Job들을 실행합니다.

---

# 5. 실행 요청 모델

```kotlin
data class DocumentationGenerationRequest(
    val plan: IncrementalGenerationPlan,
    val knowledge: KnowledgeBuildResult,
    val changeSet: ProjectChangeSet,
    val targetDocument: Path,
    val mode: GenerationExecutionMode = GenerationExecutionMode.WRITE,
)
```

`mode`는 두 가지를 제안합니다.

```kotlin
enum class GenerationExecutionMode {
    PREVIEW,
    WRITE,
}
```

### PREVIEW

* Provider를 호출하여 결과 생성
* 병합 결과를 반환
* 실제 파일은 변경하지 않음

### WRITE

* Provider 호출
* 결과 검증
* 모든 Job 성공 후 파일에 반영

CLI에서는 다음과 같은 형태로 연결할 수 있습니다.

```text
docpilot generate --preview
docpilot generate --write
```

CLI 명령 자체는 이번 RFC에서 반드시 구현하지 않아도 됩니다. 코어 API만 준비합니다.

---

# 6. 실행 결과 모델

```kotlin
data class DocumentationGenerationResult(
    val status: DocumentationGenerationStatus,
    val jobs: List<GenerationJobResult>,
    val generatedDocument: String?,
    val written: Boolean,
)
```

```kotlin
enum class DocumentationGenerationStatus {
    NO_CHANGES,
    SUCCEEDED,
    FAILED,
}
```

Job별 결과:

```kotlin
data class GenerationJobResult(
    val sectionId: ArchitectureSectionId,
    val status: GenerationJobStatus,
    val generatedSection: GeneratedSection?,
    val failure: GenerationFailure?,
)
```

```kotlin
enum class GenerationJobStatus {
    SUCCEEDED,
    FAILED,
    SKIPPED,
}
```

---

# 7. 실행 순서

RFC-0032는 RFC-0030이 제공한 Job 순서를 존중해야 합니다.

```text
IncrementalGenerationPlan.jobs
    ↓
Job 1
    ↓
Job 2
    ↓
Job 3
```

RFC-0032가 임의로 Priority를 다시 계산하거나 Job을 재정렬하지 않습니다.

다만 실행 전에 다음은 확인합니다.

```text
모든 dependency가 앞선 Job에 존재하는가?
중복 sectionId가 있는가?
순환 의존성이 있는가?
각 Job의 token budget이 유효한가?
```

잘못된 Plan이면 Provider를 호출하기 전에 실패시킵니다.

---

# 8. 순차 실행을 기본으로 제안

RFC-0032 v1에서는 병렬 실행보다 **순차 실행**을 권장합니다.

```text
Job 1 완료
    ↓
Job 2 실행
    ↓
Job 3 실행
```

이유는 다음과 같습니다.

* 섹션 사이에 의존성이 존재함
* 로컬 Ollama 환경에서 동시 호출은 메모리 부담이 큼
* 테스트와 실행 결과가 결정론적임
* 앞선 섹션 결과를 이후 섹션의 기존 컨텍스트로 사용할 수 있음
* 오류 발생 지점을 명확하게 확인할 수 있음

병렬 실행은 후속 RFC에서 독립 Job 그룹을 분석한 뒤 추가하는 것이 안전합니다.

---

# 9. Job 하나의 실행 과정

각 `GenerationJob`은 다음 단계를 거칩니다.

```text
1. 기존 문서에서 대상 Section 읽기
2. PromptBuildRequest 생성
3. RFC-0031 Prompt Builder 호출
4. PromptPlan을 AiProvider 요청으로 변환
5. AiProvider.generate() 호출
6. 응답 정규화
7. 기본 Output Contract 검증
8. GeneratedSection 임시 저장
```

의사 흐름:

```kotlin
for (job in plan.jobs) {
    val existingSection = sectionStore.read(job.sectionId)

    val promptPlan = promptBuilder.build(
        PromptBuildRequest(
            job = job,
            knowledgeGraph = knowledge.graph,
            projectChangeSet = changeSet,
            section = existingSection,
        ),
    )

    val response = provider.generate(
        promptPlanAdapter.toRequest(promptPlan),
    )

    val generatedSection = normalizer.normalize(
        response = response,
        contract = promptPlan.outputContract,
    )

    transaction.stage(generatedSection)
}

transaction.commit()
```

실제 구현은 현재 `AiProvider`의 인터페이스에 맞춰 조정합니다.

---

# 10. Provider 연결

RFC-0031의 `PromptPlan`은 Provider 중립 모델입니다.

RFC-0032에서는 이를 현재 프로젝트의 AI 요청 모델로 변환하는 Adapter가 필요합니다.

```text
PromptPlan
    ↓
AiGenerationRequestAdapter
    ↓
AiGenerationRequest
    ↓
AiProvider.generate()
```

제안 인터페이스:

```kotlin
interface AiGenerationRequestAdapter {

    fun adapt(
        promptPlan: PromptPlan,
        job: GenerationJob,
    ): AiGenerationRequest
}
```

Provider 구현은 다음과 같이 그대로 유지합니다.

```text
AiProvider
    ├─ OllamaAiProvider
    ├─ OpenAiProvider
    ├─ ClaudeAiProvider
    └─ GeminiAiProvider
```

Generator는 구체적인 Provider 클래스를 참조하지 않고 `AiProvider` 인터페이스에만 의존합니다.

---

# 11. 응답 정규화

AI 응답은 다음과 같이 다양할 수 있습니다.

```markdown
## Dependencies
내용
```

````markdown
```markdown
## Dependencies
내용
````

````

```text
Here is the updated section:

## Dependencies
내용
````

RFC-0032는 이를 요청된 섹션 Markdown으로 정규화해야 합니다.

```kotlin
interface GeneratedSectionNormalizer {

    fun normalize(
        rawResponse: String,
        contract: PromptOutputContract,
    ): GeneratedSection
}
```

기본 규칙:

* 앞뒤 공백 제거
* Markdown 코드 펜스 제거
* 모델의 설명 문장 제거 가능 여부 검토
* 요청된 Heading 확인
* Heading 중복 제거
* 마지막 줄바꿈 통일
* 빈 응답 거부
* 요청하지 않은 상위 섹션 거부

다만 공격적인 자동 수정을 하면 정상 문서가 손상될 수 있으므로, v1에서는 최소한의 정규화만 수행합니다.

---

# 12. 기본 출력 검증

RFC-0032에서는 의미 검증이 아닌 **형식 검증**만 수행합니다.

검증 항목:

```text
응답이 비어 있지 않음
요청된 Section heading이 존재함
다른 동일 레벨 Section이 포함되지 않음
Markdown 코드 펜스로 전체가 감싸져 있지 않음
최대 응답 크기를 초과하지 않음
OutputContract와 형식이 일치함
```

예를 들어 `allowAdditionalSections = false`인데 다음 응답이 오면 실패합니다.

```markdown
## Dependencies

...

## Architecture Summary

...
```

아키텍처적 정확성이나 Evidence 왜곡 여부는 RFC-0033에서 검증합니다.

---

# 13. 문서 병합

가장 중요한 요구사항은 다음입니다.

> 변경 대상이 아닌 섹션은 절대로 다시 생성하거나 수정하지 않는다.

기존 문서:

```markdown
# Architecture

## System Context
기존 내용

## Dependencies
기존 내용

## Persistence
기존 내용
```

`DEPENDENCIES`만 변경되면 결과는 다음과 같습니다.

```markdown
# Architecture

## System Context
기존 내용 그대로

## Dependencies
AI가 생성한 새 내용

## Persistence
기존 내용 그대로
```

이를 위해 Section Store를 분리합니다.

```kotlin
interface DocumentationSectionStore {

    fun read(
        sectionId: ArchitectureSectionId,
    ): ArchitectureSection?

    fun replace(
        document: String,
        generatedSections: List<GeneratedSection>,
    ): String
}
```

현재 프로젝트에 기존 Markdown Writer 또는 Architecture Document Renderer가 있다면 그것을 우선 재사용합니다.

---

# 14. 원자적 저장

기본 저장 정책은 **All-or-Nothing**을 제안합니다.

```text
Job 1 성공
Job 2 성공
Job 3 실패
    ↓
원본 문서 변경하지 않음
```

즉, Job마다 즉시 파일을 덮어쓰지 않습니다.

```text
원본 문서 읽기
    ↓
생성 결과를 메모리에 Stage
    ↓
모든 Job 성공
    ↓
임시 파일 작성
    ↓
원본 파일 교체
```

장점:

* 중간 실패로 문서가 불완전해지지 않음
* Summary만 갱신되고 세부 섹션이 실패하는 상태 방지
* 실행 전 문서로 쉽게 되돌릴 수 있음

파일 저장은 가능하면 다음 방식으로 처리합니다.

```text
architecture.md.tmp
    ↓
flush
    ↓
atomic move
    ↓
architecture.md
```

운영체제나 파일시스템이 atomic move를 지원하지 않는 경우 안전한 replace 방식으로 대체합니다.

---

# 15. 실패 정책

RFC-0032 v1의 기본 정책은 다음을 권장합니다.

```text
한 Job 실패
    ↓
뒤의 Job은 SKIPPED
    ↓
파일은 변경하지 않음
    ↓
전체 결과 FAILED
```

예:

```text
PERSISTENCE        SUCCEEDED
DEPENDENCIES       FAILED
ARCHITECTURE_SUMMARY SKIPPED
```

Summary가 마지막에 생성되더라도 앞의 세부 섹션이 실패하면 실행하지 않는 것이 일관성이 있습니다.

## 자동 재시도

RFC-0032에서는 별도의 자동 재시도를 추가하지 않는 것을 권장합니다.

이유:

* 현재 Provider가 자체 timeout이나 retry를 가질 수 있음
* Generator와 Provider 모두 재시도하면 호출 수가 급증할 수 있음
* RFC-0033의 검증 실패 재생성과 구분하기 어려움

RFC-0032는 Provider가 반환한 성공 또는 실패를 Job 결과로 기록합니다.

---

# 16. 빈 Plan 처리

`IncrementalGenerationPlan.jobs`가 비어 있으면 Provider를 호출하지 않습니다.

```kotlin
DocumentationGenerationResult(
    status = NO_CHANGES,
    jobs = emptyList(),
    generatedDocument = null,
    written = false,
)
```

이 동작은 증분 문서화에서 중요합니다.

변경이 없는데 AI를 호출하면 비용과 시간이 낭비되기 때문입니다.

---

# 17. 결정론

AI 출력 자체는 항상 동일하다고 보장할 수 없습니다.

하지만 실행 엔진은 다음을 결정론적으로 처리해야 합니다.

```text
Job 실행 순서
Prompt Builder 호출 순서
결과 배열 순서
Section 병합 순서
Markdown 줄바꿈
실패 이후 SKIPPED 처리
파일 저장 방식
```

`Instant.now()`, UUID, 랜덤 값은 생성 결과 비교를 방해하므로 핵심 모델에 불필요하게 넣지 않는 것이 좋습니다.

실행 시간 기록이 필요하다면 별도 런타임 메타데이터로 분리합니다.

---

# 18. RFC-0033과의 연결

RFC-0032 v1:

```text
AI 응답
    ↓
형식 검증
    ↓
문서 반영
```

RFC-0033 적용 후:

```text
AI 응답
    ↓
형식 검증
    ↓
Evidence 기반 검증
    ↓
모순 검사
    ↓
품질 점수
    ↓
통과 → 문서 반영
실패 → 수정 Prompt 또는 재생성
```

따라서 RFC-0032 내부에 Reviewer 로직을 직접 넣지 않고, 후속 Validator를 삽입할 수 있는 경계만 준비합니다.

예:

```kotlin
interface GeneratedSectionGate {

    fun evaluate(
        section: GeneratedSection,
    ): GenerationGateResult
}
```

다만 RFC-0032에서는 기본 형식 Gate만 제공하고, AI Reviewer 구현은 RFC-0033에서 추가합니다.

---

# 19. 테스트 범위

코드 구현 시 다음 테스트가 필요합니다.

## 실행 순서

```text
Planner가 제공한 dependency order로 Job을 실행한다.
Summary Job을 마지막에 실행한다.
```

## Prompt 연결

```text
각 Job마다 RFC-0031 Prompt Builder를 한 번 호출한다.
기존 Section 내용을 Prompt Builder에 전달한다.
```

## Provider 호출

```text
각 Job마다 AiProvider를 한 번 호출한다.
빈 Plan에서는 Provider를 호출하지 않는다.
구체적인 OllamaAiProvider 클래스에 의존하지 않는다.
```

## 문서 보존

```text
생성 대상이 아닌 Section은 변경하지 않는다.
대상 Section만 교체한다.
```

## 원자성

```text
모든 Job 성공 시에만 파일을 변경한다.
중간 Job 실패 시 원본 파일을 보존한다.
```

## 실패 처리

```text
실패한 Job 이후의 Job은 SKIPPED 처리한다.
Provider 예외를 구조화된 GenerationFailure로 변환한다.
```

## 응답 검증

```text
빈 응답을 거부한다.
Markdown 코드 펜스를 정규화한다.
요청하지 않은 추가 Section을 거부한다.
잘못된 Heading을 거부한다.
```

## 실행 모드

```text
PREVIEW는 병합 결과를 반환하지만 파일을 변경하지 않는다.
WRITE는 성공한 결과를 파일에 반영한다.
```

## 결정론

```text
동일한 Plan에서 Job 결과 순서가 항상 동일하다.
입력 순서가 고정되면 Section 병합 결과도 동일하다.
```

---

# 20. 완료 기준

RFC-0032는 다음 조건을 만족하면 완료된 것으로 봅니다.

```text
IncrementalGenerationPlan 전체를 실행할 수 있다.
RFC-0031 Prompt Builder를 재사용한다.
기존 AiProvider 인터페이스를 통해 모델을 호출한다.
Ollama 구현에 직접 의존하지 않는다.
Job 의존 순서를 유지한다.
변경 대상 Section만 갱신한다.
변경되지 않은 문서 내용은 보존한다.
중간 실패 시 원본 문서를 변경하지 않는다.
PREVIEW와 WRITE 모드를 지원한다.
기본 OutputContract를 검증한다.
Job별 실행 결과를 반환한다.
단위 테스트가 통과한다.
```

---

# 21. 구현 전 확인할 프로젝트 요소

실제 코드 작업에서는 현재 `docpilot-core`를 기준으로 다음을 먼저 확인해야 합니다.

```text
AiProvider 실제 인터페이스
AiProvider.generate() 입력·출력 타입
기존 Markdown Writer/Renderer 존재 여부
ArchitectureSection의 실제 구조
ArchitectureSectionId와 Markdown heading 매핑
RFC-0030 GenerationJob 필드
RFC-0031 PromptPlan과 OutputContract 필드
Coroutine 사용 여부
파일 출력 경로 정책
```

특히 기존 Provider가 `suspend fun`을 사용한다면 Generator도 `suspend` 기반으로 구현하는 것이 자연스럽습니다.

예:

```kotlin
interface IncrementalDocumentationGenerator {

    suspend fun generate(
        request: DocumentationGenerationRequest,
    ): DocumentationGenerationResult
}
```

이 부분은 실제 프로젝트 API를 확인한 뒤 결정합니다.

---

# 승인 제안 범위

```text
RFC-0032: Incremental Documentation Generator

- GenerationPlan을 순차 실행
- RFC-0031 Prompt Builder 사용
- 기존 AiProvider 인터페이스 사용
- Provider별 구현에는 직접 의존하지 않음
- 한 Job당 한 번의 AI 생성 호출
- PREVIEW / WRITE 모드 지원
- 형식 중심의 기본 응답 검증
- 변경 대상 Section만 교체
- 모든 Job 성공 후 원자적 저장
- 실패 시 뒤의 Job은 SKIPPED
- Generator 자체 자동 재시도 없음
- 병렬 실행 제외
- 의미·Evidence 검증은 RFC-0033으로 연기
```

이 범위 승인 후 현재 프로젝트 구조를 다시 확인하여 RFC 문서, 구현 코드, 테스트, 적용 안내 및 커밋 메시지를 포함한 패치 ZIP을 만들겠습니다.
