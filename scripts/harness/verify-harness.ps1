[CmdletBinding()]
param(
    [string]$RepositoryRoot = "",
    [switch]$RequireBehavioralCompletion,
    [ValidatePattern("^B(0[1-9]|10)$")]
    [string]$ScenarioId = ""
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$script:Failures = New-Object System.Collections.Generic.List[object]
$utf8 = New-Object System.Text.UTF8Encoding($false, $true)

function Add-RuleFailure {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RuleId,
        [Parameter(Mandatory = $true)]
        [string]$Target,
        [Parameter(Mandatory = $true)]
        [string]$Reason
    )

    $script:Failures.Add(
        [pscustomobject]@{
            RuleId = $RuleId
            Target = $Target
            Reason = $Reason
        }
    )
}

function Get-NormalizedRelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return $Path.Replace("\", "/").TrimStart("./")
}

function Test-ProtectedPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $normalized = (Get-NormalizedRelativePath $RelativePath).ToLowerInvariant()
    $leaf = [System.IO.Path]::GetFileName($normalized)

    if ($leaf -eq ".env.example") {
        return $false
    }

    if ($leaf -eq ".env" -or $leaf.StartsWith(".env.")) {
        return $true
    }

    if ($normalized -match "(^|[\/_-])(credentials?|secrets?|tokens?|private-key)([\/._-]|$)") {
        return $true
    }

    if ($leaf -match "\.(key|pem|p12|pfx)$" -or $leaf -in @("id_rsa", "id_ed25519")) {
        return $true
    }

    return $false
}

function ConvertTo-MarkdownAnchor {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Heading
    )

    $value = $Heading.Trim().ToLowerInvariant()
    $value = [regex]::Replace($value, "<[^>]+>", "")
    $value = $value.Replace('`', "").Replace("*", "").Replace("_", "")
    $value = [regex]::Replace($value, "[^\p{L}\p{Nd}\s-]", "")
    $value = [regex]::Replace($value, "\s+", "-")
    return $value.Trim("-")
}

function Invoke-GitForExitCode {
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
        & git -c "safe.directory=$safeRoot" -C $Root @Arguments *> $null
        return $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

try {
    if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
        $RepositoryRoot = Join-Path $PSScriptRoot "../.."
    }

    $RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
    if (-not (Test-Path -LiteralPath $RepositoryRoot -PathType Container)) {
        Add-RuleFailure "ENV-ROOT" $RepositoryRoot "저장소 루트 디렉터리가 없다."
        throw "저장소 루트를 확인할 수 없다."
    }

    $resolvedRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
    $rootPrefix = $resolvedRoot.TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar

    $productVerifier = Join-Path $PSScriptRoot "verify-product-knowledge.ps1"
    if (-not (Test-Path -LiteralPath $productVerifier -PathType Leaf)) {
        Add-RuleFailure "PRODUCT-KNOWLEDGE" "scripts/harness/verify-product-knowledge.ps1" (
            "제품 지식 베이스 검증 스크립트가 없다."
        )
    }
    else {
        $currentPowerShell = (Get-Process -Id $PID).Path
        $productArguments = @("-NoProfile")
        if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
            $productArguments += @("-ExecutionPolicy", "Bypass")
        }
        $productArguments += @(
            "-File",
            $productVerifier,
            "-RepositoryRoot",
            $resolvedRoot
        )

        $previousErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "Continue"
            $productOutput = & $currentPowerShell @productArguments 2>&1
            $productExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }

        foreach ($line in $productOutput) {
            Write-Output $line
        }
        if ($productExitCode -ne 0) {
            Add-RuleFailure "PRODUCT-KNOWLEDGE" "docs/product" (
                "제품 지식 베이스 검증이 실패했다."
            )
        }
    }

    $changeImpactMapRelativePath = "docs/harness/change-impact-map.md"
    $allowedTextFiles = @(
        "AGENTS.md",
        "CLAUDE.md",
        "README.md",
        "docs/harness/README.md",
        $changeImpactMapRelativePath,
        "docs/harness/completion-criteria.md",
        "docs/harness/safety-policy.md",
        "docs/harness/behavioral-validation.md",
        "docs/harness/setup-roadmap.md",
        ".github/workflows/ci.yml"
    )
    $markdownSources = @(
        "AGENTS.md",
        "CLAUDE.md",
        "docs/harness/README.md",
        $changeImpactMapRelativePath,
        "docs/harness/completion-criteria.md",
        "docs/harness/safety-policy.md",
        "docs/harness/behavioral-validation.md",
        "docs/harness/setup-roadmap.md"
    )
    $requiredFiles = @(
        $allowedTextFiles | Where-Object { $_ -ne $changeImpactMapRelativePath }
    )
    $contents = @{}

    $changeImpactMapFullPath = Join-Path $resolvedRoot $changeImpactMapRelativePath
    if (-not (Test-Path -LiteralPath $changeImpactMapFullPath -PathType Leaf)) {
        Add-RuleFailure "CHANGE-IMPACT-FILE" $changeImpactMapRelativePath "변경 영향 지도가 없다."
    }
    elseif (Test-ProtectedPath $changeImpactMapRelativePath) {
        Add-RuleFailure "CHANGE-IMPACT-FILE" $changeImpactMapRelativePath "보호 경로는 변경 영향 지도 검사 입력으로 읽을 수 없다."
    }
    else {
        try {
            $contents[$changeImpactMapRelativePath] = [System.IO.File]::ReadAllText(
                $changeImpactMapFullPath,
                $utf8
            )
        }
        catch {
            Add-RuleFailure "CHANGE-IMPACT-FILE" $changeImpactMapRelativePath "UTF-8 문서로 읽을 수 없다."
        }
    }

    foreach ($relativePath in $requiredFiles) {
        $fullPath = Join-Path $resolvedRoot $relativePath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            Add-RuleFailure "COMMON-FILES" $relativePath "필수 하네스 파일이 없다."
            continue
        }

        if (Test-ProtectedPath $relativePath) {
            Add-RuleFailure "COMMON-FILES" $relativePath "보호 경로는 검증 입력으로 읽을 수 없다."
            continue
        }

        try {
            $contents[$relativePath] = [System.IO.File]::ReadAllText($fullPath, $utf8)
        }
        catch {
            Add-RuleFailure "COMMON-FILES" $relativePath "UTF-8 문서로 읽을 수 없다."
        }
    }

    if ($contents.ContainsKey("CLAUDE.md")) {
        $claudeLines = $contents["CLAUDE.md"] -split "\r?\n"
        $firstInstruction = $claudeLines |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Select-Object -First 1

        if ($firstInstruction.Trim() -ne "@AGENTS.md") {
            Add-RuleFailure "COMMON-CLAUDE-IMPORT" "CLAUDE.md" "첫 유효 지시가 @AGENTS.md가 아니다."
        }

        if ($contents["CLAUDE.md"] -notmatch "공통.*원본.*AGENTS\.md") {
            Add-RuleFailure "COMMON-CLAUDE-IMPORT" "CLAUDE.md" "공통 규칙 원본 설명이 없다."
        }

        if ($contents["CLAUDE.md"] -match "(?m)^##\s+(보호 영역|안전과 승인|Git과 외부 작업)") {
            Add-RuleFailure "COMMON-CLAUDE-DUPLICATION" "CLAUDE.md" "공통 안전 규칙 section을 중복한다."
        }
    }

    foreach ($sourceRelativePath in $markdownSources) {
        if (-not $contents.ContainsKey($sourceRelativePath)) {
            continue
        }

        $sourceFullPath = Join-Path $resolvedRoot $sourceRelativePath
        $sourceDirectory = Split-Path -Parent $sourceFullPath
        $linkMatches = [regex]::Matches(
            $contents[$sourceRelativePath],
            "\[[^\]]+\]\((?<target><[^>]+>|[^)\s]+)(?:\s+`"[^`"]*`")?\)"
        )

        foreach ($linkMatch in $linkMatches) {
            $target = $linkMatch.Groups["target"].Value.Trim("<", ">")
            if ($target -match "^(https?|mailto):") {
                continue
            }

            $targetParts = $target.Split(@("#"), 2, [System.StringSplitOptions]::None)
            $targetPathPart = [System.Uri]::UnescapeDataString($targetParts[0])
            $anchor = ""
            if ($targetParts.Count -eq 2) {
                $anchor = [System.Uri]::UnescapeDataString($targetParts[1]).ToLowerInvariant()
            }

            if ([string]::IsNullOrWhiteSpace($targetPathPart)) {
                $targetFullPath = $sourceFullPath
            }
            elseif ([System.IO.Path]::IsPathRooted($targetPathPart)) {
                Add-RuleFailure "LINK-SCOPE" $sourceRelativePath "저장소 상대 경로가 아닌 링크가 있다: $target"
                continue
            }
            else {
                $targetFullPath = [System.IO.Path]::GetFullPath((Join-Path $sourceDirectory $targetPathPart))
            }

            if (
                $targetFullPath -ne $resolvedRoot -and
                -not $targetFullPath.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)
            ) {
                Add-RuleFailure "LINK-SCOPE" $sourceRelativePath "저장소 밖을 가리키는 링크가 있다: $target"
                continue
            }

            $targetRelativePath = $targetFullPath.Substring($rootPrefix.Length)
            if (Test-ProtectedPath $targetRelativePath) {
                Add-RuleFailure "LINK-PROTECTED" $sourceRelativePath "보호 경로를 가리키는 링크는 열지 않는다: $target"
                continue
            }

            if (-not (Test-Path -LiteralPath $targetFullPath -PathType Leaf)) {
                Add-RuleFailure "LINK-TARGET" $sourceRelativePath "링크 대상 파일이 없다: $target"
                continue
            }

            if (-not [string]::IsNullOrWhiteSpace($anchor)) {
                $normalizedTarget = Get-NormalizedRelativePath $targetRelativePath
                if (-not ($allowedTextFiles -contains $normalizedTarget)) {
                    Add-RuleFailure "LINK-ANCHOR-SCOPE" $sourceRelativePath "허용 목록 밖 문서의 anchor는 읽지 않는다: $target"
                    continue
                }

                if ($contents.ContainsKey($normalizedTarget)) {
                    $targetContent = $contents[$normalizedTarget]
                }
                else {
                    try {
                        $targetContent = [System.IO.File]::ReadAllText($targetFullPath, $utf8)
                        $contents[$normalizedTarget] = $targetContent
                    }
                    catch {
                        Add-RuleFailure "LINK-ANCHOR" $sourceRelativePath "anchor 대상 문서를 읽을 수 없다: $target"
                        continue
                    }
                }

                $anchors = [regex]::Matches(
                    $targetContent,
                    "(?m)^\s{0,3}#{1,6}\s+(?<heading>.+?)\s*#*\s*$"
                ) | ForEach-Object {
                    ConvertTo-MarkdownAnchor $_.Groups["heading"].Value
                }

                if (-not ($anchors -contains $anchor)) {
                    Add-RuleFailure "LINK-ANCHOR" $sourceRelativePath "링크 anchor가 없다: $target"
                }
            }
        }
    }

    if ($contents.ContainsKey($changeImpactMapRelativePath)) {
        $impactMapText = $contents[$changeImpactMapRelativePath]
        $impactMapSchemaPattern = '(?m)^\|\s*change_type\s*\|\s*trigger_examples\s*\|\s*canonical_sources\s*\|\s*candidate_impacts\s*\|\s*required_checks\s*\|\s*stop_conditions\s*\|'
        if (
            $impactMapText -notmatch '(?m)^##\s+변경 유형별 영향\s*$' -or
            $impactMapText -notmatch $impactMapSchemaPattern
        ) {
            Add-RuleFailure 'CHANGE-IMPACT-SCHEMA' $changeImpactMapRelativePath '변경 영향 지도에 heading 또는 필수 field 표가 없다.'
        }

        $requiredImpactTypes = @(
            'product',
            'api',
            'data',
            'architecture',
            'security',
            'harness'
        )
        $missingImpactTypes = @(
            $requiredImpactTypes | Where-Object {
                $impactTypePattern = '(?m)^\|\s*`' + [regex]::Escape($_) + '`\s*\|'
                $impactMapText -notmatch $impactTypePattern
            }
        )
        if ($missingImpactTypes.Count -gt 0) {
            Add-RuleFailure 'CHANGE-IMPACT-TYPES' $changeImpactMapRelativePath (
                '변경 영향 유형이 누락됐다: ' + ($missingImpactTypes -join ', ')
            )
        }
    }

    if ($contents.ContainsKey("AGENTS.md") -and $contents.ContainsKey("docs/harness/safety-policy.md")) {
        $safetyText = [regex]::Replace(
            $contents["AGENTS.md"] + " " + $contents["docs/harness/safety-policy.md"],
            "\s+",
            " "
        )
        $safetyContracts = [ordered]@{
            "보호 파일 직접 접근 금지" = "\.env.*직접.*읽기.*쓰기.*출력하지 않는다"
            ".env.example 예외" = "\.env\.example.*(예외|placeholder)"
            "Testcontainers 기반 시험" = "Testcontainers"
            "외부 쓰기 직전 확인" = "외부 쓰기.*실행 직전.*(확인|재확인)"
            "외부 콘텐츠 불신" = "(웹 문서|외부 콘텐츠).*(실행 권한이 아니다|실행 권한이 없는)"
            "신규 도구 권한 검토" = "(새|신규) MCP.*공급자.*출처.*버전.*권한.*(승인|검토)"
            "기술적 차단 한계" = "기술적.*(차단|보호).*(보장하지 않는다|없)"
        }
        $missingSafetyContracts = @()
        foreach ($contract in $safetyContracts.GetEnumerator()) {
            if ($safetyText -notmatch $contract.Value) {
                $missingSafetyContracts += $contract.Key
            }
        }

        if ($missingSafetyContracts.Count -gt 0) {
            Add-RuleFailure "SAFETY-CONTRACT" "AGENTS.md, docs/harness/safety-policy.md" (
                "핵심 안전 계약이 누락됐다: " + ($missingSafetyContracts -join ", ")
            )
        }
    }

    $gitAvailable = $null -ne (Get-Command git -ErrorAction SilentlyContinue)
    if (-not $gitAvailable) {
        Add-RuleFailure "REPO-GIT" $resolvedRoot "Git 실행 파일을 찾을 수 없다."
    }
    else {
        $insideWorktreeExitCode = Invoke-GitForExitCode $resolvedRoot @("rev-parse", "--is-inside-work-tree")
        if ($insideWorktreeExitCode -ne 0) {
            Add-RuleFailure "REPO-GIT" $resolvedRoot "Git worktree가 아니다."
        }
        else {
            $ignoreExitCode = Invoke-GitForExitCode $resolvedRoot @("check-ignore", "--quiet", "--", ".env")
            if ($ignoreExitCode -ne 0) {
                Add-RuleFailure "REPO-ENV-IGNORED" ".env" ".env가 Git ignore 대상이 아니다."
            }

            $exampleExitCode = Invoke-GitForExitCode $resolvedRoot @("ls-files", "--error-unmatch", "--", ".env.example")
            if ($exampleExitCode -ne 0) {
                Add-RuleFailure "REPO-ENV-EXAMPLE" ".env.example" ".env.example이 Git 추적 대상이 아니다."
            }
        }
    }

    $behaviorRows = @()
    if ($contents.ContainsKey("docs/harness/completion-criteria.md")) {
        $completionText = $contents["docs/harness/completion-criteria.md"]
        $operationHeadings = @("#### 관리 역할", "#### 갱신 조건", "#### 재검사 조건")
        $missingOperationHeadings = @(
            $operationHeadings | Where-Object {
                $completionText -notmatch ("(?m)^" + [regex]::Escape($_) + "\s*$")
            }
        )
        if ($missingOperationHeadings.Count -gt 0) {
            Add-RuleFailure "OPERATIONS-STRUCTURE" "docs/harness/completion-criteria.md" (
                "운영 section이 누락됐다: " + ($missingOperationHeadings -join ", ")
            )
        }

        $normalizedCompletion = [regex]::Replace($completionText, "\s+", " ")
        $operationContracts = [ordered]@{
            "하네스 관리자" = "하네스 관리자.*(Admin|배포)"
            "변경 작성자" = "변경 작성자"
            "검토자" = "(검토자|하네스 관리자 검토)"
            "정기 전체 회귀 제외" = "정기 전체 회귀 시험은 실행하지 않는다"
            "완성 전 전체 실행" = "(완성 판정|required approval).*(Codex|10개).*전체"
            "Claude 정적·Codex 행동 범위" = "Claude Code.*정적.*Codex"
        }
        $missingOperationContracts = @()
        foreach ($contract in $operationContracts.GetEnumerator()) {
            if ($normalizedCompletion -notmatch $contract.Value) {
                $missingOperationContracts += $contract.Key
            }
        }
        if ($missingOperationContracts.Count -gt 0) {
            Add-RuleFailure "OPERATIONS-CONTRACT" "docs/harness/completion-criteria.md" (
                "운영 계약이 누락됐다: " + ($missingOperationContracts -join ", ")
            )
        }

        $behaviorLines = $completionText -split "\r?\n" |
            Where-Object { $_ -match "^\|\s*B\d{2}\s*\|" }
        $allowedResults = @("미평가", "통과", "조건부 통과", "실패", "안전 실패")

        foreach ($line in $behaviorLines) {
            $cells = @($line.Trim().Trim("|").Split("|") | ForEach-Object { $_.Trim() })
            if ($cells.Count -ne 5) {
                Add-RuleFailure "BEHAVIOR-TABLE" "docs/harness/completion-criteria.md" "시나리오 행의 열 개수가 5개가 아니다."
                continue
            }

            $row = [pscustomobject]@{
                Id = $cells[0]
                Result = $cells[3]
                Evidence = $cells[4]
            }
            $behaviorRows += $row
            if (-not ($allowedResults -contains $row.Result)) {
                Add-RuleFailure "BEHAVIOR-RESULT-VALUE" "docs/harness/completion-criteria.md" (
                    "$($row.Id)의 결과 값이 허용 목록에 없다: $($row.Result)"
                )
            }
        }

        $expectedIds = 1..10 | ForEach-Object { "B{0:D2}" -f $_ }
        $rowIds = @($behaviorRows | ForEach-Object { $_.Id })
        $rowIdSetMatches = (
            $rowIds.Count -eq 10 -and
            @($rowIds | Select-Object -Unique).Count -eq 10 -and
            @($expectedIds | Where-Object { $rowIds -notcontains $_ }).Count -eq 0
        )
        if (-not $rowIdSetMatches) {
            Add-RuleFailure "BEHAVIOR-SCENARIO-SET" "docs/harness/completion-criteria.md" "결과표에 B01부터 B10까지 정확히 한 번씩 있어야 한다."
        }

        if ($contents.ContainsKey("docs/harness/behavioral-validation.md")) {
            $guideIds = @(
                [regex]::Matches(
                    $contents["docs/harness/behavioral-validation.md"],
                    "(?m)^##\s+(B\d{2})\b"
                ) | ForEach-Object {
                    $_.Groups[1].Value
                }
            )
            $guideIdSetMatches = (
                $guideIds.Count -eq 10 -and
                @($guideIds | Select-Object -Unique).Count -eq 10 -and
                @($expectedIds | Where-Object { $guideIds -notcontains $_ }).Count -eq 0
            )
            if (-not $guideIdSetMatches) {
                Add-RuleFailure "BEHAVIOR-SCENARIO-SET" "docs/harness/behavioral-validation.md" "실행 설명서에 B01부터 B10까지 정확히 한 번씩 있어야 한다."
            }

            $behaviorScopeText = [regex]::Replace(
                $completionText + " " + $contents["docs/harness/behavioral-validation.md"],
                "\s+",
                " "
            )
            if (
                $behaviorScopeText -notmatch "사용자가 요청할 때만" -or
                $behaviorScopeText -notmatch "Claude Code.*정적" -or
                $behaviorScopeText -notmatch "Codex"
            ) {
                Add-RuleFailure "BEHAVIOR-SCOPE" "docs/harness/behavioral-validation.md" "선택 실행, Codex 대상과 Claude 정적 범위가 일치하지 않는다."
            }
        }

        $requiredBehaviorIds = @()
        if (-not [string]::IsNullOrWhiteSpace($ScenarioId)) {
            $requiredBehaviorIds = @($ScenarioId)
        }
        elseif ($RequireBehavioralCompletion) {
            $requiredBehaviorIds = $expectedIds
        }

        foreach ($requiredId in $requiredBehaviorIds) {
            $row = $behaviorRows | Where-Object { $_.Id -eq $requiredId } | Select-Object -First 1
            if (
                $null -eq $row -or
                $row.Result -ne "통과" -or
                $row.Evidence -notmatch "^\d{4}-\d{2}-\d{2}.+\S"
            ) {
                Add-RuleFailure "BEHAVIOR-COMPLETE" "docs/harness/completion-criteria.md" (
                    "$requiredId 결과는 통과이며 날짜와 근거가 있어야 한다."
                )
            }
        }
    }

    if ($contents.ContainsKey(".github/workflows/ci.yml")) {
        $workflowText = $contents[".github/workflows/ci.yml"]
        $semanticStepPattern = "(?ms)-\s+name:\s*Verify harness semantics.*?shell:\s*pwsh.*?run:\s*\./scripts/harness/verify-harness\.ps1(?:\s|$)"
        if (
            $workflowText -notmatch "(?m)^\s{2}build:\s*$" -or
            $workflowText -notmatch $semanticStepPattern
        ) {
            Add-RuleFailure "CI-SEMANTIC" ".github/workflows/ci.yml" "기존 build job에 pwsh semantic 검증 단계가 없다."
        }

        if (
            $workflowText -match "RequireBehavioralCompletion" -or
            $workflowText -match "manage-behavioral-worktree" -or
            $workflowText -match "-ScenarioId"
        ) {
            Add-RuleFailure "CI-BEHAVIORAL" ".github/workflows/ci.yml" "CI가 behavioral 검증을 자동 호출하면 안 된다."
        }
    }
}
catch {
    if ($script:Failures.Count -eq 0) {
        Add-RuleFailure "ENV-UNEXPECTED" "verify-harness.ps1" $_.Exception.Message
    }
}

if ($script:Failures.Count -gt 0) {
    foreach ($failure in $script:Failures) {
        Write-Output "[FAIL][$($failure.RuleId)] $($failure.Target) - $($failure.Reason)"
    }
    Write-Output "하네스 semantic 검증 실패: $($script:Failures.Count)개"
    exit 1
}

Write-Output "하네스 semantic 검증 통과."
exit 0
