# ADR 0003: Automatically expire unused managed test and indexing runtimes

Status: Accepted; implemented

Date: 2026-11-16

## Context

[Runtime isolation](0002-organize-intellij-runtime-directories.md) retains a separate system/config
pair for each checkout, target SDK build, and purpose. Removed worktrees and superseded builds leave
data behind. These temporary test/indexing locations should need little developer maintenance.

## Decision

Automatically expire managed test and searchable-options indexing data after seven days without sbt
use, checking at most once per 24 hours. Both durations are configurable. Development backend/client
data, explicit directory overrides, and untracked legacy data are excluded.

"Used" means sbt loaded or worked with the checkout/build combination. Refresh a versioned usage
record on load/reload, during task activity at most hourly, and before test/indexing launches.
IDEA-only execution does not refresh this timestamp. This deliberately measures sbt interest rather
than every access by a runtime JVM.

Store records in `<intellijPluginDirectory>/.sbt-idea-plugin/runtime-usage-v1/`, outside the caches.
Record ownership, canonical checkout, target build, relative managed paths, retention, and automatic
cleanup eligibility; the file modification time is last use. Each record's policy is authoritative,
so another checkout cannot shorten it. Unknown formats and unsafe paths are skipped. Explicit paths
are never enrolled; observing an override withdraws matching prior ownership.

Use the existing setup sequence and compose sbt's task-completion hook. Cheap in-memory checks and a
persisted last-attempt timestamp debounce scans across sessions. A plugin-home lock serializes
cleanup, and stable per-record locks coordinate refreshing and deletion. Registration does not
initialize runtime caches; unavailable SDK context skips maintenance without downloading or failing
unrelated commands.

Before deletion, inspect Java processes and Java argument files for selected paths. Defer uncertain
or active candidates. These are best-effort checks: startup races remain possible. Delete only
recorded managed paths without following symlinks, count successfully removed file bytes, and report
partial failures without failing the sbt command.

Document SDK, download, and runtime cleanup together, while retaining their separate policies and
the existing SDK/download defaults and behavior.

Expose `showIntellijRuntimeCleanup` for a registry-based snapshot of recorded policies, paths,
validation failures, throttling, and current expiry/process protection. Share validation and pure
eligibility rules with deletion; keep reporting separate from registry mutation and removal. The
report body does not mutate maintenance state or evaluate SDK identity tasks. Ordinary sbt lifecycle
hooks still run, so the command invocation as a whole is not a side-effect-free dry run. Do not retain
historical skip decisions or probe locks just to report status.

## Alternatives

- **A JVM lifecycle agent:** could track direct IDEA execution and coordinate JVM lifetimes more
  reliably, but adds launch integration and complexity. We explicitly choose sbt usage markers and
  best-effort process checks instead; no bytecode agent or background JVM is required.
- **Creation age:** simpler, but expires frequently reused runtimes unnecessarily. Last sbt use is a
  better approximation for abandoned worktrees/builds.
- **Recursive modification-time scans:** avoid explicit registration, but inspect many cache files
  and do not reliably establish use or ownership. A small versioned registry is cheaper and can
  retain readers for older directory layouts.
- **Manual cleanup only:** gives control but does not meet the goal of minimal developer involvement.

## Consequences

Automatic runtime cleanup defaults to enabled, unlike existing opt-in SDK/download removal. Each
owner records its policy, including opting its refreshed records out. Expiry happens on a subsequent
due sbt task completion, not at a scheduled wall-clock time. Long-lived sbt sessions refresh active
contexts; reconfiguration invalidates cached context.

Repeated IDEA-only work may still encounter expired caches when no sbt activity refreshes their
record. Process checks reduce the risk of deleting in-use data but do not provide a JVM-lifetime
guarantee. Existing unregistered data remains untouched until managed registration or separate manual
cleanup. Development settings, scratches, and history are outside this policy.
