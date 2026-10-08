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
The [integration notes](docs/fork-integration.md) record the contributing branches,
test-environment adjustments and validation results.

Build
-----

Install **JDK 25**, set `JAVA_HOME` to that installation and use the checked-in
**Gradle 9.8.0 wrapper**. A JDK with bundled JavaFX is no longer required.

All modules, tests and applications use **Java 25**. No additional JDK 8 or
JavaFX-enabled JDK is needed. The client regression fixtures use Mockito with
an explicit test JVM agent instead of PowerMock's legacy class loader.

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

### Java baseline and runtime options

All handwritten and generated Java/Groovy modules target Java 25, including
the resource converter plugin and the standalone NPC compiler. Consumers of
the rebuilt plugin also need Java 25. Java 8 compatibility is retained only
on the independent upstream PR branches, not on this fork's `develop`.
The old `-PtestJavaVersion=8` override is rejected with an explanatory error.

Gradle application runs, generated distribution scripts and the packaged
launcher use these JVM options:

```text
--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=deny
```

The launcher's child JVMs receive the same options and must be Java 25 or newer.
It no longer reads or forwards the obsolete `launchAggressive` setting.
For a direct IDE application run, enter these options in the VM options field;
delegated Gradle runs already include them. The libGDX backend aligns LWJGL
3.4.3 across its Java bindings and native libraries.

Generated Windows scripts use a `lib/*` classpath to stay below the Windows
command-line limit. Keep the distribution's `lib` directory free of stale JARs.

The opt-in `:engine-libgdx:nativeRuntimeTest` exercises a hidden OpenGL window
with the same runtime options. It requires working desktop graphics.
See [the Java 25 migration notes](docs/java25-migration.md) for regression coverage
and [the runtime notes](docs/modernization-follow-up.md) for platform limitations.

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
