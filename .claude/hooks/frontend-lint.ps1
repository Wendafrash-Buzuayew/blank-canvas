$stdin = [Console]::In.ReadToEnd()
try { $json = $stdin | ConvertFrom-Json } catch { exit 0 }

$path = $json.tool_input.file_path
if (-not $path) { $path = $json.tool_response.filePath }
if (-not $path -or $path -notmatch '[\\/]src[\\/].*\.tsx?$') { exit 0 }

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Push-Location $root
try {
    $result = npm run lint 2>&1 | Out-String
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($code -ne 0) {
    $trimmed = $result
    if ($trimmed.Length -gt 3000) { $trimmed = $trimmed.Substring($trimmed.Length - 3000) }
    $out = @{ systemMessage = "npm run lint (tsc --noEmit) failed after editing ${path}:`n$trimmed" } | ConvertTo-Json -Compress
    Write-Output $out
}
exit 0
