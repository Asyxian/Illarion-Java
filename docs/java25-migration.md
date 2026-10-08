# Java 25 baseline for this fork

This migration follows the integrated baseline `40ffa4dd` on `develop`.
It applies to this fork; the independent upstream PR branches and `master`
retain their existing requirements and commits.

## Compilation and tests

JDK 25 now builds, compiles and tests every module. Java and Groovy output,
including generated sources, uses class-file version 69. Consumers of the
resource converter plugin therefore also need Java 25. The standalone NPC
compiler no longer requests ProGuard output targeting Java 11.

The temporary Java 8 client-test launcher has been removed. Client fixtures use
Mockito 5.24.0, Objenesis 3.3 and the same TestNG 7.5.1 as the other modules.
Mockito instrumentation is installed explicitly using `-javaagent`; tests do
not depend on dynamic agent attachment or PowerMock's separate class loader.
The old `-PtestJavaVersion=8` override fails with an explanatory message.

The six migrated fixtures retain their test methods, assertions and important
interaction checks. They still exercise real Nifty layouts, map/quest marker
management, update queues and movement code. Small test-only helpers construct
legacy objects without graphics initialisation and access private fixture state.
No production accessors were added solely to accommodate mocking.

Static and constructor mocks are scoped to the test thread and closed after
each method, including failed setup. They do not propagate to worker threads.
The existing movement race test still uses real threads; its worker paths use
injected objects and do not require the static mock context. Future threaded
tests must account for that boundary explicitly.

An ignored, local Gradle property pointing at the Java 8 toolchain was removed.
The old JDK installation remains available for work on the upstream PR branches;
it is not required by `develop`.

## Application startup

All Gradle application runs, generated distribution scripts, the packaged
launcher and the child JVMs it starts deny legacy Unsafe memory access and
enable the required native libraries. Non-JavaFX applications use:

```text
--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=deny
```

These options enable native libraries while preventing legacy Unsafe memory
access. LWJGL 3.4.3's alternative memory backend passes the native runtime test
and client startup on Windows x64. Direct IntelliJ application configurations
still need these VM options entered manually; delegated Gradle runs include them.
The launcher additionally enables native access for the named `javafx.graphics`
module, as described in the JavaFX follow-up below.

The launcher selects Java 25 or newer and gives an explicit error when no
supported runtime can be launched. macOS runtime discovery requests `25+`.
The obsolete `launchAggressive` setting is no longer read or forwarded.

Generated Windows scripts use `%APP_HOME%\lib\*` as their classpath. Enumerating
every native-library JAR expanded the client script's classpath assignment to
10,249 characters in the development checkout, exceeding `cmd.exe`'s limit and
preventing Java from starting. The wildcard loads the distribution's library
directory without depending on the installation path's length per JAR. Keep
that directory free of old or unrelated JARs: wildcard classpath order is not
guaranteed. Unix scripts retain their explicit classpath.

## Validation on 8 October 2026

The complete offline build ran with JDK 25.0.4.1 and Gradle 9.8.0, without an
additional Java 8 toolchain property:

```powershell
.\gradlew.bat --offline --no-daemon clean build :client:installDist :download:packageLauncher :engine-libgdx:nativeRuntimeTest
```

All 122 tasks executed successfully. Test result XML contains **154 cases,
zero failures, zero errors and zero skips**, including:

| Coverage | Cases |
| --- | ---: |
| Client regression tests | 96 |
| Common library | 15 |
| Launcher and version comparison | 27 |
| Map editor | 9 |
| Resource converter | 6 |
| Native graphics runtime | 1 |

The NPC compiler's executable verification also passed. All generated own
classes were checked for Java 25 bytecode. A comparison against the integration
baseline confirmed that none of the six migrated fixtures lost test methods
or assertion calls; mock interaction checks were also reviewed separately.

After the Windows script correction, scripts were regenerated for all application
modules; `assemble :client:test :download:installDist` passed with 59 tasks.
The Windows scripts inside all six application ZIP distributions were checked
for the shortened classpath and both JVM options. Gradle run-task inspection
confirmed that each native JVM option occurs
exactly once. The client opened a window from its generated `.bat` script and
remained running for a 20-second startup check with an isolated user profile.
There were no JNI, restricted native-access or deprecated Unsafe warnings.

The `jpackage` launcher image also remained running with a native window during
a separate 20-second startup check. Its embedded runtime reports Java 25.0.4.1,
and its generated configuration contains both native JVM options. No game login,
application download or production deployment was performed.

## JavaFX module follow-up on 8 October 2026

The initial Java 25 migration still loaded JavaFX from the classpath and logged
`Unsupported JavaFX configuration: classes were loaded from 'unnamed module'`.
JavaFX now loads as named modules across the supported launcher entry points:

- `:download:run` and launcher tests resolve the four platform-specific JavaFX
  JARs through the separate `javafxModules` configuration and use `--module-path`.
- Standard distributions contain a separate `javafx/` directory. Both generated
  start scripts use its location relative to the distribution root, so installation
  paths are not embedded in the scripts. Application JARs remain in `lib/`.
- `:download:packageLauncher` links JavaFX into its bundled runtime. Its
  application classpath no longer contains duplicate JavaFX JARs. The image keeps
  all JDK modules because this runtime can also start clients and editors; this
  change does not attempt to minimise the runtime image.

The launcher adds `--add-modules=javafx.controls,javafx.fxml` and changes native
access to `--enable-native-access=ALL-UNNAMED,javafx.graphics`. The existing
Unsafe denial remains. Its own application code stays on the classpath; FXML
controllers do not require a whole-application module conversion. Direct IDE
launches must use these arguments and a JavaFX module directory, or delegate to
the configured Gradle `:download:run` task.

Validation covers 31 launcher test cases, including four new checks that JavaFX
classes belong to the expected named modules. The standalone distribution starts
from a relocated Windows path containing spaces, and the linked launcher image
starts with its embedded runtime. Each remained running with a window for
20 seconds without JavaFX, restricted native-access or deprecated Unsafe warnings.
The Gradle run task also opened a window and remained running for 58 seconds;
the smoke check then deliberately stopped that process, producing Gradle's
expected non-zero process-exit report. This was not an application startup failure.
The client also started and remained running for 20 seconds with the newly
bundled runtime, without native-access or Unsafe warnings. This check did not
perform a login or exercise the launcher's application download flow.

`:download:build` passed, including PMD/SpotBugs under the existing non-blocking
policy. Archive inspection confirmed four JavaFX JARs outside `lib/` and relative
module paths in both start scripts. Git Bash accepted the Unix script's syntax;
this does not substitute for running JavaFX on Linux or macOS.

## Remaining limits and findings

- The launcher still uses the upstream Maven/download endpoints. Creating a
  local app image does not establish a fork-specific update or release channel.
- Full gameplay, audible sound output, Linux/macOS startup, native installers,
  signing and release deployment remain unvalidated by these checks.
- Fresh client profiles still log missing `lastLogin`, `fingerprint` and
  `alertVolume` entries. These pre-existing findings are described in
  [the runtime notes](modernization-follow-up.md) and remain unchanged.
- Mockito's bootstrap instrumentation produces a JVM class-data-sharing notice.
  There is no dynamic-agent-attachment warning. The notice is not suppressed.
- Existing PMD/SpotBugs findings remain under the existing non-blocking policy.
  SpotBugs Gradle plugin 6.5.12 still reports its deprecated
  `Configuration.setVisible` call; this needs attention before Gradle 10.
