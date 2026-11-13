# ADR 0002: Organize IntelliJ runtime directories by checkout

Status: Accepted

Date: 2026-11-13

Implementation: Pending

## Context

Tests need separate mutable IntelliJ runtime data in different checkouts/worktrees so they can run
in parallel. The current implementation isolates test system/config directories by checkout.
Extending isolation to distinguish IDE builds and runtime purposes, such as tests and a development
IDE, introduces three grouping axes. Their order affects browsing and bulk management, while the
identities included in the path determine which runtimes share data.

We want a shallow, understandable hierarchy that keeps a worktree's runtime data together and makes
individual runtimes easy to identify. SDK installations and download caches have a separate lifecycle
and should remain outside the runtime hierarchy.

## Decision

Use the following format for automatically managed runtime directories when implementing the
extended layout:

```text
<intellijPluginDirectory>/runtime/<checkoutHash>/<product-build>-<purpose>/{system,config}
```

- `checkoutHash` is the existing 10-character SHA-256 prefix of the canonical build-root path.
- `product-build` identifies the IDE product/edition and full IDE build, rather than only its release
  line. Resolution of mutable snapshot identities needs separate implementation design.
- `purpose` distinguishes runtimes such as `tests` and `dev`.
- Each runtime keeps `system` and `config` as separate child directories. Logs can remain under
  `system/log`, following the current convention.

Example using an illustrative IDE build number and the checkout hash `a0f9d9b86d`:

```text
~/.ScalaPluginIU/runtime/
  a0f9d9b86d/
    IU-263.12345.67-tests/
      system/
        log/
      config/
    IU-263.12345.67-dev/
      system/
      config/
    IU-263.12346.10-tests/
      system/
      config/
```

Checkout-first grouping puts all runtime data for a worktree under one parent. Combining build and
purpose removes one grouping level compared with a fully nested layout. Putting the build before
the purpose in the combined name groups a build's test and development entries together when sorted.

This record accepts the intended directory format before implementation; it does not change current
runtime paths or settle every runtime lifecycle policy.

## Alternatives

Below, `B` means product and full build, `H` means checkout hash, and `P` means purpose. Patterns are
relative to `runtime/` and all end in `/{system,config}`. A hyphen combines two axes in one directory
name; reversing those tokens changes sorting rather than the grouping structure.

### Fully nested layouts

| Pattern | Advantages | Disadvantages |
|---|---|---|
| `B/H/P` | One subtree per build, then all purposes for each checkout. | A checkout's data spans builds; three grouping levels. |
| `B/P/H` | Convenient build retirement and purpose-specific inspection within a build. | A checkout's data spans builds and purposes; three grouping levels. |
| `H/B/P` | All checkout data together, with explicit build boundaries. | Build-wide operations span checkouts; three grouping levels. |
| `H/P/B` | Convenient inspection of one checkout's test or development data across builds. | A build's data spans purposes and checkouts; three grouping levels. |
| `P/B/H` | Clear purpose-specific grouping and build management within each purpose. | Checkout-wide operations span branches; three grouping levels. |
| `P/H/B` | Groups one checkout's builds for each purpose. | Build-wide and checkout-wide operations span branches; three grouping levels. |

### Layouts combining two axes

| Pattern | Advantages | Disadvantages |
|---|---|---|
| `H/B-P` — chosen | All checkout data together; each runtime directly visible inside it; two grouping levels. | Build-wide and purpose-wide operations require traversal or metadata. |
| `B/P-H` — closest alternative | All build data together; each runtime directly visible inside it; two grouping levels. | Checkout-wide operations span builds. |
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

## Closest alternative and significance of the choice

The closest alternative is `runtime/<product-build>/<purpose>-<checkoutHash>/{system,config}`.
It is equally valid for isolation and slightly more convenient when retiring old nightly builds
across many checkouts. We prefer checkout-first because worktree ownership and lifecycle are a better
primary grouping for this use case: inspecting or retiring one worktree's runtime data is direct.

The ordering is a practical preference, not a critical correctness requirement. Both layouts include
the same identities and provide equivalent separation; the choice does not depend on an expected
performance advantage. Combining path components reduces directory depth but generally does not
reduce total path length. Ownership and activity metadata can support cleanup across either grouping.

We can revisit the ordering if actual usage favors build-first. A later path change would still need
documented migration or recreation of disposable data, with preservation of valuable development
configuration and other user data.

## Consequences and follow-up

- Runtime directories remain separate from SDKs and downloads. An individual runtime has one subtree,
  but that does not make all of its contents equally disposable.
- More build-specific runtimes increase disk usage and introduce cold caches for new builds. The
  directory structure alone is neither proof of ownership nor evidence that a runtime is idle.
- This layout does not isolate simultaneous JVMs sharing the same checkout, build, and purpose.
- Implementation must preserve support for explicit directory overrides.
- Mutable snapshot identity, development-config reuse/import, legacy-path migration, cleanup
  eligibility and retention, and active-JVM protection need separate design before implementation.
  Those policies are not implicitly accepted by choosing this hierarchy.

Related issue: [SCL-26436](https://youtrack.jetbrains.com/issue/SCL-26436).
