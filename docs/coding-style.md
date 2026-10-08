# Coding style

The project owner requires the [Illarion Coding Style][style] for all future
handwritten code changes, including Java, Groovy, Gradle scripts and tests.
Version 1.0 was reviewed against [revision 8b65b3ed][reviewed] on 28 September 2026.

## Review checklist

- Use four spaces per block level and keep code lines within 120 characters.
- Give statements and block boundaries their own lines. Separate adjacent
  blocks and logical groups with a blank line.
- Keep spacing consistent around operators and after delimiters; leave no
  trailing whitespace or space between a function name and its arguments.
- Use lowerCamelCase for variables and functions, UpperCamelCase for classes,
  descriptive names and British English in identifiers and comments.
- Keep functions focused, state local and comments useful. Prefer existing
  language/library facilities; avoid duplication, unexplained constants,
  redundant code, deprecated APIs and `goto`.
- Include the appropriate licence as a block comment in new source files.

The root `.editorconfig` supports the mechanical rules. Before submitting,
review the diff manually and run `git diff --check` plus the relevant build
checks. Neither EditorConfig nor the existing PMD/SpotBugs rules enforce the
complete guide. Existing external API identifiers and test data are preserved.

## Scope and licence notices

The review of the initial modernisation covers our changes after `d71f2c3a`,
not a reformat of the entire upstream codebase. Generated Gradle wrapper files
retain their upstream formatting and licence. Resource encodings are not changed.

The guide specifies GPLv3 for client-side sources and AGPLv3 for server-side
sources. The repository's `LICENSE` is GPLv3, but many existing Java/Groovy/Gradle
headers say AGPLv3. Preserve those existing notices: resolving this discrepancy
requires clarification from upstream, not a blanket replacement during styling.
New client tests and the new PowerShell build helper carry GPLv3 notices.

The SpotBugs plugin's Gradle deprecation warning is recorded separately in
[the modernisation notes](modernization-follow-up.md). It originates in the
dependency, not in new calls to deprecated Gradle APIs in our code.

[style]: https://github.com/vilarion/Illarion-Coding-Style
[reviewed]: https://github.com/vilarion/Illarion-Coding-Style/blob/8b65b3ed995aa00acaa1b6eb36f649c82a5a9014/README.md
