---
name: verify-backend
description: Compile and unit-test QRServe backend Java modules when ./gradlew cannot run in this environment (loopback-connection failure). Use whenever you need to verify a backend change compiles or its unit tests pass, instead of trying gradlew.
---

# verify-backend

`./gradlew` (any task that compiles or runs anything) fails in this environment with
`java.io.IOException: Unable to establish loopback connection` — reproduced on JDK
17/21/25, with or without `--no-daemon`. It is environment-specific, not a repo
problem; the user's own machine runs `./gradlew build` fine. **Always tell the user
`./gradlew build`/`test` is their outstanding step** — this harness cannot catch
dependency-alignment failures the way Gradle's real resolver would.

This skill drives `javac` + the JUnit Platform Launcher API directly, using a
classpath built from `~/.gradle/caches/modules-2/files-2.1` (Gradle's flat,
already-populated dependency cache — nothing is downloaded). Two scripts under
`backend/scripts/` do the mechanical, error-prone parts; this file is the recipe
that ties them together. Every step below has been run end-to-end against real
modules in this repo (`shared:exceptions`, `shared:common` + `shared:security`,
18/18 tests passing).

## Why not just flatten the cache onto one `-cp`

- **The cache holds every version Gradle has ever resolved, side by side** — e.g.
  Boot 3.4.3/Spring 6.2.3/JUnit Platform 1.11.4 *and* Boot 4.1.0/Spring 7.0.8/JUnit
  Platform 6.0.3. A lexically-sorted flat classpath puts the old jars first and
  they silently win. `gradle-classpath.mjs` picks the newest version per
  `group:artifact` instead.
- **`spring-cloud-gateway` must be excluded** for every non-gateway service. Its
  `GatewayEnvironmentPostProcessor` is not assignable to Boot 4's
  `EnvironmentPostProcessor`; merely having the jar on the classpath aborts
  context startup. Real Gradle would never put it on `auth-service`'s classpath —
  it's only a hazard because this harness draws from one shared, flat cache.
  `gradle-classpath.mjs` drops it unless you pass `--include-gateway` (only
  needed when verifying `api-gateway` itself).
- **Lombok needs `-processorpath`, not just `-cp`.** With 300+ jars on the
  classpath, javac's implicit annotation-processor discovery does not reliably
  find Lombok's `META-INF/services` registration. Without it, `@Slf4j` fields
  and Lombok-generated constructors silently don't exist and you get confusing
  "cannot find symbol: log" / "constructor cannot be applied" errors that look
  like real bugs. Get the jar with `--lombok-jar` and pass it explicitly.
- **`@argfile`s must use forward slashes and no BOM.** javac treats backslash in
  an `@argfile` as an escape character — a literal `C:\...` path is silently
  mangled. And `Set-Content -Encoding utf8` in Windows PowerShell 5.1 writes a
  UTF-8 BOM, which javac reads as part of the first token (`-parameters` becomes
  `?-parameters`, "invalid flag"). Use `[System.IO.File]::WriteAllLines(path,
  lines, (New-Object System.Text.UTF8Encoding($false)))` — no BOM.
- **`-parameters` is required.** Spring resolves `@PathVariable`/`@RequestParam`
  names by reflection; without it every parameterized endpoint 400s with no
  logged exception. The Boot Gradle plugin adds this automatically; javac does
  not.
- **There is no `junit-platform-console-standalone` jar in this cache** — only
  the launcher/engine libraries Gradle itself links against. Use
  `backend/scripts/JUnitRunner.java` (drives `org.junit.platform.launcher`'s API
  directly) instead of trying to invoke a console launcher tool that isn't there.

## Recipe

All paths below are forward-slash and relative to the repo root unless noted.
PowerShell examples; adapt trivially for bash.

### 1. Resolve the classpath once per session

```powershell
$cp = node backend/scripts/gradle-classpath.mjs
$lombok = node backend/scripts/gradle-classpath.mjs --lombok-jar
```

Add `--verbose` (writes to stderr) to see every resolved `group:artifact ->
version`, e.g. to confirm you got Boot 4.1.0 and not 3.4.3.

### 2. Compile dependency modules first, in dependency order

Check the target module's `build.gradle` for `project(':shared:xxx')` lines —
those must be compiled first and their output dir added to the classpath. E.g.
`shared:security` depends on `shared:common`.

For each module, write an `@argfile` (no BOM, forward slashes) and run
`javac "@argfile"`:

```powershell
$out = "$env:TEMP/qrserve-verify"   # or wherever; not committed to the repo
$srcFiles = Get-ChildItem -Recurse "backend/shared/common/src/main/java" -Filter *.java |
    ForEach-Object { $_.FullName.Replace('\','/') }
$argfile = "$out/common.args"
[System.IO.File]::WriteAllLines($argfile,
    @("-parameters","-processorpath",$lombok,"-cp",$cp,"-d","$out/common") + $srcFiles,
    (New-Object System.Text.UTF8Encoding($false)))
javac "@$argfile"
```

Then compile the target module's main sources with `$cp` **plus** every
already-compiled dependency's output dir appended (`;`-joined on Windows,
`:`-joined elsewhere):

```powershell
$cpWithCommon = "$cp;$out/common"
```

### 3. Compile the module's test sources the same way

Classpath = `$cpWithCommon` (or however many dependency dirs apply) plus the
module's own `main` output dir.

### 4. Compile `JUnitRunner.java` once

```powershell
$argfile = "$out/runner.args"
[System.IO.File]::WriteAllLines($argfile, @("-cp",$cp,"-d","$out/runner","backend/scripts/JUnitRunner.java"),
    (New-Object System.Text.UTF8Encoding($false)))
javac "@$argfile"
```

### 5. Run the tests

```powershell
$runCp = "$cp;$out/common;$out/main;$out/test;$out/runner"
$argfile = "$out/run.args"
[System.IO.File]::WriteAllLines($argfile, @("-cp",$runCp), (New-Object System.Text.UTF8Encoding($false)))
java "@$argfile" JUnitRunner com.qrserve.shared.security.JwtTokenProviderTokenTypeTest com.qrserve.shared.security.TenantContextFilterTest
```

`JUnitRunner` exits 0 iff every test passed, and prints a JUnit Platform summary
plus full failure details otherwise. Pass every test class you want to run as a
separate argument (fully-qualified class name).

## What this harness cannot catch

- Dependency-version misalignment across modules that Gradle's real resolver
  would flag (this harness always uses "newest wins", which can paper over a
  genuine conflict).
- `@SpringBootTest` / full context-loading tests — this drives plain JUnit
  Platform, not Spring's test runner. Constructor-level unit tests (like the
  `shared:security` examples above) work; full integration tests generally
  won't without much more Spring Test Context plumbing.
- Anything Gradle's build does beyond compile+test (resource processing,
  annotation-processor options set in `build.gradle`, etc).

Report `./gradlew build` as the user's outstanding step regardless of how clean
this harness's output looks.
