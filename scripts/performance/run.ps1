[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("smoke")]
    [string]$Profile,

    [Parameter()]
    [string]$TargetBaseUrl = "http://app:8080"
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$composeFile = Join-Path $projectRoot "docker-compose.performance.yml"
$composeProject = "beyond-may-be-performance"

function Get-AllowedTarget {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $uri = $null
    if (-not [System.Uri]::TryCreate($Value, [System.UriKind]::Absolute, [ref]$uri)) {
        throw "대상 주소는 올바른 절대 URL이어야 한다."
    }
    if ($uri.Scheme -ne "http") {
        throw "로컬 성능 대상은 http 주소만 허용한다."
    }
    if (
        -not [string]::IsNullOrEmpty($uri.UserInfo) -or
        -not [string]::IsNullOrEmpty($uri.Query) -or
        -not [string]::IsNullOrEmpty($uri.Fragment) -or
        $uri.AbsolutePath -ne "/"
    ) {
        throw "로컬 성능 대상은 경로·인증정보·쿼리·프래그먼트가 없는 기본 주소여야 한다."
    }

    $targetHost = $uri.DnsSafeHost
    $isLoopback = $targetHost -ieq "localhost"
    $ipAddress = $null
    if ([System.Net.IPAddress]::TryParse($targetHost, [ref]$ipAddress)) {
        $isLoopback = [System.Net.IPAddress]::IsLoopback($ipAddress)
    }

    if ($targetHost -ieq "app") {
        if ($uri.Port -ne 8080) {
            throw "성능 환경 내부 app 주소는 8080 포트만 허용한다."
        }
        return $uri
    }
    if ($isLoopback) {
        if ($uri.Port -ne 18080) {
            throw "루프백 대상은 성능 앱의 고정 포트 18080만 허용한다."
        }
        return $uri
    }

    throw "로컬 루프백과 성능 환경 내부 app 주소만 허용한다."
}

function Invoke-PerformanceDocker {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker 명령이 실패했다: docker $($Arguments -join ' ')"
    }
}

function Get-CleanupCommand {
    return "docker compose -p $composeProject -f `"$composeFile`" down --volumes --remove-orphans"
}

$target = Get-AllowedTarget $TargetBaseUrl
if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
    throw "성능 Compose 파일이 없다: $composeFile"
}
if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker 명령을 찾을 수 없다."
}

$composeArguments = @("compose", "-p", $composeProject, "-f", $composeFile)
$downArguments = $composeArguments + @("down", "--volumes", "--remove-orphans")
$environmentStarted = $false
$runSucceeded = $false

if ($target.DnsSafeHost -ieq "app") {
    $k6TargetBaseUrl = "http://app:8080"
}
else {
    $k6TargetBaseUrl = "http://host.docker.internal:$($target.Port)"
}

$previousPerformanceDbPassword = [System.Environment]::GetEnvironmentVariable(
    "PERFORMANCE_DB_PASSWORD",
    [System.EnvironmentVariableTarget]::Process
)
[System.Environment]::SetEnvironmentVariable(
    "PERFORMANCE_DB_PASSWORD",
    [guid]::NewGuid().ToString("N"),
    [System.EnvironmentVariableTarget]::Process
)

try {
    Invoke-PerformanceDocker $downArguments
    $environmentStarted = $true
    Invoke-PerformanceDocker ($composeArguments + @("up", "-d", "--build", "postgres", "app"))
    Invoke-PerformanceDocker (
        $composeArguments + @(
            "run",
            "--rm",
            "-e",
            "TARGET_BASE_URL=$k6TargetBaseUrl",
            "k6",
            "run",
            "/scripts/health.js"
        )
    )
    Write-Output "애플리케이션 health 준비 완료."
    Invoke-PerformanceDocker (
        $composeArguments + @(
            "run",
            "--rm",
            "-e",
            "TARGET_BASE_URL=$k6TargetBaseUrl",
            "k6",
            "run",
            "/scripts/signup-smoke.js"
        )
    )
    $runSucceeded = $true
}
finally {
    try {
        if ($runSucceeded) {
            try {
                Invoke-PerformanceDocker $downArguments
                Write-Output "smoke 성공: 성능 컨테이너와 전용 볼륨을 정리했다."
            }
            catch {
                Write-Warning "smoke는 성공했지만 성능 환경 정리에 실패했다."
                Write-Output "다시 정리: $(Get-CleanupCommand)"
                throw
            }
        }
        elseif ($environmentStarted) {
            Write-Warning "smoke 실패 또는 중단: 진단을 위해 성능 환경을 보존했다."
            Write-Output "진단 후 정리: $(Get-CleanupCommand)"
        }
    }
    finally {
        [System.Environment]::SetEnvironmentVariable(
            "PERFORMANCE_DB_PASSWORD",
            $previousPerformanceDbPassword,
            [System.EnvironmentVariableTarget]::Process
        )
    }
}
