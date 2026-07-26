[CmdletBinding()]
param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "sample-calculator.ps1")

$actual = Get-HarnessFixtureTotal -Left 2 -Right 3
if ($actual -ne 5) {
    Write-Output "[FAIL] 합계의 기대값은 5지만 실제 값은 $actual 이다."
    exit 1
}

Write-Output "[PASS] 샘플 계산기"
exit 0
