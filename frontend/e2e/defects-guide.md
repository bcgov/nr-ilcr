# How the defect log works (legend)

**Audience:** BA / QA and anyone reviewing the suite — you do **not** need to know the codebase.
This is the stable reference for the **per-use-case defect logs**: one `defects.md` lives in each UC
folder next to its `coverage.md` (e.g. `features/<domain>/uc-<domain>-001-create/defects.md`). To see
everything open across the whole suite at once, use the scan commands under "Where the entries live"
below.

**BA/QA own triage and disposition** — deciding whether a finding is a real bug, raising the Jira ticket,
and closing it. The test author (the skill) only records evidence and does mechanical upkeep; it never
changes the app to match the old spec, and it never adjudicates.

> **Audience note:** write every entry so a **BA/QA reader who does not know the codebase** can
> understand it. Lead each entry with a plain-language summary; put code paths / jargon *after* it as
> supporting detail. See "How to read an entry".

## Glossary (read this first)

**Suite terms** *(replace/extend the app-specific rows for your domain)*

| Term | Plain meaning |
|---|---|
| **Domain** | A subject area of the app (a short code, e.g. `<DOMAIN>`); groups related use cases. Every feature/coverage/defect file lives under its domain. |
| **Use case (UC)** | One user-facing task (e.g. create / update / view / delete a `<your-resource>`), identified `UC-<DOMAIN>-<NNN>`. |
| **Slice** | A numbered chunk of a use case's Gherkin (`S01`, `S02`, …) — the traceability unit a `@S<NN>` tag points at. |
| **ERR-### / STA-###** | Numbered error/state messages from the legacy requirements catalog. |
| **Re-grounding** | Translating the legacy Gherkin (its old routes / field names / message wording) onto the *new* app. |
| **Real seeded DB (image + patches)** | The suite runs against **real data** in a pre-built Oracle Docker image (the real extract) with the `real-test-data-patches/` SQL applied on top per container via `scripts/apply-patches.sh` — not a synthetic mock seed. Fixtures pin real anchors (record ids, codes, keys) with provenance in each `fixtures/<domain>/*-test-data.ts`. |
| **Anchor** | A pinned real value a fixture depends on (an existing record id, a code, a seed key). The `preflight/` setup asserts anchors still resolve before the suite runs. |

**What the tags on a test mean**

| Tag | Meaning |
|---|---|
| `@discovered-divergence` | A test that **deliberately stays RED** because it reproduces a divergence (see below). Filter it out of a "fresh failures only" run with `--grep-invert @discovered-divergence`. |
| `@discovered-bug` | A test that **deliberately stays RED** because it reproduces a confirmed bug/regression awaiting a fix (has a Jira ticket). Same filter idea. |
| `@skip` | A scenario that **cannot be automated today** (e.g. blocked by single-role mock auth) — skipped, never used to hide a failure. |

## Registers (what kind of finding is this?)

Every entry sits in exactly one register. The five kinds, in plain language:

| Register | Plain meaning | Compares | Fixed by |
|---|---|---|---|
| **Divergence** | The app behaves **differently from the (legacy-derived) spec**. Might be a real bug *or* a deliberate change — BA/QA decide. Kept as a genuinely-failing `@discovered-divergence` test when it looks like a defect. | app **vs** spec | Dev (via BA/QA → Jira) *or* update the spec if intended |
| **Bug / Regression** | The app is **genuinely broken** (not just different from the old spec) — e.g. something that worked now fails. Almost always a ticket. Kept as a genuinely-failing `@discovered-bug` test if it can't be fixed right away. | app **vs** correct behavior | Dev (via BA/QA → Jira) |
| **Coverage gap** | **We haven't tested it yet** — skipped, deferred, or not automatable today. Not an app problem. | our tests **vs** what should be tested | Test author, later |
| **Spec gap** | The Gherkin test-spec is **missing scenarios its own source documents list** — a paperwork mismatch, *not* an app problem. (Example: the detailed doc lists 5 required fields but the `.feature` only wrote 3.) | Gherkin **vs** its own source docs | A BA regenerates the `.feature` |
| **Verified — not a defect** | Looked wrong at first, but we **confirmed it's correct**. Kept so nobody re-investigates it. | — | N/A |

**Status flow:** Divergence & Bug/Regression: `OPEN → TRIAGED → JIRA-<key> → CLOSED` (BA/QA cut + resolve
the ticket). Coverage gap / Spec gap: `OPEN → CLOSED` when the test is finally written / the BA
regenerates the `.feature`. Verified-not-a-defect: permanent.

## Entry ids — the `BUG-` / `DIV-` / `GAP-` / `SPEC-` prefixes

Every entry is numbered **within its own register**, and the id carries the register as a prefix so a
reference is unambiguous on its own — you can read `DIV-3` in a `.feature` comment or a commit message and
know exactly which log and which register to open, without the surrounding sentence:

| Prefix | Register |
|---|---|
| `BUG-<n>` | Bug / Regression |
| `DIV-<n>` | Divergence |
| `GAP-<n>` | Coverage gap |
| `SPEC-<n>` | Spec gap |
| `VER-<n>` | Verified — not a defect *(numbered only where entries are cross-referenced)* |

**Numbers are permanent and never reused.** An entry that turns out to be wrong is kept and marked
`RETRACTED (author error)`; one that the app has made obsolete is marked `RETIRED (obsolete)`. Both stay in
place with their original number, because the id may already be cited in a `.feature` comment, a
`coverage.md` row, a commit message or a Jira ticket — renumbering would silently break those. Numbering is
also per-register, so `BUG-2` and `DIV-2` are unrelated entries.

Ids are **local to one use case's log**. When crossing UCs, name the path too — e.g.
"`features/sch1/uc-sch1-001-enter-save/defects.md` BUG-2".

## How to read an entry

Every entry — whatever its register — shares the same frame: it **leads with a plain-language summary**
you can follow without the codebase, gives supporting detail with **one part per `  - ` sub-bullet**
(code paths and jargon come *after* the summary), and ends with a **`Status:`** and a **`Test:`** line
(the scenario that reproduces or covers it, or why there is none).

Beyond that frame each register answers a **different** question, so the middle fields differ — this is
what to look for in each:

| Register | The question it answers | Tell-tale fields |
|---|---|---|
| **Bug / Regression** | "What's broken, and how bad?" | What's wrong · Expected vs actual · How we caught it · Action (→ `@discovered-bug`) |
| **Divergence** | "Differs from the old spec — is it a defect?" | Expected vs actual · **Is it a defect?** · Action (→ `@discovered-divergence`, *or* a note that it is a green accepted re-grounding) |
| **Coverage gap** | "Why isn't it tested, and when will it be?" | **Why not** · **Future action** |
| **Spec gap** | "What did the `.feature` drop from its own source?" | **What's missing** (cites `-slices.md`/`-technical.md`) · **The app is correct** |
| **Verified — not a defect** | "Looked wrong — why is it actually fine?" | one line + `(Verified <date>)` |

That is a reader's map. To **write** an entry, authors copy the matching block from the skill's authoring
template `assets/defects-entry.md` — the single source of truth for the exact field layout.

## Where the entries live & how to scan them

- **Each UC's findings** are in `features/<domain>/<uc-id>/defects.md`, next to that UC's `coverage.md`.
  Inside, entries are grouped by register (Bug/Regression · Divergences · Coverage gaps · Spec gaps ·
  Verified — not a defect).
- **To scan the whole suite** (the "what's open everywhere?" view) — no index or tooling needed, since
  every open item literally contains `Status: OPEN` and every deliberately-RED test carries its tag:

  ```bash
  grep -rn "Status:.*OPEN" features/*/*/defects.md              # every open item, suite-wide
  grep -rln "@discovered-divergence\|@discovered-bug" features/*/*/defects.md   # UCs with open reds
  ```

## Maintenance (self-check — run by the skill, not a person)

- **On every activation:** sweep the per-UC `defects.md` files — confirm each entry's `Test:` pointer
  resolves, folder names match `e2e/features/`, and no `OPEN` divergence/bug has gone unreviewed. Fix
  mechanical drift; surface `OPEN` items (and any `@discovered-divergence` / `@discovered-bug` reds) to
  BA/QA.
- **After each run / when a UC is finished:** file new findings in that UC's `defects.md`, under the
  correct register, leading with the plain-language summary. The author sets `JIRA-<key>`/`CLOSED`
  **only** on BA/QA confirmation; otherwise leave `OPEN` and flag it.
