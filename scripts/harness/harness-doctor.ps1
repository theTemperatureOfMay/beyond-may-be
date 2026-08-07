[CmdletBinding()]
param(
    [string]$RepositoryRoot = ""
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$script:Findings = New-Object System.Collections.Generic.List[object]
$utf8 = New-Object System.Text.UTF8Encoding($false, $true)

function Add-Finding {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("PASS", "WARN", "FAIL")]
        [string]$Status,
        [Parameter(Mandatory = $true)]
        [string]$RuleId,
        [Parameter(Mandatory = $true)]
        [string]$Target,
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    $script:Findings.Add(
        [pscustomobject]@{
            Status = $Status
            RuleId = $RuleId
            Target = $Target
            Message = $Message
        }
    )
}

function Invoke-SafeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $false)]
        [string[]]$Arguments = @()
    )

    $command = Get-Command -Name $Name -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandType -eq "Application" } |
        Select-Object -First 1
    if ($null -eq $command) {
        return [pscustomobject]@{
            Found = $false
            ExitCode = $null
            Output = @()
        }
    }

    $commandPath = $command.Path
    if ([string]::IsNullOrWhiteSpace($commandPath)) {
        $commandPath = $command.Source
    }

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $commandPath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
        return [pscustomobject]@{
            Found = $true
            ExitCode = $exitCode
            Output = $output
        }
    }
    catch {
        return [pscustomobject]@{
            Found = $true
            ExitCode = 1
            Output = @($_.Exception.Message)
        }
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $safeRoot = $Root.Replace("\", "/")
    return Invoke-SafeCommand "git" (@("-c", "safe.directory=$safeRoot", "-C", $Root) + $Arguments)
}

function Get-OutputText {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject]$Result
    )

    return (($Result.Output | ForEach-Object { "$_" }) -join [Environment]::NewLine)
}

function Get-Sha256Text {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    $algorithm = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        return ([System.BitConverter]::ToString($algorithm.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $algorithm.Dispose()
    }
}

function Get-SkillDigest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SkillPath
    )

    $rows = @(
        Get-ChildItem -LiteralPath $SkillPath -File -Recurse -Force |
            Sort-Object FullName |
            ForEach-Object {
                $relativePath = $_.FullName.Substring($SkillPath.Length).Replace("\", "/").TrimStart("/")
                $fileHash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
                "$relativePath=$fileHash"
            }
    )

    if ($rows.Count -eq 0) {
        throw "스킬 디렉터리에 파일이 없다: $SkillPath"
    }

    return [pscustomobject]@{
        FileCount = $rows.Count
        Hash = Get-Sha256Text ($rows -join "`n")
    }
}

function Get-TrackedFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $result = Invoke-Git $Root @("ls-files", "--error-unmatch", "--", $RelativePath)
    return $result.Found -and $result.ExitCode -eq 0
}

function Get-IgnoredFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $result = Invoke-Git $Root @("check-ignore", "--quiet", "--", $RelativePath)
    return $result.Found -and $result.ExitCode -eq 0
}

function Add-ToolVersionFinding {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CommandName,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments,
        [Parameter(Mandatory = $true)]
        [string]$RuleId,
        [Parameter(Mandatory = $true)]
        [string]$Target,
        [Parameter(Mandatory = $true)]
        [string]$VersionPattern
    )

    $result = Invoke-SafeCommand $CommandName $Arguments
    if (-not $result.Found) {
        Add-Finding "FAIL" $RuleId $Target "$CommandName 실행 파일이 없다."
        return $null
    }

    $text = Get-OutputText $result
    if ($result.ExitCode -ne 0) {
        Add-Finding "FAIL" $RuleId $Target "$CommandName 버전 확인에 실패했다."
        return $null
    }

    $match = [regex]::Match($text, $VersionPattern)
    if (-not $match.Success) {
        Add-Finding "FAIL" $RuleId $Target "$CommandName 버전을 해석할 수 없다."
        return $null
    }

    $version = $match.Groups["version"].Value
    Add-Finding "PASS" $RuleId $Target "$version"
    return $version
}

try {
    if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
        $RepositoryRoot = Join-Path $PSScriptRoot "../.."
    }

    $RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
    if (-not (Test-Path -LiteralPath $RepositoryRoot -PathType Container)) {
        Add-Finding "FAIL" "ENV-ROOT" $RepositoryRoot "저장소 루트 디렉터리가 없다."
        throw "저장소 루트를 확인할 수 없다."
    }

    $resolvedRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
    Add-Finding "PASS" "ENV-ROOT" $resolvedRoot "저장소 루트를 확인했다."

    $powerShellVersion = [version]$PSVersionTable.PSVersion
    if ($powerShellVersion -lt (New-Object Version(5, 1))) {
        Add-Finding "FAIL" "VERSION-POWERSHELL" "PowerShell" "$powerShellVersion; 최소 5.1이 필요하다."
    }
    else {
        Add-Finding "PASS" "VERSION-POWERSHELL" "PowerShell" $powerShellVersion.ToString()
    }

    [void](Add-ToolVersionFinding "git" @("--version") "VERSION-GIT" "git" "git version (?<version>\S+)")

    $gitWorktree = Invoke-Git $resolvedRoot @("rev-parse", "--is-inside-work-tree")
    if (-not $gitWorktree.Found -or $gitWorktree.ExitCode -ne 0 -or (Get-OutputText $gitWorktree).Trim() -ne "true") {
        Add-Finding "FAIL" "ENV-GIT" $resolvedRoot "Git worktree가 아니다."
    }
    else {
        $head = Invoke-Git $resolvedRoot @("rev-parse", "--short", "HEAD")
        $headText = (Get-OutputText $head).Trim()
        Add-Finding "PASS" "ENV-GIT" $resolvedRoot ("Git worktree; HEAD=" + $headText)
        Add-Finding "PASS" "PERMISSION-REPO" $resolvedRoot "저장소 읽기와 Git 명령 접근이 가능하다."

        $status = Invoke-Git $resolvedRoot @("status", "--porcelain")
        if ($status.ExitCode -eq 0) {
            $statusLines = @($status.Output | Where-Object { -not [string]::IsNullOrWhiteSpace("$_") })
            if ($statusLines.Count -eq 0) {
                Add-Finding "PASS" "ENV-WORKTREE" $resolvedRoot "작업 트리가 깨끗하다."
            }
            else {
                Add-Finding "WARN" "ENV-WORKTREE" $resolvedRoot ("변경 파일 $($statusLines.Count)개가 있다.")
            }
        }
    }

    $buildFile = Join-Path $resolvedRoot "build.gradle"
    $expectedJavaMajor = $null
    if (Test-Path -LiteralPath $buildFile -PathType Leaf) {
        $buildContent = [System.IO.File]::ReadAllText($buildFile, $utf8)
        $javaMatch = [regex]::Match($buildContent, "JavaLanguageVersion\.of\((?<major>\d+)\)")
        if ($javaMatch.Success) {
            $expectedJavaMajor = [int]$javaMatch.Groups["major"].Value
            $javaVersion = Add-ToolVersionFinding "java" @("-version") "VERSION-JAVA" "Java" 'version "(?<version>[^"]+)"'
            if ($null -ne $javaVersion) {
                $majorMatch = [regex]::Match($javaVersion, "^(?<major>\d+)")
                if (-not $majorMatch.Success -or [int]$majorMatch.Groups["major"].Value -ne $expectedJavaMajor) {
                    Add-Finding "FAIL" "VERSION-JAVA-BASELINE" "build.gradle" (
                        "Java $javaVersion 이 감지됐지만 프로젝트 기준은 $expectedJavaMajor이다."
                    )
                }
                else {
                    Add-Finding "PASS" "VERSION-JAVA-BASELINE" "build.gradle" ("Java 기준 $expectedJavaMajor")
                }
            }
        }
        else {
            Add-Finding "WARN" "VERSION-JAVA-BASELINE" "build.gradle" "Java toolchain 기준을 찾지 못했다."
        }

        $wrapperProperties = Join-Path $resolvedRoot "gradle\wrapper\gradle-wrapper.properties"
        if (-not (Test-Path -LiteralPath $wrapperProperties -PathType Leaf)) {
            Add-Finding "FAIL" "VERSION-GRADLE" $wrapperProperties "Gradle Wrapper 기준 파일이 없다."
        }
        else {
            $wrapperContent = [System.IO.File]::ReadAllText($wrapperProperties, $utf8)
            $gradleMatch = [regex]::Match($wrapperContent, "distributionUrl=.*gradle-(?<version>[^/-]+)-(?:bin|all)\.zip")
            if ($gradleMatch.Success) {
                Add-Finding "PASS" "VERSION-GRADLE" "gradle-wrapper.properties" (
                    "Gradle Wrapper " + $gradleMatch.Groups["version"].Value
                )
            }
            else {
                Add-Finding "FAIL" "VERSION-GRADLE" "gradle-wrapper.properties" "Gradle Wrapper 버전을 해석할 수 없다."
            }
        }

        $expectedWrapper = if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
            "gradlew.bat"
        }
        else {
            "gradlew"
        }
        if (Test-Path -LiteralPath (Join-Path $resolvedRoot $expectedWrapper) -PathType Leaf) {
            Add-Finding "PASS" "ENV-GRADLE-WRAPPER" $expectedWrapper "실행 파일이 있다."
        }
        else {
            Add-Finding "FAIL" "ENV-GRADLE-WRAPPER" $expectedWrapper "프로젝트 실행 환경에 필요한 wrapper가 없다."
        }
    }

    if (Test-Path -LiteralPath (Join-Path $resolvedRoot "docker-compose.yml") -PathType Leaf) {
        [void](Add-ToolVersionFinding "docker" @("--version") "VERSION-DOCKER" "Docker" 'Docker version (?<version>[^, ]+)')
        [void](Add-ToolVersionFinding "docker" @("compose", "version") "VERSION-DOCKER-COMPOSE" "Docker Compose" 'Docker Compose version v?(?<version>\S+)')
    }

    if (Test-Path -LiteralPath (Join-Path $resolvedRoot ".env.example") -PathType Leaf) {
        if (Get-IgnoredFile $resolvedRoot ".env") {
            Add-Finding "PASS" "PERMISSION-ENV" ".env" "비밀 환경 파일이 Git ignore 대상이다."
        }
        else {
            Add-Finding "FAIL" "PERMISSION-ENV" ".env" ".env가 Git ignore 대상이 아니다."
        }

        if (Get-TrackedFile $resolvedRoot ".env.example") {
            Add-Finding "PASS" "PERMISSION-ENV-EXAMPLE" ".env.example" "예시 환경 파일이 추적 대상이다."
        }
        else {
            Add-Finding "FAIL" "PERMISSION-ENV-EXAMPLE" ".env.example" "예시 환경 파일이 Git 추적 대상이 아니다."
        }
    }

    if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
        $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
        $principal = New-Object System.Security.Principal.WindowsPrincipal($identity)
        $isAdministrator = $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)
        Add-Finding "PASS" "PERMISSION-IDENTITY" "현재 계정" "현재 계정을 확인했다."
        if ($isAdministrator) {
            Add-Finding "PASS" "PERMISSION-ELEVATION" "Windows" "관리자 권한으로 실행 중이다."
        }
        else {
            Add-Finding "WARN" "PERMISSION-ELEVATION" "Windows" "관리자 권한이 아니다. 프로젝트 파일 검증에는 충분할 수 있다."
        }
    }
    else {
        $identityResult = Invoke-SafeCommand "whoami"
        if ($identityResult.Found -and $identityResult.ExitCode -eq 0) {
            Add-Finding "PASS" "PERMISSION-IDENTITY" "현재 계정" "현재 계정을 확인했다."
        }
        else {
            Add-Finding "WARN" "PERMISSION-IDENTITY" "현재 계정" "현재 계정을 확인할 수 없다."
        }
    }

    $projectSkillsRoot = Join-Path $resolvedRoot ".agents\skills"
    $claudeSkillsRoot = Join-Path $resolvedRoot ".claude\skills"
    if (-not (Test-Path -LiteralPath $projectSkillsRoot -PathType Container)) {
        Add-Finding "FAIL" "SKILL-SOURCE" ".agents/skills" "Codex 원본 스킬 디렉터리가 없다."
    }
    elseif (-not (Test-Path -LiteralPath $claudeSkillsRoot -PathType Container)) {
        Add-Finding "FAIL" "SKILL-PARITY" ".claude/skills" "Claude 연결 스킬 디렉터리가 없다."
    }
    else {
        $projectSkills = @(
            Get-ChildItem -LiteralPath $projectSkillsRoot -Directory -Force |
                Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "SKILL.md") } |
                Sort-Object Name
        )
        $claudeSkills = @(
            Get-ChildItem -LiteralPath $claudeSkillsRoot -Directory -Force |
                Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "SKILL.md") } |
                Sort-Object Name
        )
        $projectSkillNames = @($projectSkills | ForEach-Object { $_.Name })
        $claudeSkillNames = @($claudeSkills | ForEach-Object { $_.Name })
        $missingClaudeSkills = @($projectSkillNames | Where-Object { $claudeSkillNames -notcontains $_ })
        $orphanClaudeSkills = @($claudeSkillNames | Where-Object { $projectSkillNames -notcontains $_ })

        if ($missingClaudeSkills.Count -gt 0 -or $orphanClaudeSkills.Count -gt 0) {
            $details = @()
            if ($missingClaudeSkills.Count -gt 0) {
                $details += "Claude 연결 누락: $($missingClaudeSkills -join ', ')"
            }
            if ($orphanClaudeSkills.Count -gt 0) {
                $details += "원본 없는 Claude 스킬: $($orphanClaudeSkills -join ', ')"
            }
            Add-Finding "FAIL" "SKILL-PARITY" ".agents/skills, .claude/skills" ($details -join "; ")
        }
        else {
            Add-Finding "PASS" "SKILL-PARITY" ".agents/skills, .claude/skills" ("$($projectSkillNames.Count)개 스킬")
        }

        $manifestRows = @()
        foreach ($projectSkill in $projectSkills) {
            $skillName = $projectSkill.Name
            $sourceSkillPath = $projectSkill.FullName
            $sourceSkillRelativePath = ".agents/skills/$skillName"
            try {
                $digest = Get-SkillDigest $sourceSkillPath
                $manifestRows += "$skillName=$($digest.Hash)"
                Add-Finding "PASS" "SKILL-HASH" $sourceSkillRelativePath (
                    "sha256=$($digest.Hash); files=$($digest.FileCount)"
                )
            }
            catch {
                Add-Finding "FAIL" "SKILL-HASH" $sourceSkillRelativePath $_.Exception.Message
                continue
            }

            $claudeSkillPath = Join-Path $claudeSkillsRoot $skillName
            $claudeSkillFile = Join-Path $claudeSkillPath "SKILL.md"
            if (-not (Test-Path -LiteralPath $claudeSkillFile -PathType Leaf)) {
                continue
            }

            try {
                $claudeContent = [System.IO.File]::ReadAllText($claudeSkillFile, $utf8)
                $escapedSkillName = [regex]::Escape($skillName)
                if ($claudeContent -notmatch "\.agents[/\\]skills[/\\]$escapedSkillName[/\\]SKILL\.md") {
                    Add-Finding "FAIL" "SKILL-LINK" (".claude/skills/$skillName/SKILL.md") (
                        "원본 .agents/skills/$skillName/SKILL.md 연결이 없다."
                    )
                }
                else {
                    Add-Finding "PASS" "SKILL-LINK" (".claude/skills/$skillName/SKILL.md") "원본 연결을 확인했다."
                }
            }
            catch {
                Add-Finding "FAIL" "SKILL-LINK" (".claude/skills/$skillName/SKILL.md") "연결 파일을 읽을 수 없다."
            }
        }

        if ($manifestRows.Count -gt 0) {
            Add-Finding "PASS" "SKILL-MANIFEST" ".agents/skills" (
                "sha256=" + (Get-Sha256Text ($manifestRows -join "`n"))
            )
        }
    }
}
catch {
    if ($script:Findings.Count -eq 0) {
        Add-Finding "FAIL" "ENV-UNEXPECTED" "harness-doctor.ps1" $_.Exception.Message
    }
}

$failures = @($script:Findings | Where-Object { $_.Status -eq "FAIL" })
$warnings = @($script:Findings | Where-Object { $_.Status -eq "WARN" })
foreach ($finding in $script:Findings) {
    Write-Output "[$($finding.Status)][$($finding.RuleId)] $($finding.Target) - $($finding.Message)"
}

if ($failures.Count -gt 0) {
    Write-Output "하네스 doctor 실패: $($failures.Count)개 실패, $($warnings.Count)개 경고"
    exit 1
}

Write-Output "하네스 doctor 통과: $($warnings.Count)개 경고"
exit 0
