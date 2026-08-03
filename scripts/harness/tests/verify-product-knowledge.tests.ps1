[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$script:FailureCount = 0
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\.."))
$verifyScript = Join-Path $projectRoot "scripts\harness\verify-product-knowledge.ps1"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("product-knowledge-tests-" + [guid]::NewGuid().ToString("N"))
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)

function Write-TestFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string]$RelativePath,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Content
    )

    $path = Join-Path $Root $RelativePath
    $parent = Split-Path -Parent $path
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        [void](New-Item -ItemType Directory -Path $parent -Force)
    }

    [System.IO.File]::WriteAllText($path, $Content, $utf8WithoutBom)
}

function Get-FeatureGroups {
    return [ordered]@{
        "onboarding-preference.md" = @(
            "1.1.1", "1.1.2", "1.1.3", "1.2.1", "1.2.2", "1.2.3", "1.3.1"
        )
        "place-selection.md" = @(
            "2.1.1", "2.1.2", "2.1.3", "2.2.1", "2.2.2", "2.2.3", "2.2.4",
            "2.3.1", "2.3.2"
        )
        "course-design.md" = @(
            "3.1.0", "3.1.1", "3.1.2", "3.2.1", "3.2.2", "3.3.1", "3.3.2", "3.3.3"
        )
        "exploration.md" = @(
            "4.1.1", "4.2.1", "4.2.2", "4.2.3", "4.2.4", "4.3.1", "4.3.2",
            "4.3.3", "4.3.4", "4.4.1", "4.4.2"
        )
        "travel-records.md" = @(
            "5.1.1", "5.1.2", "5.2.1", "5.2.2"
        )
        "common-policies.md" = @(
            "6.1.1", "6.1.2", "6.1.3", "6.1.4", "6.1.5", "6.3.1", "6.4.1",
            "6.5.1", "6.5.2", "6.5.3"
        )
    }
}

function New-ValidFixture {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $root = Join-Path $testRoot $Name
    [void](New-Item -ItemType Directory -Path $root -Force)

    $groups = Get-FeatureGroups
    $hubLinks = @()
    $mvpRows = @()

    foreach ($entry in $groups.GetEnumerator()) {
        $fileName = $entry.Key
        $featureLines = @(
            "# 시험 기능 영역",
            "",
            "[전체 명세](../feature-spec.md) · [MVP](../mvp.md) · [논의 필요](../open-questions.md)",
            ""
        )

        foreach ($id in $entry.Value) {
            $featureLines += "#### $id 기능 $id"
            $featureLines += ""
            $featureLines += "- 상세 동작"
            $featureLines += ""
            $mvpRows += "| ``$id`` | 기능 $id | 책임 | 일반 | 미구현 | 근거 |"
        }

        Write-TestFile $root "docs\product\features\$fileName" (
            ($featureLines -join [Environment]::NewLine) + [Environment]::NewLine
        )
        $hubLinks += "- [영역 $fileName](features/$fileName)"
    }

    $hubContent = @(
            "# 상세 기능 명세",
            "",
            "## 기능 영역",
            ""
        ) + $hubLinks + @(
            "",
            "[논의 필요](open-questions.md)",
            ""
        )
    Write-TestFile $root "docs\product\feature-spec.md" (
        $hubContent -join [Environment]::NewLine
    )

    $mvpContent = @(
            "# MVP",
            "",
            "| ID | 기능 | 책임 | 우선순위 | 상태 | 근거 |",
            "|---|---|---|---|---|---|"
        ) + $mvpRows + @(
            ""
        )
    Write-TestFile $root "docs\product\mvp.md" (
        $mvpContent -join [Environment]::NewLine
    )

    Write-TestFile $root "docs\product\open-questions.md" @'
# 논의 필요

현재 논의가 필요한 항목이다.
'@

    return $root
}

function Invoke-ProductVerifier {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepositoryRoot
    )

    $powerShell = Join-Path $PSHOME "powershell.exe"
    $lines = & $powerShell `
        -NoProfile `
        -ExecutionPolicy Bypass `
        -File $verifyScript `
        -RepositoryRoot $RepositoryRoot 2>&1
    $exitCode = $LASTEXITCODE

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($lines -join [Environment]::NewLine)
    }
}

function Assert-True {
    param(
        [Parameter(Mandatory = $true)]
        [bool]$Condition,
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Assert-RuleFailure {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Result,
        [Parameter(Mandatory = $true)]
        [string]$RuleId
    )

    Assert-True ($Result.ExitCode -eq 1) "실패 exit code가 1이 아니다: $($Result.ExitCode)"
    Assert-True ($Result.Output -match [regex]::Escape($RuleId)) (
        "출력에 rule ID가 없다: $RuleId`n$($Result.Output)"
    )
}

function Invoke-TestCase {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Test
    )

    try {
        & $Test
        Write-Output "[PASS] $Name"
    }
    catch {
        $script:FailureCount++
        Write-Output "[FAIL] $Name - $($_.Exception.Message)"
    }
}

try {
    [void](New-Item -ItemType Directory -Path $testRoot -Force)

    Invoke-TestCase "정상 제품 지식 베이스" {
        $root = New-ValidFixture "valid"
        $result = Invoke-ProductVerifier $root
        Assert-True ($result.ExitCode -eq 0) "정상 fixture가 실패했다.`n$($result.Output)"
    }

    Invoke-TestCase "소기능 ID 중복 거부" {
        $root = New-ValidFixture "duplicate-id"
        $path = Join-Path $root "docs\product\features\place-selection.md"
        [System.IO.File]::AppendAllText(
            $path,
            "#### 1.1.1 중복 기능`n",
            $utf8WithoutBom
        )
        Assert-RuleFailure (Invoke-ProductVerifier $root) "PRODUCT-ID-DUPLICATE"
    }

    Invoke-TestCase "소기능 ID 누락 거부" {
        $root = New-ValidFixture "missing-id"
        $path = Join-Path $root "docs\product\features\travel-records.md"
        $content = [System.IO.File]::ReadAllText($path, $utf8WithoutBom)
        $content = [regex]::Replace(
            $content,
            "(?ms)^#### 5\.2\.2 .+?\r?\n\r?\n- 상세 동작\r?\n?",
            ""
        )
        [System.IO.File]::WriteAllText($path, $content, $utf8WithoutBom)
        Assert-RuleFailure (Invoke-ProductVerifier $root) "PRODUCT-ID-COUNT"
    }

    Invoke-TestCase "기준선 밖 소기능 ID 추가 거부" {
        $root = New-ValidFixture "unexpected-id"
        $path = Join-Path $root "docs\product\features\common-policies.md"
        [System.IO.File]::AppendAllText(
            $path,
            "#### 7.1.1 기준선 밖 기능`n",
            $utf8WithoutBom
        )
        $result = Invoke-ProductVerifier $root
        Assert-RuleFailure $result "PRODUCT-ID-COUNT"
        Assert-True ($result.Output -match "추가: 7\.1\.1") (
            "추가 ID가 실패 원인에 표시되지 않았다.`n$($result.Output)"
        )
    }

    Invoke-TestCase "MVP ID 집합 불일치 거부" {
        $root = New-ValidFixture "mvp-mismatch"
        $path = Join-Path $root "docs\product\mvp.md"
        $content = [System.IO.File]::ReadAllText($path, $utf8WithoutBom)
        $content = [regex]::Replace($content, '(?m)^\| `6\.5\.3` .+\r?\n', "")
        [System.IO.File]::WriteAllText($path, $content, $utf8WithoutBom)
        Assert-RuleFailure (Invoke-ProductVerifier $root) "PRODUCT-ID-MVP"
    }

    Invoke-TestCase "대표 지도의 영역 링크 누락 거부" {
        $root = New-ValidFixture "hub-link"
        $path = Join-Path $root "docs\product\feature-spec.md"
        $content = [System.IO.File]::ReadAllText($path, $utf8WithoutBom)
        $content = [regex]::Replace(
            $content,
            "(?m)^- \[영역 exploration\.md\]\(features/exploration\.md\)\r?\n",
            ""
        )
        [System.IO.File]::WriteAllText($path, $content, $utf8WithoutBom)
        Assert-RuleFailure (Invoke-ProductVerifier $root) "PRODUCT-HUB-LINK"
    }

    Invoke-TestCase "존재하지 않는 상대 링크 거부" {
        $root = New-ValidFixture "broken-link"
        $path = Join-Path $root "docs\product\feature-spec.md"
        [System.IO.File]::AppendAllText($path, "[깨진 링크](missing.md)`n", $utf8WithoutBom)
        Assert-RuleFailure (Invoke-ProductVerifier $root) "PRODUCT-LINK-TARGET"
    }

    Invoke-TestCase "존재하지 않는 Markdown anchor 거부" {
        $root = New-ValidFixture "broken-anchor"
        $path = Join-Path $root "docs\product\feature-spec.md"
        [System.IO.File]::AppendAllText(
            $path,
            "[깨진 anchor](features/course-design.md#없는-anchor)`n",
            $utf8WithoutBom
        )
        Assert-RuleFailure (Invoke-ProductVerifier $root) "PRODUCT-LINK-ANCHOR"
    }

    Invoke-TestCase "이전 단일 문서 상세 anchor 거부" {
        $root = New-ValidFixture "legacy-anchor"
        $path = Join-Path $root "docs\product\mvp.md"
        [System.IO.File]::AppendAllText(
            $path,
            "[이전 참조](feature-spec.md#논의-필요)`n",
            $utf8WithoutBom
        )
        Assert-RuleFailure (Invoke-ProductVerifier $root) "PRODUCT-LEGACY-ANCHOR"
    }
}
finally {
    if (Test-Path -LiteralPath $testRoot -PathType Container) {
        Remove-Item -LiteralPath $testRoot -Recurse -Force
    }
}

if ($script:FailureCount -gt 0) {
    Write-Output "제품 지식 베이스 회귀 검사 실패: ${script:FailureCount}개"
    exit 1
}

Write-Output "제품 지식 베이스 회귀 검사 통과."
exit 0
