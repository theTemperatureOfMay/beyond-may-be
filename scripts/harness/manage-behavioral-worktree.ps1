[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Prepare", "Status", "Remove")]
    [string]$Action,
    [string]$RepositoryRoot = "",
    [string]$ScenarioId = "",
    [string]$BaseRef = "HEAD",
    [string]$RunPath = ""
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)

function Stop-WorktreeFailure {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RuleId,
        [Parameter(Mandatory = $true)]
        [string]$Target,
        [Parameter(Mandatory = $true)]
        [string]$Reason
    )

    Write-Output "[FAIL][$RuleId] $Target - $Reason"
    exit 1
}

function Invoke-GitCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $safeRoot = $Root.Replace("\", "/")
        $lines = & git -c "safe.directory=$safeRoot" -C $Root @Arguments 2>$null
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Lines = @($lines)
    }
}

function Resolve-ManagedRunPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string]$CandidatePath
    )

    if ([string]::IsNullOrWhiteSpace($CandidatePath)) {
        Stop-WorktreeFailure "WORKTREE-PATH" $CandidatePath "시험 worktree 경로가 필요하다."
    }

    $runRoot = [System.IO.Path]::GetFullPath((Join-Path $Root ".dev\harness-runs"))
    $resolvedCandidate = [System.IO.Path]::GetFullPath($CandidatePath)
    $candidateParent = [System.IO.Path]::GetDirectoryName($resolvedCandidate)
    $candidateLeaf = [System.IO.Path]::GetFileName($resolvedCandidate)

    if (
        -not $candidateParent.Equals($runRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
        [string]::IsNullOrWhiteSpace($candidateLeaf)
    ) {
        Stop-WorktreeFailure "WORKTREE-PATH" $resolvedCandidate (
            "시험 worktree는 .dev/harness-runs 바로 아래에 있어야 한다."
        )
    }

    return $resolvedCandidate
}

function Get-RunMarker {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ManagedRunPath
    )

    $markerPath = Join-Path $ManagedRunPath ".harness-run.json"
    if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
        Stop-WorktreeFailure "WORKTREE-MARKER" $ManagedRunPath "관리 marker가 없다."
    }

    try {
        $markerText = [System.IO.File]::ReadAllText($markerPath, $utf8WithoutBom)
        $marker = $markerText | ConvertFrom-Json
    }
    catch {
        Stop-WorktreeFailure "WORKTREE-MARKER" $markerPath "관리 marker를 읽을 수 없다."
    }

    if (
        $null -eq $marker.scenarioId -or
        $marker.scenarioId -notmatch "^B(0[1-9]|10)$" -or
        $null -eq $marker.baseCommit -or
        $marker.baseCommit -notmatch "^[0-9a-fA-F]{40}$" -or
        $null -eq $marker.path
    ) {
        Stop-WorktreeFailure "WORKTREE-MARKER" $markerPath "관리 marker 형식이 올바르지 않다."
    }

    $markerResolvedPath = [System.IO.Path]::GetFullPath([string]$marker.path)
    if (-not $markerResolvedPath.Equals($ManagedRunPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        Stop-WorktreeFailure "WORKTREE-MARKER" $markerPath "marker 경로와 대상 경로가 다르다."
    }

    return $marker
}

function Write-RunMarker {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ManagedRunPath,
        [Parameter(Mandatory = $true)]
        [object]$Marker
    )

    $markerPath = Join-Path $ManagedRunPath ".harness-run.json"
    $json = $Marker | ConvertTo-Json
    [System.IO.File]::WriteAllText($markerPath, $json + [Environment]::NewLine, $utf8WithoutBom)
}

function Get-RegisteredWorktreePaths {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root
    )

    $result = Invoke-GitCommand $Root @("worktree", "list", "--porcelain")
    if ($result.ExitCode -ne 0) {
        Stop-WorktreeFailure "WORKTREE-GIT" $Root "등록된 Git worktree 목록을 확인할 수 없다."
    }

    return @(
        $result.Lines |
            Where-Object { $_ -match "^worktree\s+(.+)$" } |
            ForEach-Object { [System.IO.Path]::GetFullPath($Matches[1]) }
    )
}

function Assert-RegisteredWorktree {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string]$ManagedRunPath
    )

    $registered = Get-RegisteredWorktreePaths $Root
    $matched = $registered | Where-Object {
        $_.Equals($ManagedRunPath, [System.StringComparison]::OrdinalIgnoreCase)
    }
    if ($null -eq $matched) {
        Stop-WorktreeFailure "WORKTREE-REGISTRATION" $ManagedRunPath "Git에 등록된 worktree가 아니다."
    }
}

function Get-UnexpectedWorktreeChanges {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string]$ManagedRunPath
    )

    $result = Invoke-GitCommand $Root @(
        "-C",
        $ManagedRunPath,
        "status",
        "--porcelain",
        "--untracked-files=all"
    )
    if ($result.ExitCode -ne 0) {
        Stop-WorktreeFailure "WORKTREE-STATUS" $ManagedRunPath "worktree 변경 상태를 확인할 수 없다."
    }

    return @(
        $result.Lines |
            Where-Object {
                -not [string]::IsNullOrWhiteSpace($_) -and
                $_.Trim() -ne "?? .harness-run.json"
            }
    )
}

try {
    if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
        $RepositoryRoot = Join-Path $PSScriptRoot "..\.."
    }

    $RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
    if (-not (Test-Path -LiteralPath $RepositoryRoot -PathType Container)) {
        Stop-WorktreeFailure "WORKTREE-ROOT" $RepositoryRoot "저장소 루트가 없다."
    }

    $RepositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
    if ($null -eq (Get-Command git -ErrorAction SilentlyContinue)) {
        Stop-WorktreeFailure "WORKTREE-GIT" $RepositoryRoot "Git 실행 파일을 찾을 수 없다."
    }

    $topLevelResult = Invoke-GitCommand $RepositoryRoot @("rev-parse", "--show-toplevel")
    if ($topLevelResult.ExitCode -ne 0 -or $topLevelResult.Lines.Count -eq 0) {
        Stop-WorktreeFailure "WORKTREE-ROOT" $RepositoryRoot "Git repository가 아니다."
    }

    $gitTopLevel = [System.IO.Path]::GetFullPath([string]$topLevelResult.Lines[-1])
    if (-not $gitTopLevel.Equals($RepositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        Stop-WorktreeFailure "WORKTREE-ROOT" $RepositoryRoot "Git 최상위 경로를 repository root로 지정해야 한다."
    }

    switch ($Action) {
        "Prepare" {
            if ($ScenarioId -notmatch "^B(0[1-9]|10)$") {
                Stop-WorktreeFailure "WORKTREE-SCENARIO" $ScenarioId "B01부터 B10까지의 시나리오 ID가 필요하다."
            }

            if ([string]::IsNullOrWhiteSpace($BaseRef)) {
                Stop-WorktreeFailure "WORKTREE-BASE" $BaseRef "기준 commit 또는 ref가 필요하다."
            }

            $commitResult = Invoke-GitCommand $RepositoryRoot @(
                "rev-parse",
                "--verify",
                "$BaseRef`^{commit}"
            )
            if ($commitResult.ExitCode -ne 0 -or $commitResult.Lines.Count -eq 0) {
                Stop-WorktreeFailure "WORKTREE-BASE" $BaseRef "기준 commit을 확인할 수 없다."
            }
            $baseCommit = ([string]$commitResult.Lines[-1]).Trim()

            $guideResult = Invoke-GitCommand $RepositoryRoot @(
                "cat-file",
                "-e",
                "$baseCommit`:docs/harness/behavioral-validation.md"
            )
            if ($guideResult.ExitCode -ne 0) {
                Stop-WorktreeFailure "WORKTREE-BASE" $baseCommit "기준 commit에 behavioral 실행 설명서가 없다."
            }

            $runRoot = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot ".dev\harness-runs"))
            if (-not (Test-Path -LiteralPath $runRoot -PathType Container)) {
                [void](New-Item -ItemType Directory -Path $runRoot -Force)
            }

            $timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
            $runName = "$($ScenarioId.ToLowerInvariant())-$($baseCommit.Substring(0, 8))-$timestamp"
            $managedRunPath = Resolve-ManagedRunPath $RepositoryRoot (Join-Path $runRoot $runName)
            if (Test-Path -LiteralPath $managedRunPath) {
                Stop-WorktreeFailure "WORKTREE-PATH" $managedRunPath "대상 경로가 이미 존재한다."
            }

            $addResult = Invoke-GitCommand $RepositoryRoot @(
                "worktree",
                "add",
                "--detach",
                $managedRunPath,
                $baseCommit
            )
            if ($addResult.ExitCode -ne 0) {
                Stop-WorktreeFailure "WORKTREE-CREATE" $managedRunPath "detached worktree를 만들 수 없다."
            }

            $marker = [ordered]@{
                scenarioId = $ScenarioId
                baseCommit = $baseCommit
                path = $managedRunPath
                createdAt = (Get-Date -Format "yyyy-MM-ddTHH:mm:sszzz")
            }
            Write-RunMarker $managedRunPath $marker

            Write-Output "RUN_PATH=$managedRunPath"
            Write-Output "SCENARIO_ID=$ScenarioId"
            Write-Output "BASE_COMMIT=$baseCommit"
            Write-Output "GUIDE_PATH=$(Join-Path $managedRunPath 'docs\harness\behavioral-validation.md')"
            exit 0
        }

        "Status" {
            $managedRunPath = Resolve-ManagedRunPath $RepositoryRoot $RunPath
            if (-not (Test-Path -LiteralPath $managedRunPath -PathType Container)) {
                Stop-WorktreeFailure "WORKTREE-PATH" $managedRunPath "시험 worktree가 없다."
            }

            $marker = Get-RunMarker $managedRunPath
            Assert-RegisteredWorktree $RepositoryRoot $managedRunPath
            $changes = @(Get-UnexpectedWorktreeChanges $RepositoryRoot $managedRunPath)
            if ($changes.Count -gt 0) {
                Stop-WorktreeFailure "WORKTREE-DIRTY" $managedRunPath (
                    "예상하지 않은 변경이 있어 자동 정리할 수 없다: " + ($changes -join ", ")
                )
            }

            Write-Output "RUN_PATH=$managedRunPath"
            Write-Output "SCENARIO_ID=$($marker.scenarioId)"
            Write-Output "BASE_COMMIT=$($marker.baseCommit)"
            Write-Output "STATUS=CLEAN"
            exit 0
        }

        "Remove" {
            $managedRunPath = Resolve-ManagedRunPath $RepositoryRoot $RunPath
            if (-not (Test-Path -LiteralPath $managedRunPath -PathType Container)) {
                Stop-WorktreeFailure "WORKTREE-PATH" $managedRunPath "시험 worktree가 없다."
            }

            $marker = Get-RunMarker $managedRunPath
            Assert-RegisteredWorktree $RepositoryRoot $managedRunPath
            $changes = @(Get-UnexpectedWorktreeChanges $RepositoryRoot $managedRunPath)
            if ($changes.Count -gt 0) {
                Stop-WorktreeFailure "WORKTREE-DIRTY" $managedRunPath (
                    "예상하지 않은 변경이 있어 삭제하지 않는다: " + ($changes -join ", ")
                )
            }

            $markerPath = Join-Path $managedRunPath ".harness-run.json"
            Remove-Item -LiteralPath $markerPath
            $removeResult = Invoke-GitCommand $RepositoryRoot @(
                "worktree",
                "remove",
                $managedRunPath
            )
            if ($removeResult.ExitCode -ne 0) {
                if (Test-Path -LiteralPath $managedRunPath -PathType Container) {
                    Write-RunMarker $managedRunPath $marker
                }
                Stop-WorktreeFailure "WORKTREE-REMOVE" $managedRunPath "Git worktree를 안전하게 정리하지 못했다."
            }

            Write-Output "REMOVED_RUN_PATH=$managedRunPath"
            exit 0
        }
    }
}
catch {
    Write-Output "[FAIL][WORKTREE-UNEXPECTED] manage-behavioral-worktree.ps1 - $($_.Exception.Message)"
    exit 1
}
