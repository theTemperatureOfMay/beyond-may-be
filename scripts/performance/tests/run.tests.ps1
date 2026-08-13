[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$script:FailureCount = 0
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\.."))
$runner = Join-Path $projectRoot "scripts\performance\run.ps1"
$composeFile = Join-Path $projectRoot "docker-compose.performance.yml"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("beyond-performance-tests-" + [guid]::NewGuid().ToString("N"))
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)

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

function Invoke-TestCase {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Body
    )

    try {
        & $Body
        Write-Output "[PASS] $Name"
    }
    catch {
        $script:FailureCount++
        Write-Output "[FAIL] $Name"
        Write-Output $_.Exception.Message
    }
}

function Invoke-Runner {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [Parameter()]
        [int]$FakeK6ExitCode = 0
    )

    $runRoot = Join-Path $testRoot ([guid]::NewGuid().ToString("N"))
    $fakeBin = Join-Path $runRoot "fake-bin"
    $dockerLog = Join-Path $runRoot "docker.log"
    [void](New-Item -ItemType Directory -Path $fakeBin -Force)
    [System.IO.File]::WriteAllText(
        (Join-Path $fakeBin "docker.cmd"),
        "@echo off`r`necho %*>>`"%PERFORMANCE_FAKE_DOCKER_LOG%`"`r`nif defined PERFORMANCE_DB_PASSWORD echo PERFORMANCE_DB_PASSWORD_SET>>`"%PERFORMANCE_FAKE_DOCKER_LOG%`"`r`necho %* | %SystemRoot%\System32\findstr.exe /C:`"signup-smoke.js`" >nul`r`nif not errorlevel 1 exit /b %PERFORMANCE_FAKE_K6_EXIT_CODE%`r`nexit /b 0`r`n",
        $utf8WithoutBom
    )

    $previousPath = $env:PATH
    $previousDockerLog = $env:PERFORMANCE_FAKE_DOCKER_LOG
    $previousK6ExitCode = $env:PERFORMANCE_FAKE_K6_EXIT_CODE
    $previousDbPassword = $env:PERFORMANCE_DB_PASSWORD
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $env:PATH = $fakeBin + [System.IO.Path]::PathSeparator + $previousPath
        $env:PERFORMANCE_FAKE_DOCKER_LOG = $dockerLog
        $env:PERFORMANCE_FAKE_K6_EXIT_CODE = $FakeK6ExitCode.ToString()
        Remove-Item Env:PERFORMANCE_DB_PASSWORD -ErrorAction SilentlyContinue
        $ErrorActionPreference = "Continue"
        $output = & "$PSHOME\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File $runner @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
        $env:PATH = $previousPath
        $env:PERFORMANCE_FAKE_DOCKER_LOG = $previousDockerLog
        $env:PERFORMANCE_FAKE_K6_EXIT_CODE = $previousK6ExitCode
        $env:PERFORMANCE_DB_PASSWORD = $previousDbPassword
    }

    $dockerCalls = if (Test-Path -LiteralPath $dockerLog -PathType Leaf) {
        [System.IO.File]::ReadAllText($dockerLog)
    }
    else {
        ""
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output -join [Environment]::NewLine)
        DockerCalls = $dockerCalls
    }
}

try {
    [void](New-Item -ItemType Directory -Path $testRoot -Force)

    Invoke-TestCase "비로컬 대상은 Docker 실행 전에 거부한다" {
        $result = Invoke-Runner @(
            "-Profile", "smoke",
            "-TargetBaseUrl", "https://api.example.com"
        )

        Assert-True ($result.ExitCode -ne 0) "비로컬 대상 실행이 성공하면 안 된다."
        Assert-True ($result.Output -match "로컬|허용") "거부 이유가 출력되지 않았다.`n$($result.Output)"
        Assert-True ([string]::IsNullOrWhiteSpace($result.DockerCalls)) "거부 전에 Docker가 실행됐다.`n$($result.DockerCalls)"
    }

    Invoke-TestCase "성능 DB 비밀번호는 저장소에 고정하지 않고 실행할 때 주입한다" {
        $composeContent = [System.IO.File]::ReadAllText($composeFile)
        $result = Invoke-Runner @("-Profile", "smoke")

        Assert-True (-not $composeContent.Contains("performance_password")) "고정된 성능 DB 비밀번호가 Compose에 남아 있다."
        Assert-True ($composeContent -match '\$\{PERFORMANCE_DB_PASSWORD:-\}') "Compose가 실행 시 비밀번호를 받지 않는다."
        Assert-True ($result.ExitCode -eq 0) "smoke 실행이 실패했다.`n$($result.Output)"
        Assert-True ($result.DockerCalls -match "PERFORMANCE_DB_PASSWORD_SET") "Docker 호출에 일회성 DB 비밀번호가 전달되지 않았다."
    }

    Invoke-TestCase "smoke 성공 시 전용 환경을 초기화하고 정리한다" {
        $result = Invoke-Runner @("-Profile", "smoke")

        Assert-True ($result.ExitCode -eq 0) "smoke 실행이 실패했다.`n$($result.Output)"
        Assert-True ($result.DockerCalls -match "compose -p beyond-may-be-performance .* down --volumes --remove-orphans") "성능 환경 초기화가 없다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -match "compose -p beyond-may-be-performance .* up -d --build postgres app") "애플리케이션 환경을 시작하지 않았다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -match "run --rm -e TARGET_BASE_URL=http://app:8080 k6 run /scripts/health.js") "health 준비 확인을 실행하지 않았다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -match "run --rm -e TARGET_BASE_URL=http://app:8080 k6 run /scripts/signup-smoke.js") "k6 smoke를 실행하지 않았다.`n$($result.DockerCalls)"
        $cleanupCount = ([regex]::Matches($result.DockerCalls, "down --volumes --remove-orphans")).Count
        Assert-True ($cleanupCount -eq 2) "성공 시 초기화와 정리를 각각 한 번 수행해야 한다.`n$($result.DockerCalls)"
    }

    Invoke-TestCase "루프백 대상은 k6 컨테이너에서 호스트 주소로 변환한다" {
        $result = Invoke-Runner @(
            "-Profile", "smoke",
            "-TargetBaseUrl", "http://127.0.0.1:18080"
        )

        Assert-True ($result.ExitCode -eq 0) "루프백 대상 실행이 실패했다.`n$($result.Output)"
        Assert-True ($result.DockerCalls -match "TARGET_BASE_URL=http://host.docker.internal:18080") "루프백 주소를 컨테이너용 호스트 주소로 변환하지 않았다.`n$($result.DockerCalls)"
    }

    Invoke-TestCase "성능 앱 포트가 아닌 루프백 대상은 Docker 실행 전에 거부한다" {
        $result = Invoke-Runner @(
            "-Profile", "smoke",
            "-TargetBaseUrl", "http://127.0.0.1:18081"
        )

        Assert-True ($result.ExitCode -ne 0) "임의 루프백 포트 실행이 성공하면 안 된다."
        Assert-True ($result.Output -match "18080|성능 앱") "허용 포트 안내가 없다.`n$($result.Output)"
        Assert-True ([string]::IsNullOrWhiteSpace($result.DockerCalls)) "포트 거부 전에 Docker가 실행됐다.`n$($result.DockerCalls)"
    }

    Invoke-TestCase "지원하지 않는 프로필은 Docker 실행 전에 거부한다" {
        $result = Invoke-Runner @("-Profile", "load")

        Assert-True ($result.ExitCode -ne 0) "지원하지 않는 프로필 실행이 성공하면 안 된다."
        Assert-True ([string]::IsNullOrWhiteSpace($result.DockerCalls)) "프로필 거부 전에 Docker가 실행됐다.`n$($result.DockerCalls)"
    }

    Invoke-TestCase "smoke 실패 시 진단 환경을 보존한다" {
        $result = Invoke-Runner -Arguments @("-Profile", "smoke") -FakeK6ExitCode 42

        Assert-True ($result.ExitCode -ne 0) "k6 실패가 실행 실패로 반영되지 않았다."
        Assert-True ($result.Output -match "진단|보존") "환경 보존 안내가 없다.`n$($result.Output)"
        Assert-True ($result.Output -match "down --volumes --remove-orphans") "명시적인 정리 방법이 없다.`n$($result.Output)"
        $cleanupCount = ([regex]::Matches($result.DockerCalls, "down --volumes --remove-orphans")).Count
        Assert-True ($cleanupCount -eq 1) "실패 환경을 자동 정리하면 안 된다.`n$($result.DockerCalls)"
    }
}
finally {
    $resolvedTestRoot = [System.IO.Path]::GetFullPath($testRoot)
    $resolvedTempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if (
        $resolvedTestRoot.StartsWith($resolvedTempRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $resolvedTestRoot).StartsWith("beyond-performance-tests-", [System.StringComparison]::Ordinal)
    ) {
        Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($script:FailureCount -gt 0) {
    Write-Output "총 $($script:FailureCount)개 성능 실행기 시험이 실패했다."
    exit 1
}

Write-Output "모든 성능 실행기 시험이 통과했다."
exit 0
