# ADR 0001: Record architecture decisions in Markdown

Status: Accepted

Date: 2026-11-13

## Context

Design work involves alternatives and tradeoffs worth preserving beyond individual code changes.
Commit messages explain particular changes, while ticket and chat discussions can leave the broader
reasoning scattered and difficult to find. We need a concise, versioned place for design decisions
alongside the code.

## Decision

Use Markdown Architecture Decision Records (ADRs) in `docs/adr/` for consequential design decisions.
Each record captures the context, chosen approach, relevant alternatives, and consequences.

A lightweight Markdown record fits our current need and can be reviewed and versioned with the code.
Keep commit messages focused on concrete changes, linking to an ADR when useful. Usage documentation
and detailed design documents remain appropriate for their respective purposes.

## Alternatives

- **Commit messages and ticket discussions alone:** useful supporting context, but scattered and
  often tied to individual changes rather than the overall decision.
- **Requests for Comments (RFCs):** useful for gathering feedback on proposals, but unnecessary as
  the default format for preserving decisions. An RFC can lead to an ADR.
- **Larger design documents:** useful for implementation detail and complementary to ADRs. A short
  decision record is sufficient when that level of detail is unnecessary.

## Consequences

- Decisions and their rationale stay discoverable alongside the code without overloading commit
  messages or usage documentation.
- Records require a small amount of maintenance as decisions evolve. When a decision changes,
  a superseding ADR preserves the history and explains the new direction.
