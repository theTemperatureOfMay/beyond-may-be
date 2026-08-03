[CmdletBinding()]
param(
    [string]$RepositoryRoot = ""
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

function Read-Utf8File {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FullPath,
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    try {
        return [System.IO.File]::ReadAllText($FullPath, $utf8)
    }
    catch {
        Add-RuleFailure "PRODUCT-UTF8" $RelativePath "UTF-8 문서로 읽을 수 없다."
        return $null
    }
}

try {
    if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
        $RepositoryRoot = Join-Path $PSScriptRoot "../.."
    }

    $RepositoryRoot = [System.IO.Path]::GetFullPath($RepositoryRoot)
    if (-not (Test-Path -LiteralPath $RepositoryRoot -PathType Container)) {
        Add-RuleFailure "PRODUCT-ROOT" $RepositoryRoot "저장소 루트 디렉터리가 없다."
        throw "저장소 루트를 확인할 수 없다."
    }

    $resolvedRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
    $rootPrefix = $resolvedRoot.TrimEnd("\", "/") + [System.IO.Path]::DirectorySeparatorChar
    $productRoot = Join-Path $resolvedRoot "docs\product"

    $featureDocuments = @(
        "docs/product/features/onboarding-preference.md",
        "docs/product/features/place-selection.md",
        "docs/product/features/course-design.md",
        "docs/product/features/exploration.md",
        "docs/product/features/travel-records.md",
        "docs/product/features/common-policies.md"
    )
    $expectedFeatureIds = @(
        "1.1.1", "1.1.2", "1.1.3", "1.2.1", "1.2.2", "1.2.3", "1.3.1",
        "2.1.1", "2.1.2", "2.1.3", "2.2.1", "2.2.2", "2.2.3", "2.2.4",
        "2.3.1", "2.3.2",
        "3.1.0", "3.1.1", "3.1.2", "3.2.1", "3.2.2", "3.3.1", "3.3.2",
        "3.3.3",
        "4.1.1", "4.2.1", "4.2.2", "4.2.3", "4.2.4", "4.3.1", "4.3.2",
        "4.3.3", "4.3.4", "4.4.1", "4.4.2",
        "5.1.1", "5.1.2", "5.2.1", "5.2.2",
        "6.1.1", "6.1.2", "6.1.3", "6.1.4", "6.1.5", "6.3.1", "6.4.1",
        "6.5.1", "6.5.2", "6.5.3"
    )
    $requiredDocuments = @(
        "docs/product/feature-spec.md",
        "docs/product/mvp.md",
        "docs/product/open-questions.md"
    ) + $featureDocuments
    $contents = @{}

    foreach ($relativePath in $requiredDocuments) {
        $fullPath = Join-Path $resolvedRoot $relativePath
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            Add-RuleFailure "PRODUCT-FILE" $relativePath "필수 제품 지식 베이스 문서가 없다."
            continue
        }

        if (Test-ProtectedPath $relativePath) {
            Add-RuleFailure "PRODUCT-FILE" $relativePath "보호 경로는 검증 입력으로 읽을 수 없다."
            continue
        }

        $content = Read-Utf8File $fullPath $relativePath
        if ($null -ne $content) {
            $contents[$relativePath] = $content
        }
    }

    $featureRows = @()
    foreach ($relativePath in $featureDocuments) {
        if (-not $contents.ContainsKey($relativePath)) {
            continue
        }

        $matches = [regex]::Matches(
            $contents[$relativePath],
            "(?m)^####\s+(?<id>\d+\.\d+\.\d+)\s+(?<title>.+?)\s*$"
        )
        foreach ($match in $matches) {
            $featureRows += [pscustomobject]@{
                Id = $match.Groups["id"].Value
                Title = $match.Groups["title"].Value.Trim()
                Source = $relativePath
            }
        }
    }

    $duplicateIds = @(
        $featureRows |
            Group-Object Id |
            Where-Object { $_.Count -gt 1 } |
            ForEach-Object { $_.Name }
    )
    if ($duplicateIds.Count -gt 0) {
        Add-RuleFailure "PRODUCT-ID-DUPLICATE" "docs/product/features" (
            "소기능 ID가 중복됐다: " + ($duplicateIds -join ", ")
        )
    }

    $actualFeatureIds = @($featureRows | ForEach-Object { $_.Id } | Select-Object -Unique)
    $missingExpectedIds = @(
        $expectedFeatureIds | Where-Object { $actualFeatureIds -notcontains $_ }
    )
    $unexpectedFeatureIds = @(
        $actualFeatureIds | Where-Object { $expectedFeatureIds -notcontains $_ }
    )
    if (
        $featureRows.Count -ne $expectedFeatureIds.Count -or
        $missingExpectedIds.Count -gt 0 -or
        $unexpectedFeatureIds.Count -gt 0
    ) {
        $countDetails = @(
            "현재 기준선 $($expectedFeatureIds.Count)개, 실제 $($featureRows.Count)개"
        )
        if ($missingExpectedIds.Count -gt 0) {
            $countDetails += "누락: $($missingExpectedIds -join ', ')"
        }
        if ($unexpectedFeatureIds.Count -gt 0) {
            $countDetails += "추가: $($unexpectedFeatureIds -join ', ')"
        }
        Add-RuleFailure "PRODUCT-ID-COUNT" "docs/product/features" (
            $countDetails -join "; "
        )
    }

    if ($contents.ContainsKey("docs/product/mvp.md")) {
        $mvpIds = @(
            [regex]::Matches(
                $contents["docs/product/mvp.md"],
                '(?m)^\|\s*`?(?<id>\d+\.\d+\.\d+)`?\s*\|'
            ) | ForEach-Object {
                $_.Groups["id"].Value
            }
        )
        $featureIds = @($featureRows | ForEach-Object { $_.Id } | Select-Object -Unique)
        $missingFromMvp = @($featureIds | Where-Object { $mvpIds -notcontains $_ })
        $missingFromFeatures = @($mvpIds | Where-Object { $featureIds -notcontains $_ })
        $duplicateMvpIds = @(
            $mvpIds |
                Group-Object |
                Where-Object { $_.Count -gt 1 } |
                ForEach-Object { $_.Name }
        )

        if (
            $mvpIds.Count -ne $featureIds.Count -or
            $missingFromMvp.Count -gt 0 -or
            $missingFromFeatures.Count -gt 0 -or
            $duplicateMvpIds.Count -gt 0
        ) {
            $details = @()
            if ($missingFromMvp.Count -gt 0) {
                $details += "MVP 누락: $($missingFromMvp -join ', ')"
            }
            if ($missingFromFeatures.Count -gt 0) {
                $details += "상세 명세 누락: $($missingFromFeatures -join ', ')"
            }
            if ($duplicateMvpIds.Count -gt 0) {
                $details += "MVP 중복: $($duplicateMvpIds -join ', ')"
            }
            if ($details.Count -eq 0) {
                $details += "ID 개수 불일치"
            }
            Add-RuleFailure "PRODUCT-ID-MVP" "docs/product/mvp.md" ($details -join "; ")
        }
    }

    if ($contents.ContainsKey("docs/product/feature-spec.md")) {
        $hub = $contents["docs/product/feature-spec.md"]
        $requiredHubTargets = @(
            "features/onboarding-preference.md",
            "features/place-selection.md",
            "features/course-design.md",
            "features/exploration.md",
            "features/travel-records.md",
            "features/common-policies.md",
            "open-questions.md"
        )

        foreach ($target in $requiredHubTargets) {
            if ($hub -notmatch ("\]\(" + [regex]::Escape($target) + "(?:#[^)]+)?\)")) {
                Add-RuleFailure "PRODUCT-HUB-LINK" "docs/product/feature-spec.md" (
                    "대표 지도에 필수 문서 링크가 없다: $target"
                )
            }
        }
    }

    if (Test-Path -LiteralPath $productRoot -PathType Container) {
        $markdownFiles = Get-ChildItem -LiteralPath $productRoot -Recurse -File -Filter "*.md"
        foreach ($markdownFile in $markdownFiles) {
            $sourceFullPath = $markdownFile.FullName
            $sourceRelativePath = Get-NormalizedRelativePath (
                $sourceFullPath.Substring($rootPrefix.Length)
            )
            $sourceContent = Read-Utf8File $sourceFullPath $sourceRelativePath
            if ($null -eq $sourceContent) {
                continue
            }

            $linkMatches = [regex]::Matches(
                $sourceContent,
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

                if (
                    [System.IO.Path]::GetFileName($targetPathPart) -eq "feature-spec.md" -and
                    $anchor -match "^(논의-필요|[1-6]-)"
                ) {
                    Add-RuleFailure "PRODUCT-LEGACY-ANCHOR" $sourceRelativePath (
                        "이전 단일 기능명세의 상세 anchor를 참조한다: $target"
                    )
                }

                if ([string]::IsNullOrWhiteSpace($targetPathPart)) {
                    $targetFullPath = $sourceFullPath
                }
                elseif ([System.IO.Path]::IsPathRooted($targetPathPart)) {
                    Add-RuleFailure "PRODUCT-LINK-SCOPE" $sourceRelativePath (
                        "저장소 상대 경로가 아닌 링크가 있다: $target"
                    )
                    continue
                }
                else {
                    $targetFullPath = [System.IO.Path]::GetFullPath(
                        (Join-Path $markdownFile.DirectoryName $targetPathPart)
                    )
                }

                if (
                    $targetFullPath -ne $resolvedRoot -and
                    -not $targetFullPath.StartsWith(
                        $rootPrefix,
                        [System.StringComparison]::OrdinalIgnoreCase
                    )
                ) {
                    Add-RuleFailure "PRODUCT-LINK-SCOPE" $sourceRelativePath (
                        "저장소 밖을 가리키는 링크가 있다: $target"
                    )
                    continue
                }

                $targetRelativePath = Get-NormalizedRelativePath (
                    $targetFullPath.Substring($rootPrefix.Length)
                )
                if (Test-ProtectedPath $targetRelativePath) {
                    Add-RuleFailure "PRODUCT-LINK-PROTECTED" $sourceRelativePath (
                        "보호 경로를 가리키는 링크는 열지 않는다: $target"
                    )
                    continue
                }

                if (-not (Test-Path -LiteralPath $targetFullPath -PathType Leaf)) {
                    Add-RuleFailure "PRODUCT-LINK-TARGET" $sourceRelativePath (
                        "링크 대상 파일이 없다: $target"
                    )
                    continue
                }

                if (-not [string]::IsNullOrWhiteSpace($anchor)) {
                    $targetContent = Read-Utf8File $targetFullPath $targetRelativePath
                    if ($null -eq $targetContent) {
                        continue
                    }

                    $anchors = @(
                        [regex]::Matches(
                            $targetContent,
                            "(?m)^\s{0,3}#{1,6}\s+(?<heading>.+?)\s*#*\s*$"
                        ) | ForEach-Object {
                            ConvertTo-MarkdownAnchor $_.Groups["heading"].Value
                        }
                    )
                    if (-not ($anchors -contains $anchor)) {
                        Add-RuleFailure "PRODUCT-LINK-ANCHOR" $sourceRelativePath (
                            "링크 anchor가 없다: $target"
                        )
                    }
                }
            }
        }
    }
}
catch {
    if ($script:Failures.Count -eq 0) {
        Add-RuleFailure "PRODUCT-UNEXPECTED" "verify-product-knowledge.ps1" $_.Exception.Message
    }
}

if ($script:Failures.Count -gt 0) {
    foreach ($failure in $script:Failures) {
        Write-Output "[FAIL][$($failure.RuleId)] $($failure.Target) - $($failure.Reason)"
    }
    Write-Output "제품 지식 베이스 검증 실패: $($script:Failures.Count)개"
    exit 1
}

Write-Output "제품 지식 베이스 검증 통과."
exit 0
