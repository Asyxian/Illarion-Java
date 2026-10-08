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

This fork integrates changes on `develop`; `master` is reserved for releases.
See [AGENTS.md](AGENTS.md) for branch conventions and
[the coding-style checklist](docs/coding-style.md) for mandatory contribution rules.

Build
-----

Install **JDK 25**, set `JAVA_HOME` to that installation and use the checked-in
**Gradle 9.8.0 wrapper**. A JDK with bundled JavaFX is no longer required.

During branch integration, the client regression tests additionally require
**JDK 8**, because their upstream PowerMock fixtures cannot run on Java 25.
Gradle selects Java 8 only for `:client:test`; compilation, the other tests and
`:client:run` use Java 25. If needed, pass
`-Porg.gradle.java.installations.paths=/path/to/jdk8` to locate that installation.
Replacing these fixtures belongs to the separate Java 25 migration; no tests
are skipped to accommodate this transition.

```sh
./gradlew classes test
./gradlew build
```

On Windows use `gradlew.bat` instead of `./gradlew`. The first build downloads
Gradle and the dependencies; the wrapper checks the Gradle distribution's SHA-256.
The `git` executable must be on `PATH` for version metadata. Illarion resources
are pinned to `2.3.3`, so development branches can resolve the same assets
without relying on old snapshots or dynamic version selection.

An offline build requires populated caches and the existing
`illacommon/src/main/resources/skills.xml` file:

```sh
./gradlew --offline build
```

The skills download is skipped in offline mode. The wrapper itself still needs
to download its distribution if Gradle has not been cached yet.

### Compilation targets

The build JVM and compilation targets are separate:

| Modules | Target | Reason |
| --- | --- | --- |
| Client, common, game engines, map editor, easyQuest, Nifty modules | Java 8 | Preserve the existing game compatibility target using `--release 8`. |
| easyNPC and compiler | Java 11 | RSyntaxTextArea 4.0.1 and AutoComplete 4.0.0 require Java 11; the old editor dependency references JDK APIs unavailable to the modern ProGuard build. |
| Resource converter Gradle plugin | Java 17 | Uses the current Gradle API and bundled Groovy. |
| Downloader/launcher | Java 25 | Builds against separately resolved OpenJFX 25.0.4. |

These are transitional targets retained during branch integration. This fork
will adopt Java 25 for all applications in a separate, validated migration.

The integrated runtime follow-up aligns LWJGL with version 3.4.3, recognises
modern child-JVM version strings and avoids obsolete launcher options.
`:client:run` enables native access on Java 17+. When using generated client
scripts or a direct IDE application configuration, supply
`--enable-native-access=ALL-UNNAMED` through `JAVA_OPTS` or VM options on Java 17+.
See [the runtime notes](docs/modernization-follow-up.md) for platform limitations.

The opt-in `:engine-libgdx:nativeRuntimeTest` exercises a hidden OpenGL window.
It requires desktop graphics and denies legacy Unsafe memory access on Java 25.

To run the existing game/library tests on an additional installed JDK 8:

```sh
./gradlew :client:test :common:test :mapeditor:test -PtestJavaVersion=8
```

If Gradle does not discover that installation, also pass
`-Porg.gradle.java.installations.paths=/path/to/jdk8`. The override changes test
JVMs, not the Gradle JVM. Do not apply it to modules targeting a newer Java version.

### Build outputs and release limitations

`build` creates the application ZIP/TAR distributions, runs tests, PMD and
SpotBugs, and builds the standalone `illacompiler/build/compiler.jar`.
`:compiler:verifyCompiler` checks that this packaged JAR can compile the bundled
NPC template; it is included in `check` and `build`.

PMD 7 and SpotBugs replace the obsolete PMD rules and removed Gradle FindBugs
plugin. Static analysis retains the existing non-blocking policy; inspect
`build/reports` in each module for findings. The SpotBugs plugin currently emits
a Gradle deprecation warning about `Configuration.setVisible`.

```sh
./gradlew :client:installDist :download:installDist
```

The launcher distribution includes OpenJFX for the build machine's platform.
`:download:packageLauncher` creates a local `jpackage` application image with
its own Java runtime; it does not replace the production installer pipeline.
Build it separately for each supported OS/architecture. The removed JavaFX Ant
plugin depends on JDK 8 internals; the historical install4j configuration remains
in the repository, but its installer/signing/upload tasks are not migrated here.
Legacy release properties fail explicitly instead of silently producing a
different release. Native installers and release deployment need a separate
review before this build replaces the production release pipeline.

Maven publication remains an explicit operation through `publish`. Its default
destination is the module's `build/repo`; `-PtargetRepo=/path/to/repository`
selects another local repository directory.

### Resource converter plugin

The converter uses Gradle's public extension and task-property APIs instead of
removed internal APIs. Resource projects consuming the rebuilt plugin configure:

```groovy
converter {
    resourceDirectory = file('src/main/resources')
    atlasNameExtension = 'items'
    // privateKey = file('path/to/private.key') // required for encrypted tables
}
```

`buildConvert` assembles the converted resource JAR. Signing keys are not included
in the repository. The extension retains the `converter` name and supports the
legacy `compile` dependency bucket, forwarding its dependencies to `api`.

IDE integration
---------------

Import the root Gradle project using its wrapper. In IntelliJ IDEA, select
**JDK 25 as the Gradle JVM**; this is separate from the JVM running IntelliJ.
Gradle supplies the per-module compilation settings.

The optional Windows helper `build.ps1` uses `.gradle/gradle-user-home` when
`GRADLE_USER_HOME` is unset. Configure the same Gradle user home in IntelliJ
to reuse that cache.

The migration follows up on [PR #113](https://github.com/Illarion-eV/Illarion-Java/pull/113).
Unlike a small wrapper-only upgrade, Gradle 9 requires replacing removed build
APIs and plugins. Application behaviour and general dependency updates belong
in separate changes.
