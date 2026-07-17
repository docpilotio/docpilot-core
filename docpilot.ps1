param(
    [Parameter(Position = 0)]
    [ValidateSet("test", "ci", "release", "new", "tag", "publish", "help")]
    [string]$Command = "help",

    [Parameter(Position = 1)]
    [string]$Arg1,

    [Parameter(Position = 2)]
    [string]$Arg2
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Native([string]$File, [string[]]$Arguments) {
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $File $($Arguments -join ' ')"
    }
}

function Get-CurrentBranch {
    $branch = (git branch --show-current).Trim()
    if ([string]::IsNullOrWhiteSpace($branch)) {
        throw "현재 Git 브랜치를 확인할 수 없습니다."
    }
    return $branch
}

function Assert-CleanWorkingTree {
    $changes = git status --porcelain
    if ($changes) {
        throw "작업 트리에 커밋되지 않은 변경사항이 있습니다."
    }
}

function Run-Tests {
    Write-Step "전체 테스트 실행"
    Invoke-Native ".\gradlew.bat" @("clean", "test")
}

function New-RfcBranch([string]$Number, [string]$Name) {
    if ([string]::IsNullOrWhiteSpace($Number) -or [string]::IsNullOrWhiteSpace($Name)) {
        throw "사용법: .\docpilot.ps1 new 0020 document-service"
    }

    $branch = "feature/rfc-$Number-$Name"

    Write-Step "main 최신화"
    Invoke-Native "git" @("checkout", "main")
    Invoke-Native "git" @("pull", "origin", "main")

    Write-Step "새 브랜치 생성: $branch"
    Invoke-Native "git" @("checkout", "-b", $branch)

    Write-Host ""
    Write-Host "현재 브랜치: $branch" -ForegroundColor Green
}

function Release-Rfc {
    $featureBranch = Get-CurrentBranch

    if (-not $featureBranch.StartsWith("feature/")) {
        throw "현재 브랜치가 feature 브랜치가 아닙니다: $featureBranch"
    }

    Run-Tests

    $status = git status --porcelain
    if ($status) {
        $message = Read-Host "Commit message"
        if ([string]::IsNullOrWhiteSpace($message)) {
            throw "커밋 메시지는 비워둘 수 없습니다."
        }

        Write-Step "변경사항 커밋"
        Invoke-Native "git" @("add", ".")
        Invoke-Native "git" @("commit", "-m", $message)
    }
    else {
        Write-Host ""
        Write-Host "커밋할 변경사항이 없습니다. 기존 커밋을 머지합니다." -ForegroundColor Yellow
    }

    Write-Step "feature 브랜치 원격 Push"
    Invoke-Native "git" @("push", "-u", "origin", $featureBranch)

    Write-Step "main 최신화"
    Invoke-Native "git" @("checkout", "main")
    Invoke-Native "git" @("pull", "origin", "main")

    Write-Step "$featureBranch 를 main으로 머지"
    Invoke-Native "git" @("merge", "--no-ff", $featureBranch)

    Write-Step "main Push"
    Invoke-Native "git" @("push", "origin", "main")

    $delete = Read-Host "머지된 로컬 feature 브랜치를 삭제할까요? (y/N)"
    if ($delete -match "^[Yy]$") {
        Invoke-Native "git" @("branch", "-d", $featureBranch)
    }

    $next = Read-Host "새 RFC 브랜치를 바로 만들까요? (y/N)"
    if ($next -match "^[Yy]$") {
        $number = Read-Host "다음 RFC 번호 (예: 0019)"
        $name = Read-Host "브랜치 이름 (예: document-model)"
        New-RfcBranch $number $name
    }
    else {
        Write-Host ""
        Write-Host "완료: main 브랜치가 최신 상태입니다." -ForegroundColor Green
    }
}

function Create-Tag([string]$TagName) {
    if ([string]::IsNullOrWhiteSpace($TagName)) {
        $TagName = Read-Host "태그 이름 (예: v0.1.0 또는 rfc-0018)"
    }
    if ([string]::IsNullOrWhiteSpace($TagName)) {
        throw "태그 이름은 비워둘 수 없습니다."
    }

    Assert-CleanWorkingTree
    Invoke-Native "git" @("tag", "-a", $TagName, "-m", $TagName)
    Invoke-Native "git" @("push", "origin", $TagName)
}

function Publish-Placeholder {
    Write-Host ""
    Write-Host "publish 명령은 아직 배포 대상(Maven Central, GitHub Release 등)이 정해지지 않아 실행하지 않습니다." -ForegroundColor Yellow
}

switch ($Command) {
    "test" {
        Run-Tests
    }
    "ci" {
        Run-Tests
    }
    "release" {
        Release-Rfc
    }
    "new" {
        New-RfcBranch $Arg1 $Arg2
    }
    "tag" {
        Create-Tag $Arg1
    }
    "publish" {
        Publish-Placeholder
    }
    default {
        Write-Host @"

DocPilot helper

사용법:
  .\docpilot.ps1 test
  .\docpilot.ps1 ci
  .\docpilot.ps1 release
  .\docpilot.ps1 new 0020 document-service
  .\docpilot.ps1 tag rfc-0018
  .\docpilot.ps1 publish

release:
  현재 feature 브랜치를 테스트하고 커밋한 뒤 main에 머지하고 Push합니다.
  완료 후 다음 RFC 브랜치를 선택적으로 생성할 수 있습니다.

"@
    }
}
