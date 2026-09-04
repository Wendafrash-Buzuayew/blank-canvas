$stdin = [Console]::In.ReadToEnd()
try { $json = $stdin | ConvertFrom-Json } catch { exit 0 }

$path = $json.tool_input.file_path
if (-not $path) { $path = $json.tool_response.filePath }
if (-not $path -or $path -notmatch 'Controller\.java$') { exit 0 }
if (-not (Test-Path -LiteralPath $path)) { exit 0 }

$content = Get-Content -Raw -LiteralPath $path -ErrorAction SilentlyContinue
if (-not $content) { exit 0 }

$hasMutation = $content -match '@(PostMapping|PutMapping|DeleteMapping|PatchMapping)'
$hasAuth = $content -match '@PreAuthorize'

if ($hasMutation -and -not $hasAuth) {
    $msg = "Heads up: $path defines a mutating endpoint " +
        "(@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping) with no @PreAuthorize " +
        "anywhere in the file. docs/codebase-review.md flagged exactly this pattern -- " +
        "controllers relying only on SecurityConfig's anyRequest().authenticated() with " +
        "no role check -- as a recurring critical finding (privilege escalation / tenant " +
        "bypass) in this repo. Double-check role and tenant enforcement before merging."
    $out = @{ systemMessage = $msg } | ConvertTo-Json -Compress
    Write-Output $out
}
exit 0
