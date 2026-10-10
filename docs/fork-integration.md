# Fork integration

This records the integration baseline at `40ffa4dd`. Its temporary Java 8 test
setup has since been replaced by the [Java 25 migration](java25-migration.md).
Use the README and migration notes for current build instructions.

Integration started on 8 October 2026, from upstream commit `d71f2c3a`.
The integration branch is `develop`. The fork's `master` remains at `f235f368`;
release promotion is a separate decision. Existing upstream PR branches retain
their names, commits and compatibility requirements.

## Preserved history

The previously uncommitted modernisation and documentation were saved as
`adae6057` on `modernize-build` before switching the primary checkout to
`develop`. The integration uses ordinary merge commits, retaining the history
of each contributing branch.

The build modernisation from `issue_169/modernize-gradle` is the build baseline.
When merging the earlier local modernisation, the PR's release-branch guard,
resource-converter implementation and PMD configuration were retained. The
local branch adds the LWJGL alignment, launcher runtime fixes, native smoke
test, local launcher packaging and project documentation. The README combines
the build instructions with those runtime changes.

## Bug-fix branches

| Upstream issue | Branch | Behaviour covered |
| --- | --- | --- |
| #121 | `issue_121/fix-quest-markers` | Preserve quest selection while refreshing map markers. |
| #132 | `issue_132/fix-password-encoding` | Hash non-ASCII passwords using their UTF-8 byte lengths. |
| #134 | `issue_134/fix-crafting-dialogs` | Reset closed crafting dialogs and process requests in order. |
| #116 | `issue_116/fix-relogin-messages` | Wait for game-screen readiness on every login. |
| #148 | `issue_148/preserve-chat-position` | Preserve chat position on new messages and resizing. |
| #112 | `issue_112/fix-warp-movement` | Invalidate predicted and queued movement when warping. |

Each branch was integrated separately and checked before proceeding. The final
client test run included all fixtures together: their legacy shared
PowerMock class loader previously caused an interaction between GUI tests.
The correction from `f79c5158` on the #121 branch is included in that merge.

## Transitional test environment

Gradle and compilation use JDK 25. Existing module compilation targets remain
unchanged during integration. The client regression suite still uses
PowerMock 2.0.9, EasyMock 4.3 and TestNG 6.9.10 on JDK 8. These are test-only
dependencies; other module tests retain TestNG 7.5.1 and run on JDK 25 by default.

EasyMock 3.4 failed when mocking `IllaClient` compiled by javac 25 with
`--release 8`: its embedded CGLIB bridge-method visitor cannot process the
`MethodParameters` attribute of the synthetic `onEvent` bridge. The integration
updates this test-only dependency rather than altering application bytecode or
omitting the affected lifecycle tests.
PowerMock is updated alongside EasyMock because version 1.6.4 references an
internal EasyMock mock-type class that no longer exists in EasyMock 4.

Supply `-Porg.gradle.java.installations.paths=/path/to/jdk8` if Gradle cannot
discover the extra test JDK. The client test task selects it without changing
the Gradle JVM or the client runtime. No regression tests are disabled.

The planned Java 25 baseline is a separate follow-up. Replace the PowerMock
fixtures before removing this temporary test configuration or raising client
bytecode targets. Preserve the regression assertions, including the combined
GUI and movement coverage, during that migration.

## Validation

The modernisation merge passed compilation, the existing tests, the packaged
compiler check and the native smoke test on Windows with JDK 25.

The cumulative client/common regression counts after each bug-fix merge were:

| Last issue merged | Client tests | Common tests |
| --- | ---: | ---: |
| #121 | 19 | 2 (unchanged modernisation baseline) |
| #132 | 19 | 15 |
| #134 | 52 | 15 |
| #116 | 59 | 15 |
| #148 | 76 | 15 |
| #112 | 96 | 15 |

The final clean build used the following command with `JAVA_HOME` set to JDK 25
and the additional JDK 8 test installation configured:

```sh
./gradlew --offline --no-daemon clean build :client:installDist :download:installDist :engine-libgdx:nativeRuntimeTest
```

All 121 scheduled tasks executed successfully. The reports contain **150 unit
and regression test cases plus one native runtime test**, with no failures,
errors or skipped cases. The standalone compiler generated the bundled NPC
template's Lua output. The native test uses an invisible OpenGL window and
denies legacy Unsafe memory access.

The installed client distribution also opened its window and stayed running
for a 20-second startup check on Java 25 with an isolated user profile. This
ordinary launch still emitted LWJGL's deprecated Unsafe warning. A second
20-second check with `--sun-misc-unsafe-memory-access=deny` passed without native
warnings; see the [runtime follow-up notes](modernization-follow-up.md).
No account login or live gameplay was performed. Only the test processes were
stopped afterwards.

Logs are kept locally in the ignored `.gradle/integration-*.log` files and
`.gradle/integration-client-smoke/`. Machine-specific JDK discovery is configured
in the ignored `.gradle/gradle-user-home/gradle.properties`, not in shared build
configuration. The Windows helper was also checked without a JDK-path override.

Live server login, full gameplay and non-Windows platforms require separate
manual validation. Existing non-blocking PMD/SpotBugs findings and the SpotBugs
plugin's Gradle deprecation remain documented in the runtime follow-up notes.

## Avatar label cache follow-up on 10 October 2026

Upstream issue [#161](https://github.com/Illarion-eV/Illarion-Java/issues/161)
is fixed by commit `aa0315d0`, published independently in
[PR #174](https://github.com/Illarion-eV/Illarion-Java/pull/174) from
`upstream_issue_161/fix-avatar-labels` against upstream `develop`.
The same commit is merged into this fork's Java 25 `develop` without source
or test adaptations. The upstream branch retains Java 8 and Gradle 3.5.

Text dimensions and offsets are cached until the name or health text changes.
The screen rectangle continues to follow position and avatar-height changes
on each positioned update. Eleven new tests cover cache reuse, invalidation,
movement, height, resumed updates after hiding, missing positions and empty
labels. They use a recording font and require neither a graphics context nor
PowerMock/Mockito. Ten cases fail with the original measurement behaviour.

Validation:

- A clean Java 8 / Gradle 3.5 client/common test run on the upstream branch
  passed all 17 cases without failures, errors or skips. A local init script
  pinned cached resources to 2.3.3 and disabled the skills download; it is not
  included in the PR. Cleaning removed stale outputs from earlier branches.
- On the integrated Java 25 branch,
  `./gradlew --offline --no-daemon :client:test :common:test :client:installDist`
  passed. All 107 client tests ran successfully; the unchanged common test
  task was up to date with 15 passing cases. The reports contain no failures,
  errors or skips. The client distribution was rebuilt.
- Coding-style review and `git diff --check` passed. No new gameplay session
  or frame-time benchmark was performed; no numerical FPS gain is claimed.

The merge required no conflict resolution. The changed files do not overlap
with the earlier independently published bug-fix and build-modernisation PRs.
