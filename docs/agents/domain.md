# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root, or
- **`CONTEXT-MAP.md`** at the repo root if it exists — it points at one `CONTEXT.md` per context. Read each one relevant to the topic.
- **`docs/adr/`** — read ADRs that touch the area you're about to work in. In multi-context repos, also check `src/<context>/docs/adr/` for context-scoped decisions.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The `/domain-modeling` skill (reached through the documentation route of `/grill` and `/improve-codebase-architecture`) creates them lazily when terms or decisions actually get resolved.

## Repository ADR operations

- The active ADR source is `docs/adr/`; the old single ledger is not a source of truth.
- Record accepted decisions only. Keep proposals and unresolved product choices in the relevant design or product discussion documents.
- When a decision changes, create a new ADR and mark the previous ADR as superseded; do not rewrite history into the latest decision.
- Use `domain-modeling` to decide whether a change needs an ADR. When an ADR is created, changed, or superseded, run `change-impact-review` and record the affected documents, skills, code, tests, unresolved items, and evidence.
- Do not record one-off implementation choices, feature constants, current implementation status, or harness operations as domain ADRs.
- Keep detailed product and architecture rules in their canonical documents and link them from the ADR instead of duplicating them.
- If a historical decision date or rationale cannot be verified, record `확인 불가` or `기록되지 않음` rather than inferring it.

## File structure

Single-context repo (most repos):

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

Multi-context repo (presence of `CONTEXT-MAP.md` at the root):

```
/
├── CONTEXT-MAP.md
├── docs/adr/                          ← system-wide decisions
└── src/
    ├── ordering/
    │   ├── CONTEXT.md
    │   └── docs/adr/                  ← context-specific decisions
    └── billing/
        ├── CONTEXT.md
        └── docs/adr/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (event-sourced orders) — but worth reopening because…_
