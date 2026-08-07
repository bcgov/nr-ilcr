# How to read a `coverage.md` (per use-case coverage matrix)

**Audience:** BA / QA and anyone reviewing the suite — you do **not** need to know the codebase.
This explains the language, the flags, and the formatting used in every
`features/<domain>/<uc-id>/coverage.md` file.

> For app-specific domain terms and for what the test **tags** mean (`@discovered-divergence`,
> `@discovered-bug`, `@skip`), see the **Glossary** in [`defects-guide.md`](./defects-guide.md). This
> guide does not repeat them.

---

## 1. What a `coverage.md` is (and isn't)

There is **one `coverage.md` per use case**. It is a **ledger** that proves we *looked at everything the
use case asked for* and made a **deliberate, documented decision on each piece** — tested it, ruled it
out with a reason, or logged it as a gap. Nothing is allowed to silently disappear.

It exists because we **re-ground** each use case: the requirements were written from the *legacy* app,
and many of those items don't map cleanly onto the *new* app (the new app may have no per-element
permission model, no session "tombstone", etc.). Without this ledger you couldn't tell *"we chose not to
test this and here's why"* from *"we forgot."*

**It is NOT:**
- **not a pass/fail report** — it says what *should* be tested and why each item was or wasn't; it does
  not tell you which tests are currently green (that's the test run).
- **not the formal coverage authority** — formal requirements→test coverage gating is a separate
  `trace` (TR) step. These files are the author's working reconciliation that *feeds* it.
- **not the defect list** — real problems live in the UC's co-located `defects.md`; `coverage.md` only
  *points* at the relevant entry there.

---

## 2. The matrix — one row per source item

Each file has a table. **Every row is one "source item"** — a single thing the use case says the screen
must do, pulled from the source documents (the `.feature` scenarios, the UC's slice/control/message
matrix, and its technical ERR/STA catalog).

| Column | What it tells you |
|---|---|
| **Source item** | The behaviour/rule/field in plain words (e.g. "Record not found → clear error"). |
| **Source citation** | Where it came from — e.g. `S06`, `slices.md:151-189`, `technical.md:140`, `ERR-001`. This is the traceability link back to the requirement. |
| **App enforcement point** *(some files: "App enforcement / render point")* | Where the **new app** actually does it — the file + line, or **"not enforced"** / **"no new-app equivalent"** if it doesn't. |
| **Scenario (tags)** | Which test exercises it — the feature name + its `@S..`/`@p..` tags. Blank / "—" means no scenario (the Status column explains why). |
| **Status** | The verdict for this row — see §3. |
| **Gap/defect** | If it's not a clean `covered`, the pointer to *why*: a `Coverage gap #N`, `Divergence #N`, or `Spec gap` in this UC's co-located `defects.md`, or "—". |

> **`Scenario` vs `Scenario Outline` — what "outline" means in the Scenario column.** A `.feature` test
> is one of two kinds:
> - a **`Scenario`** — one concrete test that runs once; and
> - a **`Scenario Outline`** — a *template* with `<placeholder>` blanks plus an `Examples:` table below
>   it; the runner generates **one test per row** of that table (write once, cover many cases).
>
> So when the Scenario column notes **"outline"** (e.g. `@S05 @p1 outline`), the test is a Scenario
> Outline — meaning several coverage rows may each map to a *different Examples row* of that one outline.
> A plain `@S05` (no "outline") is a single Scenario. It's only a find-the-test hint for the reader; it
> never changes the verdict.

---

## 3. The **Status** vocabulary (the flags)

A status is a **base word**, sometimes followed by a **parenthetical qualifier** that adds nuance. The
qualifier plus the **Gap/defect** column together tell you the full story.

### Base statuses

| Status | Plain meaning | Is it a problem? |
|---|---|---|
| **`covered`** | A test exercises this item. | No — this is the goal. |
| **`not-applicable`** | The item has **no equivalent in the new app** — it was a legacy-only mechanic or isn't reachable in the new UI. Ruled out on purpose, with the reason in the row. | No — deliberate, documented. |
| **`deferred`** | Should be tested but **isn't yet** — parked for later. Always paired with a `Coverage gap #N`. | It's a *tracked* gap, not an app bug. |
| **`blocked`** | Can't be automated in the **current environment** — almost always because mock auth grants only a single admin role, so "wrong-role is denied" cases can't be produced. Paired with a coverage gap. The endpoint enforcement itself **IS present** (e.g. an `@IsEditor`-style guard on the mutating controllers) — it just can't be exercised with one role, so a gate should treat these as **waived**, not failing. | Environment limitation, not an app bug. |
| **`divergence`** | The app **behaves differently from the spec**. Covered by a **deliberately-RED** `@discovered-divergence` test and a `Divergence #N` in the UC's `defects.md` — BA/QA decide bug vs. intended. | Maybe — that's BA/QA's call. |

> **Which of the three "no test yet" statuses? — the one-question rule.** `not-applicable`, `blocked`,
> and `deferred` all mean "there's no passing test," and it can feel hazy which to use. They differ by
> **what would produce a test — i.e. whose job it is to change it:**
> - **`not-applicable` = "won't."** The new app doesn't do this at all (by design) — a test would have
>   nothing to assert. Only a *product* decision to build the behaviour would change that. *(e.g. the app
>   has no per-element permission model, so "button hidden when denied" can never happen.)*
> - **`blocked` = "can't (yet)."** The app **does** do it, but the test **environment** can't reach the
>   state. *Test-infra* work unblocks it. *(e.g. role-denial is enforced, but single-role mock auth can't
>   produce a denied user.)*
> - **`deferred` = "not yet."** No test yet, but one *will* be added — either the feature isn't built
>   (*dev* unblocks it, e.g. a delete flow with no endpoint) **or** it's built and testable but parked by
>   priority (*the test author* unblocks it later, e.g. a low-risk `p2` field rule).
>
> **Only `not-applicable` changes the coverage number** — it's *excluded* from the denominator (it isn't
> a thing the app does, so it doesn't count against you). `blocked` and `deferred` both count as gaps that
> lower the %. So the distinction that matters for the score is **`not-applicable` vs. everything-else**;
> the `blocked`/`deferred` split is just *why it's a gap* (env vs. unbuilt). When product intent for an
> absent feature is genuinely unknown (will it be built or not?), don't guess between `not-applicable` and
> `deferred` — pick the closest and flag it to BA/QA (see the scope questions in the `defects.md` files).

### Common `covered (...)` qualifiers

| Qualifier | Meaning |
|---|---|
| `covered (+ spec-gap)` | Tested, **but** the `.feature` is missing a scenario its own source docs list — a paperwork mismatch logged as a **Spec gap** (a BA regenerates the `.feature`). The behaviour itself *is* tested. |
| `covered (re-ground)` | Tested, but the assertion was **re-grounded** — the new app's wording/route differs from the legacy spec, and we assert the new (correct) behaviour. |
| `covered (parity)` | Not a separate test — this item runs the **exact same code path** as another item that *is* directly tested, so it's covered by parity. |
| `covered elsewhere` | Tested, but by a **different use case's** suite (cross-referenced in the row). |
| `covered (beyond slice)` | Tested even though it went **beyond** what the slice strictly required — bonus coverage. |
| `covered (verified-not-defect)` | Confirmed correct by inspection/API and filed under **"Verified — not a defect"** so nobody re-investigates it. |
| `not-applicable (legacy)` / `(UI)` / `(re-ground)` | Flavours of not-applicable: legacy-only mechanic / not reachable in the new UI / superseded by re-grounding. |

> A UC may also define its own **UC-specific** shorthand qualifier (e.g. a code for one branch of a
> two-branch flow whose twin is deliberately excluded). When you see an unfamiliar parenthetical, the
> row's **Gap/defect** column and text explain it. Each file lists its own **`Status values:`** legend
> line at the bottom — that's the authoritative key for the statuses that file actually uses.

---

## 4. The footer notes (below the table)

Every `coverage.md` ends with two or three short prose checks:

- **Status values:** the legend of every status used in *this* file (the local source of truth for §3).
- **Symmetry check:** confirms mirror-image cases are both covered — e.g. create *and* update,
  original *and* check rows, list-entry-point A *and* list-entry-point B. This is a guard against
  half-covering a pair.
- **Role / permission coverage:** what role-based behaviour could/couldn't be tested. Almost always
  notes that mock auth grants only a single admin role, so "wrong role is denied" paths are `blocked` /
  logged as coverage gaps rather than tested as true E2E.

---

## 5. Worked example — reading one row

A `covered (re-ground)` row (View screen, "record not found"):

```
| Record not found → clear error (re-grounded ERR-001) | S06 / ERR-001, technical.md:140 |
  getRecord throws on [] → isError → Error state (View index.tsx:162-170) |
  <domain>-view-navigation @S06 @p1 @ERR-001 | covered | re-grounded text "Could not retrieve..." |
```

Read left to right: *the requirement that a missing record shows a clear error* (**source**: scenario
S06 / legacy ERR-001 / technical.md line 140) *is implemented here* (**app**: the view's error state at
`index.tsx:162-170`) *and is tested by* the `@S06` scenario in the view-navigation feature; the verdict
is **covered**, with the note that the **error wording was re-grounded** from the legacy text to the new
app's message.

And a `not-applicable` row:

```
| STA-006 — "Create" action hidden when permission denied | S05 / STA-006 |
  no per-element permission model — the action always renders | — | not-applicable | Coverage gap #1 |
```

The legacy rule (*hide the action when the user lacks the permission*) has **no equivalent** in the new
app (every action renders for everyone), so there is **no scenario**, the verdict is **not-applicable**,
and the reason is filed as **Coverage gap #1** in the UC's `defects.md` for BA/QA to revisit if
per-element permissions are ever built.

---

## 6. How it all fits together

```
requirements (.feature + -slices.md + -technical.md)
        │  every source item →
        ▼
coverage.md  ── points at ──►  defects.md (same UC folder)   (the actual findings)
        │                              │  BA/QA triage → Jira
        └── feeds ──►  trace (TR)  (formal requirements→test coverage gate)
```

- **`coverage.md`** = *did we account for everything this UC asked for?* (this file)
- **`defects.md`** (co-located) = *what did we find in this UC, and what does BA/QA do about it?*
  (how it works: `defects-guide.md`)
- **`trace` (TR)** = *the formal, gate-able coverage number.*
- **the test run** = *what's actually green right now.*
