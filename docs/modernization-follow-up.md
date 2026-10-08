# Runtime findings after build modernisation

Baseline: `cbe2b665` (Gradle 9.8.0 / JDK 25). Scope: fix regressions and
compatibility issues exposed by changing the build/runtime. Existing application
bugs are recorded here, without modifying their behaviour.

## Java runtime compatibility

- libGDX 1.13.1 transitively selected LWJGL 3.3.3. On JDK 25 this produced
  `Unsupported JNI version` and `sun.misc.Unsafe::objectFieldOffset` warnings.
  The backend now exports the LWJGL 3.4.3 BOM so that Java bindings and all native
  classifiers resolve to the same version, including in the client distribution.
- LWJGL 3.4 provides an FFM backend on Java 25 and continues to support Java 8.
  The opt-in `:engine-libgdx:nativeRuntimeTest` exercises memory allocation and
  a real libGDX/OpenGL render loop with an invisible window. On Java 25 it runs
  with `--sun-misc-unsafe-memory-access=deny`; no warning suppression is used.
- `:client:run` and the launcher's child-JVM command enable native access on
  Java 17+. Java 8 commands omit the unsupported option. When launching the
  generated distribution scripts or a direct IDE application configuration,
  supply this option through `JAVA_OPTS` / VM options on Java 17+.
- The launcher no longer passes the obsolete `AggressiveOpts` flag to modern
  child JVMs. The setting's behaviour is preserved for Java 8–10.
- Windows x64 native startup is tested on Java 25 and Java 8. Full gameplay,
  audible sound output, Linux and macOS remain separate manual checks. LWJGL
  3.4 requires GLIBC 2.28 or later on Linux x64.

### Integration recheck on 8 October 2026

The assembled client distribution opens a window on Java 25, but a normal
launch with native access enabled still reports a deprecated
`sun.misc.Unsafe::objectFieldOffset` call from LWJGL 3.4.3's
`MemoryBackendUnsafeLegacy`. Updating LWJGL alone does not make the default
launch free of this warning. The opt-in native test instead runs with
`--sun-misc-unsafe-memory-access=deny`. A second startup check of the complete
client distribution with this option also passed without native warnings.

Aligning the JVM options of all launch paths belongs to the separate Java 25
baseline migration. Preserve the distinction between Gradle `run`, generated
distribution scripts, direct IDE launches and child JVMs started by the launcher.

Official references:

- [LWJGL 3.4.0 changes](https://github.com/LWJGL/lwjgl3/releases/tag/3.4.0)
- [LWJGL 3.4.3 platform and Java requirements](https://github.com/LWJGL/lwjgl3/blob/3.4.3/README.md)

## Pre-existing application findings — intentionally unchanged

| Finding | Code and impact | Possible separate follow-up |
| --- | --- | --- |
| Missing `alertVolume` | `IllaClient` registers sound/music defaults, but no alert-volume default. `AudioPlayer` reads it; `ConfigSystem.getFloat` returns zero when absent, potentially muting alerts. | Decide the desired default and register it without overwriting saved values. |
| Missing `lastLogin` / `fingerprint` | `Login.restoreLogin` / `restorePassword` request optional saved credentials through getters that log missing entries. An unset value is expected before credentials have been saved. | Use an optional-value lookup or appropriate defaults; preserve password-storage semantics. |

These findings are present in the pre-modernisation code. They are not fixed
as part of the Java/Gradle migration.

## Build warnings

SpotBugs Gradle plugin 6.5.12 calls `Configuration.setVisible`, deprecated by
Gradle 9.8. This is a build-plugin warning; it does not affect client execution
or require downgrading Gradle. Recheck the plugin before a future Gradle upgrade.
PMD/SpotBugs also report existing source findings under the existing non-blocking
policy. These reports are not evidence of new runtime failures.
