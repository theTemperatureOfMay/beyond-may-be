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
    $doctorScriptRelativePath = "scripts/harness/harness-doctor.ps1"
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
        "docs/adr/0011-aws-main-continuous-deployment.md",
        "docs/operations/deployment.md",
        "terraform/README.md",
        ".github/PULL_REQUEST_TEMPLATE.md",
        ".github/workflows/ci.yml",
        ".github/workflows/deploy.yml"
    )
    $markdownSources = @(
        "AGENTS.md",
        "CLAUDE.md",
        "README.md",
        "docs/harness/README.md",
        $changeImpactMapRelativePath,
        "docs/harness/completion-criteria.md",
        "docs/harness/safety-policy.md",
        "docs/harness/behavioral-validation.md",
        "docs/harness/setup-roadmap.md",
        "docs/adr/0011-aws-main-continuous-deployment.md",
        "docs/operations/deployment.md",
        "terraform/README.md",
        ".github/PULL_REQUEST_TEMPLATE.md"
    )
    $requiredFiles = @(
        $allowedTextFiles | Where-Object { $_ -ne $changeImpactMapRelativePath }
    )
    $contents = @{}

    $doctorScriptFullPath = Join-Path $resolvedRoot $doctorScriptRelativePath
    if (-not (Test-Path -LiteralPath $doctorScriptFullPath -PathType Leaf)) {
        Add-RuleFailure "HARNESS-DOCTOR" $doctorScriptRelativePath "하네스 환경 doctor 스크립트가 없다."
    }

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

    $routingContractPaths = @(
        "AGENTS.md",
        ".agents/skills/plan/SKILL.md",
        ".agents/skills/to-spec/SKILL.md",
        ".agents/skills/triage/SKILL.md",
        ".agents/skills/diagnosing-bugs/SKILL.md",
        ".agents/skills/wayfinder/SKILL.md",
        ".agents/skills/implement/SKILL.md",
        ".agents/skills/grill/SKILL.md"
    )
    $routingContracts = @{
        "AGENTS.md" = @(
            "작은 작업",
            "일반 구현",
            "큰 작업",
            "(?s)일반 구현.*?plan.*?계획 승인.*?implement",
            "(?s)큰 작업.*?wayfinder.*?to-spec.*?to-tickets.*?implement",
            "(?s)작업 중.*?다시 승인"
        )
        ".agents/skills/plan/SKILL.md" = @("일반 구현", "대화 요구사항", "implement")
        ".agents/skills/to-spec/SKILL.md" = @(
            "(?s)target repository.*?final title.*?full body.*?labels.*?approval",
            "(?s)intended implementation target.*?current implemented state.*?code.*?canonical documentation"
        )
        ".agents/skills/triage/SKILL.md" = @(
            "(?s)After invocation approval.*?Read and query.*?without\s+further\s+approval",
            "(?s)Before any tracker write.*?labels to add or remove.*?full final comment.*?final state",
            "(?s)Ask for explicit approval immediately before writing.*?Apply only the approved batch",
            "(?s)Without approval.*?recommendation and drafts"
        )
        ".agents/skills/diagnosing-bugs/SKILL.md" = @(
            "(?s)boundary is a diagnosis report under.*?\.dev/logs/",
            "(?s)Product fixes.*?permanent regression tests.*?separate user request",
            "(?s)temporary diagnostic change.*?removed.*?pre-existing user changes.*?intact",
            "skill ends at the diagnosis report"
        )
        ".agents/skills/wayfinder/SKILL.md" = @(
            "(?s)After invocation approval.*?Reading and classifying.*?require\s+no\s+additional\s+approval.*?Do not mutate",
            "(?s)Show the final batch exactly.*?labels.*?assignee.*?status.*?dependency edges",
            "(?s)explicit user approval.*?immediately before applying",
            "(?s)Apply only the approved batch.*?changes.*?fresh approval",
            "(?s)Without approval.*?stop without changing external state"
        )
        ".agents/skills/implement/SKILL.md" = @("일반 구현", "승인된 일반 구현")
        ".agents/skills/grill/SKILL.md" = @(
            "grilling", "grill-with-docs", "batch-grill-me", "batch-grill-with-docs",
            "(?s)조합안.*?승인할 때까지.*?질문 절차.*?문서 기록.*?시작하지 않는다",
            "(?s)문서화 경로.*?domain-modeling",
            "(?s)대화에서 종합.*?별도.*?결과 파일.*?만들지 않는다"
        )
    }
    foreach ($routingPath in $routingContractPaths) {
        $routingFullPath = Join-Path $resolvedRoot ($routingPath -replace "/", "\")
        if (-not (Test-Path -LiteralPath $routingFullPath -PathType Leaf)) {
            Add-RuleFailure "ROUTING-CONTRACT" $routingPath "작업 라우팅 계약 원본이 없다."
            continue
        }
        try { $routingText = [System.IO.File]::ReadAllText($routingFullPath, $utf8) }
        catch {
            Add-RuleFailure "ROUTING-CONTRACT" $routingPath "작업 라우팅 계약을 UTF-8로 읽을 수 없다."
            continue
        }
        foreach ($contractPattern in $routingContracts[$routingPath]) {
            if ($routingText -notmatch $contractPattern) {
                Add-RuleFailure "ROUTING-CONTRACT" $routingPath "라우팅 계약이 없다: $contractPattern"
            }
        }
    }

    $teachMeContracts = @{
        ".agents/skills/teach-me/SKILL.md" = @(
            "(?s)지속 학습 기록 관문.*?학습 완료.*?요약 요청.*?중단.*?일시정지.*?주제 전환",
            "기록 후보 있음",
            "기록 생략\s+—\s+이유",
            "부분 학습 기록",
            "(?s)\.dev/learning/teach-me\.md.*?주제.*?관련.*?섹션만 읽.*?현재 대화.*?사용자.*?기존 기록보다 우선",
            "(?s)기록 관문.*?완료.*?선언하지"
        )
        ".agents/skills/teach-me/durable-learning.md" = @(
            "\.dev/learning/teach-me\.md",
            "(?s)학습 기록.*?용어집.*?자료.*?다음 학습 방향",
            "대체됨",
            "(?s)대화.*?초안.*?정확한 경로.*?최종 저장 내용.*?명시적 승인.*?받은 뒤에만.*?파일",
            "(?s)승인 전에는 파일을\s+수정하지.*?빈 파일을 만들지"
        )
    }
    foreach ($teachMePath in $teachMeContracts.Keys) {
        $teachMeFullPath = Join-Path $resolvedRoot ($teachMePath -replace "/", "\")
        if (-not (Test-Path -LiteralPath $teachMeFullPath -PathType Leaf)) {
            Add-RuleFailure "TEACH-ME-CONTRACT" $teachMePath "teach-me 지속 학습 계약 원본이 없다."
            continue
        }
        try { $teachMeText = [System.IO.File]::ReadAllText($teachMeFullPath, $utf8) }
        catch {
            Add-RuleFailure "TEACH-ME-CONTRACT" $teachMePath "teach-me 지속 학습 계약을 UTF-8로 읽을 수 없다."
            continue
        }
        foreach ($contractPattern in $teachMeContracts[$teachMePath]) {
            if ($teachMeText -notmatch $contractPattern) {
                Add-RuleFailure "TEACH-ME-CONTRACT" $teachMePath "지속 학습 계약이 없다: $contractPattern"
            }
        }
    }

    $skillLifecycleContracts = [ordered]@{
        ".agents/skills/ask-matt/SKILL.md" = @(
            "(?is)router only.*?recommend.*?stop",
            "(?is)does not.*?invoke.*?plan.*?spec.*?ticket.*?code change.*?commit.*?external write"
        )
        ".agents/skills/plan/SKILL.md" = @(
            "(?s)개인 작업 기록.*?정본.*?대체하지 않는다",
            "(?s)계획 승인.*?implement.*?외부 쓰기.*?승인하지 않는다"
        )
        ".agents/skills/to-spec/SKILL.md" = @(
            "(?is)invocation approval.*?does\s+not\s+approve\s+publishing.*?separate\s+explicit\s+approval.*?immediately\s+before\s+writing",
            "(?is)exactly one parent spec issue.*?do not apply.*?ready-for-agent.*?to-tickets",
            "(?is)implementation is intended.*?to-tickets.*?one or more implementation.*?tickets.*?do not invoke"
        )
        ".agents/skills/to-tickets/SKILL.md" = @(
            "(?is)breakdown approval.*?does not approve GitHub writes",
            "(?is)exact issue bodies.*?labels.*?parent\s+relationships.*?dependency\s+edges.*?separate\s+explicit\s+approval.*?immediately\s+before\s+writing",
            "(?is)do not edit or close the parent body or state.*?ready-for-agent.*?complete\s+implementation\s+tickets",
            "(?is)publish\s+one\s+GitHub\s+issue\s+per\s+ticket.*?sub-issue.*?native\s+blocking\s+relationships.*?docs/agents/issue-tracker\.md"
        )
        ".agents/skills/wayfinder/SKILL.md" = @(
            "(?is)planning by default.*?Notes.*?explicitly opt into.*?execution tasks.*?Without that override.*?decisions,\s+not\s+deliverables.*?do\s+not\s+create.*?spec.*?implementation\s+plan.*?implementation\s+ticket.*?code\s+change",
            "(?is)map is clear.*?Notes.*?did not carry.*?destination.*?recommend.*?to-spec.*?separate invocation approval.*?approved.*?Notes.*?carried.*?destination.*?verified execution.*?report.*?stop",
            "(?is)stores\s+the\s+map.*?GitHub.*?docs/agents/issue-tracker\.md.*?do\s+not.*?fall\s+back\s+to\s+local\s+files"
        )
        ".agents/skills/implement/SKILL.md" = @(
            "(?s)승인된 일반 구현 plan.*?큰 작업의 implementation ticket.*?Spec은 실행 단위가.*?아니라.*?맥락",
            "(?s)특정 plan 또는 ticket.*?구현을 명확히 요청.*?승인된 입력",
            "(?s)spec만 지정되면 구현하지 않고.*?to-tickets.*?호출 승인",
            "(?s)커밋.*?브랜치.*?push.*?Pull Request.*?GitHub.*?comment.*?close.*?label.*?status.*?별도 요청.*?승인"
        )
    }
    foreach ($contract in $skillLifecycleContracts.GetEnumerator()) {
        $contractFullPath = Join-Path $resolvedRoot ($contract.Key -replace "/", "\")
        if (-not (Test-Path -LiteralPath $contractFullPath -PathType Leaf)) {
            Add-RuleFailure "SKILL-LIFECYCLE-CONTRACT" $contract.Key "작업 생명주기 스킬 원본이 없다."
            continue
        }

        try { $contractText = [System.IO.File]::ReadAllText($contractFullPath, $utf8) }
        catch {
            Add-RuleFailure "SKILL-LIFECYCLE-CONTRACT" $contract.Key "작업 생명주기 계약을 UTF-8로 읽을 수 없다."
            continue
        }

        foreach ($contractPattern in $contract.Value) {
            if ($contractText -notmatch $contractPattern) {
                Add-RuleFailure "SKILL-LIFECYCLE-CONTRACT" $contract.Key "작업 생명주기 계약이 누락됐다."
            }
        }
    }

    $claudeSettingsPath = ".claude/settings.json"
    $claudeSettingsFullPath = Join-Path $resolvedRoot ($claudeSettingsPath -replace "/", "\")
    if (-not (Test-Path -LiteralPath $claudeSettingsFullPath -PathType Leaf)) {
        Add-RuleFailure "CLAUDE-SKILL-CREATOR-PLUGIN" $claudeSettingsPath (
            "Claude Code project plugin 설정이 없다."
        )
    }
    else {
        try {
            $claudeSettingsText = [System.IO.File]::ReadAllText($claudeSettingsFullPath, $utf8)
            $claudeSettings = $claudeSettingsText | ConvertFrom-Json -ErrorAction Stop
            $enabledPluginsProperty = $null
            $skillCreatorProperty = $null
            if ($null -ne $claudeSettings) {
                $enabledPluginsProperty = $claudeSettings.PSObject.Properties["enabledPlugins"]
            }
            if ($null -ne $enabledPluginsProperty -and $null -ne $enabledPluginsProperty.Value) {
                $skillCreatorProperty = $enabledPluginsProperty.Value.PSObject.Properties[
                    "skill-creator@claude-plugins-official"
                ]
            }
            if (
                $null -eq $skillCreatorProperty -or
                $skillCreatorProperty.Value -isnot [bool] -or
                -not $skillCreatorProperty.Value
            ) {
                Add-RuleFailure "CLAUDE-SKILL-CREATOR-PLUGIN" $claudeSettingsPath (
                    "Anthropic 공식 skill-creator project plugin이 true로 활성화되지 않았다."
                )
            }
        }
        catch {
            Add-RuleFailure "CLAUDE-SKILL-CREATOR-PLUGIN" $claudeSettingsPath (
                "Claude Code project plugin 설정을 UTF-8 JSON으로 읽을 수 없다."
            )
        }
    }

    $githubUserInvokedSkills = @(
        "wayfinder",
        "to-spec",
        "to-tickets",
        "triage",
        "setup-skills",
        "gh-create-issue-from-template",
        "gh-create-project-pr"
    )
    foreach ($skillName in $githubUserInvokedSkills) {
        $skillRelativePath = ".agents/skills/$skillName/SKILL.md"
        $skillFullPath = Join-Path $resolvedRoot ($skillRelativePath -replace "/", "\")
        if (-not (Test-Path -LiteralPath $skillFullPath -PathType Leaf)) {
            Add-RuleFailure "GITHUB-SKILL-INVOCATION" $skillRelativePath "GitHub 연동 스킬 원본이 없다."
            continue
        }

        try { $skillText = [System.IO.File]::ReadAllText($skillFullPath, $utf8) }
        catch {
            Add-RuleFailure "GITHUB-SKILL-INVOCATION" $skillRelativePath "스킬 frontmatter를 UTF-8로 읽을 수 없다."
            continue
        }

        $frontmatter = [regex]::Match(
            $skillText,
            "\A---\r?\n(?<body>.*?)\r?\n---",
            [System.Text.RegularExpressions.RegexOptions]::Singleline
        )
        if (
            -not $frontmatter.Success -or
            $frontmatter.Groups["body"].Value -notmatch "(?m)^disable-model-invocation:\s*true\s*$"
        ) {
            Add-RuleFailure "GITHUB-SKILL-INVOCATION" $skillRelativePath "GitHub 연동 스킬은 user-invoked여야 한다."
        }

        $codexMetadataRelativePath = ".agents/skills/$skillName/agents/openai.yaml"
        $codexMetadataFullPath = Join-Path $resolvedRoot ($codexMetadataRelativePath -replace "/", "\")
        if (-not (Test-Path -LiteralPath $codexMetadataFullPath -PathType Leaf)) {
            Add-RuleFailure "GITHUB-SKILL-INVOCATION" $codexMetadataRelativePath "Codex 스킬 호출 정책이 없다."
            continue
        }

        try { $codexMetadataText = [System.IO.File]::ReadAllText($codexMetadataFullPath, $utf8) }
        catch {
            Add-RuleFailure "GITHUB-SKILL-INVOCATION" $codexMetadataRelativePath "Codex 스킬 호출 정책을 UTF-8로 읽을 수 없다."
            continue
        }

        if (
            $codexMetadataText -notmatch "(?m)^policy:\s*$" -or
            $codexMetadataText -notmatch "(?m)^\s+allow_implicit_invocation:\s*false\s*$"
        ) {
            Add-RuleFailure "GITHUB-SKILL-INVOCATION" $codexMetadataRelativePath "Codex 암시적 스킬 호출이 차단되지 않았다."
        }
    }

    $githubInvocationContracts = [ordered]@{
        "AGENTS.md" = @(
            "(?s)GitHub.*user-invoked.*자동 선택·실행하지 않는다.*정확한 스킬명.*스킬을 호출할까요.*명시적.*승인.*시작",
            "(?s)사용자가.*스킬명을 직접 지정.*호출 승인",
            "(?s)스킬 호출 승인은.*읽기·분류·초안 작성.*외부 쓰기 승인을.*대신하지 않는다"
        )
        "docs/harness/safety-policy.md" = @(
            "(?s)스킬 호출 승인은.*읽.*분류.*초안.*외부 쓰기 승인을 대신하지 않는다"
        )
    }
    foreach ($contract in $githubInvocationContracts.GetEnumerator()) {
        $contractFullPath = Join-Path $resolvedRoot ($contract.Key -replace "/", "\")
        if (-not (Test-Path -LiteralPath $contractFullPath -PathType Leaf)) {
            Add-RuleFailure "GITHUB-SKILL-INVOCATION" $contract.Key "GitHub 연동 스킬 호출 승인 계약 원본이 없다."
            continue
        }

        try { $contractText = [System.IO.File]::ReadAllText($contractFullPath, $utf8) }
        catch {
            Add-RuleFailure "GITHUB-SKILL-INVOCATION" $contract.Key "GitHub 연동 스킬 호출 승인 계약을 UTF-8로 읽을 수 없다."
            continue
        }

        foreach ($contractPattern in $contract.Value) {
            if ($contractText -notmatch $contractPattern) {
                Add-RuleFailure "GITHUB-SKILL-INVOCATION" $contract.Key "GitHub 연동 스킬 호출·쓰기 승인 계약이 누락됐다."
            }
        }
    }

    $retiredSkills = @("design-spec", "resolving-merge-conflicts")
    $retiredReferencePaths = @(
        "AGENTS.md",
        "skills-lock.json",
        "docs/harness/skill-catalog.md",
        ".agents/skills/ask-matt/SKILL.md",
        ".agents/skills/ask-matt/SKILL-ko.md"
    )
    foreach ($retiredSkill in $retiredSkills) {
        foreach ($skillRoot in @(".agents/skills", ".claude/skills")) {
            $retiredDirectory = Join-Path $resolvedRoot "$skillRoot/$retiredSkill"
            if (
                (Test-Path -LiteralPath $retiredDirectory -PathType Container) -and
                @(Get-ChildItem -LiteralPath $retiredDirectory -File -Recurse -Force).Count -gt 0
            ) {
                Add-RuleFailure "RETIRED-SKILL" "$skillRoot/$retiredSkill" "제거된 스킬 파일이 다시 추가됐다."
            }
        }

        foreach ($referencePath in $retiredReferencePaths) {
            $referenceFullPath = Join-Path $resolvedRoot ($referencePath -replace "/", "\")
            if (-not (Test-Path -LiteralPath $referenceFullPath -PathType Leaf)) {
                continue
            }
            try { $referenceText = [System.IO.File]::ReadAllText($referenceFullPath, $utf8) }
            catch {
                Add-RuleFailure "RETIRED-SKILL" $referencePath "제거된 스킬 참조 여부를 UTF-8로 확인할 수 없다."
                continue
            }
            if ($referenceText -match [regex]::Escape($retiredSkill)) {
                Add-RuleFailure "RETIRED-SKILL" $referencePath "활성 하네스 문서에 제거된 스킬 참조가 남았다: $retiredSkill"
            }
        }
    }

    $projectSkillsRoot = Join-Path $resolvedRoot ".agents\skills"
    $claudeSkillsRoot = Join-Path $resolvedRoot ".claude\skills"
    if (Test-Path -LiteralPath $projectSkillsRoot -PathType Container) {
        if (-not (Test-Path -LiteralPath $claudeSkillsRoot -PathType Container)) {
            Add-RuleFailure "SKILL-PARITY" ".claude/skills" "프로젝트 스킬 연결 디렉터리가 없다."
        }
        else {
            $projectSkillNames = @(Get-ChildItem -LiteralPath $projectSkillsRoot -Directory -Force | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "SKILL.md") } | ForEach-Object { $_.Name } | Sort-Object)
            $claudeSkillNames = @(Get-ChildItem -LiteralPath $claudeSkillsRoot -Directory -Force | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "SKILL.md") } | ForEach-Object { $_.Name } | Sort-Object)
            $missingClaudeSkills = @($projectSkillNames | Where-Object { $claudeSkillNames -notcontains $_ })
            $orphanClaudeSkills = @($claudeSkillNames | Where-Object { $projectSkillNames -notcontains $_ })
            if ($missingClaudeSkills.Count -gt 0 -or $orphanClaudeSkills.Count -gt 0) {
                $details = @()
                if ($missingClaudeSkills.Count -gt 0) { $details += "Claude 연결 누락: $($missingClaudeSkills -join ', ')" }
                if ($orphanClaudeSkills.Count -gt 0) { $details += "원본 없는 Claude 스킬: $($orphanClaudeSkills -join ', ')" }
                Add-RuleFailure "SKILL-PARITY" ".agents/skills, .claude/skills" ($details -join "; ")
            }
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

            if (-not (Test-Path -LiteralPath $targetFullPath)) {
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

    $deploymentContracts = [ordered]@{
        "AGENTS.md" = "(?s)(?=.*main)(?=.*(merge|병합))(?=.*push)(?=.*workflow_dispatch)(?=.*운영 배포 승인)(?=.*실행 직전)(?=.*복구)"
        "docs/harness/safety-policy.md" = "(?s)(?=.*main)(?=.*(merge|병합))(?=.*push)(?=.*workflow_dispatch)(?=.*운영 배포 승인)(?=.*실행 직전)(?=.*복구)"
        "docs/adr/0011-aws-main-continuous-deployment.md" = "(?s)main.*push.*workflow_dispatch.*운영 배포 승인"
        "docs/operations/deployment.md" = "(?s)main.*push.*workflow_dispatch.*ECS.*(이전|rollback|롤백).*task definition"
        ".github/PULL_REQUEST_TEMPLATE.md" = "(?s)main.*병합.*운영.*자동 배포.*복구"
    }
    foreach ($contract in $deploymentContracts.GetEnumerator()) {
        if ($contents.ContainsKey($contract.Key) -and $contents[$contract.Key] -notmatch $contract.Value) {
            Add-RuleFailure "DEPLOYMENT-CONTRACT" $contract.Key "자동 배포 승인 계약이 누락되거나 현재 결정과 다르다."
        }
    }

    if ($contents.ContainsKey(".github/workflows/deploy.yml")) {
        $deployWorkflowText = $contents[".github/workflows/deploy.yml"]
        $deployWorkflowContracts = [ordered]@{
            "main push trigger" = "(?ms)^on:\s*.*?push:\s*.*?branches:\s*.*?-\s*main\s*$"
            "수동 trigger" = "(?m)^\s{2}workflow_dispatch:\s*$"
            "OIDC 최소 권한" = "(?ms)^permissions:\s*.*?id-token:\s*write\s*.*?contents:\s*read\s*$"
            "테스트 선행" = "(?ms)^\s{2}deploy:\s*.*?needs:\s*test\s*$"
            "AWS OIDC 인증" = "Configure AWS credentials"
            "ECS 배포" = "Deploy to ECS"
        }
        $missingDeployContracts = @()
        foreach ($contract in $deployWorkflowContracts.GetEnumerator()) {
            if ($deployWorkflowText -notmatch $contract.Value) { $missingDeployContracts += $contract.Key }
        }
        if ($missingDeployContracts.Count -gt 0) {
            Add-RuleFailure "DEPLOYMENT-CONTRACT" ".github/workflows/deploy.yml" (
                "자동 배포 workflow 계약이 누락됐다: " + ($missingDeployContracts -join ", ")
            )
        }
        if ($deployWorkflowText -match "(?m)^\s+environment:\s*production\s*$") {
            Add-RuleFailure "DEPLOYMENT-CONTRACT" ".github/workflows/deploy.yml" "ADR-0011과 달리 별도 production Environment 승인 관문이 있다."
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

        $doctorStepPattern = "(?ms)-\s+name:\s*Verify harness environment.*?shell:\s*pwsh.*?run:\s*\.\/scripts/harness/harness-doctor\.ps1(?:\s|$)"
        if ($workflowText -notmatch $doctorStepPattern) {
            Add-RuleFailure "CI-DOCTOR" ".github/workflows/ci.yml" "pwsh harness doctor 검증 단계가 없다."
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
