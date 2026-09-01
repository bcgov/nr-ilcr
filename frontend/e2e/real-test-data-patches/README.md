# real-test-data-patches

Targeted, **case-by-case** seed patches for the E2E suite — used only when the **real** data loaded
into the local Docker DB (from the app's data extract) genuinely can't supply a fixture a scenario needs.

This folder lives **inside `e2e/`** (peer of `features/`, `fixtures/`, `scripts/`) so it travels with
the suite. Apply/tear down with the two scripts under `../scripts/` — see below.

The DB is loaded with **real extracted data**; fixtures are **discovered** from it. Only when discovery
fails — no distinct pair for a two-record case, no parent whose window covers today, no provably-absent
id for a negative case, a missing FK parent from a one-hop extract — do we add a **minimal** row here.

## Rules for a patch here
- **Discover first.** A patch is the last resort, after confirming no real record fits.
- **Minimal & targeted.** One row (or the few rows) the scenario needs — never a bulk re-import.
- **Idempotent loader + matching teardown.** Every patch script can be re-run and fully undone.
- **Sentinel-marked.** Tag inserted rows with the `E2E_SEED` sentinel so the teardown removes exactly
  what the patch added and never touches a real extract row.
- **Documented.** State which UC/scenario needs it and *why real data fell short*.
- **Re-verify on re-extract.** Real data is non-deterministic across extracts; revisit patches when
  the DB is refreshed (each `.sql` should carry its own re-pick/verification query).
- **Fold it into the CI seed, in the same change.** **These patches never run in CI.** The CI e2e job has
  no extract image and no `sqlplus` step — it rebuilds the schema with Flyway from
  `backend/src/test/resources/db/` plus `db-e2e/R__80_e2e_anchor_seed.sql`. So every anchor a patch here
  creates must also be transcribed into that seed, following **its** conventions (plain `INSERT`s with
  pre-claimed ids against an empty schema, not the guarded PL/SQL used here) with its ID-CLAIMS header
  extended. Skip this and the suite passes locally and 404s in CI, which reads as an app defect rather
  than as missing data. `preflight/ci-seed-parity.setup.ts` fails the run if you forget; it needs no
  database, so it fires locally too.

## Layout (added as needed)
```
<domain>/<name>.sql            # idempotent insert (E2E_SEED sentinel)
<domain>/<name>.teardown.sql   # undoes exactly the above (sentinel-keyed)
```

## Apply / tear down (scripts)

Two scripts in [`../scripts/`](../scripts) apply and reverse **all** patches. They **auto-discover**
every `<domain>/*.sql` (apply) and `<domain>/*.teardown.sql` (teardown) by the naming convention above,
so a new patch that follows it is picked up with **no script edit**:

```bash
cd e2e
./scripts/apply-patches.sh        # apply every patch (idempotent — safe to re-run)
./scripts/teardown-patches.sh     # remove every patch's sentinel rows (reverse order)
```

They run `sqlplus` against `ORACLE_DSN` (default `THE/default@localhost:1525/DBDOCK_01`), auto-detecting the
client (local `sqlplus`, else your DB container — set `DB_CONTAINER` in `.env` if it isn't the default).
After applying to an already-running backend, **evict the app's reference-data cache if it has one**
(SCS example: `POST /api/api/internal/cache/evict`) or restart it.

> Adding a patch: drop `<domain>/<name>.sql` + `<domain>/<name>.teardown.sql` here (idempotent insert +
> exact sentinel-keyed undo), add a row to **Current patches** below, and the scripts pick it up. If the
> new patch depends on another being applied first, name it so the sorted order is correct.

## Current patches

| Patch | UC(s) | Why real data fell short |
|---|---|---|
| `sch4/view-mode-amounts.sql` (+ `.teardown.sql`) | `UC-SCH4-001` | The extract contains **no Schedule 4 amounts at all** — 289 category-"4" `TRANSPORTATION_REPORT` rows but not one `ILCR_COST_REPORT_DETAIL` row with a Schedule 4 cost item (40–55), so all 68 locations have an empty category grid and no sub-page rows. Draft scenarios don't care (they create their own data through the app's own endpoints), but the read-only S18/STA-001 arm cannot: the Draft gate answers 409 to any write on a Submitted/Verified mill-year, and no non-Draft location has amounts to render. Adds ONE sentinel location (`E2E View Location`, `ENTRY_USERID='E2E_SEED'`) with a fixed category, a distance category and one Towing row, on each of the two non-Draft anchors. Reversible + double-sentinel-keyed; verified idempotent (2× apply → 1 copy) and fully removed by its teardown. |

| `sch3/draft-anchors.sql` (+ `.teardown.sql`) | `UC-SCH3-001` | Schedule 3 had **no create path** before defect #296, so a summary could not be made through the app: the patch seeds an empty category-3 summary on 17 Draft mill-years, one category-1 Schedule 1 for the BR-09 crown anchor, and stored amounts on 5 read-only Check Status / a11y anchors. Part 1 is retirable since #296 but deliberately kept — parts 2 and 3 depend on those summaries, and the read-only anchors must not be written to by any scenario. |
| `sch2/unsaved-check-anchors.sql` (+ `.teardown.sql`) | `UC-SCH2-001` | Two dedicated Draft mill-years for the BR-12 / #359 arms. See the note below — the extract had no free anchor left. |
| `sch11/unsaved-check-anchors.sql` (+ `.teardown.sql`) | `UC-SCH11-001` | Two dedicated Draft mill-years for the BR-12 / #359 arms. Same reason. |
| `sch4/unsaved-check-anchors.sql` (+ `.teardown.sql`) | `UC-SCH4-001` | One dedicated Draft mill-year for the BR-12 / #359 scenario. Same reason, and Schedule 4's preflight is the strictest — it enforces one anchor per mutating scenario *and* "used in at most one feature file". |

> **Why three patches exist purely to create ANCHORS (2026-08-27) — read this before adding a fourth.**
> The extract has run out of usable mill-years, and the numbers are worth knowing before you go hunting:
> **114** (mill, year) keys were already pinned across the six domain fixtures when this was measured — 119
> now, these five anchors included; Home offers only reporting
> years **2015-2021** (`GET /api/v1/reporting-years`), so an anchor outside that range cannot be selected by
> a scenario at all; and across the 17 ACT mills × those 7 years exactly **four** unclaimed pairs are
> openable — every one of them NON-DRAFT, which disables Check Status. So a new mutating scenario in sch2,
> sch4 or sch11 has nowhere to live without seeding.
>
> **Two prerequisites, both found the hard way.** A mill-year needs (1) an `ILCR_MILL_REPORT_STATUS` row —
> `MillContextService` answers 404 without it — and (2) **eleven `ILCR_REPORT_CATEGORY` rows**, which is what
> every real reporting mill-year carries. With only (1) the page opens fine and then the first save fails
> HTTP 500 `scheduleNotSavedErrorMsg`, logged type-only as `DataIntegrityViolationException`. The comparison
> that explained it: a working anchor held 11 category rows and the bare one held none.
>
> **When searching for a free anchor, do not write your own grep — reuse `preflight/anchor-keys.ts`.**
> Anchors are declared in three shapes and two of them defeat a line-based search: `at(...)` entries wrap
> across four lines (`at(\n MILL_987,\n 12050,\n 2015,`), and `sec` interleaves `millNumber`/`millName`
> between `millId` and `year`. That module pairs each `millId` with the `year` in its own enclosing braces
> and handles both. Under-scanning here has cost real time — two wrong "no anchors exist" conclusions
> (caught by Schedule 4's preflight), and the cross-domain guard itself missed 62 of the 119 keys, twice,
> for those two reasons.

**All five patches above are also folded into `backend/src/test/resources/db-e2e/R__80_e2e_anchor_seed.sql`**
(as of 2026-08-28), so the same anchors exist in CI. `preflight/ci-seed-parity.setup.ts` keeps them in step.
The one thing NOT transcribed is the eleven `ILCR_REPORT_CATEGORY` rows per anchor: the real Oracle's
composite FK is what makes them necessary, and the Flyway test schema has no such FK (that omission is
recorded in the seed's own header).

Add entries only as real gaps are hit during test creation/testing.

## Notes on re-creating a container

**Patches are NOT baked into the DB image** — each fresh container from the image needs the patches
re-applied (or re-snapshot *with* them to bake them in). When you (re)create a container: start the
**DB before the backend** (else its Spring context fails and every `/api` route 404s), run
`./scripts/apply-patches.sh`, then evict the app's reference-data cache if it has one (SCS example:
`POST /api/api/internal/cache/evict`) or restart the backend (a startup-warmed cache serves stale code lists). **Re-verify on any DB
re-extract** — real data is non-deterministic, so re-confirm each anchor still exists.
