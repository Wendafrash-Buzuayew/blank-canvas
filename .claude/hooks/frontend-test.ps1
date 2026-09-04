$stdin = [Console]::In.ReadToEnd()
try { $json = $stdin | ConvertFrom-Json } catch { exit 0 }

$path = $json.tool_input.file_path
if (-not $path) { $path = $json.tool_response.filePath }
if (-not $path) { exit 0 }

$testPath = $null
if ($path -match '\.test\.ts$') {
    $testPath = $path
} elseif ($path -match '[\\/]src[\\/]lib[\\/][^\\/]+\.ts$') {
    $candidate = $path -replace '\.ts$', '.test.ts'
    if (Test-Path -LiteralPath $candidate) { $testPath = $candidate }
}
if (-not $testPath) { exit 0 }

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Push-Location $root
try {
    $result = npx tsx $testPath 2>&1 | Out-String
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

$status = if ($code -eq 0) { 'PASSED' } else { 'FAILED' }
$trimmed = $result
if ($trimmed.Length -gt 3000) { $trimmed = $trimmed.Substring($trimmed.Length - 3000) }
$out = @{ systemMessage = "$testPath $status`:`n$trimmed" } | ConvertTo-Json -Compress
Write-Output $out
exit 0
