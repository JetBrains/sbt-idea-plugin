# Architecture Decision Records

Architecture Decision Records (ADRs) preserve meaningful design choices, their reasoning, and tradeoffs
alongside the code. Use them for consequential decisions, not every code change.

## Index

- [ADR 0001: Record architecture decisions in Markdown](0001-record-architecture-decisions.md) — Accepted
- [ADR 0002: Organize IntelliJ runtime directories by checkout](0002-organize-intellij-runtime-directories-by-checkout.md) — Accepted; implementation pending

## Conventions

- Number records sequentially using `NNNN-short-title.md`.
- Keep records concise: include status, context, decision, alternatives, and consequences.
- Use **Proposed** while a decision is under discussion and **Accepted** once adopted.
- When a decision is replaced, add a new record, mark the original **Superseded**, and link the two
  records in both directions. Preserve the original reasoning; clarifications and corrections can be
  edited in place.
