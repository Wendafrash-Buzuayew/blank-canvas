$stdin = [Console]::In.ReadToEnd()
try { $json = $stdin | ConvertFrom-Json } catch { exit 0 }

$cmd = $json.tool_input.command
if (-not $cmd) { exit 0 }
if ($cmd -notmatch 'gradlew') { exit 0 }
if ($cmd -match '--version|--help|(^|\s)-v(\s|$)') { exit 0 }

$reason = "./gradlew build/test/run tasks fail in this local environment with " +
    "'java.io.IOException: Unable to establish loopback connection' (reproduced on " +
    "JDK 17/21/25, --no-daemon included). This is environment-specific, not a repo bug " +
    "-- the user's own machine runs ./gradlew build fine. Use the verify-backend skill's " +
    "javac+JUnit harness to compile/test Java code instead, or let the user run this " +
    "gradlew command themselves."

$out = @{
    hookSpecificOutput = @{
        hookEventName            = 'PreToolUse'
        permissionDecision       = 'ask'
        permissionDecisionReason = $reason
    }
} | ConvertTo-Json -Depth 5 -Compress

Write-Output $out
exit 0
