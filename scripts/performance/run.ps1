[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("smoke", "load", "stress", "spike", "baseline")]
    [string]$Profile,

    [Parameter()]
    [ValidateRange(1, 100)]
    [Nullable[int]]$Rps,

    [Parameter()]
    [string]$TargetBaseUrl = "http://app:8080"
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$composeFile = Join-Path $projectRoot "docker-compose.performance.yml"
$resultsRoot = Join-Path $projectRoot "performance-results"
$composeProject = "beyond-may-be-performance"
$databaseVolume = "beyond-may-be-performance-postgres-data"
$prometheusVolume = "beyond-may-be-performance-prometheus-data"
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)

if ($Profile -ne "smoke" -and -not $PSBoundParameters.ContainsKey("Rps")) {
    throw "$Profile 프로필은 1 이상 100 이하의 RPS 입력이 필요하다."
}
if ($Profile -eq "smoke" -and $PSBoundParameters.ContainsKey("Rps")) {
    throw "smoke는 고정 1 RPS이므로 RPS를 입력하지 않는다."
}

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

function Invoke-PerformanceDockerLogged {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [Parameter(Mandatory = $true)]
        [string]$LogPath
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & docker @Arguments 2>&1 |
            Tee-Object -FilePath $LogPath -Append -ErrorAction Stop
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "Docker 명령이 실패했다: docker $($Arguments -join ' ')"
    }
}

function Get-PerformanceDockerOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $previousOutputEncoding = [Console]::OutputEncoding
    try {
        $ErrorActionPreference = "Continue"
        [Console]::OutputEncoding = $utf8WithoutBom
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
        [Console]::OutputEncoding = $previousOutputEncoding
    }
    if ($exitCode -ne 0) {
        throw "Docker 명령이 실패했다: docker $($Arguments -join ' ')"
    }
    return ($output -join [Environment]::NewLine)
}

function Get-ContainerEvidence {
    $rawOutput = Get-PerformanceDockerOutput (
        $script:ComposeArguments + @("ps", "-a", "--format", "json")
    )
    if ([string]::IsNullOrWhiteSpace($rawOutput)) {
        return "[]"
    }

    try {
        $containers = @($rawOutput | ConvertFrom-Json)
    }
    catch {
        $containers = @(
            $rawOutput -split "`r?`n" |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
                ForEach-Object { $_ | ConvertFrom-Json }
        )
    }

    $evidence = @(
        $containers | ForEach-Object {
            [ordered]@{
                service = $_.Service
                image = $_.Image
                state = $_.State
                health = $_.Health
                exitCode = $_.ExitCode
                publishers = @($_.Publishers)
            }
        }
    )
    return (ConvertTo-Json -InputObject $evidence -Depth 6)
}

function Write-Utf8File {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$Content
    )

    [System.IO.File]::WriteAllText($Path, $Content, $utf8WithoutBom)
}

function Test-DockerVolumeExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & docker volume inspect $Name *> $null
        $exists = $LASTEXITCODE -eq 0
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return $exists
}

function Initialize-ObservabilityVolume {
    if (-not (Test-DockerVolumeExists $prometheusVolume)) {
        Invoke-PerformanceDocker @("volume", "create", $prometheusVolume)
    }
}

function Remove-DatabaseEnvironment {
    Invoke-PerformanceDocker ($script:ComposeArguments + @("rm", "-s", "-f", "app", "postgres"))
    if (Test-DockerVolumeExists $databaseVolume) {
        Invoke-PerformanceDocker @("volume", "rm", $databaseVolume)
    }
}

function Get-ProfileStages {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [int]$TargetRps
    )

    if ($Name -eq "smoke") {
        return @([ordered]@{ rate = 1; duration = "30s" })
    }
    if ($Name -eq "load") {
        return @([ordered]@{ rate = $TargetRps; duration = "10m" })
    }

    $percentages = if ($Name -eq "stress") { @(20, 40, 60, 80, 100) } else { @(10, 100, 10) }
    $duration = if ($Name -eq "stress") { "3m" } else { "1m" }
    return @(
        $percentages | ForEach-Object {
            [ordered]@{
                rate = [Math]::Max(1, [Math]::Ceiling($TargetRps * $_ / 100.0))
                duration = $duration
            }
        }
    )
}

function Get-GitMetadata {
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $commit = (& git -c "safe.directory=*" -C $projectRoot rev-parse HEAD 2>$null) -join ""
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($commit)) {
            $commit = "unknown"
        }
        $status = (& git -c "safe.directory=*" -C $projectRoot status --porcelain 2>$null) -join [Environment]::NewLine
        $dirty = if ($LASTEXITCODE -eq 0) { -not [string]::IsNullOrWhiteSpace($status) } else { $null }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [ordered]@{
        commit = $commit.Trim()
        dirty = $dirty
    }
}

function Write-RunMetadata {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResultDirectory,
        [Parameter(Mandatory = $true)]
        [string]$TestId,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [int]$TargetRps,
        [Parameter(Mandatory = $true)]
        [object[]]$Stages,
        [Parameter(Mandatory = $true)]
        [string]$RunStartedAt,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$MeasurementStartedAt,
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string]$MeasurementEndedAt,
        [Parameter(Mandatory = $true)]
        [string]$Outcome
    )

    $dockerVersion = "unknown"
    try {
        $resolvedDockerVersion = Get-PerformanceDockerOutput @("version", "--format", "{{.Server.Version}}")
        if (-not [string]::IsNullOrWhiteSpace($resolvedDockerVersion)) {
            $dockerVersion = $resolvedDockerVersion.Trim()
        }
    }
    catch {
        Write-Warning "Docker 버전을 metadata에 기록하지 못했다."
    }

    $metadata = [ordered]@{
        schemaVersion = 1
        testid = $TestId
        profile = $Name
        rps = $TargetRps
        stages = $Stages
        runStartedAtUtc = $RunStartedAt
        measurementStartedAtUtc = $MeasurementStartedAt
        measurementEndedAtUtc = $MeasurementEndedAt
        outcome = $Outcome
        git = Get-GitMetadata
        dockerVersion = $dockerVersion
        resources = [ordered]@{
            app = [ordered]@{ cpus = 0.5; memory = "1GiB" }
            postgres = [ordered]@{ cpus = 1.0; memory = "1GiB" }
            k6 = [ordered]@{ cpus = 1.0; memory = "512MiB" }
        }
        images = [ordered]@{
            app = "local Dockerfile"
            postgres = "postgres:17-alpine"
            k6 = "grafana/k6:2.2.0"
            prometheus = "prom/prometheus:v3.13.2"
            grafana = "grafana/grafana:13.1.3"
        }
        artifacts = @(
            "metadata.json",
            "summary.json",
            "report.html",
            "console.log",
            "containers-before.json",
            "containers-after.json",
            "observations.md"
        ) | Where-Object {
            $_ -eq "metadata.json" -or (Test-Path -LiteralPath (Join-Path $ResultDirectory $_) -PathType Leaf)
        }
    }

    Write-Utf8File (Join-Path $ResultDirectory "metadata.json") ($metadata | ConvertTo-Json -Depth 8)
}

function Write-Observations {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResultDirectory,
        [Parameter(Mandatory = $true)]
        [string]$TestId,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$Outcome
    )

    $content = @"
# 성능 테스트 관찰

- testid: ``$TestId``
- profile: ``$Name``
- outcome: ``$Outcome``

## 관찰

- 지연 증가 구간:
- 오류 증가 구간:
- dropped iteration 시작 구간:
- 부하 감소 후 회복 여부:
- JVM·HTTP·HikariCP 상관관계:

## 해석 메모

- 시작부터 dropped iteration이 발생하면 애플리케이션 저하와 구분해 부하 생성기 VU 포화 여부를 먼저 확인한다.
"@
    Write-Utf8File (Join-Path $ResultDirectory "observations.md") $content
}

function Get-MedianValue {
    param(
        [Parameter(Mandatory = $true)]
        [double[]]$Values
    )

    if ($Values.Count -ne 3) {
        throw "중앙값 계산에는 정확히 세 값이 필요하다."
    }
    $sorted = @($Values | Sort-Object)
    return [double]$sorted[1]
}

function Format-MetricValue {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Value
    )

    return $Value.ToString("0.###############", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Get-BaselineMetricRow {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Label,
        [Parameter(Mandatory = $true)]
        [double[]]$Values
    )

    $formatted = @($Values | ForEach-Object { Format-MetricValue $_ })
    $median = Format-MetricValue (Get-MedianValue $Values)
    return "| $Label | $($formatted[0]) | $($formatted[1]) | $($formatted[2]) | $median |"
}

function Write-BaselineDraft {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$ResultDirectories,
        [Parameter(Mandatory = $true)]
        [int]$TargetRps
    )

    if ($ResultDirectories.Count -ne 3) {
        throw "기준선 초안에는 정확히 세 실행 결과가 필요하다."
    }

    $runs = @(
        $ResultDirectories | ForEach-Object {
            $summaryPath = Join-Path $_ "summary.json"
            $metadataPath = Join-Path $_ "metadata.json"
            if (-not (Test-Path -LiteralPath $summaryPath -PathType Leaf)) {
                throw "기준선 원본 summary가 없다: $summaryPath"
            }
            if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
                throw "기준선 원본 metadata가 없다: $metadataPath"
            }

            $summary = [System.IO.File]::ReadAllText($summaryPath) | ConvertFrom-Json
            $metadata = [System.IO.File]::ReadAllText($metadataPath) | ConvertFrom-Json
            if ($summary.profile -ne "load" -or [int]$summary.rps -ne $TargetRps) {
                throw "기준선 원본의 profile 또는 RPS가 일치하지 않는다: $summaryPath"
            }
            if (
                [string]::IsNullOrWhiteSpace([string]$metadata.git.commit) -or
                $metadata.git.commit -eq "unknown" -or
                $metadata.git.dirty -isnot [bool]
            ) {
                throw "기준선 원본의 Git commit 또는 dirty 상태가 유효하지 않다: $metadataPath"
            }

            [pscustomobject]@{
                Directory = $_
                Summary = $summary
                Metadata = $metadata
            }
        }
    )

    $baselineId = "{0}-baseline-{1}" -f (
        [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ")
    ), ([guid]::NewGuid().ToString("N").Substring(0, 8))
    $baselineDirectory = Join-Path $resultsRoot $baselineId
    [void](New-Item -ItemType Directory -Path $baselineDirectory -Force)

    $firstMetadata = $runs[0].Metadata
    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add("# 회원가입 load 기준선 초안")
    [void]$lines.Add("")
    [void]$lines.Add("> 같은 머신·Docker 자원·데이터·k6 버전·부하 조건의 결과끼리만 비교한다. 다른 머신 결과와 운영 TPS·SLO·용량 보증으로 해석하지 않는다.")
    [void]$lines.Add("")
    [void]$lines.Add("## 비교 조건")
    [void]$lines.Add("")
    [void]$lines.Add("- RPS: ``$TargetRps``")
    [void]$lines.Add("- Docker Server: ``$($firstMetadata.dockerVersion)``")
    [void]$lines.Add("- k6: ``$($firstMetadata.images.k6)``")
    [void]$lines.Add("- 각 회차: 앱·성능 DB 초기화, 30초 워밍업 후 10분 load 측정")
    [void]$lines.Add("")
    [void]$lines.Add("| 서비스 | CPU | 메모리 |")
    [void]$lines.Add("|---|---:|---:|")
    [void]$lines.Add("| 애플리케이션 | $($firstMetadata.resources.app.cpus) | $($firstMetadata.resources.app.memory) |")
    [void]$lines.Add("| PostgreSQL | $($firstMetadata.resources.postgres.cpus) | $($firstMetadata.resources.postgres.memory) |")
    [void]$lines.Add("| k6 | $($firstMetadata.resources.k6.cpus) | $($firstMetadata.resources.k6.memory) |")
    [void]$lines.Add("")
    [void]$lines.Add("## 실행 결과")
    [void]$lines.Add("")
    [void]$lines.Add("| 회차 | testid | 원본 | 측정 시작(UTC) | 측정 종료(UTC) | 결과 | Git commit | dirty |")
    [void]$lines.Add("|---:|---|---|---|---|---|---|---|")
    for ($index = 0; $index -lt $runs.Count; $index++) {
        $run = $runs[$index]
        $resultLeaf = Split-Path -Leaf $run.Directory
        [void]$lines.Add(
            "| $($index + 1) | ``$($run.Summary.testid)`` | [summary.json](../$resultLeaf/summary.json) | $($run.Summary.measurementStartedAtUtc) | $($run.Summary.measurementEndedAtUtc) | $($run.Metadata.outcome) | ``$($run.Metadata.git.commit)`` | $($run.Metadata.git.dirty) |"
        )
    }
    [void]$lines.Add("")
    [void]$lines.Add("## 지표별 중앙값")
    [void]$lines.Add("")
    [void]$lines.Add("| 지표 | 1회 | 2회 | 3회 | 중앙값 |")
    [void]$lines.Add("|---|---:|---:|---:|---:|")
    [void]$lines.Add((Get-BaselineMetricRow "요청 수" @($runs | ForEach-Object { [double]$_.Summary.metrics.throughput.count })))
    [void]$lines.Add((Get-BaselineMetricRow "처리량 (req/s)" @($runs | ForEach-Object { [double]$_.Summary.metrics.throughput.rate })))
    [void]$lines.Add((Get-BaselineMetricRow "오류율" @($runs | ForEach-Object { [double]$_.Summary.metrics.errorRate })))
    [void]$lines.Add((Get-BaselineMetricRow "checks 성공률" @($runs | ForEach-Object { [double]$_.Summary.metrics.checksRate })))
    [void]$lines.Add((Get-BaselineMetricRow "dropped iterations" @($runs | ForEach-Object { [double]$_.Summary.metrics.droppedIterations })))
    [void]$lines.Add((Get-BaselineMetricRow "p50 (ms)" @($runs | ForEach-Object { [double]$_.Summary.metrics.responseTimeMs.p50 })))
    [void]$lines.Add((Get-BaselineMetricRow "p95 (ms)" @($runs | ForEach-Object { [double]$_.Summary.metrics.responseTimeMs.p95 })))
    [void]$lines.Add((Get-BaselineMetricRow "p99 (ms)" @($runs | ForEach-Object { [double]$_.Summary.metrics.responseTimeMs.p99 })))

    $draftPath = Join-Path $baselineDirectory "baseline.md"
    Write-Utf8File $draftPath (($lines -join [Environment]::NewLine) + [Environment]::NewLine)
    return $draftPath
}

function Invoke-K6Run {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [int]$TargetRps,
        [Parameter(Mandatory = $true)]
        [string]$TestId,
        [Parameter(Mandatory = $true)]
        [string]$K6TargetBaseUrl,
        [Parameter(Mandatory = $true)]
        [string]$ResultDirectory,
        [Parameter(Mandatory = $true)]
        [string]$MeasurementStartedAt
    )

    $resultLeaf = Split-Path -Leaf $ResultDirectory
    $arguments = $script:ComposeArguments + @(
        "run", "--rm",
        "-e", "TARGET_BASE_URL=$K6TargetBaseUrl",
        "-e", "PROFILE=$Name",
        "-e", "RPS=$TargetRps",
        "-e", "TEST_ID=$TestId",
        "-e", "RESULT_DIR=/results/$resultLeaf",
        "-e", "MEASUREMENT_STARTED_AT=$MeasurementStartedAt",
        "-e", "K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write",
        "-e", "K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99)",
        "-e", "K6_NO_USAGE_REPORT=true",
        "k6", "run",
        "--out", "experimental-prometheus-rw",
        "--tag", "testid=$TestId",
        "/scripts/signup.js"
    )
    Invoke-PerformanceDockerLogged $arguments (Join-Path $ResultDirectory "console.log")
}

function Invoke-ProfileRun {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [int]$TargetRps,
        [Parameter(Mandatory = $true)]
        [string]$K6TargetBaseUrl
    )

    $testId = "{0}-{1}-{2}" -f (
        [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ")
    ), $Name, ([guid]::NewGuid().ToString("N").Substring(0, 8))
    $resultDirectory = Join-Path $resultsRoot $testId
    [void](New-Item -ItemType Directory -Path $resultDirectory -Force)
    Write-Utf8File (Join-Path $resultDirectory "console.log") ""
    Write-Utf8File (Join-Path $resultDirectory "containers-before.json") ""

    $runStartedAt = [DateTime]::UtcNow.ToString("o")
    $measurementStartedAt = ""
    $measurementEndedAt = ""
    $environmentStarted = $false
    $measurementSucceeded = $false
    $stages = @(Get-ProfileStages $Name $TargetRps)

    [System.Environment]::SetEnvironmentVariable(
        "PERFORMANCE_DB_PASSWORD",
        [guid]::NewGuid().ToString("N"),
        [System.EnvironmentVariableTarget]::Process
    )
    [System.Environment]::SetEnvironmentVariable(
        "PERFORMANCE_TEST_ID",
        $testId,
        [System.EnvironmentVariableTarget]::Process
    )

    try {
        Remove-DatabaseEnvironment
        Invoke-PerformanceDocker ($script:ComposeArguments + @("up", "-d", "--build", "postgres", "app"))
        $environmentStarted = $true
        Invoke-PerformanceDocker (
            $script:ComposeArguments + @(
                "run", "--rm",
                "-e", "TARGET_BASE_URL=$K6TargetBaseUrl",
                "k6", "run", "/scripts/health.js"
            )
        )
        Write-Output "애플리케이션과 관측 환경 준비 완료."
        Write-Utf8File (
            (Join-Path $resultDirectory "containers-before.json")
        ) (Get-ContainerEvidence)

        if ($Name -ne "smoke") {
            Invoke-PerformanceDocker (
                $script:ComposeArguments + @(
                    "run", "--rm",
                    "-e", "TARGET_BASE_URL=$K6TargetBaseUrl",
                    "-e", "PROFILE=warmup",
                    "-e", "RPS=1",
                    "-e", "TEST_ID=$testId",
                    "-e", "K6_NO_USAGE_REPORT=true",
                    "k6", "run", "/scripts/signup.js"
                )
            )
            Write-Output "$Name 워밍업 완료."
        }

        $measurementStartedAt = [DateTime]::UtcNow.ToString("o")
        Invoke-K6Run $Name $TargetRps $testId $K6TargetBaseUrl $resultDirectory $measurementStartedAt
        $measurementEndedAt = [DateTime]::UtcNow.ToString("o")
        $measurementSucceeded = $true
    }
    finally {
        if ([string]::IsNullOrWhiteSpace($measurementEndedAt)) {
            $measurementEndedAt = [DateTime]::UtcNow.ToString("o")
        }
        try {
            Write-Utf8File (
                (Join-Path $resultDirectory "containers-after.json")
            ) (Get-ContainerEvidence)
        }
        catch {
            Write-Warning "실행 후 컨테이너 상태를 기록하지 못했다."
            Write-Utf8File (Join-Path $resultDirectory "containers-after.json") ""
        }

        $outcome = if ($measurementSucceeded) { "succeeded" } else { "failed" }
        Write-Observations $resultDirectory $testId $Name $outcome
        Write-RunMetadata $resultDirectory $testId $Name $TargetRps $stages $runStartedAt $measurementStartedAt $measurementEndedAt $outcome

        if ($measurementSucceeded) {
            try {
                Remove-DatabaseEnvironment
                $script:LastResultDirectory = $resultDirectory
                Write-Output "$Name 성공: 앱·성능 DB와 전용 DB 볼륨을 정리했다."
                Write-Output "결과: $resultDirectory"
            }
            catch {
                Write-Warning "$Name 측정은 성공했지만 앱·성능 DB 정리에 실패했다."
                throw
            }
        }
        elseif ($environmentStarted) {
            Write-Warning "$Name 실패 또는 중단: 진단을 위해 앱과 성능 DB를 보존했다."
            Write-Output "결과: $resultDirectory"
            Write-Output "진단 후 앱·DB 정리: docker compose -p $composeProject -f `"$composeFile`" rm -s -f app postgres"
            Write-Output "전용 DB 볼륨 정리: docker volume rm $databaseVolume"
        }
    }
}

$target = Get-AllowedTarget $TargetBaseUrl
if (-not (Test-Path -LiteralPath $composeFile -PathType Leaf)) {
    throw "성능 Compose 파일이 없다: $composeFile"
}
if ($null -eq (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker 명령을 찾을 수 없다."
}

[void](New-Item -ItemType Directory -Path $resultsRoot -Force)
$script:ComposeArguments = @("compose", "-p", $composeProject, "-f", $composeFile)
$k6TargetBaseUrl = if ($target.DnsSafeHost -ieq "app") {
    "http://app:8080"
}
else {
    "http://host.docker.internal:$($target.Port)"
}

$previousPerformanceDbPassword = [System.Environment]::GetEnvironmentVariable(
    "PERFORMANCE_DB_PASSWORD",
    [System.EnvironmentVariableTarget]::Process
)
$previousPerformanceTestId = [System.Environment]::GetEnvironmentVariable(
    "PERFORMANCE_TEST_ID",
    [System.EnvironmentVariableTarget]::Process
)

try {
    Initialize-ObservabilityVolume
    Invoke-PerformanceDocker ($script:ComposeArguments + @("up", "-d", "prometheus", "grafana"))

    $currentRps = if ($Profile -eq "smoke") { 1 } else { [int]$Rps }
    if ($Profile -eq "baseline") {
        $baselineResultDirectories = New-Object System.Collections.Generic.List[string]
        for ($run = 1; $run -le 3; $run++) {
            Write-Output "baseline $run/3 시작."
            $script:LastResultDirectory = $null
            Invoke-ProfileRun "load" $currentRps $k6TargetBaseUrl
            if ([string]::IsNullOrWhiteSpace($script:LastResultDirectory)) {
                throw "baseline $run/3 원본 결과 경로를 확인하지 못했다."
            }
            [void]$baselineResultDirectories.Add($script:LastResultDirectory)
        }
        $baselineDraftPath = Write-BaselineDraft @($baselineResultDirectories) $currentRps
        Write-Output "기준선 초안: $baselineDraftPath"
    }
    else {
        Invoke-ProfileRun $Profile $currentRps $k6TargetBaseUrl
    }

    Write-Output "Prometheus: http://127.0.0.1:19090"
    Write-Output "Grafana: http://127.0.0.1:13000/d/k6-app-performance"
}
finally {
    [System.Environment]::SetEnvironmentVariable(
        "PERFORMANCE_DB_PASSWORD",
        $previousPerformanceDbPassword,
        [System.EnvironmentVariableTarget]::Process
    )
    [System.Environment]::SetEnvironmentVariable(
        "PERFORMANCE_TEST_ID",
        $previousPerformanceTestId,
        [System.EnvironmentVariableTarget]::Process
    )
}
