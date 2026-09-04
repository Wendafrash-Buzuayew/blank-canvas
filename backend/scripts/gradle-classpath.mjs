#!/usr/bin/env node
// Builds a javac/JUnit classpath from the flat Gradle dependency cache, for use
// when `./gradlew` itself cannot run (see the verify-backend skill for why).
//
// The cache holds every version of every artifact Gradle has ever resolved on
// this machine, side by side, with no notion of "which version this project
// wants". A naive flat classpath puts old jars first and they win silently
// (e.g. Spring Boot 3.4.3 alongside 4.1.0). This script picks the newest
// version per group:artifact instead, and drops spring-cloud-gateway by
// default because its GatewayEnvironmentPostProcessor is not assignable to
// Boot 4's EnvironmentPostProcessor and merely having it on the classpath
// aborts context startup for every non-gateway service.
//
// Usage:
//   node backend/scripts/gradle-classpath.mjs [--include-gateway] [--verbose]
//
// Prints the resolved classpath to stdout, one entry per line joined by the
// platform path separator on the LAST line — actually: prints a single line,
// the full classpath string, ready to paste after `-cp` in a javac @argfile.
// Use forward slashes even on Windows: javac treats backslash in an @argfile
// as an escape character, so a literal `C:\...` path is silently mangled.

import { readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { homedir } from 'node:os';

const args = process.argv.slice(2);
const includeGateway = args.includes('--include-gateway');
const verbose = args.includes('--verbose');
const printLombokJar = args.includes('--lombok-jar');
const printJunitConsole = args.includes('--junit-console-jar');

const cacheRoot = join(homedir(), '.gradle', 'caches', 'modules-2', 'files-2.1');

// Artifacts whose presence on a non-gateway servlet service's classpath breaks
// Spring Boot 4 context startup. Matched by artifactId substring.
const GATEWAY_ARTIFACT_MARKERS = ['spring-cloud-gateway', 'spring-cloud-starter-gateway'];

function listDirs(path) {
  try {
    return readdirSync(path, { withFileTypes: true })
      .filter((e) => e.isDirectory())
      .map((e) => e.name);
  } catch {
    return [];
  }
}

// Node's numeric locale compare handles dotted version strings well enough
// to rank Boot 3.4.3 < 4.1.0 < 7.0.8 without a full semver parser.
const versionCollator = new Intl.Collator('en', { numeric: true, sensitivity: 'base' });

function newestVersion(versions) {
  return [...versions].sort(versionCollator.compare).at(-1);
}

const chosen = new Map(); // "group:artifact" -> { version, jarPath }
let scannedGroups = 0;

for (const group of listDirs(cacheRoot)) {
  scannedGroups++;
  const groupDir = join(cacheRoot, group);
  for (const artifact of listDirs(groupDir)) {
    if (!includeGateway && GATEWAY_ARTIFACT_MARKERS.some((m) => artifact.includes(m))) {
      continue;
    }
    const artifactDir = join(groupDir, artifact);
    const versions = listDirs(artifactDir);
    if (versions.length === 0) continue;
    const version = newestVersion(versions);
    const versionDir = join(artifactDir, version);

    // Each version dir contains one subdir per hash, each holding the actual
    // jar (plus maybe -sources.jar / -javadoc.jar siblings we must skip).
    let jarPath = null;
    for (const hashDir of listDirs(versionDir)) {
      const hashPath = join(versionDir, hashDir);
      let files;
      try {
        files = readdirSync(hashPath);
      } catch {
        continue;
      }
      const jar = files.find(
        (f) => f.endsWith('.jar') && !f.endsWith('-sources.jar') && !f.endsWith('-javadoc.jar')
      );
      if (jar) {
        jarPath = join(hashPath, jar);
        break;
      }
    }
    if (!jarPath) continue;

    const key = `${group}:${artifact}`;
    chosen.set(key, { version, jarPath });
  }
}

if (chosen.size === 0) {
  console.error(
    `No jars found under ${cacheRoot}. Has this machine ever run a Gradle build ` +
      `for this project? The classpath must come from a populated cache — this ` +
      `script does not download anything.`
  );
  process.exit(1);
}

if (verbose) {
  console.error(`Scanned ${scannedGroups} groups, resolved ${chosen.size} artifacts.`);
  if (!includeGateway) {
    console.error('spring-cloud-gateway excluded (pass --include-gateway to keep it).');
  }
  for (const [key, { version }] of [...chosen.entries()].sort()) {
    console.error(`  ${key} -> ${version}`);
  }
}

// Lombok's @Slf4j/@Builder/@AllArgsConstructor etc. only expand when lombok
// runs as an annotation processor. Putting it on -cp is not enough — with
// this many jars on the classpath, javac's implicit ServiceLoader discovery
// does not reliably find it. Pass this path to javac's -processorpath
// explicitly.
if (printLombokJar) {
  const lombok = chosen.get('org.projectlombok:lombok');
  if (!lombok) {
    console.error('org.projectlombok:lombok not found in the Gradle cache.');
    process.exit(1);
  }
  console.log(lombok.jarPath.replaceAll('\\', '/'));
  process.exit(0);
}

if (printJunitConsole) {
  const launcher = chosen.get('org.junit.platform:junit-platform-console-standalone');
  if (!launcher) {
    console.error(
      'org.junit.platform:junit-platform-console-standalone not found in the Gradle cache ' +
        '(it is a separate artifact from junit-platform-launcher — Gradle only downloads it ' +
        'if something requested it). Fall back to assembling junit-platform-launcher + ' +
        'junit-jupiter-engine + junit-platform-engine from the regular classpath instead.'
    );
    process.exit(1);
  }
  console.log(launcher.jarPath.replaceAll('\\', '/'));
  process.exit(0);
}

const classpath = [...chosen.values()]
  .map((e) => e.jarPath.replaceAll('\\', '/'))
  .join(process.platform === 'win32' ? ';' : ':');

console.log(classpath);
