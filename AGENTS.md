# Project instructions

The [Illarion Coding Style](https://github.com/vilarion/Illarion-Coding-Style)
is mandatory for every future change to handwritten code in this repository.
Before editing, read the local checklist; consult the full guide when a rule
needs clarification. The version reviewed on 28 September 2026 is
[revision 8b65b3ed](https://github.com/vilarion/Illarion-Coding-Style/blob/8b65b3ed995aa00acaa1b6eb36f649c82a5a9014/README.md).
See [the local checklist](docs/coding-style.md) for its application to this project.
Explicit instructions from the project owner take precedence.

Apply the rules to new and modified code, including tests and build scripts.
Keep unrelated legacy code out of a scoped change. Preserve upstream-generated
files, such as the Gradle wrapper, rather than hand-formatting their contents.
Do not change existing third-party copyright or licence notices as a style fix.
Report any conflict between those notices and the guide.

Before handing over changes, review the diff for both formatting and design
rules, run `git diff --check`, and run checks appropriate to the affected code.
EditorConfig covers basic formatting only; it does not replace that review.

## Fork direction and branch conventions

The following conventions were agreed with the project owner on 8 October 2026.
They govern this fork; they are not requirements imposed by upstream.

Do not use the `codex/` prefix for any branch in this fork, including work that
is not associated with an issue. Use a short, descriptive name instead.

### Repository identities

- `origin`: the owner's fork, `Asyxian/Illarion-Java`.
- `upstream`: the original repository, `Illarion-eV/Illarion-Java`.

Check the actual remotes and branches before operating on them. The agreed
structure below is the target convention, not a claim that branches have already
been renamed or integration has been completed.

### Main branches

| Branch | Purpose |
| --- | --- |
| `upstream_master` | Unmodified local tracking branch for `upstream/master`. |
| `upstream_develop` | Unmodified local tracking branch for `upstream/develop`. |
| `develop` | This fork's integration branch and basis for ongoing development. |
| `master` | Tested release states of this fork, promoted deliberately from `develop`. |

Do not add fork-specific changes to `upstream_master` or `upstream_develop`.
Keep their upstream tracking relationships pointed at the original repository.
The names with underscores are local branches; names such as `upstream/master`
are Git remote-tracking references and must not be confused with them.
Integrate the modernisation and bug fixes on this fork's `develop` first.
Leave this fork's `master` at its existing state until a release is approved.

### Issue branches

Use the issue's owning repository to choose the prefix:

| Issue belongs to | Branch pattern |
| --- | --- |
| This fork (`Asyxian/Illarion-Java`) | `issue_<issue-number>/<short-descriptive-name>` |
| Upstream (`Illarion-eV/Illarion-Java`) | `upstream_issue_<issue-number>/<short-descriptive-name>` |

For example, work on upstream issue #121 would use
`upstream_issue_121/fix-quest-markers` under the new convention.
Use the actual issue number and a very short English description in lowercase
with hyphens. Do not invent issue numbers or use the `codex/` prefix.
The prefix identifies the issue's repository; it does not by itself determine
the base branch or the destination of a pull request.

Existing published PR branches are explicitly grandfathered in. Preserve their
names and PR connections, including the existing `issue_121/...`, `issue_132/...`,
`issue_134/...`, `issue_169/...`, `issue_116/...`, `issue_148/...` and `issue_112/...`
branches for upstream issues. Apply the new scheme to newly created branches;
do not rename existing PR branches as part of a naming cleanup.

### Java baseline and upstream compatibility

The agreed baseline for this fork's integrated development is Java 25 LTS for
building, compiling and running its applications. Java 8 compatibility is no
longer a requirement for this fork, and modernisation may use Java 25 features.
All module compilation targets and tests now use Java 25. Client regression
fixtures use Mockito with an explicit test agent and scoped mock cleanup;
do not reintroduce PowerMock or the temporary Java 8 test setup.
Read [the Java 25 migration notes](docs/java25-migration.md) before changing
Java targets, test dependencies or JVM startup options. Keep the regression
coverage and the consistent native-library options across launch paths.
Keep JavaFX on the launcher's module path or in its linked runtime, separately
from the application classpath; its native-access option includes `javafx.graphics`.

Keep existing independent upstream PRs separate from the fork's integration.
Do not change their Java requirements merely to match this fork. For future
upstream contributions, assess compatibility and the appropriate base branch
separately; upstream acceptance of the fork's Java baseline is not assumed.
Review the existing branch-dependent build/version logic when implementing the
branch structure: `master` has historically been treated as a release branch.

## AI disclosure in comments

From 8 October 2026 onwards, every externally addressed comment authored by the
assistant must include a brief disclosure that it was generated by AI. This
includes issue comments, pull-request comments and review comments, both drafts
provided for manual posting and comments published through tools.
Append a separate italicised sentence in the comment's language, for example:
`*This comment was generated by AI.*`
For German comments, use `*Dieser Kommentar wurde von einer KI erstellt.*`
This rule does not grant permission to publish comments; existing approval
requirements still apply. Do not retroactively edit previously posted comments
unless the project owner requests it.
