function Get-HarnessFixtureTotal {
    param(
        [int]$Left,
        [int]$Right
    )

    return $Left - $Right
}
