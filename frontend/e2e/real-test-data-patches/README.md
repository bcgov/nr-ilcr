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

_None yet — the suite runs on discovered real data. Add a row only as a real gap is hit during test
creation._

| Patch | UC(s) | Why real data fell short |
|---|---|---|
| _(example)_ `<domain>/<name>.sql` (+ `.teardown.sql`) | `UC-<DOMAIN>-<NNN>` | _One sentence: which real-data precondition was missing and why discovery couldn't satisfy it. Note it's reversible + `E2E_SEED`-sentinel-marked._ |

Add entries only as real gaps are hit during test creation/testing.

## Notes on re-creating a container

**Patches are NOT baked into the DB image** — each fresh container from the image needs the patches
re-applied (or re-snapshot *with* them to bake them in). When you (re)create a container: start the
**DB before the backend** (else its Spring context fails and every `/api` route 404s), run
`./scripts/apply-patches.sh`, then evict the app's reference-data cache if it has one (SCS example:
`POST /api/api/internal/cache/evict`) or restart the backend (a startup-warmed cache serves stale code lists). **Re-verify on any DB
re-extract** — real data is non-deterministic, so re-confirm each anchor still exists.
