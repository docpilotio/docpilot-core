오늘 오전 11:42
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> cd $McpRoot
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg = Get-Content .\package.json -Raw | ConvertFrom-Json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg | Select-Object name, version, type, main, bin


name    : docpilot-mcp
version : 0.12.1
type    : module
main    : index.js
bin     :



PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg.scripts.PSObject.Properties |
>>   Select-Object Name, Value |
>>   Format-Table -AutoSize

Name       Value
----       -----
build      tsc --noEmit
dev        tsx src/index.ts
inspector  npx -y @modelcontextprotocol/inspector tsx src/index.ts
test       vitest run
test:watch vitest


PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "n--- tsconfig.json ---"

--- tsconfig.json ---
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-Content .\tsconfig.json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "types": ["node"],
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "verbatimModuleSyntax": true,
    "noEmit": true
  },
  "include": ["src/**/*.ts"]
}
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "n--- Server source candidates ---"

--- Server source candidates ---
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-ChildItem .\src -Recurse -File |
>>   Where-Object {
>>     $_.Name -match "server|index|main"
>>   } |
>>   Select-Object FullName

FullName
--------
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\index.ts
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\server.ts
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\prompt...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\tool\G...


PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "n--- Runtime output ---"

--- Runtime output ---
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-ChildItem .\dist -Recurse -File -ErrorAction SilentlyContinue |
>>   Select-Object FullName
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
1m 10s 동안 처리함
진단 결과

현재 docpilot-mcp v0.12.1은 소스 직접 실행 방식만 가능한 상태입니다.

항목	현재 상태	판정
npm run build	tsc --noEmit	타입 검사만 수행
tsconfig.json	"noEmit": true	JavaScript 생성 안 함
dist	파일 없음	현재 설정에서는 정상
main	index.js	실제 파일이 없어 잘못된 경로
bin	미설정	CLI 실행 패키지로는 사용할 수 없음
개발 실행	tsx src/index.ts	가능
Inspector 실행	tsx src/index.ts	가능
배포형 실행	node dist/index.js	현재 불가능

따라서 dist가 비어 있는 것은 빌드 실패가 아니라, 애초에 출력물을 만들지 않도록 설정되어 있기 때문입니다.

v0.12.2에서 해결해야 할 핵심

실제 Codex MCP 서버로 안정적으로 등록하려면 다음 구조로 바꾸는 것이 적절합니다.

npm run build
    ├─ TypeScript 타입 검사
    └─ dist/index.js 생성

npm run start:mcp
    └─ node dist/index.js

bin은 npm CLI 패키지로 배포할 때 필요하며, 현재 로컬 Codex 통합에는 필수가 아닙니다. 우선 node dist/index.js를 Codex가 직접 실행하도록 구성하면 됩니다.

먼저 확인할 진입점

다음 명령을 실행해 주세요.

Write-Host "`n--- src/index.ts ---"
Get-Content .\src\index.ts

Write-Host "`n--- src/server.ts ---"
Get-Content .\src\server.ts

Write-Host "`n--- MCP stdio/runtime scan ---"
Get-ChildItem .\src -Recurse -File -Filter *.ts |
    Select-String -Pattern `
        "StdioServerTransport|server\.connect|console\.log|process\.stdout|process\.stderr" |
    Select-Object Path, LineNumber, Line |
    Format-Table -Wrap -AutoSize

확인 기준은 다음과 같습니다.

const transport = new StdioServerTransport();
await server.connect(transport);

또한 MCP stdio 서버에서는 다음 사항이 중요합니다.

stdout
- MCP JSON-RPC 전용
- console.log 사용 금지

stderr
- 시작 로그 및 진단 로그 사용 가능
- console.error 사용 가능

src/index.ts에 다음과 같은 코드가 있다면 수정 대상입니다.

console.log("DocPilot MCP server started.");

대신 다음처럼 바꿔야 합니다.

console.error("DocPilot MCP server started.");
예정된 빌드 구성

진입점이 정상임을 확인하면 기존 엄격한 타입 검사 설정을 유지하면서 별도의 배포 설정을 추가하는 방식이 안전합니다.

tsconfig.build.json
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "noEmit": false,
    "rootDir": "./src",
    "outDir": "./dist",
    "sourceMap": true
  },
  "exclude": [
    "src/**/*.test.ts",
    "src/**/*.spec.ts",
    "src/**/__tests__/**"
  ]
}
package.json 목표 상태
{
  "main": "dist/index.js",
  "scripts": {
    "typecheck": "tsc --noEmit",
    "build": "npm run typecheck && tsc -p tsconfig.build.json",
    "start:mcp": "node dist/index.js",
    "dev": "tsx src/index.ts",
    "inspector": "npx -y @modelcontextprotocol/inspector tsx src/index.ts",
    "test": "vitest run",
    "test:watch": "vitest"
  }
}

현재 결론은 명확합니다.

v0.12.1 구현과 테스트는 완료되었지만, 실제 시스템 통합에 필요한 배포형 MCP 런타임 엔트리포인트는 아직 구성되지 않았습니다.
다음 단계는 src/index.ts의 stdio 연결과 stdout 오염 여부를 확인한 후 dist/index.js 기반 실행 구성을 추가하는 것입니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "n--- src/server.ts ---"

--- src/server.ts ---
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-Content .\src\server.ts
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { registerGenerateMainPlanningSyncPrompt } from "./prompt/GenerateMainPlanningSyncPrompt.js";
import { ProjectStateRepository } from "./repository/ProjectStateRepository.js";
import { registerProjectDashboardResource } from "./resource/ProjectDashboardResource.js";
import { registerProjectStatusResource } from "./resource/ProjectStatusResource.js";
import { ProjectStatusService } from "./service/ProjectStatusService.js";
import { ImplementationOrchestrationService } from "./service/ImplementationOrchestrationService.js";
import { ControlledProcessRunner } from "./orchestration/ControlledProcessRunner.js";
import { GitRepositoryController } from "./orchestration/GitRepositoryController.js";
import { LocalCodexWorkerAdapter } from "./orchestration/CodexWorkerAdapter.js";
import { registerCompleteCurrentRfcTool } from "./tool/CompleteCurrentRfcTool.js";
import { registerGenerateMainPlanningSyncTool } from "./tool/GenerateMainPlanningSyncTool.js";
import { registerGetCurrentRfcTool } from "./tool/GetCurrentRfcTool.js";
import { registerGetProjectStatusTool } from "./tool/GetProjectStatusTool.js";
import { registerGetPlanningSynchronizationStatusTool } from "./tool/GetPlanningSynchronizationStatusTool.js";
import { registerLoadRfcContextTool } from "./tool/LoadRfcContextTool.js";
import { registerSubmitRfcHandoffTool } from "./tool/SubmitRfcHandoffTool.js";
import { registerGetPendingRfcHandoffTool } from "./tool/GetPendingRfcHandoffTool.js";
import { registerEvaluateRfcCompletionReadinessTool } from "./tool/EvaluateRfcCompletionReadinessTool.js";
import { registerGetDocPilotProjectControlContextTool } from "./tool/GetDocPilotProjectControlContextTool.js";
import { registerListCompletedRfcsTool } from "./tool/ListCompletedRfcsTool.js";
import { registerMarkCurrentRfcCompletedTool } from "./tool/MarkCurrentRfcCompletedTool.js";
import { registerPreviewCurrentRfcRollbackTool } from "./tool/PreviewCurrentRfcRollbackTool.js";
import { registerRollbackCurrentRfcTool } from "./tool/RollbackCurrentRfcTool.js";
import { registerStartNextRfcTool } from "./tool/StartNextRfcTool.js";
import { registerUpdateProjectStatusTool } from "./tool/UpdateProjectStatusTool.js";
import { registerUpdateReleaseReadinessTool } from "./tool/UpdateReleaseReadinessTool.js";
import { registerPrepareImplementationWorkOrderTool } from "./tool/PrepareImplementationWorkOrderTool.js";
import { registerGetPendingImplementationWorkOrderTool } from "./tool/GetPendingImplementationWorkOrderTool.js";
import { registerExecutePendingImplementationWorkOrderTool } from "./tool/ExecutePendingImplementationWorkOrderTool.js";
import { registerCreateImplementationCommitTool } from "./tool/CreateImplementationCommitTool.js";

export function createServer(): McpServer {
  const repository = new ProjectStateRepository();
  const service = new ProjectStatusService(repository);
  const runner = new ControlledProcessRunner();
  const git = new GitRepositoryController(runner);
  const orchestration = new ImplementationOrchestrationService(repository, service, runner, git, new LocalCodexWorkerAdapter(runner));

  const server = new McpServer({
    name: "docpilot-project-control",
    version: "0.12.1",
  });

  registerGetProjectStatusTool(server, service);
  registerGetCurrentRfcTool(server, service);
  registerCompleteCurrentRfcTool(server, service);
  registerMarkCurrentRfcCompletedTool(server, service);
  registerRollbackCurrentRfcTool(server, service);
  registerPreviewCurrentRfcRollbackTool(server, service);
  registerGetPlanningSynchronizationStatusTool(server, service);
  registerLoadRfcContextTool(server, service);
  registerSubmitRfcHandoffTool(server, service);
  registerGetPendingRfcHandoffTool(server, service);
  registerGetDocPilotProjectControlContextTool(server, service);
  registerEvaluateRfcCompletionReadinessTool(server, service);
  registerPrepareImplementationWorkOrderTool(server, orchestration);
  registerGetPendingImplementationWorkOrderTool(server, orchestration);
  registerExecutePendingImplementationWorkOrderTool(server, orchestration);
  registerCreateImplementationCommitTool(server, orchestration);
  registerGenerateMainPlanningSyncTool(server, service);
  registerListCompletedRfcsTool(server, service);
  registerUpdateProjectStatusTool(server, service);
  registerUpdateReleaseReadinessTool(server, service);
  registerStartNextRfcTool(server, service);

  registerProjectStatusResource(server, service);
  registerProjectDashboardResource(server, service);
  registerGenerateMainPlanningSyncPrompt(server, service);

  return server;
}
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "n--- MCP stdio/runtime scan ---"

--- MCP stdio/runtime scan ---
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-ChildItem .\src -Recurse -File -Filter *.ts |
>>     Select-String -Pattern 
>>         "StdioServerTransport|server\.connect|console\.log|process\.stdout|process\.stderr" |
>>     Select-Object Path, LineNumber, Line |
>>     Format-Table -Wrap -AutoSize

Path
----
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\index.ts
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\index.ts
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\index.ts


PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
39s 동안 처리함

확인 완료입니다.

StdioServerTransport  ✅
server.connect()       ✅
일반 로그 stderr       ✅
오류 로그 stderr       ✅
stdout 오염 없음       ✅

src/index.ts와 src/server.ts는 MCP stdio 서버 진입점으로 정상입니다. 남은 문제는 배포용 JavaScript 빌드 설정 부재뿐입니다.

1. tsconfig.build.json 생성
@'
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "noEmit": false,
    "rootDir": "./src",
    "outDir": "./dist",
    "sourceMap": true
  },
  "exclude": [
    "src/**/*.test.ts",
    "src/**/*.spec.ts",
    "src/**/__tests__/**"
  ]
}
'@ | Set-Content .\tsconfig.build.json -Encoding utf8
2. package.json 백업 및 스크립트 수정

아래 명령은 기존 dependencies와 기타 설정을 유지하면서 실행 관련 항목만 변경합니다.

Copy-Item .\package.json .\package.json.backup -Force

$pkg = Get-Content .\package.json -Raw | ConvertFrom-Json

$pkg.main = "dist/index.js"

$pkg.scripts.build = "npm run clean && npm run typecheck && tsc -p tsconfig.build.json"

if (-not $pkg.scripts.PSObject.Properties["typecheck"]) {
    $pkg.scripts |
        Add-Member -NotePropertyName "typecheck" `
                   -NotePropertyValue "tsc --noEmit"
}
else {
    $pkg.scripts.typecheck = "tsc --noEmit"
}

if (-not $pkg.scripts.PSObject.Properties["clean"]) {
    $pkg.scripts |
        Add-Member -NotePropertyName "clean" `
                   -NotePropertyValue "node -e `"require('node:fs').rmSync('dist',{recursive:true,force:true})`""
}
else {
    $pkg.scripts.clean = "node -e `"require('node:fs').rmSync('dist',{recursive:true,force:true})`""
}

if (-not $pkg.scripts.PSObject.Properties["start:mcp"]) {
    $pkg.scripts |
        Add-Member -NotePropertyName "start:mcp" `
                   -NotePropertyValue "node dist/index.js"
}
else {
    $pkg.scripts."start:mcp" = "node dist/index.js"
}

if (-not $pkg.scripts.PSObject.Properties["inspector:dev"]) {
    $pkg.scripts |
        Add-Member -NotePropertyName "inspector:dev" `
                   -NotePropertyValue "npx -y @modelcontextprotocol/inspector tsx src/index.ts"
}

$pkg.scripts.inspector = "npx -y @modelcontextprotocol/inspector node dist/index.js"

$pkg |
    ConvertTo-Json -Depth 100 |
    Set-Content .\package.json -Encoding utf8
3. 변경 결과 확인
$pkg = Get-Content .\package.json -Raw | ConvertFrom-Json

$pkg | Select-Object name, version, type, main, bin

$pkg.scripts.PSObject.Properties |
    Select-Object Name, Value |
    Format-Table -AutoSize

예상 결과:

name    : docpilot-mcp
version : 0.12.1
type    : module
main    : dist/index.js
bin     :

스크립트는 대략 다음과 같아야 합니다.

build          npm run clean && npm run typecheck && tsc -p tsconfig.build.json
clean          node -e "require('node:fs').rmSync('dist',{recursive:true,force:true})"
typecheck      tsc --noEmit
start:mcp      node dist/index.js
dev            tsx src/index.ts
inspector      npx -y @modelcontextprotocol/inspector node dist/index.js
inspector:dev  npx -y @modelcontextprotocol/inspector tsx src/index.ts
test           vitest run
test:watch     vitest
4. 전체 검증
npm run build
npm test

그다음 빌드 결과를 확인합니다.

Get-ChildItem .\dist -Recurse -File |
    Select-Object FullName, Length |
    Format-Table -AutoSize

최소한 다음 파일들이 있어야 합니다.

dist\index.js
dist\index.js.map
dist\server.js
dist\server.js.map

그리고 각 도구·서비스·리포지토리 파일도 src 구조에 대응하여 생성되어야 합니다.

5. 빌드된 MCP 서버 Inspector 검증
npm run inspector

Inspector에서 다음을 확인합니다.

서버 이름: docpilot-project-control
서버 버전: 0.12.1
Tools 목록 정상 표시
Resources 목록 정상 표시
Prompt 목록 정상 표시
getProjectStatus 같은 조회 도구 호출 성공
6. 직접 실행 시 주의
npm run start:mcp

이 명령은 일반 CLI처럼 실행 후 종료되는 프로그램이 아닙니다. MCP 클라이언트의 JSON-RPC 입력을 기다리므로 계속 실행 상태로 남는 것이 정상입니다.

종료할 때는:

Ctrl+C
최종 구조
docpilot-mcp
├─ src
│  ├─ index.ts
│  ├─ server.ts
│  └─ ...
├─ dist
│  ├─ index.js
│  ├─ server.js
│  └─ ...
├─ package.json
├─ tsconfig.json
└─ tsconfig.build.json

이 작업이 성공하면 docpilot-mcp는 다음 두 실행 방식을 모두 지원하게 됩니다.

개발 실행
tsx src/index.ts

실제 시스템 통합 실행
node dist/index.js

즉, 빌드 성공 후에는 Codex MCP 설정에서 tsx나 TypeScript 소스를 직접 참조하지 않고, node와 dist/index.js를 실행 대상으로 등록할 수 있습니다.

tsconfig.buid.json 파일의 위치는 어디야?

tsconfig.build.json은 package.json, tsconfig.json과 같은 폴더, 즉 MCP 프로젝트 루트에 생성합니다.

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\tsconfig.build.json

구조는 다음과 같습니다.

docpilot-mcp
├─ package.json
├─ tsconfig.json
├─ tsconfig.build.json   ← 여기
├─ src
│  ├─ index.ts
│  └─ server.ts
└─ ...

현재 PowerShell 위치가 이미 프로젝트 루트이므로 다음 명령을 실행하면 정확한 위치에 생성됩니다.

@'
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "noEmit": false,
    "rootDir": "./src",
    "outDir": "./dist",
    "sourceMap": true
  },
  "exclude": [
    "src/**/*.test.ts",
    "src/**/*.spec.ts",
    "src/**/__tests__/**"
  ]
}
'@ | Set-Content .\tsconfig.build.json -Encoding utf8

생성 확인:

Get-Item .\tsconfig.build.json
Get-Content .\tsconfig.build.json

파일명은 tsconfig.buid.json이 아니라 **tsconfig.build.json**입니다.

package.json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Copy-Item .\package.json .\package.json.backup -Force
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg = Get-Content .\package.json -Raw | ConvertFrom-Json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg.main = "dist/index.js"
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg.scripts.build = "npm run clean && npm run typecheck && tsc -p tsconfig.build.json"
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> if (-not $pkg.scripts.PSObject.Properties["typecheck"]) {
>>     $pkg.scripts |
>>         Add-Member -NotePropertyName "typecheck" 
>>                    -NotePropertyValue "tsc --noEmit"
>> }
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> else {
>>     $pkg.scripts.typecheck = "tsc --noEmit"
>> }
else : 'else' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 있는 프
로그램 이름으로 인식되지 않습니다. 이름이 정확한지 확인하고 경로가 포함
된 경우 경로가 올바른지 검증한 다음 다시 시도하십시오.
위치 줄:1 문자:1
+ else {
+ ~~~~
    + CategoryInfo          : ObjectNotFound: (else:String) [], Comman
   dNotFoundException
    + FullyQualifiedErrorId : CommandNotFoundException

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> if (-not $pkg.scripts.PSObject.Properties["clean"]) {
>>     $pkg.scripts |
>>         Add-Member -NotePropertyName "clean" 
>>                    -NotePropertyValue "node -e "require('node:fs').rmSync('dist',{recursive:true,force:true})""
>> }
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> else {
>>     $pkg.scripts.clean = "node -e "require('node:fs').rmSync('dist',{recursive:true,force:true})""
>> }
else : 'else' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 있는 프
로그램 이름으로 인식되지 않습니다. 이름이 정확한지 확인하고 경로가 포함
된 경우 경로가 올바른지 검증한 다음 다시 시도하십시오.
위치 줄:1 문자:1
+ else {
+ ~~~~
    + CategoryInfo          : ObjectNotFound: (else:String) [], Comman
   dNotFoundException
    + FullyQualifiedErrorId : CommandNotFoundException

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> if (-not $pkg.scripts.PSObject.Properties["start:mcp"]) {
>>     $pkg.scripts |
>>         Add-Member -NotePropertyName "start:mcp" 
>>                    -NotePropertyValue "node dist/index.js"
>> }
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> else {
>>     $pkg.scripts."start:mcp" = "node dist/index.js"
>> }
else : 'else' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 있는 프
로그램 이름으로 인식되지 않습니다. 이름이 정확한지 확인하고 경로가 포함
된 경우 경로가 올바른지 검증한 다음 다시 시도하십시오.
위치 줄:1 문자:1
+ else {
+ ~~~~
    + CategoryInfo          : ObjectNotFound: (else:String) [], Comman
   dNotFoundException
    + FullyQualifiedErrorId : CommandNotFoundException

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> if (-not $pkg.scripts.PSObject.Properties["inspector:dev"]) {
>>     $pkg.scripts |
>>         Add-Member -NotePropertyName "inspector:dev" 
>>                    -NotePropertyValue "npx -y @modelcontextprotocol/inspector tsx src/index.ts"
>> }
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg.scripts.inspector = "npx -y @modelcontextprotocol/inspector node dist/index.js"
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg |
>>     ConvertTo-Json -Depth 100 |
>>     Set-Content .\package.json -Encoding utf8
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

오류 원인은 PowerShell에서 else를 별도 명령으로 실행했기 때문입니다.

PowerShell에서는 다음처럼 }와 else가 같은 입력 블록에 있어야 합니다.

if ($condition) {
    # 실행
} else {
    # 실행
}

다만 이번에는 각 속성이 없어서 if 안의 Add-Member가 이미 정상 실행됐습니다. 따라서 else 오류는 package.json 변경을 망가뜨린 오류가 아닙니다.

현재 상태를 먼저 확인하세요.

$pkg = Get-Content .\package.json -Raw | ConvertFrom-Json

$pkg | Select-Object name, version, type, main

$pkg.scripts.PSObject.Properties |
    Select-Object Name, Value |
    Format-Table -AutoSize

다음 항목들이 있으면 정상입니다.

main           dist/index.js
build          npm run clean && npm run typecheck && tsc -p tsconfig.build.json
typecheck      tsc --noEmit
clean          node -e "require('node:fs').rmSync('dist',{recursive:true,force:true})"
start:mcp      node dist/index.js
inspector      npx -y @modelcontextprotocol/inspector node dist/index.js
inspector:dev  npx -y @modelcontextprotocol/inspector tsx src/index.ts
확실하게 다시 정리하는 명령

아래 명령은 if/else 없이 기존 값이 있든 없든 안전하게 덮어씁니다.

$pkg = Get-Content .\package.json -Raw | ConvertFrom-Json

$pkg.main = "dist/index.js"

$pkg.scripts |
    Add-Member -NotePropertyName "typecheck" `
               -NotePropertyValue "tsc --noEmit" `
               -Force

$pkg.scripts |
    Add-Member -NotePropertyName "clean" `
               -NotePropertyValue 'node -e "require(''node:fs'').rmSync(''dist'',{recursive:true,force:true})"' `
               -Force

$pkg.scripts |
    Add-Member -NotePropertyName "build" `
               -NotePropertyValue "npm run clean && npm run typecheck && tsc -p tsconfig.build.json" `
               -Force

$pkg.scripts |
    Add-Member -NotePropertyName "start:mcp" `
               -NotePropertyValue "node dist/index.js" `
               -Force

$pkg.scripts |
    Add-Member -NotePropertyName "inspector" `
               -NotePropertyValue "npx -y @modelcontextprotocol/inspector node dist/index.js" `
               -Force

$pkg.scripts |
    Add-Member -NotePropertyName "inspector:dev" `
               -NotePropertyValue "npx -y @modelcontextprotocol/inspector tsx src/index.ts" `
               -Force

$pkg |
    ConvertTo-Json -Depth 100 |
    Set-Content .\package.json -Encoding utf8

그다음 검증합니다.

Get-Content .\package.json

npm run build
npm test

빌드 성공 후:

Get-ChildItem .\dist -Recurse -File |
    Select-Object FullName, Length |
    Format-Table -AutoSize

dist\index.js와 dist\server.js가 생성되면 배포용 MCP 빌드 구성이 정상 완료된 것입니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg.scripts.inspector = "npx -y @modelcontextprotocol/inspector node dist/index.js"
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg |
>>     ConvertTo-Json -Depth 100 |
>>     Set-Content .\package.json -Encoding utf8
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg = Get-Content .\package.json -Raw | ConvertFrom-Json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg | Select-Object name, version, type, main

name         version type   main
----         ------- ----   ----
docpilot-mcp 0.12.1  module dist/index.js


PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg.scripts.PSObject.Properties |
>>     Select-Object Name, Value |
>>     Format-Table -AutoSize

Name          Value
----          -----
build         npm run clean && npm run typecheck && tsc -p tsconfig....
dev           tsx src/index.ts
inspector     npx -y @modelcontextprotocol/inspector node dist/index.js
test          vitest run
test:watch    vitest
typecheck     tsc --noEmit
clean         node -e "require('node:fs').rmSync('dist',{recursive:t...
start:mcp     node dist/index.js
inspector:dev npx -y @modelcontextprotocol/inspector tsx src/index.ts


PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

현재 package.json 수정은 정상적으로 반영되었습니다.

확인된 상태:

main          dist/index.js
build         배포용 빌드 명령 등록
typecheck     등록 완료
clean         등록 완료
start:mcp     등록 완료
inspector     dist/index.js 기준으로 변경
inspector:dev TypeScript 직접 실행 유지

표에서 build, clean 값이 ...로 보이는 것은 오류가 아니라 PowerShell 테이블 너비 때문에 잘린 것입니다. 정확한 값은 다음으로 확인할 수 있습니다.

$pkg.scripts |
    Format-List build, typecheck, clean, "start:mcp", inspector, "inspector:dev"

이제 tsconfig.build.json이 프로젝트 루트에 있는지 확인합니다.

Test-Path .\tsconfig.build.json
Get-Content .\tsconfig.build.json

True가 나오면 다음 검증을 진행하세요.

npm run build

빌드 성공 후 출력 파일 확인:

Get-ChildItem .\dist -Recurse -File |
    Select-Object FullName, Length |
    Format-Table -AutoSize

최소한 다음 파일이 있어야 합니다.

dist\index.js
dist\index.js.map
dist\server.js
dist\server.js.map

그다음 전체 테스트를 실행합니다.

npm test

마지막으로 배포 결과물을 사용하는 MCP Inspector를 실행합니다.

npm run inspector

직접 서버 실행도 확인할 수 있습니다.

npm run start:mcp

이 명령이 종료되지 않고 입력을 기다리는 것은 정상입니다. MCP stdio 서버이므로 종료하려면 Ctrl+C를 누릅니다.


PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Test-Path .\tsconfig.build.json
True
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-Content .\tsconfig.build.json
@'
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "noEmit": false,
    "rootDir": "./src",
    "outDir": "./dist",
    "sourceMap": true
  },
  "exclude": [
    "src/**/*.test.ts",
    "src/**/*.spec.ts",
    "src/**/__tests__/**"
  ]
}
'@ | Set-Content .\tsconfig.build.json -Encoding utf8
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

tsconfig.build.json 내용이 잘못되었습니다. 현재 파일 안에 JSON이 아니라 PowerShell 명령문 전체가 저장되어 있습니다.

아래 명령으로 파일을 정확히 덮어쓰세요.

@'
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "noEmit": false,
    "rootDir": "./src",
    "outDir": "./dist",
    "sourceMap": true
  },
  "exclude": [
    "src/**/*.test.ts",
    "src/**/*.spec.ts",
    "src/**/__tests__/**"
  ]
}
'@ | Set-Content .\tsconfig.build.json -Encoding utf8

중요한 점은 첫 줄의 @'와 마지막 줄의 '@를 포함한 전체 블록을 한 번에 붙여넣는 것입니다.

수정 후 확인하세요.

Get-Content .\tsconfig.build.json

정상 출력은 다음처럼 순수 JSON만 보여야 합니다.

{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "noEmit": false,
    "rootDir": "./src",
    "outDir": "./dist",
    "sourceMap": true
  },
  "exclude": [
    "src/**/*.test.ts",
    "src/**/*.spec.ts",
    "src/**/__tests__/**"
  ]
}

그다음 빌드합니다.

npm run build

빌드 성공 후 확인:

Test-Path .\dist\index.js
Get-ChildItem .\dist -Recurse -File

Test-Path 결과가 True이면 배포용 MCP 빌드가 정상 생성된 것입니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Test-Path .\dist\index.js
False
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-ChildItem .\dist -Recurse -File

False는 아직 빌드가 성공적으로 실행되지 않았거나, tsconfig.build.json이 여전히 잘못된 상태라는 뜻입니다.

PowerShell here-string 입력 문제를 피하도록 아래 명령을 그대로 실행하세요.

1. tsconfig.build.json을 안전하게 다시 생성
$buildConfig = [ordered]@{
    extends = "./tsconfig.json"
    compilerOptions = [ordered]@{
        noEmit    = $false
        rootDir   = "./src"
        outDir    = "./dist"
        sourceMap = $true
    }
    exclude = @(
        "src/**/*.test.ts"
        "src/**/*.spec.ts"
        "src/**/__tests__/**"
    )
}

$buildConfig |
    ConvertTo-Json -Depth 10 |
    Set-Content .\tsconfig.build.json -Encoding utf8
2. JSON 형식 확인
Get-Content .\tsconfig.build.json
Get-Content .\tsconfig.build.json -Raw | ConvertFrom-Json | Out-Null

Write-Host "tsconfig.build.json JSON validation passed."

오류가 없으면 정상입니다.

3. 빌드 스크립트 실제 값 확인
$pkg = Get-Content .\package.json -Raw | ConvertFrom-Json

Write-Host "build     : $($pkg.scripts.build)"
Write-Host "clean     : $($pkg.scripts.clean)"
Write-Host "typecheck : $($pkg.scripts.typecheck)"

다음처럼 나와야 합니다.

build     : npm run clean && npm run typecheck && tsc -p tsconfig.build.json
clean     : node -e "require('node:fs').rmSync('dist',{recursive:true,force:true})"
typecheck : tsc --noEmit
4. 실제 빌드 실행
npm run build

BUILD에 해당하는 명령은 자동으로 종료되어야 합니다. 끝난 뒤 exit code를 확인하세요.

Write-Host "Build exit code: $LASTEXITCODE"

정상 값:

Build exit code: 0
5. 결과 확인
Test-Path .\dist
Test-Path .\dist\index.js

Get-ChildItem .\dist -Recurse -File |
    Select-Object FullName, Length |
    Format-Table -AutoSize

정상이라면 두 Test-Path 결과가 모두 True여야 합니다.

빌드 후에도 생성되지 않을 경우

TypeScript 컴파일러를 직접 실행하여 출력 파일 목록과 오류를 확인합니다.

npx tsc -p .\tsconfig.build.json --listEmittedFiles

정상이라면 다음과 같은 출력이 표시됩니다.

TSFILE: ...\dist\index.js
TSFILE: ...\dist\index.js.map
TSFILE: ...\dist\server.js
TSFILE: ...\dist\server.js.map

현재 단계에서는 Test-Path만 실행한 것으로 보이며, 반드시 npm run build가 오류 없이 완료되어야 dist\index.js가 생성됩니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $pkg = Get-Content .\package.json -Raw | ConvertFrom-Json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "build     : $($pkg.scripts.build)"
build     : npm run clean && npm run typecheck && tsc -p tsconfig.build.json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "clean     : $($pkg.scripts.clean)"
clean     : node -e "require('node:fs').rmSync('dist',{recursive:true,force:true})"
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "typecheck : $($pkg.scripts.typecheck)"
typecheck : tsc --noEmit

설정값은 모두 정상입니다. 이제 실제 빌드만 실행하면 됩니다.

npm run build

완료 후 바로 확인하세요.

Write-Host "Build exit code: $LASTEXITCODE"

Test-Path .\dist
Test-Path .\dist\index.js

Get-ChildItem .\dist -Recurse -File |
    Select-Object FullName, Length |
    Format-Table -AutoSize

정상 결과는 다음과 같습니다.

Build exit code: 0
True
True

그리고 목록에 최소한 아래 파일이 보여야 합니다.

dist\index.js
dist\index.js.map
dist\server.js
dist\server.js.map

npm run build 중 오류가 발생하면, 출력 전체를 그대로 보내주세요. 특히 TypeScript 오류가 있으면 dist가 생성되지 않습니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "Build exit code: $LASTEXITCODE"
Build exit code: 0
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Test-Path .\dist
False
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Test-Path .\dist\index.js
False
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-ChildItem .\dist -Recurse -File |
>>     Select-Object FullName, Length |
>>     Format-Table -AutoSize
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

현재 출력만으로는 npm run build가 실제 실행되었다고 확인할 수 없습니다. $LASTEXITCODE는 가장 최근 외부 프로그램의 종료 코드가 남아 있는 값이라 이전 명령의 0일 수 있습니다.

아래 블록을 한 번에 실행해 빌드와 종료 코드를 바로 연결해서 확인하세요.

Remove-Item .\dist -Recurse -Force -ErrorAction SilentlyContinue

npm run build
$buildExitCode = $LASTEXITCODE

Write-Host "`nBuild exit code: $buildExitCode"
Write-Host "dist exists: $(Test-Path .\dist)"
Write-Host "index.js exists: $(Test-Path .\dist\index.js)"

if (Test-Path .\dist) {
    Get-ChildItem .\dist -Recurse -File |
        Select-Object FullName, Length |
        Format-Table -AutoSize
}

정상이라면 npm run build 중 다음 세 단계가 출력되어야 합니다.

npm run clean
npm run typecheck
tsc -p tsconfig.build.json
여전히 dist가 없을 때

먼저 실제 적용된 TypeScript 설정을 확인합니다.

npx tsc -p .\tsconfig.build.json --showConfig |
    Set-Content .\effective-tsconfig.json -Encoding utf8

$effective = Get-Content .\effective-tsconfig.json -Raw | ConvertFrom-Json

$effective.compilerOptions |
    Select-Object noEmit, rootDir, outDir, sourceMap |
    Format-List

Write-Host "Included source count: $($effective.files.Count)"

정상 기준:

noEmit    : False
rootDir   : ./src
outDir    : ./dist
sourceMap : True

files 개수도 0보다 커야 합니다.

그다음 TypeScript 컴파일을 직접 실행합니다.

Remove-Item .\dist -Recurse -Force -ErrorAction SilentlyContinue

npx tsc -p .\tsconfig.build.json --listEmittedFiles --pretty false
$tscExitCode = $LASTEXITCODE

Write-Host "`nTSC exit code: $tscExitCode"
Write-Host "dist exists: $(Test-Path .\dist)"
Write-Host "index.js exists: $(Test-Path .\dist\index.js)"

정상이라면 다음과 같은 행이 출력됩니다.

TSFILE: C:\WorkSpace\...\dist\index.js
TSFILE: C:\WorkSpace\...\dist\server.js
가장 중요한 확인

tsconfig.build.json 내용도 다시 확인하세요.

Get-Content .\tsconfig.build.json

파일에는 아래 JSON만 있어야 합니다.

{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "noEmit": false,
    "rootDir": "./src",
    "outDir": "./dist",
    "sourceMap": true
  },
  "exclude": [
    "src/**/*.test.ts",
    "src/**/*.spec.ts",
    "src/**/__tests__/**"
  ]
}

현재 가장 가능성이 높은 원인은 npm run build가 실제로 실행되지 않은 상태에서 이전 $LASTEXITCODE 값만 확인한 것입니다. 위 첫 번째 명령 블록으로 바로 판별할 수 있습니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Remove-Item .\dist -Recurse -Force -ErrorAction SilentlyContinue
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> npm run build

> docpilot-mcp@0.12.1 build
> npm run clean && npm run typecheck && tsc -p tsconfig.build.json


> docpilot-mcp@0.12.1 clean
> node -e "require('node:fs').rmSync('dist',{recursive:true,force:true})"


> docpilot-mcp@0.12.1 typecheck
> tsc --noEmit

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $buildExitCode = $LASTEXITCODE
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "nBuild exit code: $buildExitCode"

Build exit code: 0
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "dist exists: $(Test-Path .\dist)"
dist exists: True
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "index.js exists: $(Test-Path .\dist\index.js)"
index.js exists: True
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> if (Test-Path .\dist) {
>>     Get-ChildItem .\dist -Recurse -File |
>>         Select-Object FullName, Length |
>>         Format-Table -AutoSize
>> }

FullName
--------
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\serve...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\serve...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\model...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\orche...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\orche...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\orche...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\orche...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\orche...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\orche...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\orche...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\orche...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\promp...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\promp...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\repos...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\repos...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\resou...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\resou...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\resou...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\resou...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\servi...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\servi...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\servi...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\servi...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\tool\...


PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

빌드가 정상 완료되었습니다.

확인 결과:

Build exit code: 0
dist exists: True
index.js exists: True

즉, 이제 배포형 MCP 실행 파일이 생성되었습니다.

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js

파일 목록이 ...로 잘려 보이는 것은 PowerShell 표 너비 문제일 뿐입니다.

정확한 상대 경로만 보려면:

Get-ChildItem .\dist -Recurse -File |
    ForEach-Object {
        $_.FullName.Replace("$PWD\", "")
    }
다음 검증

먼저 전체 테스트를 실행합니다.

npm test
$testExitCode = $LASTEXITCODE

Write-Host "Test exit code: $testExitCode"

정상 기준:

Test exit code: 0

그다음 배포된 JavaScript를 사용하는 Inspector를 실행합니다.

npm run inspector

Inspector에서 다음을 확인합니다.

서버 연결 성공
서버명: docpilot-project-control
버전: 0.12.1
Tools 목록 표시
Resources 목록 표시
Prompts 목록 표시
getProjectStatus 호출 성공

직접 런타임도 확인할 수 있습니다.

npm run start:mcp

다음 메시지가 보이고 프로세스가 계속 대기하면 정상입니다.

DocPilot MCP server started.

종료는 Ctrl+C입니다.

현재 상태는 다음과 같습니다.

TypeScript typecheck     PASS
JavaScript emit          PASS
dist/index.js 생성       PASS
배포형 MCP 진입점        READY

이제 Codex 또는 다른 MCP 클라이언트에는 tsx src/index.ts 대신 다음 실행 구성을 사용하면 됩니다.

command: node
args:
  C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> npm test

> docpilot-mcp@0.12.1 test
> vitest run


 RUN  v4.1.10 C:/WorkSpace/docpilot-core-integration/tools/docpilot-mcp

 ✓ tests/orchestration/ImplementationOrchestrationService.test.ts (10 tests) 10984ms
     ✓ creates a deterministic persisted Work Order and restores it after restart  1926ms
     ✓ rejects missing plans, unsafe scope, dirty trees, and baseline mismatch  1813ms
     ✓ dry-run is deterministic and side-effect free  1131ms
     ✓ runs a fake Worker, verifies evidence, and creates a Pending Handoff without lifecycle advance  1784ms
     ✓ releases the repository lock after Worker failure and timeout  1776ms
     ✓ releases the repository lock after cancellation  925ms
     ✓ diagnoses persisted RUNNING state without writing or automatic retry  553ms
     ✓ reports a live lock for persisted RUNNING state and rejects orphan records  542ms
     ✓ reports orphan Worker result files without changing state  532ms
 ✓ tests/orchestration/ControlledProcessRunner.test.ts (7 tests) 4065ms
     ✓ times out and rejects working-directory escape  1213ms
     ✓ terminates a spawned process tree on timeout  2395ms
 ✓ tests/orchestration/GitRepositoryController.test.ts (3 tests) 1685ms
     ✓ preserves exact staged, unstaged, untracked, deleted, and renamed evidence  459ms
     ✓ blocks pre-existing staged content  507ms
     ✓ restores its own staged candidate when commit fails  717ms
 ✓ tests/orchestration/RepositoryExecutionLock.test.ts (6 tests) 626ms
     ✓ enforces exclusion across real Node processes  550ms
 ✓ tests/service/ProjectStatusService.test.ts (29 tests) 185ms
 ✓ tests/server/ServerRegistration.test.ts (2 tests) 103ms
 ✓ tests/tool/MainPlanningLifecycleGuidance.test.ts (6 tests) 100ms
 ✓ tests/service/ProjectControlContext.test.ts (18 tests) 100ms
 ✓ tests/service/RfcContextHandoff.test.ts (13 tests) 83ms
 ✓ tests/service/PlanningSynchronizationStatus.test.ts (12 tests) 84ms
 ✓ tests/repository/ProjectStateRepository.test.ts (14 tests) 69ms
 ✓ tests/tool/RfcContextHandoffTools.test.ts (4 tests) 76ms
 ✓ tests/service/RfcRollback.test.ts (9 tests) 63ms
 ✓ tests/tool/UpdateReleaseReadinessTool.test.ts (5 tests) 60ms
 ✓ tests/resource/ProjectDashboardResource.test.ts (5 tests) 58ms
 ✓ tests/tool/StartNextRfcTool.test.ts (4 tests) 55ms
 ✓ tests/tool/MarkCurrentRfcCompletedTool.test.ts (4 tests) 53ms
 ✓ tests/tool/RollbackCurrentRfcTool.test.ts (4 tests) 55ms
 ✓ tests/service/RfcRollbackPreview.test.ts (7 tests) 44ms
 ✓ tests/repository/ImplementationOrchestrationPersistence.test.ts (7 tests) 38ms
 ✓ tests/tool/ProjectControlTools.test.ts (3 tests) 45ms
 ✓ tests/tool/PreviewCurrentRfcRollbackTool.test.ts (3 tests) 39ms
 ✓ tests/tool/GetPlanningSynchronizationStatusTool.test.ts (3 tests) 38ms

 Test Files  23 passed (23)
      Tests  178 passed (178)
   Start at  12:02:16
   Duration  28.60s (transform 1.31s, setup 0ms, import 6.07s, tests 18.71s, environment 1ms)

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $testExitCode = $LASTEXITCODE
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "Test exit code: $testExitCode"
Test exit code: 0
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

검증이 정상 완료되었습니다.

Build                ✅
TypeScript typecheck ✅
JavaScript emit      ✅
dist/index.js        ✅
Test files           23 / 23 PASS
Tests                178 / 178 PASS
Test exit code       0

현재 docpilot-mcp v0.12.1은 배포형 MCP 서버로 실행 가능한 상태입니다.

다음으로 Inspector를 실행하세요.

npm run inspector

Inspector에서 확인할 항목:

서버 연결 성공
서버명 docpilot-project-control
버전 0.12.1
Tools 목록 표시
Resources 목록 표시
Prompts 목록 표시
getProjectStatus 같은 조회 도구 호출 성공

Inspector 검증까지 통과하면 실제 Codex MCP 설정은 다음 실행 경로를 사용하면 됩니다.

command: node
args:
  C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js

현재 Release Readiness 관점에서는 다음과 같이 볼 수 있습니다.

Core Build         ✅
Core Tests         ✅
MCP Runtime Build  ✅
MCP Unit Tests     ✅
MCP Inspector      ⏳
Codex Integration  ⏳
Failed to load DocPilot project status: ENOENT: no such file or directory, open 'C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\project-state.json'

원인은 명확합니다.

현재 MCP 서버가 실행된 작업 디렉터리에서 다음 파일을 찾고 있습니다.

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\project-state.json

하지만 project-state.json은 보통 프로젝트 루트에 있어야 하므로, MCP 서버가 잘못된 working directory에서 실행된 상태입니다.

현재 실행 위치:

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp

원하는 기준 위치:

C:\WorkSpace\docpilot-core-integration
Inspector 실행 수정

package.json의 Inspector 명령을 프로젝트 루트를 작업 디렉터리로 지정하도록 바꾸는 방법이 가장 간단합니다.

먼저 프로젝트 상태 파일 위치를 확인하세요.

Get-ChildItem C:\WorkSpace\docpilot-core-integration `
    -Filter project-state.json `
    -Recurse `
    -File |
    Select-Object FullName

파일이 다음 위치에 있다고 가정합니다.

C:\WorkSpace\docpilot-core-integration\project-state.json

그렇다면 MCP 폴더에서 다음처럼 실행하세요.

Push-Location C:\WorkSpace\docpilot-core-integration

npx -y @modelcontextprotocol/inspector `
    node .\tools\docpilot-mcp\dist\index.js

Pop-Location

이렇게 실행하면 process.cwd()가 다음으로 설정됩니다.

C:\WorkSpace\docpilot-core-integration

따라서 MCP 서버가 다음 파일을 읽게 됩니다.

C:\WorkSpace\docpilot-core-integration\project-state.json
package.json 스크립트 수정

현재 inspector는 MCP 하위 폴더를 기준으로 실행되므로, 다음처럼 프로젝트 루트로 이동한 뒤 실행하도록 수정할 수 있습니다.

$pkg = Get-Content .\package.json -Raw | ConvertFrom-Json

$pkg.scripts |
    Add-Member `
        -NotePropertyName "inspector" `
        -NotePropertyValue 'cd ../.. && npx -y @modelcontextprotocol/inspector node tools/docpilot-mcp/dist/index.js' `
        -Force

$pkg.scripts |
    Add-Member `
        -NotePropertyName "start:mcp" `
        -NotePropertyValue 'cd ../.. && node tools/docpilot-mcp/dist/index.js' `
        -Force

$pkg |
    ConvertTo-Json -Depth 100 |
    Set-Content .\package.json -Encoding utf8

tools\docpilot-mcp에서 ../..는 다음 위치입니다.

C:\WorkSpace\docpilot-core-integration

수정 결과 확인:

$pkg = Get-Content .\package.json -Raw | ConvertFrom-Json

Write-Host "inspector : $($pkg.scripts.inspector)"
Write-Host "start:mcp : $($pkg.scripts.'start:mcp')"

예상 결과:

inspector : cd ../.. && npx -y @modelcontextprotocol/inspector node tools/docpilot-mcp/dist/index.js
start:mcp : cd ../.. && node tools/docpilot-mcp/dist/index.js

그다음 실행합니다.

npm run inspector
Codex MCP 설정

Codex에서도 command와 args만 지정하면 기본 작업 디렉터리가 달라질 수 있습니다. 따라서 가능하면 cwd를 명시해야 합니다.

{
  "command": "node",
  "args": [
    "C:\\WorkSpace\\docpilot-core-integration\\tools\\docpilot-mcp\\dist\\index.js"
  ],
  "cwd": "C:\\WorkSpace\\docpilot-core-integration"
}

사용 중인 MCP 설정 형식에서 cwd를 지원하지 않는다면, PowerShell 래퍼를 사용할 수 있습니다.

{
  "command": "powershell.exe",
  "args": [
    "-NoProfile",
    "-Command",
    "Set-Location 'C:\\WorkSpace\\docpilot-core-integration'; node '.\\tools\\docpilot-mcp\\dist\\index.js'"
  ]
}
확인해야 할 최종 상태
Test-Path C:\WorkSpace\docpilot-core-integration\project-state.json

결과가 True여야 합니다.

그다음 프로젝트 루트에서 직접 실행해 확인합니다.

cd C:\WorkSpace\docpilot-core-integration
node .\tools\docpilot-mcp\dist\index.js

DocPilot MCP server started.가 표시되고 ENOENT 오류가 발생하지 않으면 정상입니다.

핵심은 다음 한 줄입니다.

MCP 실행 파일의 위치가 아니라, project-state.json이 있는 프로젝트 루트를 working directory로 사용해야 합니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-ChildItem C:\WorkSpace\docpilot-core-integration 
>>     -Filter project-state.json 
>>     -Recurse 
>>     -File |
>>     Select-Object FullName
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

검색 결과가 비어 있으므로 project-state.json은 프로젝트 어디에도 존재하지 않습니다. 앞서 작업 디렉터리 문제라고 판단한 것은 성급했습니다. 현재 핵심 문제는 상태 파일 자체가 없다는 것입니다.

빈 {} 파일을 임의로 만들면 스키마 오류가 생길 수 있으므로, 먼저 저장소가 요구하는 위치와 초기 구조를 확인해야 합니다.

다음 명령을 실행하세요.

Write-Host "`n--- Repository implementation ---"
Get-Content .\src\repository\ProjectStateRepository.ts

Write-Host "`n--- project-state references ---"
Get-ChildItem . -Recurse -File |
    Where-Object {
        $_.FullName -notmatch "\\node_modules\\|\\dist\\"
    } |
    Select-String -Pattern `
        "project-state\.json|ProjectStateRepository|PROJECT_STATE|statePath" |
    Format-List Path, LineNumber, Line

테스트에서 초기 상태 생성 방법도 찾습니다.

Write-Host "`n--- State creation in tests ---"

Get-ChildItem .\tests -Recurse -File -Filter *.ts |
    Select-String -Pattern `
        "project-state\.json|writeFile|writeFileSync|ProjectStateRepository|currentRfc|releaseReadiness" |
    Format-List Path, LineNumber, Line

프로젝트에 상태 파일 샘플이나 다른 이름의 상태 파일이 있는지도 확인하세요.

Get-ChildItem C:\WorkSpace\docpilot-core-integration -Recurse -File |
    Where-Object {
        $_.Name -match "state|status|planning|dashboard" -and
        $_.Extension -in ".json", ".yaml", ".yml", ".md"
    } |
    Select-Object FullName |
    Format-Table -AutoSize

현재 판정은 다음과 같습니다.

MCP 서버 실행             ✅
빌드 결과물                ✅
178개 테스트               ✅
project-state.json         ❌ 존재하지 않음
Project Status 도구 호출   ❌ 초기 상태 부재

ProjectStateRepository.ts와 관련 테스트 출력으로 정확한 초기 JSON 스키마를 확인한 뒤 상태 파일을 생성해야 합니다. 지금 단계에서는 빈 파일이나 임의 필드를 넣은 파일을 만들지 않는 것이 안전합니다.

붙여넣은 텍스트 (1)(4).txt
문서
54s 동안 처리함

원인이 확정되었습니다.

ProjectStateRepository는 process.cwd()\project-state.json을 읽기만 하며 자동 생성하지 않습니다. 필수 필드는 project, phase, currentRfc, release, completedRfcs이고, releaseReadiness와 lifecycleHistory는 없으면 기본값으로 처리됩니다. 현재 Inspector의 작업 디렉터리가 docpilot-mcp이므로 여기에 파일을 생성해야 합니다.

현재 위치에서 아래 블록을 한 번에 실행하세요.

$state = [ordered]@{
    project       = "DocPilot"
    phase         = "Phase 1 — MVP / POC"
    currentRfc    = "RFC-0044"
    release       = "v0.5 MVP"
    completedRfcs = @(
        1..43 | ForEach-Object {
            "RFC-{0:D4}" -f $_
        }
    )
}

$statePath = Join-Path $PWD "project-state.json"
$json = $state | ConvertTo-Json -Depth 10

# UTF-8 BOM 없이 저장합니다.
# ProjectStateRepository가 JSON.parse()를 직접 사용하므로 이 방식이 안전합니다.
[System.IO.File]::WriteAllText(
    $statePath,
    $json + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "Created: $statePath"
Get-Content $statePath

생성 위치는 다음입니다.

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\project-state.json
JSON 유효성 확인
node -e "const fs=require('node:fs'); const s=JSON.parse(fs.readFileSync('project-state.json','utf8')); console.error('Valid project state:', s.currentRfc);"

정상 결과:

Valid project state: RFC-0044

파일 존재 여부도 확인합니다.

Test-Path .\project-state.json

결과:

True
Inspector 재시작

기존 Inspector가 실행 중이면 Ctrl+C로 종료한 다음 다시 실행하세요.

npm run inspector

이후 getProjectStatus를 호출하면 최소한 다음 상태가 로드되어야 합니다.

Project:       DocPilot
Phase:         Phase 1 — MVP / POC
Current RFC:   RFC-0044
Release:       v0.5 MVP
Completed RFC: RFC-0001 ~ RFC-0043

releaseReadiness를 파일에서 생략했으므로 저장소가 8개 항목을 모두 pending으로 기본 처리합니다. Inspector 연결이 성공한 후 updateReleaseReadiness 도구로 실제 상태를 반영하면 됩니다.

또한 이 파일은 런타임 파일이며 .gitignore에도 등록되어 있으므로 Git에 커밋하지 않는 것이 맞습니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $statePath = Join-Path $PWD "project-state.json"
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $json = $state | ConvertTo-Json -Depth 10
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> # UTF-8 BOM 없이 저장합니다.
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> # ProjectStateRepository가 JSON.parse()를 직접 사용하므로 이 방식이 안전합니다.
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> [System.IO.File]::WriteAllText(
>>     $statePath,
>>     $json + [Environment]::NewLine,
>>     [System.Text.UTF8Encoding]::new($false)
>> )
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Write-Host "Created: $statePath"
Created: C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\project-state.json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-Content $statePath
{
    "project":  "DocPilot",
    "phase":  "Phase 1 ??MVP / POC",
    "currentRfc":  "RFC-0044",
    "release":  "v0.5 MVP",
    "completedRfcs":  [
                          "RFC-0001",
                          "RFC-0002",
                          "RFC-0003",
                          "RFC-0004",
                          "RFC-0005",
                          "RFC-0006",
                          "RFC-0007",
                          "RFC-0008",
                          "RFC-0009",
                          "RFC-0010",
                          "RFC-0011",
                          "RFC-0012",
                          "RFC-0013",
                          "RFC-0014",
                          "RFC-0015",
                          "RFC-0016",
                          "RFC-0017",
                          "RFC-0018",
                          "RFC-0019",
                          "RFC-0020",
                          "RFC-0021",
                          "RFC-0022",
                          "RFC-0023",
                          "RFC-0024",
                          "RFC-0025",
                          "RFC-0026",
                          "RFC-0027",
                          "RFC-0028",
                          "RFC-0029",
                          "RFC-0030",
                          "RFC-0031",
                          "RFC-0032",
                          "RFC-0033",
                          "RFC-0034",
                          "RFC-0035",
                          "RFC-0036",
                          "RFC-0037",
                          "RFC-0038",
                          "RFC-0039",
                          "RFC-0040",
                          "RFC-0041",
                          "RFC-0042",
                          "RFC-0043"
                      ]
}
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

project-state.json은 정상 생성됐지만, phase의 대시 문자(—)가 PowerShell 입력 과정에서 ??로 손상되었습니다.

기능상 문자열이므로 로드는 되지만, 상태 표시가 깨지므로 ASCII 하이픈으로 수정하는 것이 안전합니다.

$state = Get-Content .\project-state.json -Raw | ConvertFrom-Json
$state.phase = "Phase 1 - MVP / POC"

$json = $state | ConvertTo-Json -Depth 100

[System.IO.File]::WriteAllText(
    (Join-Path $PWD "project-state.json"),
    $json + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)

확인:

Get-Content .\project-state.json

정상 값:

"phase": "Phase 1 - MVP / POC"

Node.js에서도 JSON과 주요 필드를 검증하세요.

node -e "const fs=require('node:fs'); const s=JSON.parse(fs.readFileSync('project-state.json','utf8')); console.error({project:s.project,phase:s.phase,currentRfc:s.currentRfc,completed:s.completedRfcs.length});"

예상 결과:

{
  project: 'DocPilot',
  phase: 'Phase 1 - MVP / POC',
  currentRfc: 'RFC-0044',
  completed: 43
}

그다음 Inspector를 재시작합니다.

npm run inspector

현재 상태 파일의 나머지 항목은 정상입니다.

project          ✅ DocPilot
currentRfc       ✅ RFC-0044
release          ✅ v0.5 MVP
completedRfcs    ✅ RFC-0001 ~ RFC-0043
phase            ⚠ 문자만 수정 필요
s\docpilot-mcp> $state = Get-Content .\project-state.json -Raw | ConvertFrom-Json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $state.phase = "Phase 1 - MVP / POC"
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $json = $state | ConvertTo-Json -Depth 100
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> [System.IO.File]::WriteAllText(
>>     (Join-Path $PWD "project-state.json"),
>>     $json + [Environment]::NewLine,
>>     [System.Text.UTF8Encoding]::new($false)
>> )
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

수정이 정상적으로 저장되었습니다. 이제 값과 JSON 형식만 확인하면 됩니다.

Get-Content .\project-state.json

또는 핵심 값만 확인:

node -e "const fs=require('node:fs'); const s=JSON.parse(fs.readFileSync('project-state.json','utf8')); console.log(s.phase, s.currentRfc, s.completedRfcs.length);"

예상 출력:

Phase 1 - MVP / POC RFC-0044 43

그다음 Inspector를 다시 실행하세요.

npm run inspector

이제 getProjectStatus 호출 시 ENOENT 오류 없이 프로젝트 상태가 표시되어야 합니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> node -e "const fs=require('node:fs'); const s=JSON.parse(fs.readFileSync('project-state.json','utf8')); console.log(s.phase, s.currentRfc, s.completedRfcs.length);"
Phase 1 - MVP / POC RFC-0044 43
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-Content .\project-state.json
{
    "project":  "DocPilot",
    "phase":  "Phase 1 - MVP / POC",
    "currentRfc":  "RFC-0044",
    "release":  "v0.5 MVP",
    "completedRfcs":  [
                          "RFC-0001",
                          "RFC-0002",
                          "RFC-0003",
                          "RFC-0004",
                          "RFC-0005",
                          "RFC-0006",
                          "RFC-0007",
                          "RFC-0008",
                          "RFC-0009",
                          "RFC-0010",
                          "RFC-0011",
                          "RFC-0012",
                          "RFC-0013",
                          "RFC-0014",
                          "RFC-0015",
                          "RFC-0016",
                          "RFC-0017",
                          "RFC-0018",
                          "RFC-0019",
                          "RFC-0020",
                          "RFC-0021",
                          "RFC-0022",
                          "RFC-0023",
                          "RFC-0024",
                          "RFC-0025",
                          "RFC-0026",
                          "RFC-0027",
                          "RFC-0028",
                          "RFC-0029",
                          "RFC-0030",
                          "RFC-0031",
                          "RFC-0032",
                          "RFC-0033",
                          "RFC-0034",
                          "RFC-0035",
                          "RFC-0036",
                          "RFC-0037",
                          "RFC-0038",
                          "RFC-0039",
                          "RFC-0040",
                          "RFC-0041",
                          "RFC-0042",
                          "RFC-0043"
                      ]
}

아래 순서로 확인하면 됩니다.

1. Inspector 실행

현재 위치에서:

npm run inspector

정상이라면 터미널에 Inspector 주소가 표시되고 브라우저가 열립니다. 브라우저가 자동으로 열리지 않으면 터미널에 표시된 http://localhost:... 주소를 직접 여세요.

2. 서버 연결

Inspector 화면에서 연결 정보가 다음처럼 되어 있는지 확인합니다.

Transport Type: STDIO
Command: node
Arguments: dist/index.js
Working Directory:
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp

npm run inspector로 실행했다면 대부분 자동으로 입력되어 있습니다.

그다음 Connect 버튼을 누릅니다.

연결 성공 시 서버 정보에 다음 값이 보여야 합니다.

Name: docpilot-project-control
Version: 0.12.1
3. Tools 목록 열기

왼쪽 또는 상단 메뉴에서 Tools를 선택합니다.

도구 목록에서 다음 이름을 찾습니다.

getProjectStatus

실제 등록 이름이 snake case로 표시되는 경우에는 다음과 비슷할 수 있습니다.

get_project_status

목록에 getCurrentRfc, updateProjectStatus, startNextRfc 등도 같이 보이면 서버 등록은 정상입니다.

4. getProjectStatus 실행

getProjectStatus를 선택한 뒤 Run Tool 또는 Execute 버튼을 누릅니다.

이 도구는 보통 입력 인자가 없으므로 입력란은 비워 둡니다.

{}

입력 JSON을 요구하는 경우 {}를 넣고 실행하세요.

5. 결과 확인

정상이라면 응답에서 다음 상태를 확인할 수 있습니다.

project: DocPilot
phase: Phase 1 - MVP / POC
currentRfc: RFC-0044
release: v0.5 MVP
completedRfcs: RFC-0001 ~ RFC-0043

releaseReadiness가 표시되면 초기 상태는 모두 pending일 가능성이 큽니다.

coreBuild: pending
coreTests: pending
cli: pending
incremental: pending
reviewWorkflow: pending
architectureSamplesValidation: pending
documentationSync: pending
releaseCandidate: pending
오류별 확인
ENOENT project-state.json

Inspector를 실행한 PowerShell 위치가 다음인지 확인합니다.

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp

그리고 파일이 존재해야 합니다.

Test-Path .\project-state.json

결과:

True
서버 연결 실패

빌드 파일을 확인합니다.

Test-Path .\dist\index.js

결과:

True

직접 실행도 확인할 수 있습니다.

node .\dist\index.js

다음 메시지가 나오고 대기하면 정상입니다.

DocPilot MCP server started.

종료는 Ctrl+C입니다.

Tools 목록이 비어 있음

서버 연결을 끊었다가 다시 연결하거나 Inspector를 완전히 종료한 후 다시 실행합니다.

npm run inspector

최종 성공 기준은 다음과 같습니다.

Inspector Connect       ✅
Server name/version     ✅
getProjectStatus 표시   ✅
Tool 실행 성공          ✅
RFC-0044 상태 조회      ✅
> docpilot-mcp@0.12.1 inspector
> npx -y @modelcontextprotocol/inspector node dist/index.js

Starting MCP inspector...
⚙️ Proxy server listening on localhost:6277
🔑 Session token: b5b81854c42c1cda1bf4eaffa6fd7ad2ffbf8ad51f1e52072b9938686e40b253
   Use this token to authenticate requests or set DANGEROUSLY_OMIT_AUTH=true to disable auth

🚀 MCP Inspector is up and running at:
   http://localhost:6274/?MCP_PROXY_AUTH_TOKEN=b5b81854c42c1cda1bf4eaffa6fd7ad2ffbf8ad51f1e52072b9938686e40b253

🌐 Opening browser...

정상적으로 MCP Inspector가 실행되었습니다. 브라우저가 열린 뒤 다음 순서로 getProjectStatus를 확인하세요. MCP Inspector는 서버가 제공하는 도구 목록을 조회하고 개별 도구를 직접 호출할 수 있습니다.

1. 서버 연결

브라우저의 Inspector 화면에서 연결 상태를 확인합니다.

자동 연결되지 않았다면:

Transport Type: STDIO
Command: node
Arguments: dist/index.js
Connect 클릭

연결에 성공하면 서버 정보가 표시됩니다.

Name: docpilot-project-control
Version: 0.12.1
2. Tools 메뉴 선택

Inspector 화면에서 Tools 탭을 누릅니다.

필요한 경우 List Tools 버튼을 누르세요. 도구 목록에서 다음 항목을 찾습니다.

getProjectStatus
3. 도구 실행

getProjectStatus를 선택합니다.

이 도구는 입력값이 없으므로 Arguments 입력란에는 다음을 넣습니다.

{}

그다음 Run Tool을 클릭합니다.

4. 정상 결과 확인

응답에 다음 상태가 포함되어야 합니다.

project: DocPilot
phase: Phase 1 - MVP / POC
currentRfc: RFC-0044
release: v0.5 MVP
completedRfcs: RFC-0001 ~ RFC-0043

releaseReadiness도 반환된다면 현재 초기값은 다음과 같이 나올 수 있습니다.

coreBuild: pending
coreTests: pending
cli: pending
incremental: pending
reviewWorkflow: pending
architectureSamplesValidation: pending
documentationSync: pending
releaseCandidate: pending
오류가 다시 발생할 경우

Inspector를 실행한 PowerShell 창에서 다음 두 파일이 모두 존재하는지 확인합니다.

Test-Path .\dist\index.js
Test-Path .\project-state.json

둘 다 다음처럼 나와야 합니다.

True
True

현재 Inspector는 다음 폴더를 작업 디렉터리로 사용하므로 상태 파일 위치도 정확합니다.

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\project-state.json

참고로 출력에 보인 세션 토큰은 현재 Inspector 프록시 접속용입니다. 외부 문서나 공개 저장소에는 기록하지 않는 것이 좋습니다.

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>npm run inspector

> docpilot-mcp@0.12.1 inspector
> npx -y @modelcontextprotocol/inspector node dist/index.js

Starting MCP inspector...
⚙️ Proxy server listening on localhost:6277
🔑 Session token: 4bf615d5d2b3c0da7fff992526c5404b0f0e8332b4ddf3b02b19c9b87b19a5d4
   Use this token to authenticate requests or set DANGEROUSLY_OMIT_AUTH=true to disable auth

🚀 MCP Inspector is up and running at:
   http://localhost:6274/?MCP_PROXY_AUTH_TOKEN=4bf615d5d2b3c0da7fff992526c5404b0f0e8332b4ddf3b02b19c9b87b19a5d4

🌐 Opening browser...
New STDIO connection request
Query parameters: {"command":"node","args":"dist/index.js","env":"{\"APPDATA\":\"C:\\\\Users\\\\nk782\\\\AppData\\\\Roaming\",\"HOMEDRIVE\":\"C:\",\"HOMEPATH\":\"\\\\Users\\\\nk782\",\"LOCALAPPDATA\":\"C:\\\\Users\\\\nk782\\\\AppData\\\\Local\",\"PATH\":\"C:\\\\Users\\\\nk782\\\\AppData\\\\Local\\\\npm-cache\\\\_npx\\\\5a9d879542beca3a\\\\node_modules\\\\.bin;C:\\\\WorkSpace\\\\docpilot-core-integration\\\\tools\\\\docpilot-mcp\\\\node_modules\\\\.bin;C:\\\\WorkSpace\\\\docpilot-core-integration\\\\tools\\\\node_modules\\\\.bin;C:\\\\WorkSpace\\\\docpilot-core-integration\\\\node_modules\\\\.bin;C:\\\\WorkSpace\\\\node_modules\\\\.bin;C:\\\\node_modules\\\\.bin;C:\\\\Program Files\\\\nodejs\\\\node_modules\\\\npm\\\\node_modules\\\\@npmcli\\\\run-script\\\\lib\\\\node-gyp-bin;C:\\\\WorkSpace\\\\docpilot-core-integration\\\\tools\\\\docpilot-mcp\\\\node_modules\\\\.bin;C:\\\\WorkSpace\\\\docpilot-core-integration\\\\tools\\\\node_modules\\\\.bin;C:\\\\WorkSpace\\\\docpilot-core-integration\\\\node_modules\\\\.bin;C:\\\\WorkSpace\\\\node_modules\\\\.bin;C:\\\\node_modules\\\\.bin;C:\\\\Program Files\\\\nodejs\\\\node_modules\\\\npm\\\\node_modules\\\\@npmcli\\\\run-script\\\\lib\\\\node-gyp-bin;C:\\\\Python314\\\\Scripts\\\\;C:\\\\Python314\\\\;C:\\\\Windows\\\\system32;C:\\\\Windows;C:\\\\Windows\\\\System32\\\\Wbem;C:\\\\Windows\\\\System32\\\\WindowsPowerShell\\\\v1.0\\\\;C:\\\\Windows\\\\System32\\\\OpenSSH\\\\;C:\\\\Program Files\\\\dotnet\\\\;C:\\\\Program Files\\\\Git\\\\cmd;C:\\\\Program Files\\\\Android\\\\Android Studio\\\\jbr\\\\bin;C:\\\\Program Files\\\\nodejs\\\\;C:\\\\ProgramData\\\\chocolatey\\\\bin;C:\\\\Users\\\\nk782\\\\AppData\\\\Local\\\\Microsoft\\\\WindowsApps;C:\\\\Users\\\\nk782\\\\AppData\\\\Local\\\\Programs\\\\Microsoft VS Code\\\\bin;C:\\\\Users\\\\nk782\\\\AppData\\\\Local\\\\Python\\\\bin;C:\\\\Users\\\\nk782\\\\AppData\\\\Local\\\\GitHubDesktop\\\\bin;C:\\\\Users\\\\nk782\\\\AppData\\\\Local\\\\Programs\\\\Ollama;C:\\\\Users\\\\nk782\\\\AppData\\\\Roaming\\\\npm\",\"PROCESSOR_ARCHITECTURE\":\"AMD64\",\"SYSTEMDRIVE\":\"C:\",\"SYSTEMROOT\":\"C:\\\\Windows\",\"TEMP\":\"C:\\\\Users\\\\nk782\\\\AppData\\\\Local\\\\Temp\",\"USERNAME\":\"nk782\",\"USERPROFILE\":\"C:\\\\Users\\\\nk782\",\"PROGRAMFILES\":\"C:\\\\Program Files\"}","transportType":"stdio"}
STDIO transport: command=C:\Program Files\nodejs\node.exe, args=dist/index.js
Created client transport
Created server transport
Received POST message for sessionId b38ae10f-c850-4b28-960f-78bc72f3f1b4
Received POST message for sessionId b38ae10f-c850-4b28-960f-78bc72f3f1b4

로그상 MCP Inspector와 DocPilot MCP 서버 연결은 정상적으로 완료된 상태입니다.

확인된 내용:

STDIO transport 생성       ✅
node dist/index.js 실행     ✅
클라이언트 transport 생성   ✅
서버 transport 생성         ✅
Inspector 요청 수신         ✅

특히 아래 로그가 연결 성공을 의미합니다.

Created client transport
Created server transport
Received POST message for sessionId ...

이제 브라우저 Inspector 화면에서 다음 순서로 확인하세요.

getProjectStatus 실행 절차
왼쪽 메뉴에서 Tools 선택
List Tools 버튼 클릭
도구 목록에서 getProjectStatus 선택
Arguments가 필요하면 아래 입력
{}
Run Tool 클릭

정상 응답에는 다음 값이 포함되어야 합니다.

project: DocPilot
phase: Phase 1 - MVP / POC
currentRfc: RFC-0044
release: v0.5 MVP
completedRfcs: 43개

releaseReadiness가 표시되면 현재 상태 파일에 해당 필드가 없으므로 각 항목이 기본값인 pending으로 보이는 것이 정상입니다.

Inspector를 사용하는 동안 현재 PowerShell 창은 닫지 마세요. 종료할 때는 터미널에서 Ctrl+C를 누르면 됩니다.

또한 출력된 세션 토큰은 Inspector 접속 인증값이므로 공개 저장소나 외부 게시물에는 남기지 않는 것이 좋습니다.

{
  "project": "DocPilot",
  "phase": "Phase 1 - MVP / POC",
  "currentRfc": "RFC-0044",
  "release": "v0.5 MVP",
  "completedRfcs": [
    "RFC-0001",
    "RFC-0002",
    "RFC-0003",
    "RFC-0004",
    "RFC-0005",
    "RFC-0006",
    "RFC-0007",
    "RFC-0008",
    "RFC-0009",
    "RFC-0010",
    "RFC-0011",
    "RFC-0012",
    "RFC-0013",
    "RFC-0014",
    "RFC-0015",
    "RFC-0016",
    "RFC-0017",
    "RFC-0018",
    "RFC-0019",
    "RFC-0020",
    "RFC-0021",
    "RFC-0022",
    "RFC-0023",
    "RFC-0024",
    "RFC-0025",
    "RFC-0026",
    "RFC-0027",
    "RFC-0028",
    "RFC-0029",
    "RFC-0030",
    "RFC-0031",
    "RFC-0032",
    "RFC-0033",
    "RFC-0034",
    "RFC-0035",
    "RFC-0036",
    "RFC-0037",
    "RFC-0038",
    "RFC-0039",
    "RFC-0040",
    "RFC-0041",
    "RFC-0042",
    "RFC-0043"
  ],
  "releaseReadiness": {
    "coreBuild": "pending",
    "coreTests": "pending",
    "cli": "pending",
    "incremental": "pending",
    "reviewWorkflow": "pending",
    "architectureSamplesValidation": "pending",
    "documentationSync": "pending",
    "releaseCandidate": "pending"
  },
  "lifecycleHistory": []
}

getProjectStatus가 정상 동작했습니다.

현재 확인된 상태:

MCP Inspector 연결       ✅
getProjectStatus 등록    ✅
project-state.json 로드  ✅
현재 RFC                 RFC-0044
완료 RFC                 43개
Release Readiness        8개 항목 정상 반환
Lifecycle History        정상 반환

releaseReadiness가 모두 pending인 것은 상태 파일에 해당 항목을 저장하지 않았기 때문에 저장소가 기본값을 적용한 결과입니다.

coreBuild                         pending
coreTests                         pending
cli                               pending
incremental                       pending
reviewWorkflow                    pending
architectureSamplesValidation     pending
documentationSync                 pending
releaseCandidate                  pending

lifecycleHistory: []도 정상입니다. 현재 생성한 초기 상태 파일에는 RFC 시작·완료·Planning Sync 이벤트가 아직 기록되지 않았기 때문입니다.

현재 단계 판정
배포용 JavaScript 빌드       PASS
dist/index.js 실행           PASS
MCP 테스트 178개             PASS
Inspector STDIO 연결         PASS
getProjectStatus 호출        PASS
런타임 상태 연동             PASS

이제 실제 Codex MCP 설정에는 다음 구성을 사용할 수 있습니다.

{
  "command": "node",
  "args": [
    "C:\\WorkSpace\\docpilot-core-integration\\tools\\docpilot-mcp\\dist\\index.js"
  ],
  "cwd": "C:\\WorkSpace\\docpilot-core-integration\\tools\\docpilot-mcp"
}

cwd가 반드시 project-state.json이 있는 다음 폴더여야 합니다.

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp

Inspector 종료는 실행 중인 터미널에서 Ctrl+C를 누르면 됩니다.

 C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp 디렉터리

2026-07-20  오후 12:10    <DIR>          .
2026-07-20  오전 12:59    <DIR>          ..
2026-07-20  오전 12:59               283 .gitignore
2026-07-20  오전 12:59               954 AGENTS.md
2026-07-20  오후 12:01    <DIR>          dist
2026-07-20  오전 12:59    <DIR>          docs
2026-07-20  오전 01:00    <DIR>          node_modules
2026-07-20  오전 12:59           114,387 package-lock.json
2026-07-20  오전 11:52             1,478 package.json
2026-07-20  오전 12:59               724 package.json.backup
2026-07-20  오후 12:11             1,860 project-state.json
2026-07-20  오전 12:59            25,269 README.md
2026-07-20  오전 12:59    <DIR>          src
2026-07-20  오전 12:59    <DIR>          tests
2026-07-20  오전 11:59               458 tsconfig.build.json
2026-07-20  오전 12:59               390 tsconfig.json
2026-07-20  오전 12:59               169 vitest.config.ts
              10개 파일             145,972 바이트
               7개 디렉터리  71,336,124,416 바이트 남음

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

현재 디렉터리 구성이 정상입니다.

docpilot-mcp
├─ dist/                    ✅ 배포용 JavaScript
├─ src/                     ✅ TypeScript 소스
├─ tests/                   ✅ 테스트
├─ node_modules/            ✅ 의존성
├─ package.json             ✅ dist 실행 설정 반영
├─ package-lock.json        ✅
├─ tsconfig.json            ✅ 타입 검사
├─ tsconfig.build.json      ✅ 배포 빌드
├─ project-state.json       ✅ MCP 런타임 상태
└─ vitest.config.ts         ✅

특히 실제 MCP 실행에 필요한 두 파일이 모두 있습니다.

C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\project-state.json

따라서 실제 클라이언트 설정에서는 다음 값이 중요합니다.

command: node
argument:
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js

working directory:
C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp
정리해도 되는 파일

package.json.backup은 작업 중 생성한 임시 백업입니다. 현재 빌드와 테스트가 모두 통과했으므로 필요 없다면 삭제해도 됩니다.

Remove-Item .\package.json.backup

다만 현재 변경 전 설정을 보관하고 싶다면 그대로 둬도 실행에는 영향을 주지 않습니다. Git에 포함할 필요는 없습니다.

최종 확인 상태
npm run build              ✅
dist/index.js              ✅
23 test files              ✅
178 tests                  ✅
Inspector 연결             ✅
getProjectStatus           ✅
project-state.json 로드    ✅

현재 docpilot-mcp v0.12.1은 Inspector 기준 실제 실행 가능한 MCP 서버 상태입니다. 다음 단계는 Codex MCP 설정에 이 서버를 등록하고 Codex에서 getProjectStatus를 직접 호출하는 것입니다.

다음으로 무엇을 해야할까요?
59s 동안 처리함

다음 단계는 Inspector가 아니라 실제 Codex에서 DocPilot MCP를 호출하는 종단 간 검증입니다.

1. Codex 프로젝트 설정 추가

Codex는 사용자 전역 ~/.codex/config.toml 또는 신뢰된 프로젝트의 .codex/config.toml에서 MCP 설정을 읽습니다. STDIO 서버는 command, args, cwd를 지원합니다.

프로젝트 루트로 이동합니다.

cd C:\WorkSpace\docpilot-core-integration

기존 설정 파일이 있는지 확인하세요.

Test-Path .\.codex\config.toml
결과가 False인 경우
New-Item .\.codex -ItemType Directory -Force | Out-Null

$config = @'
[mcp_servers.docpilot]
command = "C:\\Program Files\\nodejs\\node.exe"
args = ["C:\\WorkSpace\\docpilot-core-integration\\tools\\docpilot-mcp\\dist\\index.js"]
cwd = "C:\\WorkSpace\\docpilot-core-integration\\tools\\docpilot-mcp"
enabled = true
startup_timeout_sec = 20
tool_timeout_sec = 1800
default_tools_approval_mode = "prompt"
'@

[System.IO.File]::WriteAllText(
    (Join-Path $PWD ".codex\config.toml"),
    $config + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false)
)
결과가 True인 경우

기존 설정을 먼저 확인합니다.

Get-Content .\.codex\config.toml

[mcp_servers.docpilot]가 없다면 위 설정 블록만 기존 파일 마지막에 추가하세요. 기존 파일 전체를 덮어쓰면 안 됩니다.

2. 설정 확인
Get-Content .\.codex\config.toml
codex mcp list

Codex 공식 CLI에서는 codex mcp list로 등록된 MCP 서버를 확인할 수 있고, Codex 터미널 안에서는 /mcp로 활성 서버를 확인할 수 있습니다.

예상 서버:

docpilot
3. Codex 실행

프로젝트 루트에서 실행합니다.

cd C:\WorkSpace\docpilot-core-integration
codex

프로젝트 신뢰 여부를 묻는다면 이 저장소를 신뢰하도록 선택해야 프로젝트의 .codex/config.toml이 로드됩니다.

Codex 화면에서:

/mcp

docpilot 서버가 연결됨 또는 활성 상태로 보여야 합니다.

4. 실제 getProjectStatus 호출

Codex에 다음 문장을 입력합니다.

DocPilot MCP의 getProjectStatus 도구를 호출해서 현재 프로젝트 상태를 조회해 주세요.
파일을 직접 읽지 말고 반드시 MCP 도구를 사용하세요.
currentRfc와 completedRfcs 개수도 알려주세요.

정상 결과:

currentRfc: RFC-0044
completedRfcs: 43개

Codex 실행 로그나 UI에서 실제로 getProjectStatus 도구가 호출됐는지도 확인해야 합니다.

5. Query zero-write 검증

조회 도구가 상태 파일을 수정하지 않는지 확인합니다.

Codex 호출 전:

$beforeHash = (
    Get-FileHash `
        .\tools\docpilot-mcp\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "Before: $beforeHash"

Codex에서 다시 getProjectStatus를 호출한 후:

$afterHash = (
    Get-FileHash `
        .\tools\docpilot-mcp\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "After : $afterHash"
Write-Host "Unchanged: $($beforeHash -eq $afterHash)"

정상 결과:

Unchanged: True
6. 읽기 전용 MCP 흐름 추가 검증

Codex에서 차례대로 요청합니다.

DocPilot MCP의 getCurrentRfc를 호출해 주세요.
DocPilot MCP의 getDocPilotProjectControlContext를 호출해 주세요.
DocPilot MCP의 evaluateRfcCompletionReadiness를 호출해서
RFC-0044의 완료 준비도를 평가해 주세요.
상태를 변경하는 도구는 호출하지 마세요.

현재 초기 상태에는 Pending Handoff나 Work Order가 없으므로, 완료 준비도는 대개 NOT_READY 또는 이에 준하는 결과가 나오는 것이 정상입니다.

지금은 다음 쓰기 도구를 호출하지 마세요.

completeCurrentRfc
markCurrentRfcCompleted
updateProjectStatus
updateReleaseReadiness
startNextRfc
rollbackCurrentRfc
executePendingImplementationWorkOrder
createImplementationCommit
7. 실제 연동 성공 후 코드 정리

Codex 종단 간 검증까지 통과하면 이번 변경은 단순 로컬 조치가 아니라 배포형 MCP 런타임 지원 추가이므로 v0.12.2로 정리하는 것이 적절합니다.

커밋 대상:

package.json
tsconfig.build.json
README.md
src/server.ts의 서버 버전
필요하면 package-lock.json

커밋 제외:

project-state.json
dist/
node_modules/
package.json.backup

백업 파일은 삭제해도 됩니다.

Remove-Item .\tools\docpilot-mcp\package.json.backup

권장 커밋 메시지:

feat(mcp): add compiled runtime entrypoint for Codex integration

- add TypeScript emit configuration for dist output
- add production MCP start and Inspector scripts
- configure package main entrypoint as dist/index.js
- support stable Node.js STDIO execution
- verify build, 23 test files, and 178 tests
- validate getProjectStatus through MCP Inspector

바로 해야 할 한 가지는 .codex/config.toml에 docpilot 서버를 등록하고 Codex에서 getProjectStatus를 호출하는 것입니다.


C:\WorkSpace\docpilot-core-integration>Test-Path .\.codex\config.toml
'Test-Path'은(는) 내부 또는 외부 명령, 실행할 수 있는 프로그램, 또는
배치 파일이 아닙니다.

현재 창은 PowerShell이 아니라 명령 프롬프트(CMD)입니다.

프롬프트 차이:

PowerShell: PS C:\WorkSpace\...
CMD:        C:\WorkSpace\...>

Test-Path는 PowerShell 명령이라 CMD에서는 실행되지 않습니다.

CMD에서 확인
if exist .codex\config.toml (
  echo config.toml exists
) else (
  echo config.toml does not exist
)

또는 간단히:

dir .codex\config.toml
PowerShell로 전환

현재 CMD에서 다음을 실행하세요.

powershell

프롬프트가 아래처럼 바뀝니다.

PS C:\WorkSpace\docpilot-core-integration>

그다음 실행하세요.

Test-Path .\.codex\config.toml
True: 기존 파일이 있으므로 내용을 확인
False: .codex 폴더와 config.toml을 새로 생성

현재처럼 PowerShell 명령을 계속 사용할 예정이라면, 먼저 powershell을 입력해 PowerShell로 전환하는 것이 좋습니다.

C:\WorkSpace\docpilot-core-integration>powershell
Windows PowerShell
Copyright (C) Microsoft Corporation. All rights reserved.

Install the latest PowerShell for new features and improvements! https://aka.ms/PSWindows

PS C:\WorkSpace\docpilot-core-integration> Test-Path .\.codex\config.toml
False

False이므로 프로젝트 전용 Codex 설정 파일을 새로 생성하면 됩니다. 프로젝트 범위의 .codex/config.toml은 신뢰된 프로젝트에서만 로드되며, MCP STDIO 설정은 command, args, cwd를 지원합니다.

현재 PowerShell에서 아래 블록을 그대로 실행하세요.

New-Item .\.codex -ItemType Directory -Force | Out-Null

$configLines = @(
    "[mcp_servers.docpilot]"
    "command = 'C:\Program Files\nodejs\node.exe'"
    "args = ['C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js']"
    "cwd = 'C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp'"
    "enabled = true"
    "startup_timeout_sec = 20"
    "tool_timeout_sec = 1800"
    "default_tools_approval_mode = 'prompt'"
)

$configContent = ($configLines -join [Environment]::NewLine) +
    [Environment]::NewLine

[System.IO.File]::WriteAllText(
    (Join-Path $PWD ".codex\config.toml"),
    $configContent,
    [System.Text.UTF8Encoding]::new($false)
)

생성 결과를 확인합니다.

Test-Path .\.codex\config.toml
Get-Content .\.codex\config.toml

정상 결과:

[mcp_servers.docpilot]
command = 'C:\Program Files\nodejs\node.exe'
args = ['C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js']
cwd = 'C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp'
enabled = true
startup_timeout_sec = 20
tool_timeout_sec = 1800
default_tools_approval_mode = 'prompt'

그다음 프로젝트 루트에서 Codex를 실행합니다.

codex

처음 실행하면서 프로젝트 신뢰 여부를 묻는다면 신뢰하도록 승인하세요. 프로젝트가 신뢰되지 않으면 .codex/config.toml이 무시됩니다.

Codex가 열린 뒤 다음 명령을 입력합니다.

/mcp

docpilot 서버가 표시되는지 확인한 후 다음 요청을 입력하세요.

DocPilot MCP의 getProjectStatus 도구를 호출해서 현재 프로젝트 상태를 조회해 주세요.
project-state.json을 직접 읽지 말고 반드시 MCP 도구를 사용하세요.

정상 응답 핵심값은 다음입니다.

currentRfc: RFC-0044
completedRfcs: 43개

Codex CLI에서는 /mcp로 활성 MCP 서버를 확인할 수 있고, codex mcp list로 구성된 서버 목록도 확인할 수 있습니다.

>> )
PS C:\WorkSpace\docpilot-core-integration> Test-Path .\.codex\config.toml
True
PS C:\WorkSpace\docpilot-core-integration> Get-Content .\.codex\config.toml
[mcp_servers.docpilot]
command = 'C:\Program Files\nodejs\node.exe'
args = ['C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js']
cwd = 'C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp'
enabled = true
startup_timeout_sec = 20
tool_timeout_sec = 1800
default_tools_approval_mode = 'prompt'

설정 파일은 정상입니다. command, args, cwd, 타임아웃, 승인 모드 모두 현재 Codex MCP 설정에서 지원되는 항목입니다.

이제 같은 프로젝트 루트에서 다음을 실행하세요.

codex mcp list

목록에 docpilot이 표시되면 등록이 완료된 것입니다. Codex 공식 문서도 codex mcp list로 구성된 서버를 확인하도록 안내합니다.

그다음 Codex를 실행합니다.

codex

프로젝트 신뢰 여부를 묻는다면 신뢰하도록 선택하세요. 프로젝트의 .codex/config.toml은 신뢰된 프로젝트에서만 로드됩니다.

Codex 화면에서:

/mcp

docpilot이 활성 서버로 표시되는지 확인합니다.

마지막으로 다음 요청을 입력하세요.

DocPilot MCP의 getProjectStatus 도구를 호출해서 현재 프로젝트 상태를 조회해 주세요.
project-state.json을 직접 읽지 말고 반드시 MCP 도구를 사용하세요.
currentRfc와 completedRfcs 개수도 알려주세요.

정상 결과 기준:

currentRfc: RFC-0044
completedRfcs: 43개

우선 지금은 codex mcp list를 실행하면 됩니다.

오늘 오후 1:52
codex mcp list
Name                 Command                                                                                         Args                                                                     Env                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        Cwd                                                                Status   Auth
docpilot             C:\Program Files\nodejs\node.exe                                                                C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js  -                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp          enabled  Unsupported
node_repl            C:\Users\nk782\AppData\Local\OpenAI\Codex\runtimes\cua_node\ecfc0d9aa02807e3\bin\node_repl.exe  -                                                                        BROWSER_USE_AVAILABLE_BACKENDS=*****, BROWSER_USE_CODEX_APP_BUILD_FLAVOR=*****, BROWSER_USE_CODEX_APP_VERSION=*****, CODEX_CLI_PATH=*****, CODEX_HOME=*****, NODE_REPL_INSTRUCTIONS_USE_CASE_BROWSER=*****, NODE_REPL_INSTRUCTIONS_USE_CASE_CHROME=*****, NODE_REPL_NATIVE_PIPE_CONNECT_TIMEOUT_MS=*****, NODE_REPL_NODE_MODULE_DIRS=*****, NODE_REPL_NODE_PATH=*****, NODE_REPL_TRUSTED_BROWSER_CLIENT_SHA256S=*****, NODE_REPL_TRUSTED_CODE_PATHS=*****, SKY_CUA_NATIVE_PIPE=*****, SKY_CUA_NATIVE_PIPE_DIRECTORY=*****  -                                                                  enabled  Unsupported
sites-design-picker  node                                                                                            ./mcp/server.mjs                                                         -                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          C:\Users\nk782\.codex\plugins\cache\openai-bundled\sites\0.1.27\.  enabled  Unsupported

정상 등록되었습니다.

docpilot  enabled
Command   C:\Program Files\nodejs\node.exe
Args      ...\dist\index.js
Cwd       ...\tools\docpilot-mcp

Auth: Unsupported는 오류가 아닙니다. docpilot은 로컬 STDIO MCP 서버이므로 별도 OAuth 인증을 사용하지 않습니다. Codex 문서에서도 인증은 주로 Streamable HTTP 서버에 적용되고, STDIO 서버는 로컬 명령으로 시작한다고 설명합니다.

1. Codex 실행

현재 프로젝트 루트에서:

codex
2. MCP 연결 확인

Codex 화면에서 입력합니다.

/mcp

목록에 다음이 표시되어야 합니다.

docpilot

Codex TUI에서는 /mcp로 활성 MCP 서버를 확인할 수 있습니다.

3. 실제 도구 호출

아래 문장을 그대로 입력하세요.

DocPilot MCP의 getProjectStatus 도구를 호출해서 현재 프로젝트 상태를 조회해 주세요.

project-state.json을 직접 읽거나 셸 명령으로 확인하지 말고, 반드시 docpilot MCP의 getProjectStatus 도구를 사용하세요.

currentRfc와 completedRfcs 개수도 알려주세요.

승인 창이 나오면 허용합니다. 현재 설정이 다음과 같기 때문입니다.

default_tools_approval_mode = "prompt"

정상 결과 핵심:

currentRfc: RFC-0044
completedRfcs: 43개
4. 조회가 파일을 변경하지 않았는지 확인

Codex 호출 전후의 해시를 비교하면 됩니다.

호출 후 PowerShell에서:

Get-FileHash `
  .\tools\docpilot-mcp\project-state.json `
  -Algorithm SHA256

더 정확하게 검증하려면 Codex 호출 전에:

$beforeHash = (
    Get-FileHash `
        .\tools\docpilot-mcp\project-state.json `
        -Algorithm SHA256
).Hash

호출 후:

$afterHash = (
    Get-FileHash `
        .\tools\docpilot-mcp\project-state.json `
        -Algorithm SHA256
).Hash

"Query zero-write: $($beforeHash -eq $afterHash)"

정상 결과:

Query zero-write: True

현재 상태는 Codex 등록 완료이며, 이제 codex 실행 → /mcp 확인 → getProjectStatus 호출만 남았습니다.

\Users\nk782> cd ..\..\WorkSpace\docpilot-core-integration\
PS C:\WorkSpace\docpilot-core-integration> codex
╭───────────────────────────────────────────────────╮
│ >_ OpenAI Codex (v0.144.6)                        │
│                                                   │
│ model:     gpt-5.6-sol   /model to change         │
│ directory: C:\WorkSpace\docpilot-core-integration │
╰───────────────────────────────────────────────────╯

  Tip: New Use /fast to enable our fastest inference with increased plan
  usage.

/mcp

🔌  MCP Tools

  • codex_apps
    • Auth: Bearer token
    • Tools: codex_document_control.execute_document_command,
    codex_document_control.get_document_tool_schemas,
    codex_document_control.list_document_sessions,
    hotline.get_local_hotline, plugin_management.get_app_permissions,
    plugin_management.get_plugin_dependencies,
    plugin_management.uninstall_app,
    plugin_management.update_app_permissions, sites.add_custom_domain,
    sites.create_site, sites.create_source_repository_write_credential,
    sites.deploy_private_site_version, sites.deploy_site_version,
    sites.generate_siwc_bypass_token, sites.get_deployment_status,
    sites.get_environment, sites.get_environment_variables,
    sites.get_project, sites.get_site,
    sites.get_site_analytics_overview, sites.get_site_version,
    sites.get_site_worker_logs, sites.list_custom_domains,
    sites.list_projects, sites.list_site_analytics_events,
    sites.list_site_versions, sites.list_sites,
    sites.query_site_analytics_event,
    sites.refresh_custom_domain_status, sites.remove_custom_domain,
    sites.save_site_version, sites.update_access,
    sites.update_environment, sites.update_environment_variables,
    sites.update_site_access, sites.update_site_metadata

  • docpilot
    • Auth: Unsupported
    • Tools: completeCurrentRfc, createImplementationCommit,
    evaluateRfcCompletionReadiness,
    executePendingImplementationWorkOrder, generateMainPlanningSync,
    getCurrentRfc, getDocPilotProjectControlContext,
    getPendingImplementationWorkOrder, getPendingRfcHandoff,
    getPlanningSynchronizationStatus, getProjectStatus,
    listCompletedRfcs, loadRfcContext, markCurrentRfcCompleted,
    prepareImplementationWorkOrder, previewCurrentRfcRollback,
    rollbackCurrentRfc, startNextRfc, submitRfcHandoff,
    updateProjectStatus, updateReleaseReadiness

  • node_repl
    • Auth: Unsupported
    • Tools: js, js_add_node_module_dir, js_reset

  • sites-design-picker
    • Auth: Unsupported
    • Tools: choose_site_design



› Find and fix a bug in @filename

  gpt-5.6-sol default · C:\WorkSpace\docpilot-core-integration

정상입니다. Codex가 DocPilot MCP를 실제로 로드했고, 21개 도구까지 모두 인식했습니다.

현재 상태:

Codex 실행                    ✅
프로젝트 디렉터리 인식         ✅
docpilot MCP 연결             ✅
getProjectStatus 도구 노출     ✅
구현 오케스트레이션 도구 노출   ✅

이제 Codex 입력창에 아래 요청을 그대로 입력하세요.

DocPilot MCP의 getProjectStatus 도구를 호출해서 현재 프로젝트 상태를 조회해 주세요.

project-state.json을 직접 읽거나 셸 명령으로 확인하지 말고,
반드시 docpilot MCP의 getProjectStatus 도구를 사용하세요.

currentRfc와 completedRfcs 개수도 알려주세요.
상태를 변경하는 다른 도구는 호출하지 마세요.

정상 결과의 핵심은 다음과 같아야 합니다.

currentRfc: RFC-0044
completedRfcs: 43개
조회 도구의 zero-write 검증

현재 Codex는 실행 중이므로 별도 PowerShell 창을 하나 열고 프로젝트 루트에서 호출 전 해시를 저장합니다.

cd C:\WorkSpace\docpilot-core-integration

$beforeHash = (
    Get-FileHash `
        .\tools\docpilot-mcp\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "Before: $beforeHash"

Codex에서 getProjectStatus 호출을 완료한 뒤 같은 PowerShell 창에서:

$afterHash = (
    Get-FileHash `
        .\tools\docpilot-mcp\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "After : $afterHash"
Write-Host "Query zero-write: $($beforeHash -eq $afterHash)"

정상 결과:

Query zero-write: True

현재는 쓰기 도구를 실행하지 마세요. 특히 다음 도구는 프로젝트 상태나 Git 작업을 변경할 수 있습니다.

completeCurrentRfc
markCurrentRfcCompleted
updateProjectStatus
updateReleaseReadiness
startNextRfc
rollbackCurrentRfc
executePendingImplementationWorkOrder
createImplementationCommit

먼저 getProjectStatus 호출과 Query zero-write: True까지 확인하면 Codex–DocPilot MCP 종단 간 읽기 연동 검증 완료입니다.


• Called docpilot.getProjectStatus({})
  └ {"project": "DocPilot", "phase": "Phase 1 - MVP / POC",
        "currentRfc": "RFC-0044", "release": "v0.5 MVP",
        "completedRfcs": ["RFC-0001", "RFC-0002", "RFC-0003", "RFC-
        0004", "RFC-0005", "RFC-0006", "RFC-0007", "RFC-0008", "RFC-
        0009", "RFC-0010", "RFC-0011", "RFC-0012", "RFC-0013", "RFC-
        0014", "RFC-0015", "RFC-0016", "RFC-0017", "RFC-...

────────────────────────────────────────────────────────────────────────

• getProjectStatus 응답을 기다리고 있습니다. 추가 DocPilot 도구는 호출하
  지 않고 이 조회 결과만 사용하겠습니다.

────────────────────────────────────────────────────────────────────────

• DocPilot MCP의 getProjectStatus로 조회한 현재 상태입니다.

  - 프로젝트: DocPilot
  - 단계: Phase 1 - MVP / POC
  - 릴리스: v0.5 MVP
  - currentRfc: RFC-0044
  - completedRfcs 개수: 43개
  - Release Readiness: 모든 항목 pending

  상태를 변경하는 도구는 호출하지 않았습니다.

Codex–DocPilot MCP 종단 간 읽기 연동이 성공했습니다.

확인된 항목:

Codex가 docpilot MCP 로드           ✅
getProjectStatus 도구 직접 호출      ✅
project-state.json 정상 조회         ✅
currentRfc = RFC-0044               ✅
completedRfcs = 43개                ✅
다른 상태 변경 도구 미호출           ✅

이제 마지막으로 Query zero-write를 확인하세요. 별도 PowerShell 창에서 실행합니다.

cd C:\WorkSpace\docpilot-core-integration

$beforeHash = (
    Get-FileHash `
        .\tools\docpilot-mcp\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "Before: $beforeHash"

Codex에서 getProjectStatus를 한 번 더 호출한 뒤:

$afterHash = (
    Get-FileHash `
        .\tools\docpilot-mcp\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "After : $afterHash"
Write-Host "Query zero-write: $($beforeHash -eq $afterHash)"

정상 결과:

Query zero-write: True

그 후 다음 읽기 전용 도구를 순서대로 검증하는 것이 좋습니다.

getCurrentRfc
getDocPilotProjectControlContext
evaluateRfcCompletionReadiness
getPlanningSynchronizationStatus
getPendingRfcHandoff
getPendingImplementationWorkOrder

Codex 요청 예시는 다음과 같습니다.

DocPilot MCP의 getCurrentRfc,
getDocPilotProjectControlContext,
evaluateRfcCompletionReadiness를 순서대로 호출해 주세요.

파일을 직접 읽지 말고 반드시 MCP 도구를 사용하세요.
상태를 변경하는 도구는 호출하지 마세요.

현재 Pending Handoff와 Work Order가 없으므로 완료 준비도는 NOT_READY 또는 그에 준하는 상태가 나오는 것이 정상입니다.

읽기 검증까지 통과하면 이번 작업은 다음 상태로 고정할 수 있습니다.

MCP compiled runtime       PASS
23 test files / 178 tests  PASS
Inspector integration      PASS
Codex MCP registration     PASS
Codex tool invocation      PASS
Query zero-write           검증 예정

그다음 단계는 빌드 런타임 변경을 v0.12.2로 정리하고, package.json, package-lock.json, tsconfig.build.json, README 변경만 커밋하는 것입니다. dist, node_modules, project-state.json, package.json.backup은 커밋하지 않습니다.

Windows PowerShell
Copyright (C) Microsoft Corporation. All rights reserved.

Install the latest PowerShell for new features and improvements! https://aka.ms/PSWindows

PS C:\Users\nk782> cd C:\WorkSpace\docpilot-core-integration\
PS C:\WorkSpace\docpilot-core-integration> cd C:\WorkSpace\docpilot-core-integration
PS C:\WorkSpace\docpilot-core-integration>
PS C:\WorkSpace\docpilot-core-integration> $beforeHash = (
>>     Get-FileHash 
>>         .\tools\docpilot-mcp\project-state.json 
>>         -Algorithm SHA256
>> ).Hash
PS C:\WorkSpace\docpilot-core-integration>
PS C:\WorkSpace\docpilot-core-integration> Write-Host "Before: $beforeHash"
Before: C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5
PS C:\WorkSpace\docpilot-core-integration> $afterHash = (
>>     Get-FileHash 
>>         .\tools\docpilot-mcp\project-state.json 
>>         -Algorithm SHA256
>> ).Hash
PS C:\WorkSpace\docpilot-core-integration>
PS C:\WorkSpace\docpilot-core-integration> Write-Host "After : $afterHash"
After : C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5
PS C:\WorkSpace\docpilot-core-integration> Write-Host "Query zero-write: $($beforeHash -eq $afterHash)"
Query zero-write: True

검증이 완전히 성공했습니다.

Before: C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5
After : C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5
Query zero-write: True

즉, getProjectStatus 호출이 project-state.json을 전혀 변경하지 않았습니다.

현재 상태는 다음과 같습니다.

MCP TypeScript build       PASS
dist runtime               PASS
23 test files              PASS
178 tests                  PASS
Inspector 연결             PASS
Codex MCP 등록             PASS
Codex 도구 호출            PASS
Query zero-write           PASS

다음 단계는 두 가지입니다.

먼저 Codex에서 나머지 읽기 전용 도구를 검증합니다.

DocPilot MCP의 다음 도구를 순서대로 호출해 주세요.

- getCurrentRfc
- getDocPilotProjectControlContext
- evaluateRfcCompletionReadiness
- getPlanningSynchronizationStatus
- getPendingRfcHandoff
- getPendingImplementationWorkOrder

파일을 직접 읽지 말고 반드시 DocPilot MCP 도구를 사용하세요.
상태를 변경하는 도구는 호출하지 마세요.
각 결과를 간단히 요약해 주세요.

그다음 이번 런타임 통합 변경을 v0.12.2로 정리하는 것이 좋습니다.

커밋 대상:

package.json
package-lock.json
tsconfig.build.json
README.md
src/server.ts의 서버 버전

커밋 제외:

dist/
node_modules/
project-state.json
package.json.backup
.codex/config.toml

현재 시점에서 Codex–DocPilot MCP 실제 읽기 통합은 완료로 판단할 수 있습니다.


• 지정한 순서대로 DocPilot MCP 읽기 전용 도구를 호출했습니다.

  1. getCurrentRfc
      - 현재 RFC: RFC-0044
      - 단계: Phase 1 - MVP / POC
      - 릴리스: v0.5 MVP

  2. getDocPilotProjectControlContext
      - RFC 상태: in_progress
      - 완료된 RFC: 43개
      - Pending Handoff: 없음
      - 완료 준비 상태: NOT_READY
      - Planning 동기화: neverSynced
      - Release Readiness: 모든 항목 pending
      - 자동 완료·진행·커밋·푸시는 비활성화됨

  3. evaluateRfcCompletionReadiness
      - 결과: NOT_READY
      - RFC 식별 확인은 충족됨
      - 필수 Pending Handoff가 없어 미충족
      - 구현, 빌드, 테스트, 회귀, 스모크, 범위 및 Alpha Review 증거는 아
        직 확인 불가

  4. getPlanningSynchronizationStatus
      - 상태: neverSynced
      - 동기화 여부: false
      - 예상 문서 동기화 상태: pending
      - 권장 작업: generateMainPlanningSync

  5. getPendingRfcHandoff
      - RFC-0044의 Pending Handoff 없음

  6. getPendingImplementationWorkOrder
      - RFC-0044의 Pending Implementation Work Order 없음

  파일·셸 및 상태 변경 도구는 사용하지 않았습니다.

읽기 전용 MCP 종단 간 검증은 완료되었습니다.

Codex → DocPilot MCP 연결                 PASS
getProjectStatus                          PASS
getCurrentRfc                             PASS
getDocPilotProjectControlContext          PASS
evaluateRfcCompletionReadiness             PASS
getPlanningSynchronizationStatus          PASS
getPendingRfcHandoff                      PASS
getPendingImplementationWorkOrder         PASS
상태 변경 도구 미호출                     PASS

현재 반환된 NOT_READY, neverSynced, Pending Handoff 없음, Pending Work Order 없음은 RFC-0044가 아직 구현 오케스트레이션을 시작하지 않은 초기 상태와 일치합니다.

1. 전체 읽기 호출의 zero-write 최종 확인

앞서 저장한 원본 해시는 다음이었습니다.

C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5

현재 PowerShell에서 확인하세요.

$currentHash = (
    Get-FileHash `
        .\tools\docpilot-mcp\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "Current: $currentHash"
Write-Host "All read queries zero-write: $(
    $currentHash -eq 'C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5'
)"

True가 나오면 이번에 호출한 모든 읽기 도구까지 zero-write 검증이 완료됩니다.

2. 이번 변경을 v0.12.2로 고정

이번 작업은 기능 추가라기보다 실제 Codex 배포 런타임 통합 안정화이므로 v0.12.2 패치 버전이 적절합니다.

변경 대상으로 정리할 파일:

tools/docpilot-mcp/package.json
tools/docpilot-mcp/package-lock.json
tools/docpilot-mcp/tsconfig.build.json
tools/docpilot-mcp/src/server.ts
tools/docpilot-mcp/README.md

커밋에서 제외할 파일:

tools/docpilot-mcp/dist/
tools/docpilot-mcp/node_modules/
tools/docpilot-mcp/project-state.json
tools/docpilot-mcp/package.json.backup
.codex/config.toml

특히 .codex/config.toml에는 사용자 PC의 절대 경로가 있으므로 커밋하지 않는 것이 안전합니다.

3. 버전 변경
cd C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp

npm version 0.12.2 --no-git-tag-version

src/server.ts 버전도 동일하게 변경합니다.

$serverPath = Join-Path $PWD "src\server.ts"
$content = [System.IO.File]::ReadAllText($serverPath)
$content = $content.Replace(
    'version: "0.12.1"',
    'version: "0.12.2"'
)

[System.IO.File]::WriteAllText(
    $serverPath,
    $content,
    [System.Text.UTF8Encoding]::new($false)
)

확인:

Select-String `
    -Path .\package.json, .\package-lock.json, .\src\server.ts `
    -Pattern "0\.12\.[12]"
4. README에 실제 실행 방법 기록

README에는 최소한 다음 사항을 추가해야 합니다.

Production build:
npm run build

Production runtime:
npm run start:mcp

Inspector:
npm run inspector

Runtime state:
project-state.json must exist in the configured working directory.

Codex:
Use node with dist/index.js and set cwd to the directory containing
project-state.json.

또한 project-state.json의 최소 필수 필드도 기록하는 것이 좋습니다.

{
  "project": "DocPilot",
  "phase": "Phase 1 - MVP / POC",
  "currentRfc": "RFC-0044",
  "release": "v0.5 MVP",
  "completedRfcs": []
}
5. 최종 회귀 검증
npm run build
npm test

정상 기준:

Build exit code       0
Test Files            23 passed
Tests                 178 passed
dist/index.js         존재

Git 변경도 확인합니다.

cd C:\WorkSpace\docpilot-core-integration

git status --short
git diff -- tools/docpilot-mcp/package.json
git diff -- tools/docpilot-mcp/package-lock.json
git diff -- tools/docpilot-mcp/tsconfig.build.json
git diff -- tools/docpilot-mcp/src/server.ts
git diff -- tools/docpilot-mcp/README.md
6. 권장 커밋
feat(mcp): add production runtime for Codex integration

- emit production JavaScript into dist
- add typecheck, clean, runtime, and Inspector scripts
- configure dist/index.js as the package entrypoint
- document runtime state and Codex working-directory requirements
- validate MCP Inspector and Codex STDIO integration
- verify 23 test files and 178 tests
- verify query zero-write behavior

이 커밋까지 완료한 후 다음 개발 단계는 별도 테스트 브랜치와 복제된 상태 파일에서 Controlled Implementation Orchestration의 쓰기 경로를 실제 검증하는 것입니다. 활성 RFC-0044 상태에 바로 쓰기 도구를 적용하기 전에, 먼저 이번 런타임 통합을 v0.12.2로 고정하는 순서가 안전합니다.

PS C:\WorkSpace\docpilot-core-integration> Write-Host "After : $afterHash"
After : C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5
PS C:\WorkSpace\docpilot-core-integration> Write-Host "Query zero-write: $($beforeHash -eq $afterHash)"
Query zero-write: True
PS C:\WorkSpace\docpilot-core-integration> $currentHash = (
>>     Get-FileHash 
>>         .\tools\docpilot-mcp\project-state.json 
>>         -Algorithm SHA256
>> ).Hash
PS C:\WorkSpace\docpilot-core-integration>
PS C:\WorkSpace\docpilot-core-integration> Write-Host "Current: $currentHash"
Current: C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5
PS C:\WorkSpace\docpilot-core-integration> Write-Host "All read queries zero-write: $(
>>     $currentHash -eq 'C481A98C8E05CFCD3E4F64C3A82BFBAFFF0B3A0922C368D532B4F962476E37A5'
>> )"
All read queries zero-write: True
PS C:\WorkSpace\docpilot-core-integration>

모든 읽기 전용 호출의 zero-write 검증이 최종 통과했습니다.

getProjectStatus                         PASS
getCurrentRfc                            PASS
getDocPilotProjectControlContext         PASS
evaluateRfcCompletionReadiness            PASS
getPlanningSynchronizationStatus         PASS
getPendingRfcHandoff                     PASS
getPendingImplementationWorkOrder        PASS
project-state.json 변경 없음             PASS

이제 Codex 실사용 런타임 통합을 v0.12.2로 고정하면 됩니다.

1. 현재 Git 변경 확인
cd C:\WorkSpace\docpilot-core-integration

git status --short
2. 버전 변경
cd .\tools\docpilot-mcp

npm version 0.12.2 --no-git-tag-version

src/server.ts도 맞춥니다.

$serverPath = Join-Path $PWD "src\server.ts"
$content = [System.IO.File]::ReadAllText($serverPath)
$content = $content.Replace(
    'version: "0.12.1"',
    'version: "0.12.2"'
)

[System.IO.File]::WriteAllText(
    $serverPath,
    $content,
    [System.Text.UTF8Encoding]::new($false)
)

확인:

Select-String `
    -Path .\package.json, .\package-lock.json, .\src\server.ts `
    -Pattern '0\.12\.2'
3. 최종 빌드와 테스트
npm run build
npm test

성공 기준:

Build exit code      0
Test Files           23 passed
Tests                178 passed
dist/index.js        존재
4. 커밋 제외 확인

다음은 커밋하면 안 됩니다.

dist/
node_modules/
project-state.json
package.json.backup
.codex/config.toml

백업 파일은 삭제해도 됩니다.

Remove-Item .\package.json.backup -ErrorAction SilentlyContinue

프로젝트 루트에서 확인합니다.

cd C:\WorkSpace\docpilot-core-integration
git status --short

권장 커밋 대상은 다음입니다.

tools/docpilot-mcp/package.json
tools/docpilot-mcp/package-lock.json
tools/docpilot-mcp/tsconfig.build.json
tools/docpilot-mcp/src/server.ts
tools/docpilot-mcp/README.md

권장 커밋 메시지:

feat(mcp): add production runtime for Codex integration

- emit production JavaScript into dist
- add typecheck, clean, runtime, and Inspector scripts
- configure dist/index.js as the package entrypoint
- support Codex STDIO execution with explicit working directory
- validate Inspector and Codex MCP integration
- verify 23 test files and 178 tests
- verify read-only MCP queries are zero-write

현재 시점에서 DocPilot MCP의 실제 Codex 읽기 연동은 완료로 판정할 수 있습니다.

PS C:\WorkSpace\docpilot-core-integration> git status --short
 M tools/docpilot-mcp/package.json
?? .codex/
?? tools/docpilot-mcp/package.json.backup
?? tools/docpilot-mcp/tsconfig.build.json
PS C:\WorkSpace\docpilot-core-integration>

현재 상태는 예상대로입니다.

M  package.json          → 정상 변경
?? tsconfig.build.json   → 새 빌드 설정, 커밋 대상
?? .codex/               → PC 전용 설정, 커밋 제외
?? package.json.backup   → 임시 백업, 삭제

아래 순서로 정리하세요.

1. 임시 백업 삭제
Remove-Item .\tools\docpilot-mcp\package.json.backup
2. .codex/를 로컬 Git 제외 목록에 추가

절대 경로가 들어 있는 개인 환경 설정이므로 저장소의 .gitignore보다 .git/info/exclude에 넣는 것이 안전합니다.

if (-not (Select-String -Path .\.git\info\exclude -Pattern '^\.codex/$' -Quiet)) {
    Add-Content .\.git\info\exclude ".codex/"
}

확인:

Get-Content .\.git\info\exclude
git status --short

이제 .codex/와 백업 파일은 목록에서 사라져야 합니다.

3. v0.12.2로 버전 변경
cd .\tools\docpilot-mcp

npm version 0.12.2 --no-git-tag-version

이 명령으로 다음 두 파일이 변경됩니다.

package.json
package-lock.json

서버 버전도 변경합니다.

$serverPath = Join-Path $PWD "src\server.ts"
$content = [System.IO.File]::ReadAllText($serverPath)

if (-not $content.Contains('version: "0.12.1"')) {
    throw "src/server.ts에서 version 0.12.1을 찾지 못했습니다."
}

$content = $content.Replace(
    'version: "0.12.1"',
    'version: "0.12.2"'
)

[System.IO.File]::WriteAllText(
    $serverPath,
    $content,
    [System.Text.UTF8Encoding]::new($false)
)

확인:

Select-String `
    -Path .\package.json, .\package-lock.json, .\src\server.ts `
    -Pattern '0\.12\.2'
4. 최종 빌드 및 테스트
npm run build
if ($LASTEXITCODE -ne 0) {
    throw "MCP build failed."
}

npm test
if ($LASTEXITCODE -ne 0) {
    throw "MCP tests failed."
}

기대 결과:

Build             PASS
Test Files        23 passed
Tests             178 passed
dist/index.js     생성
5. Git 변경 확인
cd C:\WorkSpace\docpilot-core-integration

git status --short

정상적인 최종 변경 목록은 대략 다음과 같아야 합니다.

 M tools/docpilot-mcp/package.json
 M tools/docpilot-mcp/package-lock.json
 M tools/docpilot-mcp/src/server.ts
?? tools/docpilot-mcp/tsconfig.build.json

README에 운영 빌드와 Codex 등록 절차를 기록하면 다음도 추가됩니다.

 M tools/docpilot-mcp/README.md

dist/, project-state.json, .codex/, node_modules/는 표시되지 않아야 합니다.

?? tools/docpilot-mcp/tsconfig.build.json
PS C:\WorkSpace\docpilot-core-integration> Remove-Item .\tools\docpilot-mcp\package.json.backup
PS C:\WorkSpace\docpilot-core-integration> if (-not (Select-String -Path .\.git\info\exclude -Pattern '^\.codex/$' -Quiet)) {
>>     Add-Content .\.git\info\exclude ".codex/"
>> }
Select-String : 'C:\WorkSpace\docpilot-core-integration\.git\info\exclu
de' 경로는 존재하지 않으므로 찾을 수 없습니다.
위치 줄:1 문자:11
+ if (-not (Select-String -Path .\.git\info\exclude -Pattern '^\.codex/
 ...
+           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : ObjectNotFound: (C:\WorkSpace\do...it\in
   fo\exclude:String) [Select-String], ItemNotFoundException
    + FullyQualifiedErrorId : PathNotFound,Microsoft.PowerShell.Comman
   ds.SelectStringCommand

오류 원인은 이 저장소의 실제 Git 메타데이터 경로가 .\.git\info\exclude가 아니거나, exclude 파일이 아직 생성되지 않았기 때문입니다. Git이 인식하는 경로를 직접 조회해 처리하면 됩니다.

$excludePath = (git rev-parse --git-path info/exclude).Trim()

if (-not [System.IO.Path]::IsPathRooted($excludePath)) {
    $excludePath = [System.IO.Path]::GetFullPath(
        (Join-Path $PWD $excludePath)
    )
}

$excludeDirectory = Split-Path $excludePath -Parent

New-Item `
    -Path $excludeDirectory `
    -ItemType Directory `
    -Force |
    Out-Null

if (-not (Test-Path $excludePath)) {
    New-Item `
        -Path $excludePath `
        -ItemType File `
        -Force |
        Out-Null
}

if (-not (Select-String -Path $excludePath -Pattern '^\.codex/$' -Quiet)) {
    Add-Content -Path $excludePath -Value ".codex/"
}

Write-Host "Git exclude path: $excludePath"
Get-Content $excludePath

그다음 제외 적용 여부를 확인합니다.

git check-ignore -v .codex\config.toml
git status --short

정상이라면 .codex/는 git status에서 사라지고 다음 정도만 남습니다.

 M tools/docpilot-mcp/package.json
?? tools/docpilot-mcp/tsconfig.build.json

tsconfig.build.json의 ??는 문제없습니다. 새로 추가한 커밋 대상 파일이므로 최종 커밋 때 stage하면 됩니다.

PS C:\WorkSpace\docpilot-core-integration>
PS C:\WorkSpace\docpilot-core-integration> Write-Host "Git exclude path: $excludePath"
Git exclude path: C:/WorkSpace/docpilot-core/.git/info/exclude
PS C:\WorkSpace\docpilot-core-integration> Get-Content $excludePath
# git ls-files --others --exclude-from=.git/info/exclude
# Lines that start with '#' are comments.
# For a project mostly in C, the following would be a good set of
# exclude patterns (uncomment them if you want to use them):
# *.[oa]
# *~
.codex/

정상입니다. 현재 docpilot-core-integration은 독립 .git 폴더를 가진 저장소가 아니라, C:\WorkSpace\docpilot-core의 Git 메타데이터를 공유하는 연결된 작업 트리일 가능성이 큽니다.

그래서 Git exclude 경로가 다음으로 나온 것입니다.

C:\WorkSpace\docpilot-core\.git\info\exclude

그리고 .codex/ 등록도 정상입니다.

.codex/

이제 적용 여부를 확인하세요.

git check-ignore -v .codex\config.toml
git status --short

정상이라면 첫 명령은 대략 다음처럼 표시됩니다.

C:/WorkSpace/docpilot-core/.git/info/exclude:...:.codex/    .codex/config.toml

git status --short에서는 .codex/가 사라지고 다음만 남아야 합니다.

 M tools/docpilot-mcp/package.json
?? tools/docpilot-mcp/tsconfig.build.json

Git 구조도 확인해 두는 것이 좋습니다.

git rev-parse --show-toplevel
git rev-parse --git-dir
git rev-parse --git-common-dir
git worktree list

주의할 점은 현재 info/exclude가 공통 Git 디렉터리에 있으므로, 같은 저장소의 다른 worktree에서도 .codex/가 로컬 제외될 수 있다는 것입니다. .codex/는 개인 PC용 절대 경로 설정이므로 이 동작은 대체로 적절합니다.

.codex/
PS C:\WorkSpace\docpilot-core-integration> git check-ignore -v .codex\config.toml
C:/WorkSpace/docpilot-core/.git/info/exclude:7:.codex/  ".codex\\config.toml"
PS C:\WorkSpace\docpilot-core-integration> git status --short
 M tools/docpilot-mcp/package.json
?? tools/docpilot-mcp/tsconfig.build.json
PS C:\WorkSpace\docpilot-core-integration>


정상입니다. .codex/는 로컬 제외 처리되었고, 현재 Git 변경은 두 개만 남았습니다.

M  tools/docpilot-mcp/package.json
?? tools/docpilot-mcp/tsconfig.build.json

다음은 v0.12.2로 정리하는 단계입니다.

cd C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp

npm version 0.12.2 --no-git-tag-version

이어서 MCP 서버 버전을 맞춥니다.

$serverPath = ".\src\server.ts"
$content = [System.IO.File]::ReadAllText($serverPath)
$content = $content.Replace(
    'version: "0.12.1"',
    'version: "0.12.2"'
)

[System.IO.File]::WriteAllText(
    $serverPath,
    $content,
    [System.Text.UTF8Encoding]::new($false)
)

버전 확인:

Select-String `
    -Path .\package.json, .\package-lock.json, .\src\server.ts `
    -Pattern '0\.12\.[12]'

그다음 최종 검증합니다.

npm run build
if ($LASTEXITCODE -ne 0) { throw "MCP build failed." }

npm test
if ($LASTEXITCODE -ne 0) { throw "MCP tests failed." }

프로젝트 루트에서 변경 목록을 확인하세요.

cd C:\WorkSpace\docpilot-core-integration
git status --short

예상되는 변경은 다음입니다.

 M tools/docpilot-mcp/package.json
 M tools/docpilot-mcp/package-lock.json
 M tools/docpilot-mcp/src/server.ts
?? tools/docpilot-mcp/tsconfig.build.json

README에 배포 실행법을 기록하면 다음도 추가됩니다.

 M tools/docpilot-mcp/README.md

아직 커밋은 하지 말고, 먼저 버전 변경과 최종 빌드·178개 테스트까지 통과시키는 순서가 안전합니다.


PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> npm version 0.12.2 --no-git-tag-version
v0.12.2
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $serverPath = ".\src\server.ts"
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $content = [System.IO.File]::ReadAllText($serverPath)
"1"개의 인수가 있는 "ReadAllText"을(를) 호출하는 동안 예외가 발생했습니
다. "'C:\Users\nk782\src\server.ts' 경로의 일부를 찾을 수 없습니다."
위치 줄:1 문자:1
+ $content = [System.IO.File]::ReadAllText($serverPath)
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : NotSpecified: (:) [], MethodInvocationEx
   ception
    + FullyQualifiedErrorId : DirectoryNotFoundException

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $content = $content.Replace(
>>     'version: "0.12.1"',
>>     'version: "0.12.2"'
>> )
null 값 식에서 메서드를 호출할 수 없습니다.
위치 줄:1 문자:1
+ $content = $content.Replace(
+ ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeExcepti
   on
    + FullyQualifiedErrorId : InvokeMethodOnNull

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> [System.IO.File]::WriteAllText(
>>     $serverPath,
>>     $content,
>>     [System.Text.UTF8Encoding]::new($false)
>> )

원인은 PowerShell의 현재 위치와 .NET의 프로세스 작업 디렉터리가 달라서입니다. [System.IO.File]::ReadAllText(".\src\server.ts")가 PowerShell 위치가 아니라 C:\Users\nk782를 기준으로 해석했습니다.

먼저 실제 파일이 손상되지 않았는지 확인하세요.

$actualServerPath = "C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\server.ts"

Test-Path $actualServerPath
(Get-Item $actualServerPath).Length
Get-Content $actualServerPath -TotalCount 10

정상이라면 True, 0보다 큰 파일 크기, 그리고 import 구문들이 표시됩니다.

그다음 절대 경로로 버전을 수정하세요.

$actualServerPath = "C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\server.ts"

$content = [System.IO.File]::ReadAllText($actualServerPath)

if (-not $content.Contains('version: "0.12.1"')) {
    throw "server.ts에서 version 0.12.1을 찾지 못했습니다. 이미 변경됐거나 파일 상태를 확인해야 합니다."
}

$content = $content.Replace(
    'version: "0.12.1"',
    'version: "0.12.2"'
)

[System.IO.File]::WriteAllText(
    $actualServerPath,
    $content,
    [System.Text.UTF8Encoding]::new($false)
)

변경 확인:

Select-String `
    -Path $actualServerPath `
    -Pattern 'version:'

정상 결과:

version: "0.12.2",

패키지 버전도 확인하세요.

cd C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp

node -p "require('./package.json').version"
node -p "require('./package-lock.json').version"

두 결과 모두:

0.12.2

마지막으로 빌드와 테스트를 실행합니다.

npm run build
if ($LASTEXITCODE -ne 0) { throw "MCP build failed." }

npm test
if ($LASTEXITCODE -ne 0) { throw "MCP tests failed." }

앞으로 [System.IO.File] 메서드에는 .\... 상대 경로보다 $PWD.Path로 만든 절대 경로나 완전한 절대 경로를 사용하는 것이 안전합니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $actualServerPath = "C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\server.ts"
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Test-Path $actualServerPath
True
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> (Get-Item $actualServerPath).Length
4736
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Get-Content $actualServerPath -TotalCount 10
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";

import { registerGenerateMainPlanningSyncPrompt } from "./prompt/GenerateMainPlanningSyncPrompt.js";
import { ProjectStateRepository } from "./repository/ProjectStateRepository.js";
import { registerProjectDashboardResource } from "./resource/ProjectDashboardResource.js";
import { registerProjectStatusResource } from "./resource/ProjectStatusResource.js";
import { ProjectStatusService } from "./service/ProjectStatusService.js";
import { ImplementationOrchestrationService } from "./service/ImplementationOrchestrationService.js";
import { ControlledProcessRunner } from "./orchestration/ControlledProcessRunner.js";
import { GitRepositoryController } from "./orchestration/GitRepositoryController.js";
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

server.ts는 손상되지 않았습니다. 파일 크기와 내용이 정상입니다.

이제 절대 경로로 버전을 변경하세요.

$actualServerPath = "C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\src\server.ts"

$currentVersionLine = Select-String `
    -Path $actualServerPath `
    -Pattern 'version:\s*"[^"]+"'

$currentVersionLine.Line

현재 0.12.1이라면 다음을 실행합니다.

$content = [System.IO.File]::ReadAllText($actualServerPath)

if ($content.Contains('version: "0.12.1"')) {
    $content = $content.Replace(
        'version: "0.12.1"',
        'version: "0.12.2"'
    )

    [System.IO.File]::WriteAllText(
        $actualServerPath,
        $content,
        [System.Text.UTF8Encoding]::new($false)
    )
}
elseif ($content.Contains('version: "0.12.2"')) {
    Write-Host "server.ts is already version 0.12.2"
}
else {
    throw "server.ts에서 예상한 버전 문자열을 찾지 못했습니다."
}

변경 결과를 확인합니다.

Select-String `
    -Path $actualServerPath `
    -Pattern 'version:'

정상 결과:

version: "0.12.2",

패키지 버전도 확인하세요.

cd C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp

node -p "require('./package.json').version"
node -p "require('./package-lock.json').version"

둘 다 다음과 같아야 합니다.

0.12.2

그다음 최종 검증을 실행합니다.

npm run build
if ($LASTEXITCODE -ne 0) {
    throw "MCP build failed."
}

npm test
if ($LASTEXITCODE -ne 0) {
    throw "MCP tests failed."
}

마지막으로 변경 파일을 확인합니다.

cd C:\WorkSpace\docpilot-core-integration
git status --short

예상 목록:

 M tools/docpilot-mcp/package-lock.json
 M tools/docpilot-mcp/package.json
 M tools/docpilot-mcp/src/server.ts
?? tools/docpilot-mcp/tsconfig.build.json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> cd C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> node -p "require('./package.json').version"
0.12.2
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> node -p "require('./package-lock.json').version"
0.12.2
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

package.json과 package-lock.json 버전은 모두 정상적으로 0.12.2로 변경되었습니다.

이제 server.ts 버전을 확인하세요.

Select-String `
    -Path .\src\server.ts `
    -Pattern 'version:\s*"[^"]+"'
0.12.1로 나오면 수정
$path = (Resolve-Path .\src\server.ts).Path
$content = Get-Content $path -Raw

$content = $content.Replace(
    'version: "0.12.1"',
    'version: "0.12.2"'
)

[System.IO.File]::WriteAllText(
    $path,
    $content,
    [System.Text.UTF8Encoding]::new($false)
)

다시 확인합니다.

Select-String `
    -Path .\src\server.ts `
    -Pattern 'version:\s*"[^"]+"'

정상 결과:

version: "0.12.2",

그다음 최종 빌드와 테스트를 실행하세요.

npm run build
if ($LASTEXITCODE -ne 0) {
    throw "MCP build failed."
}

npm test
if ($LASTEXITCODE -ne 0) {
    throw "MCP tests failed."
}

마지막으로 프로젝트 루트에서 변경 파일을 확인합니다.

cd C:\WorkSpace\docpilot-core-integration
git status --short

예상 결과:

 M tools/docpilot-mcp/package-lock.json
 M tools/docpilot-mcp/package.json
 M tools/docpilot-mcp/src/server.ts
?? tools/docpilot-mcp/tsconfig.build.json
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Select-String 
>>     -Path .\src\server.ts 
>>     -Pattern 'version:\s*"[^"]+"'

src\server.ts:43:    version: "0.12.1",

package.json과 package-lock.json은 0.12.2지만, MCP 서버가 보고하는 버전은 아직 0.12.1입니다. 아래 명령으로 수정하세요.

$serverPath = (Resolve-Path .\src\server.ts).Path
$content = Get-Content $serverPath -Raw

if (-not $content.Contains('version: "0.12.1"')) {
    throw "server.ts에서 version 0.12.1을 찾지 못했습니다."
}

$content = $content.Replace(
    'version: "0.12.1"',
    'version: "0.12.2"'
)

[System.IO.File]::WriteAllText(
    $serverPath,
    $content,
    [System.Text.UTF8Encoding]::new($false)
)

변경 확인:

Select-String `
    -Path .\src\server.ts `
    -Pattern 'version:\s*"[^"]+"'

정상 결과:

src\server.ts:43:    version: "0.12.2",

그다음 빌드와 테스트를 실행합니다.

npm run build
if ($LASTEXITCODE -ne 0) { throw "MCP build failed." }

npm test
if ($LASTEXITCODE -ne 0) { throw "MCP tests failed." }

빌드가 끝난 뒤 배포 파일에도 새 버전이 반영됐는지 확인하세요.

Select-String `
    -Path .\dist\server.js `
    -Pattern 'version:\s*"[^"]+"'

여기도 version: "0.12.2"가 나오면 버전 동기화가 완료된 것입니다.

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $serverPath = (Resolve-Path .\src\server.ts).Path
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $content = Get-Content $serverPath -Raw
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> if (-not $content.Contains('version: "0.12.1"')) {
>>     throw "server.ts에서 version 0.12.1을 찾지 못했습니다."
>> }
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> $content = $content.Replace(
>>     'version: "0.12.1"',
>>     'version: "0.12.2"'
>> )
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> [System.IO.File]::WriteAllText(
>>     $serverPath,
>>     $content,
>>     [System.Text.UTF8Encoding]::new($false)
>> )
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> Select-String 
>>     -Path .\src\server.ts 
>>     -Pattern 'version:\s*"[^"]+"'

src\server.ts:43:    version: "0.12.2",


PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> npm run build

> docpilot-mcp@0.12.2 build
> npm run clean && npm run typecheck && tsc -p tsconfig.build.json


> docpilot-mcp@0.12.2 clean
> node -e "require('node:fs').rmSync('dist',{recursive:true,force:true})"


> docpilot-mcp@0.12.2 typecheck
> tsc --noEmit

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> if ($LASTEXITCODE -ne 0) { throw "MCP build failed." }
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> npm test

> docpilot-mcp@0.12.2 test
> vitest run


 RUN  v4.1.10 C:/WorkSpace/docpilot-core-integration/tools/docpilot-mcp

 ✓ tests/orchestration/ImplementationOrchestrationService.test.ts (10 tests) 10095ms
     ✓ creates a deterministic persisted Work Order and restores it after restart  1208ms
     ✓ rejects missing plans, unsafe scope, dirty trees, and baseline mismatch  1795ms
     ✓ dry-run is deterministic and side-effect free  1072ms
     ✓ runs a fake Worker, verifies evidence, and creates a Pending Handoff without lifecycle advance  1691ms
     ✓ releases the repository lock after Worker failure and timeout  1775ms
     ✓ releases the repository lock after cancellation  933ms
     ✓ diagnoses persisted RUNNING state without writing or automatic retry  545ms
     ✓ reports a live lock for persisted RUNNING state and rejects orphan records  535ms
     ✓ reports orphan Worker result files without changing state  539ms
 ✓ tests/orchestration/ControlledProcessRunner.test.ts (7 tests) 4066ms
     ✓ times out and rejects working-directory escape  1192ms
     ✓ terminates a spawned process tree on timeout  2401ms
 ✓ tests/orchestration/GitRepositoryController.test.ts (3 tests) 1649ms
     ✓ preserves exact staged, unstaged, untracked, deleted, and renamed evidence  455ms
     ✓ blocks pre-existing staged content  492ms
     ✓ restores its own staged candidate when commit fails  701ms
 ✓ tests/orchestration/RepositoryExecutionLock.test.ts (6 tests) 596ms
     ✓ enforces exclusion across real Node processes  519ms
 ✓ tests/service/ProjectStatusService.test.ts (29 tests) 182ms
 ✓ tests/server/ServerRegistration.test.ts (2 tests) 101ms
 ✓ tests/tool/MainPlanningLifecycleGuidance.test.ts (6 tests) 99ms
 ✓ tests/service/ProjectControlContext.test.ts (18 tests) 90ms
 ✓ tests/service/PlanningSynchronizationStatus.test.ts (12 tests) 79ms
 ✓ tests/service/RfcContextHandoff.test.ts (13 tests) 91ms
 ✓ tests/tool/RfcContextHandoffTools.test.ts (4 tests) 86ms
 ✓ tests/repository/ProjectStateRepository.test.ts (14 tests) 73ms
 ✓ tests/service/RfcRollback.test.ts (9 tests) 68ms
 ✓ tests/tool/UpdateReleaseReadinessTool.test.ts (5 tests) 60ms
 ✓ tests/resource/ProjectDashboardResource.test.ts (5 tests) 61ms
 ✓ tests/tool/StartNextRfcTool.test.ts (4 tests) 53ms
 ✓ tests/tool/RollbackCurrentRfcTool.test.ts (4 tests) 57ms
 ✓ tests/tool/MarkCurrentRfcCompletedTool.test.ts (4 tests) 56ms
 ✓ tests/tool/ProjectControlTools.test.ts (3 tests) 47ms
 ✓ tests/service/RfcRollbackPreview.test.ts (7 tests) 47ms
 ✓ tests/tool/PreviewCurrentRfcRollbackTool.test.ts (3 tests) 42ms
 ✓ tests/tool/GetPlanningSynchronizationStatusTool.test.ts (3 tests) 38ms
 ✓ tests/repository/ImplementationOrchestrationPersistence.test.ts (7 tests) 43ms

 Test Files  23 passed (23)
      Tests  178 passed (178)
   Start at  14:27:53
   Duration  26.38s (transform 1.38s, setup 0ms, import 4.83s, tests 17.78s, environment 1ms)

PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp> if ($LASTEXITCODE -ne 0) { throw "MCP tests failed." }
PS C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp>

좋습니다. v0.12.2 버전 동기화와 회귀 검증이 모두 통과했습니다.

package.json            0.12.2 ✅
package-lock.json       0.12.2 ✅
src/server.ts           0.12.2 ✅
TypeScript build               ✅
Test Files              23/23  ✅
Tests                   178/178 ✅

이제 다음 순서로 마무리하면 됩니다.

1. 배포 파일 버전 확인
Select-String `
    -Path .\dist\server.js `
    -Pattern 'version:\s*"[^"]+"'

Test-Path .\dist\index.js

정상 기준:

version: "0.12.2"
True
2. Git 변경 확인
cd C:\WorkSpace\docpilot-core-integration
git status --short

예상 결과:

 M tools/docpilot-mcp/package-lock.json
 M tools/docpilot-mcp/package.json
 M tools/docpilot-mcp/src/server.ts
?? tools/docpilot-mcp/tsconfig.build.json

dist, project-state.json, .codex, node_modules가 표시되지 않아야 합니다.

3. 실제 변경 내용 확인
git diff -- tools/docpilot-mcp/package.json
git diff -- tools/docpilot-mcp/package-lock.json
git diff -- tools/docpilot-mcp/src/server.ts
git diff --no-index NUL tools/docpilot-mcp/tsconfig.build.json

마지막 명령은 새 파일 내용을 확인하기 위한 것이므로 차이가 있다는 종료 코드가 나와도 정상입니다.

4. README 보완 권장

현재 변경 목록에 README가 없습니다. 이번 실사용 통합에서 확인된 아래 내용을 README에 기록하는 것이 좋습니다.

Production build:
npm run build

Production runtime:
npm run start:mcp

Inspector:
npm run inspector

Runtime requirements:
- project-state.json must exist
- cwd must point to the directory containing project-state.json
- stdout is reserved for MCP JSON-RPC
- diagnostic logs must use stderr

Codex runtime:
command = node.exe
args = dist/index.js
cwd = tools/docpilot-mcp

README를 수정하면 최종 변경 목록에 다음이 추가됩니다.

 M tools/docpilot-mcp/README.md
5. Codex에서 버전 재검증

기존 Codex 세션은 이전 MCP 프로세스를 유지할 수 있으므로 종료 후 다시 시작합니다.

Ctrl+C

다시 프로젝트 루트에서:

codex

Codex에서 /mcp를 확인하고 getProjectStatus를 한 번 호출하세요. 서버 버전을 확인할 수 있는 Inspector 화면에서는 0.12.2로 표시되어야 합니다.

6. 커밋

README 보완과 최종 확인이 끝나면 명시적으로 필요한 파일만 stage합니다.

git add `
  tools/docpilot-mcp/package.json `
  tools/docpilot-mcp/package-lock.json `
  tools/docpilot-mcp/tsconfig.build.json `
  tools/docpilot-mcp/src/server.ts `
  tools/docpilot-mcp/README.md

README를 수정하지 않았다면 해당 경로는 제외하세요.

스테이징 확인:

git diff --cached --stat
git diff --cached

권장 커밋 메시지:

feat(mcp): add production runtime for Codex integration

- emit production JavaScript into dist
- add clean, typecheck, runtime, and Inspector scripts
- configure dist/index.js as the package entrypoint
- document runtime state and working-directory requirements
- synchronize package and MCP server versions to 0.12.2
- verify 23 test files and 178 tests
- validate Inspector and Codex STDIO integration
- verify read-only MCP queries are zero-write

현재 기술 검증 기준으로는 v0.12.2 구현 완료 상태이며, 남은 핵심 작업은 README 동기화와 커밋입니다.

readme.md 타입 파일입니다. 추가할 내용도 md 타입으로 작성해주세요

README.md에 아래 내용을 그대로 추가하면 됩니다. 기존 Runtime state 또는 Development 섹션 뒤에 배치하는 것이 자연스럽습니다.

Production Runtime and Codex MCP Integration
Production Runtime and Codex MCP Integration

DocPilot MCP supports a compiled production runtime for stable integration with Codex and other MCP clients.

The development runtime executes TypeScript directly through tsx, while the production runtime compiles the server to JavaScript under dist/ and executes it with Node.js.

Build

Run the production build from the tools/docpilot-mcp directory:

npm run build

The build performs the following steps:

Removes the previous dist/ directory.
Runs strict TypeScript type checking without emitting files.
Compiles the production JavaScript runtime into dist/.

A successful build must create at least the following files:

dist/index.js
dist/index.js.map
dist/server.js
dist/server.js.map

The package production entry point is:

dist/index.js
Runtime commands

Run the compiled MCP server:

npm run start:mcp

Run the MCP Inspector against the compiled runtime:

npm run inspector

Run the development server directly from TypeScript:

npm run dev

Run the MCP Inspector against the TypeScript development runtime:

npm run inspector:dev
STDIO transport requirements

DocPilot MCP uses the MCP STDIO transport.

Standard output is reserved exclusively for MCP JSON-RPC communication. Application logs must not be written to standard output.

Use standard error for startup and diagnostic messages:

console.error("DocPilot MCP server started.");

Do not use standard output for ordinary logging:

// Do not use this in an STDIO MCP server.
console.log("DocPilot MCP server started.");

Writing non-protocol data to standard output can corrupt the MCP connection.

Runtime project state

The MCP server resolves project-state.json relative to the process working directory:

<working-directory>/project-state.json

The runtime state file must exist before project status tools are called. The server does not automatically create an initial state file.

A minimal valid state file is:

{
  "project": "DocPilot",
  "phase": "Phase 1 - MVP / POC",
  "currentRfc": "RFC-0044",
  "release": "v0.5 MVP",
  "completedRfcs": []
}

The required fields are:

project
phase
currentRfc
release
completedRfcs

When releaseReadiness is omitted, all readiness fields are loaded with the deterministic pending default.

When lifecycleHistory is omitted, it is loaded as an empty array.

Example expanded state:

{
  "project": "DocPilot",
  "phase": "Phase 1 - MVP / POC",
  "currentRfc": "RFC-0044",
  "release": "v0.5 MVP",
  "completedRfcs": [
    "RFC-0001",
    "RFC-0002"
  ],
  "releaseReadiness": {
    "coreBuild": "pending",
    "coreTests": "pending",
    "cli": "pending",
    "incremental": "pending",
    "reviewWorkflow": "pending",
    "architectureSamplesValidation": "pending",
    "documentationSync": "pending",
    "releaseCandidate": "pending"
  },
  "lifecycleHistory": []
}

project-state.json and project-state.tmp.json are runtime files. They must not be committed to Git.

Codex integration

A Codex project can register DocPilot through a project-level .codex/config.toml.

Example Windows configuration:

[mcp_servers.docpilot]
command = 'C:\Program Files\nodejs\node.exe'
args = ['C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js']
cwd = 'C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp'
enabled = true
startup_timeout_sec = 20
tool_timeout_sec = 1800
default_tools_approval_mode = 'prompt'

The configured cwd must point to the directory containing project-state.json.

The absolute paths above are examples and must be adjusted for the local installation.

Verify the registration from the project root:

codex mcp list

Start Codex:

codex

Inside Codex, use the following command to inspect active MCP servers:

/mcp

The docpilot server should appear with its registered tools, including:

getProjectStatus
getCurrentRfc
getDocPilotProjectControlContext
evaluateRfcCompletionReadiness
getPlanningSynchronizationStatus
getPendingRfcHandoff
getPendingImplementationWorkOrder
Read-only integration check

Request a project status query through Codex:

Call the DocPilot MCP getProjectStatus tool and report the current project
status. Do not read project-state.json directly and do not call any
state-changing tool.

A successful response should reflect the values stored in the runtime state file.

Query zero-write verification

Read-only MCP tools must not change project-state.json.

Capture the state hash before the query:

$beforeHash = (
    Get-FileHash `
        .\project-state.json `
        -Algorithm SHA256
).Hash

Call one or more read-only DocPilot MCP tools, then capture the hash again:

$afterHash = (
    Get-FileHash `
        .\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "Query zero-write: $($beforeHash -eq $afterHash)"

Expected result:

Query zero-write: True
Production verification

Run the complete production verification from tools/docpilot-mcp:

npm run build
npm test

The v0.12.2 production runtime verification baseline is:

Build:       PASS
Test files:  23 passed
Tests:       178 passed
Inspector:   PASS
Codex STDIO: PASS
Zero-write:  PASS

The compiled runtime can also be checked directly:

Test-Path .\dist\index.js
Select-String `
    -Path .\dist\server.js `
    -Pattern 'version:\s*"[^"]+"'

Expected results:

True
version: "0.12.2"
Git hygiene

The following files and directories are generated, runtime-specific, or machine-specific and must not be committed:

dist/
node_modules/
project-state.json
project-state.tmp.json
package.json.backup
.codex/config.toml
.docpilot/

The production runtime source changes that should be committed include:

package.json
package-lock.json
tsconfig.build.json
src/server.ts
README.md

프로젝트의 실제 README 목차에 맞춰 제목 수준만 ##에서 ###로 조정하면 됩니다.

마크다운 형식으로 REadme에 들어갈 내용 작성해주세요

아래 내용을 tools/docpilot-mcp/README.md의 기존 Runtime 또는 Development 관련 섹션 뒤에 그대로 추가하면 됩니다.

Production Runtime and Codex Integration
Production Runtime and Codex Integration

DocPilot MCP supports a compiled JavaScript runtime for stable integration with Codex and other MCP clients.

The development runtime executes TypeScript directly through tsx. The production runtime compiles the TypeScript source into dist/ and executes the generated JavaScript with Node.js.

Production Build

Run the production build from the tools/docpilot-mcp directory:

npm run build

The build performs the following steps:

Removes the previous dist/ directory.
Runs strict TypeScript type checking.
Compiles the MCP server into production JavaScript.

A successful build creates files including:

dist/index.js
dist/index.js.map
dist/server.js
dist/server.js.map

The production package entry point is:

dist/index.js
Runtime Commands

Run the compiled MCP server:

npm run start:mcp

Run MCP Inspector against the compiled runtime:

npm run inspector

Run the TypeScript development server:

npm run dev

Run MCP Inspector against the TypeScript development runtime:

npm run inspector:dev
STDIO Transport Requirements

DocPilot MCP uses the MCP STDIO transport.

Standard output is reserved exclusively for MCP JSON-RPC communication. Application and diagnostic messages must not be written to standard output.

Use standard error for runtime diagnostics:

console.error("DocPilot MCP server started.");

Do not use standard output for ordinary logging:

// Do not use this for an STDIO MCP server.
console.log("DocPilot MCP server started.");

Writing non-protocol data to standard output can corrupt the MCP connection.

Runtime Project State

DocPilot MCP resolves project-state.json relative to the process working directory:

<working-directory>/project-state.json

The runtime state file must exist before project status tools are called. The server does not automatically create an initial state file.

A minimal valid state file is:

{
  "project": "DocPilot",
  "phase": "Phase 1 - MVP / POC",
  "currentRfc": "RFC-0044",
  "release": "v0.5 MVP",
  "completedRfcs": []
}

The required fields are:

project
phase
currentRfc
release
completedRfcs

When releaseReadiness is omitted, all readiness fields are loaded with the deterministic pending default.

When lifecycleHistory is omitted, it is loaded as an empty array.

An expanded state example is:

{
  "project": "DocPilot",
  "phase": "Phase 1 - MVP / POC",
  "currentRfc": "RFC-0044",
  "release": "v0.5 MVP",
  "completedRfcs": [
    "RFC-0001",
    "RFC-0002"
  ],
  "releaseReadiness": {
    "coreBuild": "pending",
    "coreTests": "pending",
    "cli": "pending",
    "incremental": "pending",
    "reviewWorkflow": "pending",
    "architectureSamplesValidation": "pending",
    "documentationSync": "pending",
    "releaseCandidate": "pending"
  },
  "lifecycleHistory": []
}

The following files are runtime state and must not be committed:

project-state.json
project-state.tmp.json
Codex MCP Registration

A Codex project can register DocPilot MCP through a project-level .codex/config.toml file.

Example Windows configuration:

[mcp_servers.docpilot]
command = 'C:\Program Files\nodejs\node.exe'
args = ['C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js']
cwd = 'C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp'
enabled = true
startup_timeout_sec = 20
tool_timeout_sec = 1800
default_tools_approval_mode = 'prompt'

The absolute paths in this example must be adjusted for the local environment.

The configured cwd must point to the directory containing project-state.json.

Verify the registered MCP servers from the project root:

codex mcp list

Start Codex:

codex

Inside Codex, inspect the active MCP servers:

/mcp

The docpilot server should expose tools including:

getProjectStatus
getCurrentRfc
getDocPilotProjectControlContext
evaluateRfcCompletionReadiness
getPlanningSynchronizationStatus
getPendingRfcHandoff
getPendingImplementationWorkOrder
prepareImplementationWorkOrder
executePendingImplementationWorkOrder
createImplementationCommit

An Auth value of Unsupported is expected for the local STDIO server because it does not use HTTP or OAuth authentication.

Read-Only Integration Verification

Request a project status query through Codex:

Call the DocPilot MCP getProjectStatus tool and report the current project
status.

Do not read project-state.json directly.
Do not use shell commands to obtain the status.
Do not call any state-changing tool.

A successful result must reflect the values stored in project-state.json.

Example expected values:

Project: DocPilot
Current RFC: RFC-0044
Completed RFC count: 43

Additional read-only tools can be checked with the following request:

Call the following DocPilot MCP tools in order:

1. getCurrentRfc
2. getDocPilotProjectControlContext
3. evaluateRfcCompletionReadiness
4. getPlanningSynchronizationStatus
5. getPendingRfcHandoff
6. getPendingImplementationWorkOrder

Use only DocPilot MCP tools.
Do not read files directly.
Do not call state-changing tools.

For a newly initialized RFC without a Pending Handoff or Pending Implementation Work Order, the expected state is generally:

Completion readiness: NOT_READY
Planning synchronization: neverSynced
Pending RFC Handoff: none
Pending Implementation Work Order: none
Query Zero-Write Verification

Read-only MCP tools must not change project-state.json.

Capture the file hash before calling a read-only tool:

$beforeHash = (
    Get-FileHash `
        .\project-state.json `
        -Algorithm SHA256
).Hash

Call one or more read-only DocPilot MCP tools, then capture the hash again:

$afterHash = (
    Get-FileHash `
        .\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "Before: $beforeHash"
Write-Host "After : $afterHash"
Write-Host "Query zero-write: $($beforeHash -eq $afterHash)"

Expected result:

Query zero-write: True
Production Verification

Run the complete production verification from tools/docpilot-mcp:

npm run build
npm test

The v0.12.2 verification baseline is:

Build:                  PASS
Test files:             23 passed
Tests:                  178 passed
MCP Inspector:          PASS
Codex STDIO connection: PASS
Read-only zero-write:   PASS

Verify that the compiled entry point exists:

Test-Path .\dist\index.js

Expected result:

True

Verify the compiled MCP server version:

Select-String `
    -Path .\dist\server.js `
    -Pattern 'version:\s*"[^"]+"'

Expected result:

version: "0.12.2"
Git Hygiene

The following generated, runtime-specific, or machine-specific paths must not be committed:

dist/
node_modules/
project-state.json
project-state.tmp.json
package.json.backup
.codex/config.toml
.docpilot/

The production runtime source changes that should be committed are:

package.json
package-lock.json
tsconfig.build.json
src/server.ts
README.md
붙여넣은 텍스트 (1)(5).txt
문서
기존의 Readme 파일입니다. 내용을 합쳐서 하나로 만들어 주세요
2m 53s 동안 처리함

기존 README 전체 내용을 유지하면서 v0.12.2 운영 런타임 내용을 통합한 단일 Markdown 파일을 만들었습니다. 기존 문서의 아키텍처, 도구, RFC 워크플로, Persistence 설명을 보존하고 다음 내용을 정리했습니다.

Production JavaScript 빌드와 dist/index.js 실행
MCP Inspector와 Codex 등록 방법
project-state.json 초기화 및 cwd 요구사항
STDIO의 stdout/stderr 규칙
Codex 읽기 전용 호출과 zero-write 검증
Commands 섹션을 실제 v0.12.2 스크립트에 맞게 수정
테스트 기준을 23개 파일, 178개 테스트로 갱신
Git 제외 대상과 커밋 대상 구분
기존 제한사항의 중복 및 오래된 표현 정리

README.md

라이브러리
/
README.md
# DocPilot MCP

DocPilot MCP is the MCP control plane for DocPilot project status, RFC workflow, documentation, and release operations. This package establishes the initial product foundation while retaining the behavior of the existing implementation.

## Product scope

The current product reads and updates a compact DocPilot project status and persistent Release Readiness, supports the current RFC completion workflow, reports completed RFCs, provides a consolidated read-only project dashboard, and generates a Main Planning synchronization artifact. Documentation operations remain part of the intended control-plane scope but are not implemented yet.

## Current architecture

The TypeScript server uses MCP over standard input/output. `src/index.ts` starts the transport, and `src/server.ts` creates the repository and service before registering all Tools, Resources, and Prompts. The implementation is organized into model, repository, service, tool, resource, and prompt layers.

Tools call `ProjectStatusService`; they do not access persistence directly. The service owns business validation and workflow rules. `ProjectStateRepository` owns JSON loading, validation at the persistence boundary, serialization, and atomic replacement of the state file. The project status and dashboard Resources and the planning Prompt read through the service.

See [docs/architecture.md](docs/architecture.md) for the detailed architecture and dependency rules.


## Production runtime and Codex integration

DocPilot MCP v0.12.2 supports a compiled JavaScript runtime for stable integration with Codex and other MCP clients.

The development runtime executes TypeScript directly through `tsx`. The production runtime compiles the TypeScript source into `dist/` and executes the generated JavaScript with Node.js.

### Build and runtime model

Run commands from `tools/docpilot-mcp`.

The production build performs a clean, strict type-check, and JavaScript emit:

```sh
npm run build
```

A successful build creates files including:

```text
dist/index.js
dist/index.js.map
dist/server.js
dist/server.js.map
```

The production package entry point is `dist/index.js`.

Run the compiled MCP server:

```sh
npm run start:mcp
```

Run MCP Inspector against the compiled runtime:

```sh
npm run inspector
```

For TypeScript development, use:

```sh
npm run dev
npm run inspector:dev
```

### STDIO transport requirements

DocPilot MCP uses MCP over standard input/output.

Standard output is reserved exclusively for MCP JSON-RPC communication. Application and diagnostic messages must not be written to standard output because non-protocol output can corrupt the MCP connection.

Use standard error for runtime diagnostics:

```ts
console.error("DocPilot MCP server started.");
```

Do not use ordinary standard-output logging in the STDIO server:

```ts
// Do not use this in the STDIO MCP runtime.
console.log("DocPilot MCP server started.");
```

### Runtime state initialization

`ProjectStateRepository` resolves the runtime state file from the process working directory:

```text
<working-directory>/project-state.json
```

The state file must exist before project status Tools, Resources, or Prompts are called. The server deliberately does not invent or automatically initialize project status.

A minimal valid state file is:

```json
{
  "project": "DocPilot",
  "phase": "Phase 1 - MVP / POC",
  "currentRfc": "RFC-0044",
  "release": "v0.5 MVP",
  "completedRfcs": []
}
```

When `releaseReadiness` is omitted, all eight readiness fields load as `pending`. When `lifecycleHistory` is omitted, it loads as an empty array. Reads do not rewrite the legacy or minimal file.

`project-state.json` and `project-state.tmp.json` are runtime artifacts and must not be committed.

### Codex MCP registration

A Codex project can register DocPilot MCP through a project-level `.codex/config.toml`.

Example Windows configuration:

```toml
[mcp_servers.docpilot]
command = 'C:\Program Files\nodejs\node.exe'
args = ['C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js']
cwd = 'C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp'
enabled = true
startup_timeout_sec = 20
tool_timeout_sec = 1800
default_tools_approval_mode = 'prompt'
```

The paths above are examples and must be adjusted for the local installation. The configured `cwd` must point to the directory that contains `project-state.json`.

Verify registration from the project root:

```sh
codex mcp list
```

Start Codex and inspect active MCP servers:

```sh
codex
```

```text
/mcp
```

For a local STDIO server, an `Auth` value of `Unsupported` is expected because the server does not use HTTP or OAuth authentication.

### Read-only integration verification

A basic end-to-end verification is to ask Codex to call `getProjectStatus` while explicitly forbidding direct file or shell access:

```text
Call the DocPilot MCP getProjectStatus Tool and report the current project
status.

Do not read project-state.json directly.
Do not use shell commands to obtain the status.
Do not call any state-changing Tool.
```

Additional read-only checks can call the following Tools:

```text
getCurrentRfc
getDocPilotProjectControlContext
evaluateRfcCompletionReadiness
getPlanningSynchronizationStatus
getPendingRfcHandoff
getPendingImplementationWorkOrder
```

For a newly initialized RFC with no Pending Handoff or Pending Implementation Work Order, typical results are:

```text
Completion Readiness: NOT_READY
Planning Synchronization: neverSynced
Pending RFC Handoff: none
Pending Implementation Work Order: none
```

### Query zero-write verification

Read-only Tools must not change `project-state.json`.

Capture the SHA-256 hash before the query:

```powershell
$beforeHash = (
    Get-FileHash `
        .\project-state.json `
        -Algorithm SHA256
).Hash
```

Call one or more read-only Tools, then compare the hash:

```powershell
$afterHash = (
    Get-FileHash `
        .\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "Before: $beforeHash"
Write-Host "After : $afterHash"
Write-Host "Query zero-write: $($beforeHash -eq $afterHash)"
```

Expected result:

```text
Query zero-write: True
```

### v0.12.2 production verification baseline

The verified production-runtime baseline is:

```text
Build:                  PASS
Test files:             23 passed
Tests:                  178 passed
MCP Inspector:          PASS
Codex STDIO connection: PASS
Read-only zero-write:   PASS
```

The compiled entry point and server version can be checked with:

```powershell
Test-Path .\dist\index.js

Select-String `
    -Path .\dist\server.js `
    -Pattern 'version:\s*"[^"]+"'
```

Expected results:

```text
True
version: "0.12.2"
```

## Available Tools

### Project Control Boundary

- `getDocPilotProjectControlContext` accepts strict empty input and composes the official current project, lifecycle, RFC Context, Pending Handoff summary, Completion Readiness, Capability Manifest, policies, Planning Synchronization, Release Readiness, and explicit evidence limitations. It is read-only.
- `evaluateRfcCompletionReadiness` optionally accepts the current `rfcId` and evaluates deterministic Alpha Gates without writing state or executing submitted commands.

Project Control Query Boundary consists of `loadRfcContext`, `getPendingRfcHandoff`, `getDocPilotProjectControlContext`, `evaluateRfcCompletionReadiness`, and `getPendingImplementationWorkOrder`. Commands are `submitRfcHandoff`, `prepareImplementationWorkOrder`, `executePendingImplementationWorkOrder`, and `createImplementationCommit`. Acknowledge, consume, archive, history, cloud workers, push/PR/release automation, and lifecycle advancement remain outside the boundary.

Completion Readiness uses fixed ordered checks for identity, Handoff presence/schema/RFC, implementation, build, tests, regression, smoke, scope, alpha review, known limitations, and Git push policy. Results are `NOT_READY`, `BLOCKED`, `READY_WITH_WARNINGS`, or `READY`. Submitted evidence is structurally validated but MCP does not independently execute commands or verify Git diffs. Missing allowed paths are disclosed on the Scope check.

The Capability Manifest reports deterministic Work Order generation, controlled local execution, Alpha-gated commit creation, and a push-approval boundary as supported. `git.pushApproval=true` describes the boundary only: no push implementation exists. Cloud execution, push, PR/release automation, and automatic lifecycle completion/advance remain false.

### Controlled Implementation Orchestration

- `prepareImplementationWorkOrder` fixes the current RFC, Git root/branch/HEAD baseline, approved plan, normalized scope, controlled verification commands, result contract, and conservative Git policy. One restart-safe Pending Work Order is allowed per current RFC.
- `getPendingImplementationWorkOrder` is a strict, deterministic, zero-write query.
- `executePendingImplementationWorkOrder` accepts optional `{ "dryRun": true }`. Dry-run returns ordered preflight checks plus the deterministic Codex prompt/command without executing or saving. A real run requires a clean working tree, fixed HEAD, valid in-repository paths, an available Codex executable, no Pending Handoff, and valid controlled commands. It records RUNNING before execution and a terminal result afterward.
- `createImplementationCommit` accepts `{ "message": "..." }` only after MCP Alpha passes and the Work Order permits commits. It stages explicit authorized evidence paths, runs cached diff checks, creates one non-amended commit, and returns `PENDING_APPROVAL`. It never pushes.

`ControlledCommand` separates executable and arguments, fixes an in-repository working directory, requires a timeout, runs without a shell, passes only allowlisted environment variables, limits output, and masks common secret forms. Verification order is targeted tests, module tests, build, regression, then smoke; a required failure skips subsequent commands. Git evidence uses porcelain status and records branch, baseline/HEAD, changed, created, deleted, renamed, staged, and untracked paths. Diff validation blocks forbidden/out-of-scope paths, unauthorized dependency/build configuration changes, unapproved public-API candidates, and disallowed untracked files.

The Worker JSON is treated as a claim. MCP independently validates its schema/RFC/Work Order identity, actual Git evidence, verification results, policy review, and twelve ordered Alpha Gates. Only `PASSED` or `PASSED_WITH_LIMITATIONS` creates the official Pending Handoff; failure remains in the Execution Record. Work Order preparation, execution, Handoff generation, and commit never complete or advance an RFC and never mark Planning synchronized.

### v0.12.1 orchestration stabilization

Real execution uses an atomic repository-scoped lock at `.docpilot/orchestration-lock`. The lock directory is acquired with one atomic filesystem operation and contains validated schema, canonical repository identity, Work Order/RFC, PID, process-start identity, acquisition time, and hostname. A live or uncertain owner is never removed. Only a demonstrably dead PID is recovered, and malformed metadata requires manual recovery. Dry-run and read-only queries never create the lock or runtime directory.

A persisted `RUNNING` record remains evidence rather than being silently rewritten. Pending Work Order queries add optional recovery diagnostics based on current lock evidence; execution remains blocked and is never automatically resumed. Missing Work Order/Execution counterparts are rejected as inconsistent state. Existing v0.10-v0.12 state remains loadable without migration.

Process termination distinguishes timeout from cancellation, records exit signal and termination stages, bounds buffers before concatenation, removes listeners, and attempts process-tree termination (POSIX process group; Windows `taskkill /T` plus direct termination). Windows command wrappers retain strict metacharacter rejection. Canonical path resolution follows existing parents to prevent symlink escape for not-yet-created result paths.

Git evidence uses NUL-delimited porcelain and preserves staged, unstaged, untracked, deletion, rename, and type-change categories. Runtime-only `.docpilot` paths do not make Preflight dirty, but cannot authorize product changes. Execution captures evidence after lock acquisition and before Worker start, then blocks if HEAD or non-runtime state changed after Preflight.

Commit creation rejects every pre-existing staged path, rechecks expected HEAD, stages explicit candidate files only, verifies the exact cached path set and cached diff, and validates an actual new commit object. Its own staged paths are restored if commit creation fails. Restart evidence with an unexpected HEAD is blocked as a possible already-created commit; no duplicate commit or push is attempted.

### RFC Context and Handoff

- `loadRfcContext` optionally accepts the current `rfcId` and returns deterministic official project context, operating rules, alpha criteria, guidance, synchronization, readiness, and warnings for unavailable RFC metadata. It is read-only.
- `submitRfcHandoff` accepts `{ "handoff": RfcHandoff }`, validates schema version `1.0` and current-RFC ownership, normalizes file lists, and atomically stores one Pending Handoff. It never advances lifecycle, synchronizes Planning, commits, or pushes.
- `getPendingRfcHandoff` accepts strict empty input and returns the current Pending Handoff plus deterministic Markdown, or a normal `found: false` result.

Duplicate submission is rejected rather than silently replacing review evidence. No Handoff history, Approval Registry, or Evidence Registry is created.

### Project Status

- `getProjectStatus` returns the complete current project status.
- `getCurrentRfc` returns the current RFC with its phase and release context.
- `updateProjectStatus` updates one or more of `phase`, `release`, and `currentRfc` after service validation.
- `listCompletedRfcs` returns completed RFC identifiers, their count, and current project context.
- `updateReleaseReadiness` accepts an `updates` object containing one or more readiness fields and returns the updated project status. Each value must be `pending`, `passed`, or `failed`; omitted fields retain their prior values.

### RFC Workflow

- `markCurrentRfcCompleted` accepts strict empty input (`{}`), marks the current RFC completed, and leaves the current RFC, phase, release, and Release Readiness unchanged.
- `startNextRfc` starts an explicitly supplied later RFC, preserves completed history, optionally updates `phase` and `release`, and resets all Release Readiness fields to `pending`. Input is `{ "nextRfc": "RFC-0045", "phase"?: "...", "release"?: "..." }`; no other fields are accepted.
- `rollbackCurrentRfc` accepts strict empty input (`{}`) and restores the immediately previous active RFC from lifecycle-history evidence. It resets Release Readiness and appends an audit event; it does not roll back Git, source files, branches, or commits.
- `previewCurrentRfcRollback` accepts strict empty input (`{}`) and reports whether that same one-step rollback is eligible, including the restored RFC, phase, release, and all-pending readiness state. It is read-only and returns a stable blocking reason when ineligible.
- `completeCurrentRfc` is the legacy shortcut that records the current RFC and advances immediately to its required `nextRfc`.

The preferred lifecycle is `markCurrentRfcCompleted` → `startNextRfc` → `generateMainPlanningSync`. Marking validates the current RFC against exact `RFC-[0-9]{4}` syntax, explicitly rejects an already completed RFC, numerically orders and deduplicates completed history, and performs one Repository save. It does not reset readiness or invoke planning.

`startNextRfc` requires the exact `RFC-[0-9]{4}` format with no surrounding whitespace. The next RFC must differ from and be numerically greater than the current RFC, must not already be completed, and the current RFC must already appear in completed history. Optional phase and release values must be non-empty. The Service validates each transition and sends one complete state to the Repository for persistence.

For backward compatibility, `completeCurrentRfc` retains its existing input, response, and combined complete-and-advance behavior. Completed history produced by either completion method is canonicalized into numeric RFC order without duplicates. No workflow automatically generates or writes Main Planning sync output; planning remains a separate Tool or Prompt operation.

### RFC Lifecycle Guidance

Lifecycle guidance is derived from the current persisted project status and is never stored. It reports one of three stable states:

- `in_progress` recommends `markCurrentRfcCompleted` when the current RFC is absent from completed history.
- `completed_waiting_next` recommends `startNextRfc` when the current RFC is already completed.
- `inconsistent` recommends `manualReview` when the current RFC or completed history contains malformed identifiers or duplicate completed entries.

The Service owns these decisions and deterministic reason strings. The `generateMainPlanningSync` Tool exposes guidance in structured content and in its Markdown, the Prompt appends the same RFC Lifecycle section, and `docpilot://project/dashboard` includes an additive `lifecycleGuidance` object. Guidance derivation, dashboard reads, and Prompt generation do not persist state or execute a recommended Tool. The explicit planning Tool records its own lifecycle-history event as described below.

Because `completeCurrentRfc` advances directly to a new current RFC without storing transition metadata, its result is structurally identical to ordinary in-progress work. Guidance therefore recommends `markCurrentRfcCompleted` for that new current RFC and does not infer how it became active.

### Planning

- `generateMainPlanningSync` generates a Markdown Main Planning status summary and structured status data.
- `getPlanningSynchronizationStatus` accepts strict empty input (`{}`) and read-only evaluates whether lifecycle changes are covered by the latest `planningSynced` event.

### Planning Synchronization Status

Planning status is derived from validated lifecycle history in persisted append order; timestamps are never compared. `started`, `completed`, and `rollbackCompleted` are planning-relevant changes. A later `planningSynced` covers those changes without itself making Planning stale.

- `neverSynced`: no valid `planningSynced` event exists; the recommended action is `generateMainPlanningSync`.
- `current`: no relevant transition occurs after the latest sync; the recommended action is `none`.
- `stale`: a relevant transition occurs after the latest sync; the recommended action is `generateMainPlanningSync`. Rollback uses a dedicated stable reason.

Evaluation creates no timestamp, performs no save, appends no event, consumes no event ID, and does not mutate Project Status. The derived expected `documentationSync` is `passed` for `current` and `pending` otherwise. The result reports whether persisted readiness agrees and supplies a deterministic mismatch reason; it never corrects readiness automatically. Lifecycle Guidance adds synchronization state and required status separately while retaining its primary RFC workflow action.

The legacy combined completion workflow has no transition event. For compatibility, an explicit `planningSynced` event matching persisted `currentRfc` may re-anchor planning evaluation after that workflow; rollback resolution remains strict. Future work may automatically maintain Documentation Sync and use this status in release gates.

## Available Resources

- `project-status` at `docpilot://project/status` returns the current project status as `application/json`.
- `project-dashboard` at `docpilot://project/dashboard` returns a consolidated read-only dashboard as `application/json`. Its fields are `project`, `phase`, `currentRfc`, `release`, `completedCount`, `completedRfcs`, and `releaseReadiness`. Current values, including persisted readiness, come from `ProjectStatusService`; `completedCount` is derived from the ordered `completedRfcs` array.

The `releaseReadiness` object contains `coreBuild`, `coreTests`, `cli`, `incremental`, `reviewWorkflow`, `architectureSamplesValidation`, `documentationSync`, and `releaseCandidate`. Each field is persisted as `pending`, `passed`, or `failed`.

The dashboard also exposes the append-only `lifecycleHistory` array, derived `rollbackPreview`, and complete `planningSynchronization` status. Repeated dashboard reads do not save state or append events.

## Available Prompts

- `generateMainPlanningSync` creates the existing Main Planning synchronization prompt. Optional `completedWork` and `nextWork` arguments add workflow context; current project data is loaded through the service.

## Persistence model

`pendingRfcHandoff` is an optional additive field in the existing atomically replaced `project-state.json`. Legacy v0.9 files without it remain valid and are not rewritten by reads. When present, schema version `1.0` is required; unsupported future versions are rejected. Submission changes only this field and preserves project status, lifecycle, readiness, and planning state.

`pendingImplementationWorkOrder` and `implementationExecutionRecord` are optional v0.12 additive fields in the same atomic state document. Legacy v0.11 state loads without migration. A persisted orphaned `RUNNING` record is exposed as `BLOCKED` with a recovery warning and is never retried automatically. Work Order/result IDs derive from RFC plus baseline commit; no UUID or timestamp drives orchestration identity.

`RfcExecutionContext` is a non-persistent read model. Because Project State does not store RFC title, goal, detailed scope, acceptance criteria, next RFC, or repository baseline, Context returns conservative empty/optional values and a warning instead of inventing data. Default alpha criteria cover build, focused tests, regression, smoke, scope, and review in stable order.

`RfcHandoff` is the structured source for implementation, verification, alpha review, limitations, architecture/API changes, Git reporting, and planning updates. Markdown is rendered from it and never parsed back into official state. Main Planning Markdown includes a Pending Handoff when present.

Runtime state is stored in `project-state.json`, resolved relative to the process working directory. The repository parses and validates the complete status shape on reads and writes. Saves serialize formatted JSON to `project-state.tmp.json` and rename it over `project-state.json`. The runtime state and temporary state files are not source artifacts and must not be committed.

The server expects `project-state.json` to exist and contain string values for `project`, `phase`, `currentRfc`, and `release`, plus a string array named `completedRfcs`. The additive `releaseReadiness` object stores all eight readiness fields. Legacy files without the object, and objects with missing individual fields, load with deterministic `pending` defaults in memory. Reads do not rewrite legacy files; the complete readiness object is serialized on the next normal save. Invalid readiness values are rejected.

## RFC Lifecycle History

`lifecycleHistory` is an additive, append-only array stored alongside project status. Legacy files without it load with an empty history and are not rewritten merely by reading. Each immutable event contains `id`, `type`, `rfc`, `phase`, `release`, and an ISO `timestamp`; event types are `started`, `completed`, `planningSynced`, and `rollbackCompleted`. Rollback events additionally contain `fromRfc` so an audit reader can see both sides of the transition. Existing events remain valid without that optional field.

The Service assigns deterministic sequence IDs such as `rfc-event-000001` and preserves array order as event order. `markCurrentRfcCompleted` appends `completed`, `startNextRfc` appends `started`, and the explicit `generateMainPlanningSync` Tool appends `planningSynced`. Each event is included in the same complete state save as its operation. The legacy `completeCurrentRfc` shortcut remains behaviorally unchanged and does not synthesize history events.

Main Planning Markdown includes an RFC Lifecycle Timeline derived from persisted events. Rollback entries render as `Rolled back RFC-0048 → RFC-0047`. The Service exposes full and latest-event queries, and the dashboard returns the full history including compensating rollback events.

### Project-state rollback

`rollbackCurrentRfc` is deliberately limited to one project-management transition. The Service validates every event and replays the canonical append order to identify the active RFC immediately before the latest `started` transition. It never guesses by subtracting an RFC number. The current persisted RFC must agree with the replayed active RFC, the latest transition must have activated it, and ambiguous, missing, malformed, or repeated-rollback evidence is rejected before any save.

On success, the prior event context restores the RFC, phase, and release; completed RFCs remain historical completion records; all eight Release Readiness fields reset to `pending`; and one `rollbackCompleted` compensating event is appended in the same single save. Earlier history is never deleted, edited, reordered, or normalized. Lifecycle Guidance remains useful after rollback even when the restored RFC is in completed history. Arbitrary targets, multi-step rollback, generalized event replay, source restoration, and Git rollback are future work.

### Rollback eligibility and Preview

`previewCurrentRfcRollback` and `rollbackCurrentRfc` use the same internal Service resolver, so eligibility cannot be weaker than execution. Eligible output predicts the current and restored RFC, restored phase and release, and the deterministic all-`pending` Release Readiness state. Ineligible output contains the resolver's stable blocking reason. Normal business ineligibility is structured output; invalid persisted JSON may still be rejected by the Repository before domain preview calculation.

Preview data is derived each time and is never added to `project-state.json`. Preview performs no save, appends no lifecycle event, consumes no event ID, and does not mutate readiness or the loaded status. Dashboard, Main Planning structured output and Markdown, and the read-only planning Prompt expose it additively. The explicit Main Planning Tool retains its established single `planningSynced` event; Preview itself creates no additional event. Rollback remains exceptional corrective behavior and does not replace Lifecycle Guidance's forward-workflow recommendation. Multi-step Preview and an operator confirmation workflow remain future work.

## Commands

Run commands from `tools/docpilot-mcp`.

Clean the compiled output:

```sh
npm run clean
```

Run strict TypeScript type-checking without emitting files:

```sh
npm run typecheck
```

Build the production JavaScript runtime:

```sh
npm run build
```

Run the compiled MCP server:

```sh
npm run start:mcp
```

Run the TypeScript development server:

```sh
npm run dev
```

Run MCP Inspector against the compiled runtime:

```sh
npm run inspector
```

Run MCP Inspector against the TypeScript development runtime:

```sh
npm run inspector:dev
```

Run tests once:

```sh
npm test
```

Run tests in watch mode:

```sh
npm run test:watch
```


## Automated tests

Vitest is the single test framework. Tests live under `tests/`, grouped into `repository`, `service`, `resource`, `tool`, `server`, `orchestration`, and shared `support` directories. Persistence tests create isolated directories through the operating-system temporary-directory APIs and remove them after each test; they never use the runtime `project-state.json`.

The v0.12.2 verification baseline is 23 passing test files and 178 passing tests. The suites cover repository serialization and backward compatibility, RFC lifecycle and rollback, Project Control Context and Completion Readiness, dashboard Resources, Release Readiness, Planning Synchronization, MCP registration, controlled process execution, repository locking, Git evidence, implementation orchestration persistence, timeout and cancellation handling, and commit-candidate recovery behavior.

Coverage reporting is not configured. The test suite does not yet provide exhaustive coverage of every legacy Tool error branch, every planning-output detail, or the production STDIO entry point as a spawned child process. Inspector and Codex STDIO integration are therefore verified as explicit runtime checks in addition to the automated test suite.

## Git hygiene

The following generated, runtime-specific, or machine-specific paths must not be committed:

```text
dist/
node_modules/
project-state.json
project-state.tmp.json
package.json.backup
.codex/config.toml
.docpilot/
```

The v0.12.2 production-runtime source changes are represented by:

```text
package.json
package-lock.json
tsconfig.build.json
src/server.ts
README.md
```

A machine-specific `.codex/config.toml` commonly contains absolute local paths. Keep it outside version control or provide a separately sanitized example if a shared template is needed.


## Current limitations

- Completion Readiness still evaluates an official Pending Handoff, while controlled execution independently collects process and Git evidence before generating that Handoff.
- Project Control does not acknowledge, consume, or archive Pending Handoffs.
- Pending Handoff supports one current-RFC item with reject-on-duplicate behavior; consumption, archival history, approval, and automatic RFC advancement are not implemented.
- Local Codex execution depends on a compatible installed CLI. No cloud Worker, retry queue, push, PR, merge, tag, release, or CI/CD execution is implemented.
- Lock liveness relies on operating-system process probes. A live PID whose complete cross-process start identity cannot be proven is conservatively retained rather than automatically recovered.
- Recovery is diagnostic and blocking. v0.12.2 does not add retry, consume, archive, or automatic workspace-cleanup operations.
- Detailed RFC definitions are not persisted, so Context warns about unavailable title, goal, detailed scope, acceptance criteria, next-RFC, and repository-baseline metadata.
- Release Readiness is manually updated; automated build, test, and release-system integrations are not implemented.
- Planning status detects Documentation Sync mismatch but does not automatically update readiness or enforce a release gate.
- The legacy `completeCurrentRfc` operation remains supported, so clients can still bypass the preferred split lifecycle.
- Lifecycle guidance is derived from persisted status and validated lifecycle history but does not infer undocumented transition provenance.
- Rollback supports only the immediately previous eligible lifecycle transition; arbitrary targets, consecutive rollback, and generalized replay are not implemented.
- Documentation operations are not implemented.
- Project-state persistence remains a single local JSON document. Orchestration has a repository-scoped execution lock, but general multi-writer state coordination, schema migration, and remote persistence are not implemented.
- The state file is not initialized automatically and errors are returned when it is missing or invalid.
- The server exposes only the STDIO transport and has no server-side authentication or multi-project selection.
- Automated tests do not yet spawn and exercise the compiled STDIO entry point as a child process or provide coverage metrics.

라이브러리
/
README.md
# DocPilot MCP

DocPilot MCP is the MCP control plane for DocPilot project status, RFC workflow, documentation, and release operations. This package establishes the initial product foundation while retaining the behavior of the existing implementation.

## Product scope

The current product reads and updates a compact DocPilot project status and persistent Release Readiness, supports the current RFC completion workflow, reports completed RFCs, provides a consolidated read-only project dashboard, and generates a Main Planning synchronization artifact. Documentation operations remain part of the intended control-plane scope but are not implemented yet.

## Current architecture

The TypeScript server uses MCP over standard input/output. `src/index.ts` starts the transport, and `src/server.ts` creates the repository and service before registering all Tools, Resources, and Prompts. The implementation is organized into model, repository, service, tool, resource, and prompt layers.

Tools call `ProjectStatusService`; they do not access persistence directly. The service owns business validation and workflow rules. `ProjectStateRepository` owns JSON loading, validation at the persistence boundary, serialization, and atomic replacement of the state file. The project status and dashboard Resources and the planning Prompt read through the service.

See [docs/architecture.md](docs/architecture.md) for the detailed architecture and dependency rules.


## Production runtime and Codex integration

DocPilot MCP v0.12.2 supports a compiled JavaScript runtime for stable integration with Codex and other MCP clients.

The development runtime executes TypeScript directly through `tsx`. The production runtime compiles the TypeScript source into `dist/` and executes the generated JavaScript with Node.js.

### Build and runtime model

Run commands from `tools/docpilot-mcp`.

The production build performs a clean, strict type-check, and JavaScript emit:

```sh
npm run build
```

A successful build creates files including:

```text
dist/index.js
dist/index.js.map
dist/server.js
dist/server.js.map
```

The production package entry point is `dist/index.js`.

Run the compiled MCP server:

```sh
npm run start:mcp
```

Run MCP Inspector against the compiled runtime:

```sh
npm run inspector
```

For TypeScript development, use:

```sh
npm run dev
npm run inspector:dev
```

### STDIO transport requirements

DocPilot MCP uses MCP over standard input/output.

Standard output is reserved exclusively for MCP JSON-RPC communication. Application and diagnostic messages must not be written to standard output because non-protocol output can corrupt the MCP connection.

Use standard error for runtime diagnostics:

```ts
console.error("DocPilot MCP server started.");
```

Do not use ordinary standard-output logging in the STDIO server:

```ts
// Do not use this in the STDIO MCP runtime.
console.log("DocPilot MCP server started.");
```

### Runtime state initialization

`ProjectStateRepository` resolves the runtime state file from the process working directory:

```text
<working-directory>/project-state.json
```

The state file must exist before project status Tools, Resources, or Prompts are called. The server deliberately does not invent or automatically initialize project status.

A minimal valid state file is:

```json
{
  "project": "DocPilot",
  "phase": "Phase 1 - MVP / POC",
  "currentRfc": "RFC-0044",
  "release": "v0.5 MVP",
  "completedRfcs": []
}
```

When `releaseReadiness` is omitted, all eight readiness fields load as `pending`. When `lifecycleHistory` is omitted, it loads as an empty array. Reads do not rewrite the legacy or minimal file.

`project-state.json` and `project-state.tmp.json` are runtime artifacts and must not be committed.

### Codex MCP registration

A Codex project can register DocPilot MCP through a project-level `.codex/config.toml`.

Example Windows configuration:

```toml
[mcp_servers.docpilot]
command = 'C:\Program Files\nodejs\node.exe'
args = ['C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp\dist\index.js']
cwd = 'C:\WorkSpace\docpilot-core-integration\tools\docpilot-mcp'
enabled = true
startup_timeout_sec = 20
tool_timeout_sec = 1800
default_tools_approval_mode = 'prompt'
```

The paths above are examples and must be adjusted for the local installation. The configured `cwd` must point to the directory that contains `project-state.json`.

Verify registration from the project root:

```sh
codex mcp list
```

Start Codex and inspect active MCP servers:

```sh
codex
```

```text
/mcp
```

For a local STDIO server, an `Auth` value of `Unsupported` is expected because the server does not use HTTP or OAuth authentication.

### Read-only integration verification

A basic end-to-end verification is to ask Codex to call `getProjectStatus` while explicitly forbidding direct file or shell access:

```text
Call the DocPilot MCP getProjectStatus Tool and report the current project
status.

Do not read project-state.json directly.
Do not use shell commands to obtain the status.
Do not call any state-changing Tool.
```

Additional read-only checks can call the following Tools:

```text
getCurrentRfc
getDocPilotProjectControlContext
evaluateRfcCompletionReadiness
getPlanningSynchronizationStatus
getPendingRfcHandoff
getPendingImplementationWorkOrder
```

For a newly initialized RFC with no Pending Handoff or Pending Implementation Work Order, typical results are:

```text
Completion Readiness: NOT_READY
Planning Synchronization: neverSynced
Pending RFC Handoff: none
Pending Implementation Work Order: none
```

### Query zero-write verification

Read-only Tools must not change `project-state.json`.

Capture the SHA-256 hash before the query:

```powershell
$beforeHash = (
    Get-FileHash `
        .\project-state.json `
        -Algorithm SHA256
).Hash
```

Call one or more read-only Tools, then compare the hash:

```powershell
$afterHash = (
    Get-FileHash `
        .\project-state.json `
        -Algorithm SHA256
).Hash

Write-Host "Before: $beforeHash"
Write-Host "After : $afterHash"
Write-Host "Query zero-write: $($beforeHash -eq $afterHash)"
```

Expected result:

```text
Query zero-write: True
```

### v0.12.2 production verification baseline

The verified production-runtime baseline is:

```text
Build:                  PASS
Test files:             23 passed
Tests:                  178 passed
MCP Inspector:          PASS
Codex STDIO connection: PASS
Read-only zero-write:   PASS
```

The compiled entry point and server version can be checked with:

```powershell
Test-Path .\dist\index.js

Select-String `
    -Path .\dist\server.js `
    -Pattern 'version:\s*"[^"]+"'
```

Expected results:

```text
True
version: "0.12.2"
```

## Available Tools

### Project Control Boundary

- `getDocPilotProjectControlContext` accepts strict empty input and composes the official current project, lifecycle, RFC Context, Pending Handoff summary, Completion Readiness, Capability Manifest, policies, Planning Synchronization, Release Readiness, and explicit evidence limitations. It is read-only.
- `evaluateRfcCompletionReadiness` optionally accepts the current `rfcId` and evaluates deterministic Alpha Gates without writing state or executing submitted commands.

Project Control Query Boundary consists of `loadRfcContext`, `getPendingRfcHandoff`, `getDocPilotProjectControlContext`, `evaluateRfcCompletionReadiness`, and `getPendingImplementationWorkOrder`. Commands are `submitRfcHandoff`, `prepareImplementationWorkOrder`, `executePendingImplementationWorkOrder`, and `createImplementationCommit`. Acknowledge, consume, archive, history, cloud workers, push/PR/release automation, and lifecycle advancement remain outside the boundary.

Completion Readiness uses fixed ordered checks for identity, Handoff presence/schema/RFC, implementation, build, tests, regression, smoke, scope, alpha review, known limitations, and Git push policy. Results are `NOT_READY`, `BLOCKED`, `READY_WITH_WARNINGS`, or `READY`. Submitted evidence is structurally validated but MCP does not independently execute commands or verify Git diffs. Missing allowed paths are disclosed on the Scope check.

The Capability Manifest reports deterministic Work Order generation, controlled local execution, Alpha-gated commit creation, and a push-approval boundary as supported. `git.pushApproval=true` describes the boundary only: no push implementation exists. Cloud execution, push, PR/release automation, and automatic lifecycle completion/advance remain false.

### Controlled Implementation Orchestration

- `prepareImplementationWorkOrder` fixes the current RFC, Git root/branch/HEAD baseline, approved plan, normalized scope, controlled verification commands, result contract, and conservative Git policy. One restart-safe Pending Work Order is allowed per current RFC.
- `getPendingImplementationWorkOrder` is a strict, deterministic, zero-write query.
- `executePendingImplementationWorkOrder` accepts optional `{ "dryRun": true }`. Dry-run returns ordered preflight checks plus the deterministic Codex prompt/command without executing or saving. A real run requires a clean working tree, fixed HEAD, valid in-repository paths, an available Codex executable, no Pending Handoff, and valid controlled commands. It records RUNNING before execution and a terminal result afterward.
- `createImplementationCommit` accepts `{ "message": "..." }` only after MCP Alpha passes and the Work Order permits commits. It stages explicit authorized evidence paths, runs cached diff checks, creates one non-amended commit, and returns `PENDING_APPROVAL`. It never pushes.

`ControlledCommand` separates executable and arguments, fixes an in-repository working directory, requires a timeout, runs without a shell, passes only allowlisted environment variables, limits output, and masks common secret forms. Verification order is targeted tests, module tests, build, regression, then smoke; a required failure skips subsequent commands. Git evidence uses porcelain status and records branch, baseline/HEAD, changed, created, deleted, renamed, staged, and untracked paths. Diff validation blocks forbidden/out-of-scope paths, unauthorized dependency/build configuration changes, unapproved public-API candidates, and disallowed untracked files.

The Worker JSON is treated as a claim. MCP independently validates its schema/RFC/Work Order identity, actual Git evidence, verification results, policy review, and twelve ordered Alpha Gates. Only `PASSED` or `PASSED_WITH_LIMITATIONS` creates the official Pending Handoff; failure remains in the Execution Record. Work Order preparation, execution, Handoff generation, and commit never complete or advance an RFC and never mark Planning synchronized.

### v0.12.1 orchestration stabilization

Real execution uses an atomic repository-scoped lock at `.docpilot/orchestration-lock`. The lock directory is acquired with one atomic filesystem operation and contains validated schema, canonical repository identity, Work Order/RFC, PID, process-start identity, acquisition time, and hostname. A live or uncertain owner is never removed. Only a demonstrably dead PID is recovered, and malformed metadata requires manual recovery. Dry-run and read-only queries never create the lock or runtime directory.

A persisted `RUNNING` record remains evidence rather than being silently rewritten. Pending Work Order queries add optional recovery diagnostics based on current lock evidence; execution remains blocked and is never automatically resumed. Missing Work Order/Execution counterparts are rejected as inconsistent state. Existing v0.10-v0.12 state remains loadable without migration.

Process termination distinguishes timeout from cancellation, records exit signal and termination stages, bounds buffers before concatenation, removes listeners, and attempts process-tree termination (POSIX process group; Windows `taskkill /T` plus direct termination). Windows command wrappers retain strict metacharacter rejection. Canonical path resolution follows existing parents to prevent symlink escape for not-yet-created result paths.

Git evidence uses NUL-delimited porcelain and preserves staged, unstaged, untracked, deletion, rename, and type-change categories. Runtime-only `.docpilot` paths do not make Preflight dirty, but cannot authorize product changes. Execution captures evidence after lock acquisition and before Worker start, then blocks if HEAD or non-runtime state changed after Preflight.

Commit creation rejects every pre-existing staged path, rechecks expected HEAD, stages explicit candidate files only, verifies the exact cached path set and cached diff, and validates an actual new commit object. Its own staged paths are restored if commit creation fails. Restart evidence with an unexpected HEAD is blocked as a possible already-created commit; no duplicate commit or push is attempted.

### RFC Context and Handoff

- `loadRfcContext` optionally accepts the current `rfcId` and returns deterministic official project context, operating rules, alpha criteria, guidance, synchronization, readiness, and warnings for unavailable RFC metadata. It is read-only.
- `submitRfcHandoff` accepts `{ "handoff": RfcHandoff }`, validates schema version `1.0` and current-RFC ownership, normalizes file lists, and atomically stores one Pending Handoff. It never advances lifecycle, synchronizes Planning, commits, or pushes.
- `getPendingRfcHandoff` accepts strict empty input and returns the current Pending Handoff plus deterministic Markdown, or a normal `found: false` result.

Duplicate submission is rejected rather than silently replacing review evidence. No Handoff history, Approval Registry, or Evidence Registry is created.

### Project Status

- `getProjectStatus` returns the complete current project status.
- `getCurrentRfc` returns the current RFC with its phase and release context.
- `updateProjectStatus` updates one or more of `phase`, `release`, and `currentRfc` after service validation.
- `listCompletedRfcs` returns completed RFC identifiers, their count, and current project context.
- `updateReleaseReadiness` accepts an `updates` object containing one or more readiness fields and returns the updated project status. Each value must be `pending`, `passed`, or `failed`; omitted fields retain their prior values.

### RFC Workflow

- `markCurrentRfcCompleted` accepts strict empty input (`{}`), marks the current RFC completed, and leaves the current RFC, phase, release, and Release Readiness unchanged.
- `startNextRfc` starts an explicitly supplied later RFC, preserves completed history, optionally updates `phase` and `release`, and resets all Release Readiness fields to `pending`. Input is `{ "nextRfc": "RFC-0045", "phase"?: "...", "release"?: "..." }`; no other fields are accepted.
- `rollbackCurrentRfc` accepts strict empty input (`{}`) and restores the immediately previous active RFC from lifecycle-history evidence. It resets Release Readiness and appends an audit event; it does not roll back Git, source files, branches, or commits.
- `previewCurrentRfcRollback` accepts strict empty input (`{}`) and reports whether that same one-step rollback is eligible, including the restored RFC, phase, release, and all-pending readiness state. It is read-only and returns a stable blocking reason when ineligible.
- `completeCurrentRfc` is the legacy shortcut that records the current RFC and advances immediately to its required `nextRfc`.

The preferred lifecycle is `markCurrentRfcCompleted` → `startNextRfc` → `generateMainPlanningSync`. Marking validates the current RFC against exact `RFC-[0-9]{4}` syntax, explicitly rejects an already completed RFC, numerically orders and deduplicates completed history, and performs one Repository save. It does not reset readiness or invoke planning.

`startNextRfc` requires the exact `RFC-[0-9]{4}` format with no surrounding whitespace. The next RFC must differ from and be numerically greater than the current RFC, must not already be completed, and the current RFC must already appear in completed history. Optional phase and release values must be non-empty. The Service validates each transition and sends one complete state to the Repository for persistence.

For backward compatibility, `completeCurrentRfc` retains its existing input, response, and combined complete-and-advance behavior. Completed history produced by either completion method is canonicalized into numeric RFC order without duplicates. No workflow automatically generates or writes Main Planning sync output; planning remains a separate Tool or Prompt operation.

### RFC Lifecycle Guidance

Lifecycle guidance is derived from the current persisted project status and is never stored. It reports one of three stable states:

- `in_progress` recommends `markCurrentRfcCompleted` when the current RFC is absent from completed history.
- `completed_waiting_next` recommends `startNextRfc` when the current RFC is already completed.
- `inconsistent` recommends `manualReview` when the current RFC or completed history contains malformed identifiers or duplicate completed entries.

The Service owns these decisions and deterministic reason strings. The `generateMainPlanningSync` Tool exposes guidance in structured content and in its Markdown, the Prompt appends the same RFC Lifecycle section, and `docpilot://project/dashboard` includes an additive `lifecycleGuidance` object. Guidance derivation, dashboard reads, and Prompt generation do not persist state or execute a recommended Tool. The explicit planning Tool records its own lifecycle-history event as described below.

Because `completeCurrentRfc` advances directly to a new current RFC without storing transition metadata, its result is structurally identical to ordinary in-progress work. Guidance therefore recommends `markCurrentRfcCompleted` for that new current RFC and does not infer how it became active.

### Planning

- `generateMainPlanningSync` generates a Markdown Main Planning status summary and structured status data.
- `getPlanningSynchronizationStatus` accepts strict empty input (`{}`) and read-only evaluates whether lifecycle changes are covered by the latest `planningSynced` event.

### Planning Synchronization Status

Planning status is derived from validated lifecycle history in persisted append order; timestamps are never compared. `started`, `completed`, and `rollbackCompleted` are planning-relevant changes. A later `planningSynced` covers those changes without itself making Planning stale.

- `neverSynced`: no valid `planningSynced` event exists; the recommended action is `generateMainPlanningSync`.
- `current`: no relevant transition occurs after the latest sync; the recommended action is `none`.
- `stale`: a relevant transition occurs after the latest sync; the recommended action is `generateMainPlanningSync`. Rollback uses a dedicated stable reason.

Evaluation creates no timestamp, performs no save, appends no event, consumes no event ID, and does not mutate Project Status. The derived expected `documentationSync` is `passed` for `current` and `pending` otherwise. The result reports whether persisted readiness agrees and supplies a deterministic mismatch reason; it never corrects readiness automatically. Lifecycle Guidance adds synchronization state and required status separately while retaining its primary RFC workflow action.

The legacy combined completion workflow has no transition event. For compatibility, an explicit `planningSynced` event matching persisted `currentRfc` may re-anchor planning evaluation after that workflow; rollback resolution remains strict. Future work may automatically maintain Documentation Sync and use this status in release gates.

## Available Resources

- `project-status` at `docpilot://project/status` returns the current project status as `application/json`.
- `project-dashboard` at `docpilot://project/dashboard` returns a consolidated read-only dashboard as `application/json`. Its fields are `project`, `phase`, `currentRfc`, `release`, `completedCount`, `completedRfcs`, and `releaseReadiness`. Current values, including persisted readiness, come from `ProjectStatusService`; `completedCount` is derived from the ordered `completedRfcs` array.

The `releaseReadiness` object contains `coreBuild`, `coreTests`, `cli`, `incremental`, `reviewWorkflow`, `architectureSamplesValidation`, `documentationSync`, and `releaseCandidate`. Each field is persisted as `pending`, `passed`, or `failed`.

The dashboard also exposes the append-only `lifecycleHistory` array, derived `rollbackPreview`, and complete `planningSynchronization` status. Repeated dashboard reads do not save state or append events.

## Available Prompts

- `generateMainPlanningSync` creates the existing Main Planning synchronization prompt. Optional `completedWork` and `nextWork` arguments add workflow context; current project data is loaded through the service.

## Persistence model

`pendingRfcHandoff` is an optional additive field in the existing atomically replaced `project-state.json`. Legacy v0.9 files without it remain valid and are not rewritten by reads. When present, schema version `1.0` is required; unsupported future versions are rejected. Submission changes only this field and preserves project status, lifecycle, readiness, and planning state.

`pendingImplementationWorkOrder` and `implementationExecutionRecord` are optional v0.12 additive fields in the same atomic state document. Legacy v0.11 state loads without migration. A persisted orphaned `RUNNING` record is exposed as `BLOCKED` with a recovery warning and is never retried automatically. Work Order/result IDs derive from RFC plus baseline commit; no UUID or timestamp drives orchestration identity.

`RfcExecutionContext` is a non-persistent read model. Because Project State does not store RFC title, goal, detailed scope, acceptance criteria, next RFC, or repository baseline, Context returns conservative empty/optional values and a warning instead of inventing data. Default alpha criteria cover build, focused tests, regression, smoke, scope, and review in stable order.

`RfcHandoff` is the structured source for implementation, verification, alpha review, limitations, architecture/API changes, Git reporting, and planning updates. Markdown is rendered from it and never parsed back into official state. Main Planning Markdown includes a Pending Handoff when present.

Runtime state is stored in `project-state.json`, resolved relative to the process working directory. The repository parses and validates the complete status shape on reads and writes. Saves serialize formatted JSON to `project-state.tmp.json` and rename it over `project-state.json`. The runtime state and temporary state files are not source artifacts and must not be committed.

The server expects `project-state.json` to exist and contain string values for `project`, `phase`, `currentRfc`, and `release`, plus a string array named `completedRfcs`. The additive `releaseReadiness` object stores all eight readiness fields. Legacy files without the object, and objects with missing individual fields, load with deterministic `pending` defaults in memory. Reads do not rewrite legacy files; the complete readiness object is serialized on the next normal save. Invalid readiness values are rejected.

## RFC Lifecycle History

`lifecycleHistory` is an additive, append-only array stored alongside project status. Legacy files without it load with an empty history and are not rewritten merely by reading. Each immutable event contains `id`, `type`, `rfc`, `phase`, `release`, and an ISO `timestamp`; event types are `started`, `completed`, `planningSynced`, and `rollbackCompleted`. Rollback events additionally contain `fromRfc` so an audit reader can see both sides of the transition. Existing events remain valid without that optional field.

The Service assigns deterministic sequence IDs such as `rfc-event-000001` and preserves array order as event order. `markCurrentRfcCompleted` appends `completed`, `startNextRfc` appends `started`, and the explicit `generateMainPlanningSync` Tool appends `planningSynced`. Each event is included in the same complete state save as its operation. The legacy `completeCurrentRfc` shortcut remains behaviorally unchanged and does not synthesize history events.

Main Planning Markdown includes an RFC Lifecycle Timeline derived from persisted events. Rollback entries render as `Rolled back RFC-0048 → RFC-0047`. The Service exposes full and latest-event queries, and the dashboard returns the full history including compensating rollback events.

### Project-state rollback

`rollbackCurrentRfc` is deliberately limited to one project-management transition. The Service validates every event and replays the canonical append order to identify the active RFC immediately before the latest `started` transition. It never guesses by subtracting an RFC number. The current persisted RFC must agree with the replayed active RFC, the latest transition must have activated it, and ambiguous, missing, malformed, or repeated-rollback evidence is rejected before any save.

On success, the prior event context restores the RFC, phase, and release; completed RFCs remain historical completion records; all eight Release Readiness fields reset to `pending`; and one `rollbackCompleted` compensating event is appended in the same single save. Earlier history is never deleted, edited, reordered, or normalized. Lifecycle Guidance remains useful after rollback even when the restored RFC is in completed history. Arbitrary targets, multi-step rollback, generalized event replay, source restoration, and Git rollback are future work.

### Rollback eligibility and Preview

`previewCurrentRfcRollback` and `rollbackCurrentRfc` use the same internal Service resolver, so eligibility cannot be weaker than execution. Eligible output predicts the current and restored RFC, restored phase and release, and the deterministic all-`pending` Release Readiness state. Ineligible output contains the resolver's stable blocking reason. Normal business ineligibility is structured output; invalid persisted JSON may still be rejected by the Repository before domain preview calculation.

Preview data is derived each time and is never added to `project-state.json`. Preview performs no save, appends no lifecycle event, consumes no event ID, and does not mutate readiness or the loaded status. Dashboard, Main Planning structured output and Markdown, and the read-only planning Prompt expose it additively. The explicit Main Planning Tool retains its established single `planningSynced` event; Preview itself creates no additional event. Rollback remains exceptional corrective behavior and does not replace Lifecycle Guidance's forward-workflow recommendation. Multi-step Preview and an operator confirmation workflow remain future work.

## Commands

Run commands from `tools/docpilot-mcp`.

Clean the compiled output:

```sh
npm run clean
```

Run strict TypeScript type-checking without emitting files:

```sh
npm run typecheck
```

Build the production JavaScript runtime:

```sh
npm run build
```

Run the compiled MCP server:

```sh
npm run start:mcp
```

Run the TypeScript development server:

```sh
npm run dev
```

Run MCP Inspector against the compiled runtime:

```sh
npm run inspector
```

Run MCP Inspector against the TypeScript development runtime:

```sh
npm run inspector:dev
```

Run tests once:

```sh
npm test
```

Run tests in watch mode:

```sh
npm run test:watch
```


## Automated tests

Vitest is the single test framework. Tests live under `tests/`, grouped into `repository`, `service`, `resource`, `tool`, `server`, `orchestration`, and shared `support` directories. Persistence tests create isolated directories through the operating-system temporary-directory APIs and remove them after each test; they never use the runtime `project-state.json`.

The v0.12.2 verification baseline is 23 passing test files and 178 passing tests. The suites cover repository serialization and backward compatibility, RFC lifecycle and rollback, Project Control Context and Completion Readiness, dashboard Resources, Release Readiness, Planning Synchronization, MCP registration, controlled process execution, repository locking, Git evidence, implementation orchestration persistence, timeout and cancellation handling, and commit-candidate recovery behavior.

Coverage reporting is not configured. The test suite does not yet provide exhaustive coverage of every legacy Tool error branch, every planning-output detail, or the production STDIO entry point as a spawned child process. Inspector and Codex STDIO integration are therefore verified as explicit runtime checks in addition to the automated test suite.

## Git hygiene

The following generated, runtime-specific, or machine-specific paths must not be committed:

```text
dist/
node_modules/
project-state.json
project-state.tmp.json
package.json.backup
.codex/config.toml
.docpilot/
```

The v0.12.2 production-runtime source changes are represented by:

```text
package.json
package-lock.json
tsconfig.build.json
src/server.ts
README.md
```

A machine-specific `.codex/config.toml` commonly contains absolute local paths. Keep it outside version control or provide a separately sanitized example if a shared template is needed.


## Current limitations

- Completion Readiness still evaluates an official Pending Handoff, while controlled execution independently collects process and Git evidence before generating that Handoff.
- Project Control does not acknowledge, consume, or archive Pending Handoffs.
- Pending Handoff supports one current-RFC item with reject-on-duplicate behavior; consumption, archival history, approval, and automatic RFC advancement are not implemented.
- Local Codex execution depends on a compatible installed CLI. No cloud Worker, retry queue, push, PR, merge, tag, release, or CI/CD execution is implemented.
- Lock liveness relies on operating-system process probes. A live PID whose complete cross-process start identity cannot be proven is conservatively retained rather than automatically recovered.
- Recovery is diagnostic and blocking. v0.12.2 does not add retry, consume, archive, or automatic workspace-cleanup operations.
- Detailed RFC definitions are not persisted, so Context warns about unavailable title, goal, detailed scope, acceptance criteria, next-RFC, and repository-baseline metadata.
- Release Readiness is manually updated; automated build, test, and release-system integrations are not implemented.
- Planning status detects Documentation Sync mismatch but does not automatically update readiness or enforce a release gate.
- The legacy `completeCurrentRfc` operation remains supported, so clients can still bypass the preferred split lifecycle.
- Lifecycle guidance is derived from persisted status and validated lifecycle history but does not infer undocumented transition provenance.
- Rollback supports only the immediately previous eligible lifecycle transition; arbitrary targets, consecutive rollback, and generalized replay are not implemented.
- Documentation operations are not implemented.
- Project-state persistence remains a single local JSON document. Orchestration has a repository-scoped execution lock, but general multi-writer state coordination, schema migration, and remote persistence are not implemented.
- The state file is not initialized automatically and errors are returned when it is missing or invalid.
- The server exposes only the STDIO transport and has no server-side authentication or multi-project selection.
- Automated tests do not yet spawn and exercise the compiled STDIO entry point as a child process or provide coverage metrics.