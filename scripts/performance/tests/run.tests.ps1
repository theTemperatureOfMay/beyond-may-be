[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$script:FailureCount = 0
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\.."))
$runner = Join-Path $projectRoot "scripts\performance\run.ps1"
$composeFile = Join-Path $projectRoot "docker-compose.performance.yml"
$dashboardFile = Join-Path $projectRoot "observability\grafana\dashboards\k6-app-performance.json"
$healthScript = Join-Path $projectRoot "performance\health.js"
$signupScript = Join-Path $projectRoot "performance\signup.js"
$performanceResultsRoot = Join-Path $projectRoot "performance-results"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("beyond-performance-tests-" + [guid]::NewGuid().ToString("N"))
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
$script:CreatedResultDirectories = New-Object System.Collections.Generic.List[string]

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
        [int]$FakeK6ExitCode = 0,
        [Parameter()]
        [bool]$ExistingPrometheusVolume = $false,
        [Parameter()]
        [bool]$ExistingDatabaseVolume = $false,
        [Parameter()]
        [bool]$FailGitStatus = $false
    )

    $runRoot = Join-Path $testRoot ([guid]::NewGuid().ToString("N"))
    $fakeBin = Join-Path $runRoot "fake-bin"
    $dockerLog = Join-Path $runRoot "docker.log"
    $k6RunCountPath = Join-Path $runRoot "k6-run-count.txt"
    [void](New-Item -ItemType Directory -Path $fakeBin -Force)
    [System.IO.File]::WriteAllText(
        (Join-Path $fakeBin "docker.cmd"),
        "@echo off`r`n`"$PSHOME\powershell.exe`" -NoProfile -ExecutionPolicy Bypass -File `"%~dp0docker.ps1`" %*`r`nexit /b %ERRORLEVEL%`r`n",
        $utf8WithoutBom
    )
    [System.IO.File]::WriteAllText(
        (Join-Path $fakeBin "docker.ps1"),
        @'
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$DockerArguments)

$call = $DockerArguments -join " "
[System.IO.File]::AppendAllText($env:PERFORMANCE_FAKE_DOCKER_LOG, $call + [Environment]::NewLine)
if (-not [string]::IsNullOrWhiteSpace($env:PERFORMANCE_DB_PASSWORD)) {
    [System.IO.File]::AppendAllText($env:PERFORMANCE_FAKE_DOCKER_LOG, "PERFORMANCE_DB_PASSWORD_SET" + [Environment]::NewLine)
}

if ($call -match '^volume inspect (?<volume>[^ ]+)$') {
    $exists = if ($Matches.volume -eq 'beyond-may-be-performance-prometheus-data') {
        $env:PERFORMANCE_FAKE_PROMETHEUS_VOLUME_EXISTS -eq 'true'
    }
    else {
        $env:PERFORMANCE_FAKE_DATABASE_VOLUME_EXISTS -eq 'true'
    }
    if (-not $exists) {
        exit 1
    }
    exit 0
}
if ($call -match '^version ') {
    Write-Output "27.0.0"
    exit 0
}
if ($call -match '^compose .* ps .*--format json') {
    [pscustomobject]@{
        Service = "app"
        Image = "beyond-may-be-app"
        State = "running"
        Health = "healthy"
        ExitCode = 0
        Publishers = @()
        Labels = "com.docker.compose.project.working_dir=C:\Users\$env:USERNAME\repo"
    } | ConvertTo-Json -Compress
    exit 0
}
if (
    $call -match 'signup.js' -and
    $call -match 'PROFILE=(smoke|load|stress|spike)' -and
    $call -match 'RESULT_DIR=/results/(?<testId>[^ ]+)'
) {
    $testId = $Matches.testId
    $profileName = [regex]::Match($call, 'PROFILE=(?<value>[^ ]+)').Groups['value'].Value
    $profileRps = [int][regex]::Match($call, 'RPS=(?<value>\d+)').Groups['value'].Value
    $resultDirectory = Join-Path $env:PERFORMANCE_FAKE_RESULTS_ROOT $testId
    [void](New-Item -ItemType Directory -Path $resultDirectory -Force)
    $runCount = if (Test-Path -LiteralPath $env:PERFORMANCE_FAKE_K6_RUN_COUNT -PathType Leaf) {
        [int][System.IO.File]::ReadAllText($env:PERFORMANCE_FAKE_K6_RUN_COUNT) + 1
    }
    else {
        1
    }
    [System.IO.File]::WriteAllText($env:PERFORMANCE_FAKE_K6_RUN_COUNT, $runCount.ToString())
    $metricValue = @(30, 10, 20)[($runCount - 1) % 3]
    $summary = [ordered]@{
        schemaVersion = 1
        testid = $testId
        profile = $profileName
        rps = $profileRps
        measurementStartedAtUtc = "2026-08-16T00:00:00Z"
        measurementEndedAtUtc = "2026-08-16T00:10:00Z"
        metrics = [ordered]@{
            throughput = [ordered]@{ count = $metricValue * 100; rate = $metricValue }
            errorRate = $metricValue / 1000
            checksRate = 1 - ($metricValue / 1000)
            droppedIterations = $metricValue / 10
            responseTimeMs = [ordered]@{
                p50 = $metricValue
                p95 = $metricValue + 5
                p99 = $metricValue + 10
            }
        }
    }
    [System.IO.File]::WriteAllText(
        (Join-Path $resultDirectory "summary.json"),
        ($summary | ConvertTo-Json -Depth 6)
    )
    [System.IO.File]::WriteAllText((Join-Path $resultDirectory "report.html"), "<html></html>")
    exit [int]$env:PERFORMANCE_FAKE_K6_EXIT_CODE
}

exit 0
'@,
        $utf8WithoutBom
    )

    $previousPath = $env:PATH
    $previousDockerLog = $env:PERFORMANCE_FAKE_DOCKER_LOG
    $previousK6ExitCode = $env:PERFORMANCE_FAKE_K6_EXIT_CODE
    $previousFakeResultsRoot = $env:PERFORMANCE_FAKE_RESULTS_ROOT
    $previousK6RunCount = $env:PERFORMANCE_FAKE_K6_RUN_COUNT
    $previousPrometheusVolumeExists = $env:PERFORMANCE_FAKE_PROMETHEUS_VOLUME_EXISTS
    $previousDatabaseVolumeExists = $env:PERFORMANCE_FAKE_DATABASE_VOLUME_EXISTS
    $previousDbPassword = $env:PERFORMANCE_DB_PASSWORD
    $previousGitIndexFile = $env:GIT_INDEX_FILE
    $previousErrorActionPreference = $ErrorActionPreference
    $resultsBefore = if (Test-Path -LiteralPath $performanceResultsRoot -PathType Container) {
        @(Get-ChildItem -LiteralPath $performanceResultsRoot -Directory | ForEach-Object { $_.FullName })
    }
    else {
        @()
    }
    try {
        $env:PATH = $fakeBin + [System.IO.Path]::PathSeparator + $previousPath
        $env:PERFORMANCE_FAKE_DOCKER_LOG = $dockerLog
        $env:PERFORMANCE_FAKE_K6_EXIT_CODE = $FakeK6ExitCode.ToString()
        $env:PERFORMANCE_FAKE_RESULTS_ROOT = $performanceResultsRoot
        $env:PERFORMANCE_FAKE_K6_RUN_COUNT = $k6RunCountPath
        $env:PERFORMANCE_FAKE_PROMETHEUS_VOLUME_EXISTS = $ExistingPrometheusVolume.ToString().ToLowerInvariant()
        $env:PERFORMANCE_FAKE_DATABASE_VOLUME_EXISTS = $ExistingDatabaseVolume.ToString().ToLowerInvariant()
        if ($FailGitStatus) {
            $env:GIT_INDEX_FILE = $runRoot
        }
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
        $env:PERFORMANCE_FAKE_RESULTS_ROOT = $previousFakeResultsRoot
        $env:PERFORMANCE_FAKE_K6_RUN_COUNT = $previousK6RunCount
        $env:PERFORMANCE_FAKE_PROMETHEUS_VOLUME_EXISTS = $previousPrometheusVolumeExists
        $env:PERFORMANCE_FAKE_DATABASE_VOLUME_EXISTS = $previousDatabaseVolumeExists
        $env:PERFORMANCE_DB_PASSWORD = $previousDbPassword
        $env:GIT_INDEX_FILE = $previousGitIndexFile
    }

    $resultDirectories = if (Test-Path -LiteralPath $performanceResultsRoot -PathType Container) {
        @(
            Get-ChildItem -LiteralPath $performanceResultsRoot -Directory |
                Where-Object { $resultsBefore -notcontains $_.FullName } |
                ForEach-Object {
                    $script:CreatedResultDirectories.Add($_.FullName)
                    $_.FullName
                }
        )
    }
    else {
        @()
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
        ResultDirectories = $resultDirectories
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

    Invoke-TestCase "smoke 성공 시 앱·DB만 정리하고 관측 환경과 결과를 보존한다" {
        $result = Invoke-Runner @("-Profile", "smoke")

        Assert-True ($result.ExitCode -eq 0) "smoke 실행이 실패했다.`n$($result.Output)"
        Assert-True ($result.DockerCalls -match "volume create beyond-may-be-performance-prometheus-data") "외부 Prometheus 볼륨을 준비하지 않았다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -match "up -d prometheus grafana") "관측 환경을 시작하지 않았다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -match "rm -s -f app postgres") "앱·DB 초기화 또는 정리가 없다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -match "compose -p beyond-may-be-performance .* up -d --build postgres app") "애플리케이션 환경을 시작하지 않았다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -match "run --rm -e TARGET_BASE_URL=http://app:8080 k6 run /scripts/health.js") "health 준비 확인을 실행하지 않았다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -match "PROFILE=smoke .*RPS=1 .*signup.js") "k6 smoke를 실행하지 않았다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -notmatch "down --volumes") "일반 정리로 관측 환경을 중단하면 안 된다.`n$($result.DockerCalls)"
        Assert-True (@($result.ResultDirectories).Count -eq 1) "smoke 결과 디렉터리가 정확히 하나여야 한다."
    }

    Invoke-TestCase "기존 Prometheus 볼륨은 다시 만들거나 삭제하지 않는다" {
        $result = Invoke-Runner -Arguments @("-Profile", "smoke") -ExistingPrometheusVolume $true

        Assert-True ($result.ExitCode -eq 0) "기존 관측 볼륨을 사용하는 smoke가 실패했다.`n$($result.Output)"
        Assert-True ($result.DockerCalls -notmatch "volume create beyond-may-be-performance-prometheus-data") "기존 관측 볼륨을 다시 만들었다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -notmatch "volume rm beyond-may-be-performance-prometheus-data") "일반 실행에서 관측 볼륨을 삭제했다.`n$($result.DockerCalls)"
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

    Invoke-TestCase "load는 RPS 입력이 없으면 Docker 실행 전에 거부한다" {
        $result = Invoke-Runner @("-Profile", "load")

        Assert-True ($result.ExitCode -ne 0) "RPS 없는 load 실행이 성공하면 안 된다."
        Assert-True ($result.Output -match "RPS|Rps") "필수 RPS 안내가 없다.`n$($result.Output)"
        Assert-True ([string]::IsNullOrWhiteSpace($result.DockerCalls)) "RPS 검증 전에 Docker가 실행됐다.`n$($result.DockerCalls)"
    }

    Invoke-TestCase "baseline은 RPS 입력이 없으면 Docker 실행 전에 거부한다" {
        $result = Invoke-Runner @("-Profile", "baseline")

        Assert-True ($result.ExitCode -ne 0) "RPS 없는 baseline 실행이 성공하면 안 된다."
        Assert-True ($result.Output -match "RPS|Rps") "필수 RPS 안내가 없다.`n$($result.Output)"
        Assert-True ([string]::IsNullOrWhiteSpace($result.DockerCalls)) "RPS 검증 전에 Docker가 실행됐다.`n$($result.DockerCalls)"
    }

    Invoke-TestCase "load는 워밍업과 측정을 분리하고 실행 증거를 남긴다" {
        $result = Invoke-Runner @("-Profile", "load", "-Rps", "20")

        Assert-True ($result.ExitCode -eq 0) "load 실행이 실패했다.`n$($result.Output)"
        Assert-True ($result.DockerCalls -match "PROFILE=warmup .*RPS=1 .*signup.js") "1 RPS 워밍업이 없다.`n$($result.DockerCalls)"
        Assert-True ($result.DockerCalls -match "PROFILE=load .*RPS=20 .*RESULT_DIR=/results/[^ ]+ .*experimental-prometheus-rw .*signup.js") "load 측정과 remote write가 없다.`n$($result.DockerCalls)"
        Assert-True (@($result.ResultDirectories).Count -eq 1) "load 결과 디렉터리가 정확히 하나여야 한다."
        $resultDirectory = @($result.ResultDirectories)[0]
        foreach ($artifact in @("metadata.json", "summary.json", "report.html", "console.log", "containers-before.json", "containers-after.json", "observations.md")) {
            Assert-True (Test-Path -LiteralPath (Join-Path $resultDirectory $artifact) -PathType Leaf) "$artifact 결과가 없다."
        }
    }

    Invoke-TestCase "baseline은 load를 세 번 순차 실행하고 매회 환경을 초기화한다" {
        $result = Invoke-Runner @("-Profile", "baseline", "-Rps", "20")

        Assert-True ($result.ExitCode -eq 0) "baseline 실행이 실패했다.`n$($result.Output)"
        Assert-True (([regex]::Matches($result.DockerCalls, "PROFILE=warmup .*RPS=1 .*signup.js")).Count -eq 3) "세 번의 워밍업이 없다.`n$($result.DockerCalls)"
        Assert-True (([regex]::Matches($result.DockerCalls, "PROFILE=load .*RPS=20 .*RESULT_DIR=/results/[^ ]+")).Count -eq 3) "load가 세 번 실행되지 않았다.`n$($result.DockerCalls)"
        Assert-True (([regex]::Matches($result.DockerCalls, "up -d --build postgres app")).Count -eq 3) "각 회차가 독립 환경을 시작하지 않았다.`n$($result.DockerCalls)"
        Assert-True (([regex]::Matches($result.DockerCalls, "rm -s -f app postgres")).Count -eq 6) "각 회차 전후 초기화가 없다.`n$($result.DockerCalls)"
        $rawResultDirectories = @(
            $result.ResultDirectories |
                Where-Object { Test-Path -LiteralPath (Join-Path $_ "summary.json") -PathType Leaf }
        )
        Assert-True ($rawResultDirectories.Count -eq 3) "회차별 원본 결과 디렉터리가 정확히 세 개여야 한다."
    }

    Invoke-TestCase "baseline은 세 원본 결과와 지표별 중앙값 Markdown 초안을 남긴다" {
        $result = Invoke-Runner @("-Profile", "baseline", "-Rps", "20")

        Assert-True ($result.ExitCode -eq 0) "baseline 실행이 실패했다.`n$($result.Output)"
        $rawResultDirectories = @(
            $result.ResultDirectories |
                Where-Object { Test-Path -LiteralPath (Join-Path $_ "summary.json") -PathType Leaf }
        )
        $draftDirectories = @(
            $result.ResultDirectories |
                Where-Object { Test-Path -LiteralPath (Join-Path $_ "baseline.md") -PathType Leaf }
        )
        Assert-True ($rawResultDirectories.Count -eq 3) "원본 결과가 정확히 세 개여야 한다."
        Assert-True ($draftDirectories.Count -eq 1) "중앙값 Markdown 초안이 정확히 하나여야 한다."

        $draft = [System.IO.File]::ReadAllText((Join-Path $draftDirectories[0] "baseline.md"))
        Assert-True ($draft -match '\| (True|False) \|') "초안에 Git dirty boolean 값이 없다.`n$draft"
        foreach ($resultDirectory in $rawResultDirectories) {
            Assert-True ($draft.Contains((Split-Path -Leaf $resultDirectory))) "초안에 회차별 원본 결과 경로가 없다."
        }
        foreach ($expected in @(
            "# 회원가입 load 기준선 초안",
            "RPS: ``20``",
            "Docker Server: ``27.0.0``",
            "k6: ``grafana/k6:2.2.0``",
            "Git commit",
            "dirty",
            "| 요청 수 | 3000 | 1000 | 2000 | 2000 |",
            "| 처리량 (req/s) | 30 | 10 | 20 | 20 |",
            "| 오류율 | 0.03 | 0.01 | 0.02 | 0.02 |",
            "| checks 성공률 | 0.97 | 0.99 | 0.98 | 0.98 |",
            "| dropped iterations | 3 | 1 | 2 | 2 |",
            "| p50 (ms) | 30 | 10 | 20 | 20 |",
            "| p95 (ms) | 35 | 15 | 25 | 25 |",
            "| p99 (ms) | 40 | 20 | 30 | 30 |",
            "같은 머신",
            "운영 TPS·SLO·용량 보증"
        )) {
            Assert-True ($draft.Contains($expected)) "초안에 필수 내용이 없다: $expected`n$draft"
        }
    }

    Invoke-TestCase "baseline은 Git dirty 상태를 수집하지 못하면 초안을 만들지 않는다" {
        $result = Invoke-Runner -Arguments @("-Profile", "baseline", "-Rps", "20") -FailGitStatus $true

        Assert-True ($result.ExitCode -ne 0) "Git 상태를 모르는 baseline이 성공하면 안 된다."
        Assert-True ($result.Output -match "Git|dirty") "Git 상태 수집 실패 이유가 없다.`n$($result.Output)"
        $draftDirectories = @(
            $result.ResultDirectories |
                Where-Object { Test-Path -LiteralPath (Join-Path $_ "baseline.md") -PathType Leaf }
        )
        Assert-True ($draftDirectories.Count -eq 0) "Git 상태를 모르는 중앙값 초안이 생성됐다."
    }

    Invoke-TestCase "Docker stderr 경고와 종료 코드를 분리한다" {
        $runnerContent = [System.IO.File]::ReadAllText($runner)

        Assert-True (([regex]::Matches($runnerContent, '\$ErrorActionPreference = "Continue"')).Count -ge 2) "stderr 병합 함수가 비종료 경고를 허용하지 않는다."
        Assert-True (([regex]::Matches($runnerContent, '\$exitCode = \$LASTEXITCODE')).Count -eq 2) "Docker 종료 코드를 경고와 별도로 보존하지 않는다."
    }

    Invoke-TestCase "Docker JSON 출력은 UTF-8로 읽고 인코딩을 복원한다" {
        $runnerContent = [System.IO.File]::ReadAllText($runner)
        $json = '{"Command":"\"java -jar /app/app' + [char]0x2026 + '\""}'
        $bytes = $utf8WithoutBom.GetBytes($json)
        $cp949Failed = $false
        try {
            [void]([System.Text.Encoding]::GetEncoding(949).GetString($bytes) | ConvertFrom-Json)
        }
        catch {
            $cp949Failed = $true
        }

        Assert-True $cp949Failed "CP949 회귀 입력이 JSON 파싱 실패를 재현하지 못했다."
        [void]($utf8WithoutBom.GetString($bytes) | ConvertFrom-Json)
        Assert-True ($runnerContent.Contains('$previousOutputEncoding = [Console]::OutputEncoding')) "기존 출력 인코딩을 보존하지 않는다."
        Assert-True ($runnerContent.Contains('[Console]::OutputEncoding = $utf8WithoutBom')) "Docker 출력을 UTF-8로 읽지 않는다."
        Assert-True ($runnerContent.Contains('[Console]::OutputEncoding = $previousOutputEncoding')) "Docker 호출 뒤 출력 인코딩을 복원하지 않는다."
    }

    Invoke-TestCase "all은 Docker 실행 전에 거부한다" {
        $result = Invoke-Runner @("-Profile", "all", "-Rps", "5")

        Assert-True ($result.ExitCode -ne 0) "all 실행이 성공하면 안 된다."
        Assert-True ([string]::IsNullOrWhiteSpace($result.DockerCalls)) "profile 검증 전에 Docker가 실행됐다.`n$($result.DockerCalls)"
    }

    Invoke-TestCase "RPS 하한 미만과 상한 초과는 Docker 실행 전에 거부한다" {
        foreach ($invalidRps in @(0, 101)) {
            $result = Invoke-Runner @("-Profile", "stress", "-Rps", $invalidRps.ToString())

            Assert-True ($result.ExitCode -ne 0) "$invalidRps RPS 실행이 성공하면 안 된다."
            Assert-True ([string]::IsNullOrWhiteSpace($result.DockerCalls)) "RPS 범위 검증 전에 Docker가 실행됐다.`n$($result.DockerCalls)"
        }
    }

    Invoke-TestCase "지원하지 않는 profile은 Docker 실행 전에 거부한다" {
        $result = Invoke-Runner @("-Profile", "soak", "-Rps", "1")

        Assert-True ($result.ExitCode -ne 0) "지원하지 않는 profile 실행이 성공하면 안 된다."
        Assert-True ([string]::IsNullOrWhiteSpace($result.DockerCalls)) "profile 검증 전에 Docker가 실행됐다.`n$($result.DockerCalls)"
    }

    Invoke-TestCase "metadata에는 비교 조건을 남기고 PC 식별 정보는 수집하지 않는다" {
        $result = Invoke-Runner @("-Profile", "smoke")
        Assert-True ($result.ExitCode -eq 0) "metadata 검증용 smoke가 실패했다.`n$($result.Output)"

        $metadataPath = Join-Path @($result.ResultDirectories)[0] "metadata.json"
        $metadataContent = [System.IO.File]::ReadAllText($metadataPath)
        $metadata = $metadataContent | ConvertFrom-Json
        Assert-True ($metadata.profile -eq "smoke" -and $metadata.rps -eq 1) "profile·RPS가 metadata에 없다."
        Assert-True (-not [string]::IsNullOrWhiteSpace($metadata.git.commit)) "Git commit이 metadata에 없다."
        Assert-True ($metadata.git.dirty -is [bool]) "Git dirty 상태가 boolean으로 기록되지 않았다."
        Assert-True ($metadata.resources.app.cpus -eq 0.5) "Docker 자원 조건이 metadata에 없다."
        Assert-True ($metadata.images.prometheus -eq "prom/prometheus:v3.13.2") "관측 image 버전이 metadata에 없다."
        $evidenceContent = $metadataContent +
            [System.IO.File]::ReadAllText((Join-Path @($result.ResultDirectories)[0] "containers-before.json")) +
            [System.IO.File]::ReadAllText((Join-Path @($result.ResultDirectories)[0] "containers-after.json"))
        foreach ($identity in @($env:COMPUTERNAME, $env:USERNAME)) {
            if (-not [string]::IsNullOrWhiteSpace($identity)) {
                Assert-True (-not $evidenceContent.Contains($identity)) "호스트명 또는 Windows 사용자명이 실행 증거에 포함됐다."
            }
        }
    }

    Invoke-TestCase "Grafana 대시보드는 k6 remote write 지표 이름을 조회한다" {
        $dashboardContent = [System.IO.File]::ReadAllText($dashboardFile)

        foreach ($metricName in @(
            "k6_http_req_duration_p50",
            "k6_http_req_duration_p95",
            "k6_http_req_duration_p99"
        )) {
            Assert-True ($dashboardContent.Contains($metricName)) "$metricName 쿼리가 대시보드에 없다."
        }
        Assert-True (-not $dashboardContent.Contains("k6_http_req_duration_seconds_")) "trend gauge에 존재하지 않는 seconds suffix를 사용한다."
    }

    Invoke-TestCase "health 준비 관문은 콜드 스타트 3분을 허용한다" {
        $scriptContent = [System.IO.File]::ReadAllText($healthScript)

        Assert-True ($scriptContent.Contains("attempt < 90")) "health 준비 대기가 최소 3분이 아니다."
    }

    Invoke-TestCase "smoke와 load는 오류를 한 건도 허용하지 않는다" {
        $scriptContent = [System.IO.File]::ReadAllText($signupScript)

        Assert-True ($scriptContent.Contains('checks: ["rate==1"]')) "응답 계약 오류 0건 기준이 아니다."
        Assert-True ($scriptContent.Contains('http_req_failed: ["rate==0"]')) "HTTP 오류 0건 기준이 아니다."
        Assert-True ($scriptContent.Contains('dropped_iterations: ["count<1"]')) "dropped iteration 0건 기준이 아니다."
    }

    Invoke-TestCase "smoke 실패 시 진단 환경을 보존한다" {
        $result = Invoke-Runner -Arguments @("-Profile", "smoke") -FakeK6ExitCode 42

        Assert-True ($result.ExitCode -ne 0) "k6 실패가 실행 실패로 반영되지 않았다."
        Assert-True ($result.Output -match "진단|보존") "환경 보존 안내가 없다.`n$($result.Output)"
        Assert-True ($result.Output -match "docker volume rm beyond-may-be-performance-postgres-data") "명시적인 DB 볼륨 정리 방법이 없다.`n$($result.Output)"
        $cleanupCount = ([regex]::Matches($result.DockerCalls, "rm -s -f app postgres")).Count
        Assert-True ($cleanupCount -eq 1) "실패 환경을 자동 정리하면 안 된다.`n$($result.DockerCalls)"
    }
}
finally {
    foreach ($resultDirectory in $script:CreatedResultDirectories) {
        $resolvedResultDirectory = [System.IO.Path]::GetFullPath($resultDirectory)
        $resolvedResultsRoot = [System.IO.Path]::GetFullPath($performanceResultsRoot)
        if ($resolvedResultDirectory.StartsWith($resolvedResultsRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedResultDirectory -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
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
