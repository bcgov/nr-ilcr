# Decision: seed data moves to repeatable migrations (`R__`); Flyway keeps only the schema

**Status:** **ACCEPTED 2026-08-20.** Option 2 selected. Superseded the original proposal of Option 3, which is retained below as a stretch goal.
**Scope:** backend test fixtures only (`backend/src/test/resources/db`). Not production — the runtime ships no application DDL (AD-2), and ITs run against a throwaway Testcontainers Oracle with **no persisted Flyway history**.

## Problem

Every schedule's integration tests seed their data through one **flat, shared, versioned Flyway namespace** (`V<n>__…sql`). With several schedules being built on parallel branches, two branches routinely claim the same version, and the clash only surfaces on merge. **Five occurrences on record:**

- **`V20260814`** and **`V20260815`** — claimed twice in one day, both lost by the bridge/culvert FK migration (to schedule 5 subpage fixtures, then schedule 9 write fixtures). #277 worked around it by switching to a repeatable `R__` migration.
- **`V20260815`** — also claimed by both Story 9.2's `seed_schedule9_write_fixtures` and #277.
- The `V33`/`V34` "timestamp escape hatch" note, and the mill-id renumbering (690–696 → 700–706) done to dodge cross-seed `ORA-00001` collisions.
- **`V20260819`** — 2026-08-19, claimed by both the Schedule 10 TSA/TSB code-table migration and Story 24.1's reporting-year fixtures. Flyway answered `Found more than one migration with version 20260819` and the whole IT suite died at boot.

### The root cause, stated precisely

The original framing here was "a flat, shared, sequential namespace." That is close, but the operative property is sharper: **the collision is invisible to git.**

Two branches picking `V20260819` produce two *differently named files*. Git merges them cleanly — no conflict, nothing to review, nothing for a PR to catch — and the failure appears only when Flyway loads. As `R__cost_detail_bridge_culvert_fks.sql` puts it: *"a version number is a scarce shared resource and 'highest' is only knowable at merge time."*

That property is what selects the fix: **an identifier derived from content rather than sequence puts the collision back onto a single file path, where git catches it as an ordinary conflict.**

Two distinct problems are worth separating, because they have different answers:

- **P1 — version-number collision.** Flyway refuses to load; the whole IT suite dies at boot. This is what breaks builds.
- **P2 — one shared mutable dataset.** PK collisions, cross-class write leakage, order-dependence. Real, but currently managed by hand (`Schedule2WriteIT:120` explicitly undoes a summary it created so it "must not leak across IT classes").

Because these are **test-only** seeds (fresh DB per run, no checksum/immutability constraints, no prod chain to preserve), we can reorganize freely.

## Options considered

1. **Second-granularity timestamps** (`Vyyyymmddhhmmss__…`). **Rejected — already tried and failed.** Day-granularity timestamps were adopted for exactly this reason and collided twice on 2026-08-13 and again on 2026-08-19, because everyone hand-picks *today's* date, making a date exactly as scarce as an integer. Second granularity would work only if machine-generated, and it still leaves the shared namespace and P2 untouched.
2. **Repeatable migrations (`R__`) — SELECTED.** No version, so nothing to collide with; Flyway applies them after all versioned migrations. The original objection here was that "most of our seeds are stateful ordered inserts" — true, but ordering is controllable by name (see below), so the objection does not hold.
3. **Split schema baseline from per-test data via `@Sql`.** Flyway owns only the schema; per-test fixture data moves onto the tests. Solves P1 *and* P2 in principle. **Deferred — see "Why not Option 3 now."**
- **`flyway.outOfOrder=true` — rejected.** It permits a lower-version migration to apply after higher ones, which helps the "my branch's version is now below main's" case, but does **nothing** for the duplicate-version collisions we actually hit.

## Decision: Option 2

**Seed data becomes `R__`. Only DDL keeps a version.**

- **`V__` = schema only.** Table and constraint DDL. Few files, rarely touched, and `FlywayMigrationVersionUniquenessTest` already catches a collision there at PR time.
- **`R__` = all seed/fixture data**, with a **content-derived name and a numeric ordering prefix**: seeds `10`–`80`, constraints/FKs/indexes `90`+.
- **The existing chain freezes as the baseline.** Existing files are *not* converted wholesale; the convention governs new work, and an old file converts only when someone is already editing it. This is deliberately incremental — no stop-the-world refactor.

### Verified behaviour, not assumed

Probed against the real Oracle container on Flyway 12.4.0 (four scratch `R__` files plus a `flyway_schema_history` dump, since removed):

- All 46 versioned migrations applied at ranks **1–46**; all repeatables at **47–51**. So **`R__`-after-`V__` is a Flyway guarantee** — the *tool* enforces schema-before-data, which is the separation Option 3 sought by convention.
- Repeatables apply in **lexicographic order of description** (observed: `05` → `50` → `90` → `bbb`). Digits sort before letters, so a numeric prefix yields fully deterministic FK ordering.

### Why this ends P1 structurally

| Two branches pick… | Git sees | Flyway sees |
| --- | --- | --- |
| the same `V` version *(before this decision)* | **nothing** — two filenames, clean merge | **fatal** — refuses to load, suite dies at boot |
| the same `R__` numeric prefix | nothing | **harmless** — both run, order decided by the rest of the name |
| the same `R__` full name | **merge conflict on one path** | n/a — never reaches Flyway |

The failure mode moves from *invisible and fatal* to *visible or harmless*. A shared prefix is no longer an error at all; only an identical filename collides, and that is the same path, which git reports normally.

### Consequences

- **No test changes and no file conversions** are required to adopt this. Cost is the convention, the README, and one rename.
- **`R__cost_detail_bridge_culvert_fks.sql` should gain a `90_` prefix** when the first prefixed seed lands beside it. It sorts last among repeatables today only by ASCII accident (`c` sorts after any digit), which happens to be the order it needs; the prefix makes that explicit rather than incidental. Not renamed in this PR — it changes no behaviour until a prefixed `R__` constraint exists to sort against, and this PR is deliberately docs-only.
- **P1 is closed. P2 is not.** Cross-schedule data collisions remain governed by the mill-ID range registry in `backend/src/test/resources/db/README.md`.
- **The convention needs a machine check to hold.** The README convention alone did not prevent any of the five collisions, whereas `FlywayMigrationVersionUniquenessTest` caught the fifth at PR time. Extending it to reject a *new* `V__` file containing `INSERT`s — so seed data cannot drift back into the versioned namespace — is the follow-up that makes this self-enforcing. **Not done in this PR and not yet ticketed** — it needs an owner, or the convention rests on the same README discipline that failed five times.

## Why not Option 3 now

Option 3 remains the more thorough shape and is **retained as a stretch goal**, to revisit only if test pollution (P2) becomes untamable. Measurements taken against the tree on 2026-08-20 argue against doing it now:

- **74% of fixture data is shared backbone.** Of 1,232 `INSERT` statements, **912** target `MILL`, `ILCR_MILL_STATUS_XREF`, `ILCR_MILL_REPORT_STATUS`, `ILCR_COST_REPORT_DETAIL`, `ILCR_REPORT_COST_ITEM` and `ILCR_REPORT_SUMMARY`; two of those are each written by **29 of 47 files**. So Option 3 would relocate ~26% of the inserts and leave 74% in a shared baseline — it adds a *second* mechanism rather than replacing the shared dataset. (Statement counts, not row counts, so approximate; the direction is not.)
- **Rollback is not available.** There are **115 IT classes and exactly 2 use `@Transactional`**; the other 113 commit real writes through MockMvc, so per-class `@Sql` needs idempotent load *plus* teardown. `@Sql` appears in **zero** test files today. Separately, ~16 of the 46 files mix DDL and `INSERT`s, so "freeze the chain as the baseline" is not a clean cut without splitting them first.
- **Performance runs the other way.** Flyway applies all 51 files in **8.57s, once per JVM** (slowest single file 846ms) against a ~65s Oracle container start — about 12% of setup. Reloading subsets across 115 classes multiplies that and grows with every class added.
- **It can silently weaken IDOR coverage — the decisive risk.** Several cross-tenant tests depend on *another mill's rows existing*: `update_foreignMillBridgeId_notFound`, `delete_foreignMillBridgeId_notFound`, `foreignMillCulvertReturns404`, `foreignMillCamp_404`, `sameNameOtherMillYear_succeeds`. Each asserts "someone else's row → 404." Under per-class fixtures, if the foreign mill is not deliberately provisioned the test still passes — because the row does not exist at all. The assertion survives while its meaning evaporates, and CI stays green.

Option 2 is not a lesser Option 3; it is the **first step Option 3 needs anyway**, since it separates schema from data with Flyway enforcing the boundary. Adopting it now forfeits nothing if the team later wants full per-test isolation.

## Authoring a new fixture — the rule

1. **Seeding data?** `R__<prefix>_<what_it_seeds>.sql`, prefix `10`–`80`. Pick the prefix by dependency order (parents before children); a tie is harmless.
2. **Adding a constraint, index or FK over data others seed?** `R__9x_<name>.sql`.
3. **Adding or altering a table?** `V<next>__<name>.sql`, and keep `INSERT`s out of it.
4. **Never** put seed rows in a new `V__` file — that reopens the collision.
