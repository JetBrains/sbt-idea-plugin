# ADR 0004: Derive custom properties defaults from the selected runtime config

Status: Accepted

Date: 2026-11-25

## Context

The SDK contributes a production `idea.paths.selector`. IntelliJ's `PathManager.loadProperties`
uses that selector to find custom properties independently of `idea.config.path`. Consequently,
separate development directories do not prevent production-profile properties from being read.
An explicit `idea.properties.file` alone does not prevent that fallback either.

Runtime config paths already express isolation by checkout, target build, and purpose, as well as
explicit overrides and shared development mode (ADR 0002). A separate product/version policy for
properties would disagree with those choices.

## Decision

For Dev IDEA, tests, and indexing, derive these defaults from the selected config directory:

- `idea.paths.selector=SbtIdeaPlugin-<hash>`, using the first 16 lowercase hexadecimal characters
  of SHA-256 of the config path's normalized absolute string, encoded as UTF-8.
- `idea.properties.file=<config>/idea.properties`.

Replace SDK-provided selector/properties-file options. Append explicit user VM options after the
generated defaults, preserving deliberate overrides. Managed directory keys remain authoritative;
legacy builders without directory keys derive defaults from the effective custom config VM option.
Hashing is lexical, with no filesystem access, directory creation, or symlink resolution. Paths
already resolved to the same config reuse the selector across builds and checkouts. Different
symlink spellings supplied as explicit overrides may have different selectors.

The properties file is optional and is never created empty by option generation. It follows its
config directory's lifecycle. Use an explicit external file for customizations that must outlive
managed runtime cleanup. No project-root properties or VM-options discovery is introduced.

The explicit test-runtime relocation helper uses the same defaults. Since `ForkOptions` contains
strings rather than provenance, values equal to defaults derived from the previous config are
treated as managed and relocated; different effective values are preserved as explicit overrides.
An explicitly supplied value identical to a generated default is therefore relocated too.

## Alternatives

- **Only set a properties file:** still reads production-profile fallback properties.
- **Product selector plus `-Dev`:** separates production, but shares fallback profiles across
  isolated checkouts and disagrees with config sharing across target builds.
- **Fixed neutral selector:** shares fallback properties between unrelated runtime configs.
- **Remove the selector:** avoids selector fallback with less code, but disables the platform's
  Edit Custom Properties action on the inspected platform version.
- **Dev-only file defaults:** adds a separate policy for tests and indexing without improving
  isolation. A common rule follows each runtime's selected config.

## Consequences

Production-profile properties are no longer inherited by default. Shared development configs
also share their properties file and selector without another public setting. The hash is a compact
selector name, not a filesystem path; its 64-bit prefix has a nonzero collision probability.

IntelliJ still reads its other properties sources. Edit Custom Properties still targets the
selector's profile directory and can fail if that directory is absent; fixing that action belongs
to [IJPL-37142](https://youtrack.jetbrains.com/issue/IJPL-37142). No selector directory is created.
The split-client properties handoff is preserved. Generated IDEA configurations must be regenerated.

Related issue: [SCL-25595](https://youtrack.jetbrains.com/issue/SCL-25595).
