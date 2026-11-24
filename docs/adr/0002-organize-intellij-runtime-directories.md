# ADR 0002: Organize IntelliJ runtimes by build, checkout, and purpose

Status: Accepted

Date: 2026-11-13

Implementation: Implemented

## Context

Tests need separate mutable IntelliJ runtime data in different checkouts/worktrees so they can run
in parallel. The preceding implementation isolated test system/config directories by checkout.
Extending isolation to distinguish IDE builds and runtime purposes, such as tests and a development
IDE, introduces three grouping axes. Their order affects browsing and bulk management, while the
identities included in the path determine which runtimes share data.

We want an understandable hierarchy with useful information near the top and a manageable balance
between width and depth. SDK installations and download caches have a separate lifecycle and should
remain outside the runtime hierarchy.

## Decision

Use the following format for automatically managed runtime directories:

```text
<intellijPluginDirectory>/runtime/<product-build>/<checkoutHash>/<purpose>/{system,config}
```

- `checkoutHash` is the existing 10-character SHA-256 prefix of the canonical build-root path.
- `product-build` identifies the installed target SDK's product/edition and full IDE build, rather
  than its requested alias or only its release line. The primary IDE used to edit the project does
  not determine this identity.
- `purpose` distinguishes runtimes such as `tests` and `dev`.
- Each runtime keeps `system` and `config` as separate child directories. Logs can remain under
  `system/log`, following the current convention.

Example using an illustrative IDE build number and the checkout hash `a0f9d9b86d`:

```text
~/.ScalaPluginIU/runtime/
  IU-263.12345.67/
    a0f9d9b86d/
      tests/
        system/
          log/
        config/
      dev/
        system/
        config/
  IU-263.12346.10/
    a0f9d9b86d/
      tests/
        system/
        config/
```

Build-first grouping makes product and full build visible when opening `runtime/`. Each build/checkout
pair keeps its purposes together, and each purpose sits directly above `system` and `config`. A
worktree's data spans builds, and the fully nested format adds a level compared with combining axes.

The initial local implementation used checkout-first grouping with combined build/purpose names.
This record and the implementation were revised together before release. The earlier preference and
the other alternatives remain below. The separate runtime-retention decision is recorded in
[ADR 0003](0003-expire-unused-managed-runtimes.md).

### Subjective preference and revisiting the choice

We do not have hard evidence that this ordering is the best alternative. The final choice is partly
an intuitive visual preference: keeping purpose directories immediately above `system` and `config`
looks pleasant and makes the structure feel coherent. The browsing and grouping benefits are useful
tradeoffs, not proof that this arrangement is objectively superior.

There are many reasonable layouts, and the exact ordering is not essential to correctness or to
agent-assisted development. We choose a workable format rather than prolonging the comparison. If
practical evidence favors another layout, such as repeated browsing friction or awkward runtime
management, raise the question again with the team and choose another alternative. A future change
must still account for existing runtime paths and preserve valuable data.

### Runtime coverage and build identity

Move tests, development IDEs, split-mode clients, and searchable-options indexing together. Use
`tests`, `dev`, and `build-index` purposes; the split-mode frontend lives under the development
runtime's `client/{system,config}` directories. Logs follow each selected system directory.

Resolve the default `intellijRuntimeBuildId` from installed `product-info.json` as product code plus
full build number. An explicit task-key override supplies a distinct, path-safe identity for custom
SDKs rebuilt without changing their reported build number. Do not introduce automatic installation
tracking or silently fall back to a requested alias when metadata is missing.

### Directory keys and compatibility

Change `intellijTestSystemDir` and `intellijTestConfigDir` from settings to tasks. Installed SDK
metadata is already task-based and may be unavailable when settings are evaluated. Keeping settings
would instead require SDK setup and reload coordination; tasks fit the existing consumers, which
are already tasks. Path resolution only reads metadata, without installing SDKs or creating runtimes.

Ordinary `:=` directory overrides and task reads remain supported. Setting reads must be adapted,
and the key-type change is both source and binary incompatible for affected consumers. Keep existing
SDK-selection and custom VM-option settings as settings. Preserve the published explicit-ID
`IntellijTestRuntimeDirectories.forRuntimeId` and `applyTo` APIs and their existing path semantics.

Add independent task keys for development system/config and frontend system/config paths. The
VM-option builder prefers directory keys over conflicting raw directory
properties. Lower-level sbt `Test / javaOptions` overrides remain available and can deliberately
diverge from the directory keys, generated JUnit configurations, markers, and cleanup targets.
Fully explicit directory overrides must avoid unnecessary identity dependencies.

### Optional shared development directories

Isolate development directories by default, just like tests. A single boolean setting,
`intellijDevUseSharedDirectories`, selects these paths across checkouts and builds when enabled:

```text
runtime/system-shared/
runtime/config-shared/
runtime/system-client-shared/
runtime/config-client-shared/
```

The setting defaults to false, or to the value supplied with
`-Dsbtidea.dev.sharedDirectories=true` to the sbt JVM. An explicit sbt assignment overrides this
property-derived default. Each exact directory override wins independently over the mode's default
path. Tests and indexing remain isolated. Shared development directories assume sequential use.

### Existing data and preparation

Start new development configs fresh; settings import is manual. Preserve all old directories,
including those from the earlier local checkout-first layout, without automatic copying, migration,
or deletion during layout adoption. Subsequent expiry of registered managed data follows
[ADR 0003](0003-expire-unused-managed-runtimes.md); untracked legacy data remains excluded. Developers
can explicitly select legacy paths when they want to reuse them. Installed plugins remain SDK-scoped;
sharing config does not share plugin files.

Keep non-overwriting README markers in managed test directories, identifying checkout and build.
Create them during test preparation or JUnit configuration generation, never on path inspection or
in explicitly overridden directories. `cleanUpTestEnvironment` deletes only the selected test system/config
directories, never an entire checkout root, other builds, or shared development data.

Regenerate IDEA configurations after changing build identity or directory settings. Automatic
retention is specified separately in [ADR 0003](0003-expire-unused-managed-runtimes.md), using
best-effort process checks rather than JVM-lifetime tracking. The release version bump remains
deferred to pre-push/pre-release preparation.

## Alternatives

Below, `B` means product and full build, `H` means checkout hash, and `P` means purpose. Patterns are
relative to `runtime/` and all end in `/{system,config}`. A hyphen combines two axes in one directory
name; reversing those tokens changes sorting rather than the grouping structure.

### Fully nested layouts

| Pattern | Advantages | Disadvantages |
|---|---|---|
| `B/H/P` — chosen | Meaningful build directories at the root; all purposes for a build/checkout pair together; purpose directly above system/config. | A checkout's data spans builds; three grouping levels. |
| `B/P/H` — closest fully nested alternative | Convenient build retirement and purpose-specific inspection within a build; same grouping depth as the chosen layout. | A checkout's purposes are separated; three grouping levels. |
| `H/B/P` | All checkout data together, with explicit build boundaries. | Build-wide operations span checkouts; three grouping levels. |
| `H/P/B` | Convenient inspection of one checkout's test or development data across builds. | A build's data spans purposes and checkouts; three grouping levels. |
| `P/B/H` | Clear purpose-specific grouping and build management within each purpose. | Checkout-wide operations span branches; three grouping levels. |
| `P/H/B` | Groups one checkout's builds for each purpose. | Build-wide and checkout-wide operations span branches; three grouping levels. |

### Layouts combining two axes

| Pattern | Advantages | Disadvantages |
|---|---|---|
| `H/B-P` — initial local choice | All checkout data together; each runtime directly visible inside it; two grouping levels. | Opaque hashes at the root; build-wide and purpose-wide operations require traversal or metadata. |
| `B/P-H` — shallower build-first alternative | All build data together; each runtime directly visible inside it; two grouping levels. | Checkout-wide operations span builds; more siblings inside each build. |
| `P/B-H` | Clear test/development separation and purpose-specific management. | Neither builds nor checkouts have a complete subtree. |
| `B-H/P` | All purposes for one build/checkout pair together. | More entries at the runtime root; neither build nor checkout has its own parent. |
| `B-P/H` | Groups all checkouts for one build and purpose. | Build-wide operations span purposes; checkout-wide operations span multiple parents. |
| `H-P/B` | Groups all builds for one checkout and purpose. | Checkout-wide operations span purposes; build-wide operations span multiple parents. |

### Other placements

- **Fully flattened, such as `P-B-H`:** one grouping level and self-contained runtime names, but a
  crowded root as builds and checkouts accumulate. Build-wide and checkout-wide operations both need
  filtering. Token order mainly determines listing order.
- **Build above `runtime`, such as `<pluginHome>/B/runtime/P-H`:** reasonable for a wider redesign
  that organizes the entire plugin home by build. Keeping `runtime/` directly under the plugin home
  gives runtime management one clear boundary without restructuring shared SDK/download locations.
- **Build below `system` and `config`:** permits different version scopes, such as system data per
  full build and config per release line. One running IDE's data then spans separate branches,
  complicating inspection and cleanup; shared config still needs its own concurrency policy.

## Closest alternatives and earlier preference

The closest fully nested alternative is
`runtime/<product-build>/<purpose>/<checkoutHash>/{system,config}`. It has the same grouping depth
but shows purpose before checkout. It is equally valid; our preference for keeping purpose next to
system/config is subjective rather than backed by evidence of better usability.

The strongest shallower contender is
`runtime/<product-build>/<purpose>-<checkoutHash>/{system,config}`. It keeps build information at the
root and removes one level, at the cost of more entries inside each build directory.

Our initial checkout-first choice, `runtime/<checkoutHash>/<product-build>-<purpose>/{system,config}`,
prioritized keeping all of a worktree's data together. On reconsideration, hashes give little useful
information at the root, while product/build names identify a release line and exact SDK build.
Worktrees typically stay within a release line, although SDK updates can accumulate several builds.
These observations support build-first browsing without proving a unique best layout.

All these layouts include the same identities and provide equivalent separation. No performance
advantage is claimed. Combining path components reduces directory depth but generally does not
reduce total path length. Ownership and activity metadata can support cleanup across these groupings.

## Consequences and follow-up

- Runtime directories remain separate from SDKs and downloads. An individual runtime has one subtree,
  but that does not make all of its contents equally disposable.
- More build-specific runtimes increase disk usage and introduce cold caches for new builds. The
  directory structure alone is neither proof of ownership nor evidence that a runtime is idle.
- This layout does not isolate simultaneous JVMs sharing the same checkout, build, and purpose.
- Explicit directory overrides remain supported.
- Automatic cleanup eligibility, retention, and best-effort active-process checks are specified
  separately in [ADR 0003](0003-expire-unused-managed-runtimes.md). The hierarchy and manual cleanup
  command alone do not establish that a runtime is safe to remove.

Usage and migration: [Runtime directory isolation](../../README.md#runtime-directory-isolation).

Related issue: [SCL-26436](https://youtrack.jetbrains.com/issue/SCL-26436).
