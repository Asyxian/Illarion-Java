Illarion Java Applications
==========================

[![Build Status](https://illarion.org:8080/job/Illarion%20Applications/badge/icon)](https://illarion.org:8080/job/Illarion%20Applications/)

Objectives
----------

Illarion is the online multiplayer roleplaying game developed and maintained by
the Illarion e.V. This repository contains all published client applications.

Details
-------

The applications in this repository are the Illarion Client, the easyNPC
editor, the easyQuest editor and the map editor. Also there is a small utility
to download and update the applications on the players' computers.

Contributing
------------

The team of Illarion is always looking for new members who want to support our
project. This project is developed only by volunteers, who don't get
paid for their work. However, there is much experience to be gained by joining
the development of this project.

For further information check out our homepage: https://illarion.org

Any changes to the applications can be applied using pull requests.

Build
-----

Use **JDK 25** and the checked-in **Gradle 9.8.0 wrapper**. A normal JDK is
sufficient; JavaFX is resolved from Maven Central for the launcher. Set
`JAVA_HOME` to your JDK 25 installation, then run:

```powershell
.\gradlew.bat classes test
.\gradlew.bat build
```

On Linux and macOS use `./gradlew`. The optional Windows helper `build.ps1`
uses the same wrapper and defaults to `build`. It temporarily sets the Gradle
user home to `.gradle/gradle-user-home` unless `GRADLE_USER_HOME` is already set.

The first build needs network access for Gradle and dependencies. The wrapper
verifies the distribution's SHA-256. Illarion resources are pinned to `2.3.3`
so development branches use the same assets as the reference build.
`--offline` requires populated caches and an existing
`illacommon/src/main/resources/skills.xml`; the skills download is skipped in
offline mode. The wrapper itself still downloads Gradle if it is missing.

### Java compatibility

The build runs on Java 25. The client, common libraries, map editor and easyQuest
are compiled with `--release 8`, checking both Java syntax and JDK API use.
easyNPC and the compiler target Java 11 because the current editor components
require it (RSyntaxTextArea 4.0.1 and AutoComplete 4.0.0).
The resource-converter Gradle plugin targets Java 17. The separate launcher
uses **Java 25 and OpenJFX 25.0.4** and does not support Java 8.

Java 8 remains a compatibility target for the game, not a constraint on future
modernization. To run the available game/library tests on an installed JDK 8:

```powershell
.\gradlew.bat :client:test :common:test :mapeditor:test -PtestJavaVersion=8
```

Gradle discovers JDKs in standard locations. If necessary pass
`-Porg.gradle.java.installations.paths=C:/path/to/jdk8`.
Do not apply the Java 8 test override to the launcher or Gradle plugin tests.
A successful compilation/test run does not replace an in-game graphics,
audio and login check.

The libGDX backend uses LWJGL **3.4.3** (bindings and natives aligned via its BOM).
On Java 25 this uses LWJGL's FFM backend; Java 8 remains supported. The Gradle
`:client:run` task and the Illarion launcher enable native access automatically
when the selected child JVM supports that option. For a direct IDE application
configuration or distribution script on Java 17+, add
`--enable-native-access=ALL-UNNAMED` to VM options / `JAVA_OPTS`; omit it on Java 8.

An opt-in desktop integration test starts libGDX with an invisible OpenGL window:

```powershell
.\gradlew.bat :engine-libgdx:nativeRuntimeTest
.\gradlew.bat :engine-libgdx:nativeRuntimeTest -PtestJavaVersion=8
```

The Java 25 test additionally denies legacy Unsafe memory access. These tests
require working desktop graphics and have been checked on Windows x64.
LWJGL 3.4 raises the Linux x64 requirement to GLIBC 2.28; other platforms still
need runtime validation. See [modernization follow-up notes](docs/modernization-follow-up.md)
for remaining warnings and pre-existing findings.

### Distributions and launcher

`build` produces the application ZIP/TAR distributions and the standalone
`illacompiler/build/compiler.jar`. It also runs the tests, PMD 7 and SpotBugs.
`:compiler:verifyCompiler` additionally compiles the bundled NPC template with
the packaged compiler JAR and is included in `check` and `build`.
Static analysis retains the existing non-blocking policy (`ignoreFailures`);
findings are available in each module's `build/reports` directory.

```powershell
.\gradlew.bat :client:installDist :download:installDist
.\gradlew.bat :download:packageLauncher
```

`installDist` creates runnable directories under each module's `build/install`.
The launcher distribution includes JavaFX for the build machine's OS and CPU
architecture and requires JDK 25 to run. `packageLauncher` uses JDK `jpackage`
to create a self-contained application image, including its Java runtime,
under `illadownload/build/package`. Build native packages on their target OS.
The bundled runtime retains the `java` executable for starting child applications.

This replaces the Java-8-only JavaFX Ant packaging. The historical
`installer.install4j` file is retained for reference; its installer/signing
pipeline is not used by this build. Native installers, release signing and
publishing need a separate release setup. No packaging task uploads files or
installs software on the build machine. Maven publishing remains explicit.

### Resource converter

The `convert` plugin uses a named extension instead of removed Gradle
conventions. Resource projects consuming the rebuilt plugin configure it as:

```groovy
converter {
    resourceDirectory = file('src/main/resources')
    atlasNameExtension = 'items'
    // privateKey = file('path/to/private.key') // needed for encrypted tables
}
```

`buildConvert` generates converted resources and assembles the JAR. Signing
keys are never part of the repository.

IDE integration
---------------

Import the root Gradle project using the wrapper. In IntelliJ IDEA select
**JDK 25 as the Gradle JVM**; this setting is separate from the Java runtime
used to run IntelliJ itself. Gradle supplies per-module compilation settings.
If you use `build.ps1`, set the IDE's Gradle user home to
`<project>/.gradle/gradle-user-home` to reuse its cache.

The modernization was informed by upstream
[PR #113](https://github.com/Illarion-eV/Illarion-Java/pull/113), especially its
JavaFX separation and launcher Java-version detection. Except for the LWJGL
runtime compatibility fix, game-library upgrades and gameplay bug fixes remain
separate from this build migration.
