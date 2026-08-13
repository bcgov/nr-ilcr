# Decision: split the schema baseline from per-test fixture data (Flyway test seeds)

**Status:** Proposed — decision needed (this branch/PR is a conversation piece, not a merge)
**Scope:** backend test fixtures only (`backend/src/test/resources/db`). Not production — the runtime ships no application DDL (AD-2), and ITs run against a throwaway Testcontainers Oracle with **no persisted Flyway history**.

## Problem

Every schedule's integration tests seed their data through one **flat, shared, versioned Flyway namespace** (`V<n>__…sql`). With several schedules being built on parallel branches, two branches routinely claim the same version, and the clash only surfaces on merge:

- **`V20260815`** was claimed by *both* Story 9.2's `seed_schedule9_write_fixtures` and PR #277's bridge/culvert FK migration → Flyway `Found more than one migration with version 20260815` → the whole IT suite red. (#277 worked around it by switching to a repeatable `R__` migration.)
- The `V33`/`V34` "timestamp escape hatch" note, and the mill-id renumbering (690–696 → 700–706) done to dodge cross-seed `ORA-00001` collisions, are earlier symptoms of the same root cause.
- We've also had **order-dependent fixture clashes** — one schedule's IT committing rows another schedule's IT asserted on — because all fixtures share one global dataset.

The root cause is structural: **a single, ever-growing, sequential namespace that every parallel branch appends to.** Renumbering and timestamps only reduce the odds; they don't remove the shared namespace.

Because these are **test-only** seeds (fresh DB per run, no checksum/immutability constraints, no prod chain to preserve), we have latitude a normal Flyway chain doesn't — we can reorganize freely.

## Options considered

1. **Second-granularity timestamps** (`Vyyyymmddhhmmss__…`). Cheap, keeps the current convention, drops collision odds to ~nil. **But** the shared namespace and cross-schedule data leakage remain. *Good interim, not the fix.*
2. **Repeatable migrations (`R__`)** for order-independent, idempotent scripts (constraints, static reference data, views). No version → can't collide; runs after all versioned migrations. *Right tool for that class (as #277 used), but most of our seeds are stateful ordered inserts.*
3. **Split schema baseline from per-test data (recommended).** Flyway owns only the **schema** (tables/constraints) + truly-global reference data; **per-test fixture data moves onto the tests** via Spring `@Sql` (or a shared fixture helper), living beside the ITs that use them. *Removes the shared sequential namespace entirely.*
- **`flyway.outOfOrder=true` — rejected.** It permits a lower-version migration to apply after higher ones, which helps the "my branch's version is now below main's" case, but does **nothing** for the duplicate-version collisions we actually hit.

## Proposed decision

Adopt **Option 3** as the target state, with **Option 1 as the immediate interim** and **Option 2** for constraint/reference scripts.

### Target shape
- **Flyway = schema baseline only.** The table + constraint DDL (today's `*_snapshot.sql` migrations) and any genuinely global reference/code-table rows. Small, stable, rarely touched → collisions become rare and low-stakes.
- **Per-test fixture data = owned by the tests.** Each schedule's ITs load their own rows via class-level `@Sql("/fixtures/scheduleN/…sql")` (or a small `FixtureLoader`), scoped and tore down per test class. Fixtures live next to the tests that assert on them.

### Why this fixes it
- **No shared sequential namespace → version collisions are structurally impossible** for fixture data.
- **Fixtures are local** → no more cross-schedule order-dependent clashes; a test's data is readable right where it's used.
- Schema changes (the only remaining Flyway files) are infrequent and easy to coordinate.

### Migration path (incremental — not a big-bang)
1. **Now (interim):** any *new* `V<n>` seed uses a second-granularity timestamp; new constraint/reference scripts use `R__`.
2. **Freeze** the current `V<n>` chain as the schema baseline; stop adding fixture-data migrations to it.
3. **New fixtures** go via `@Sql` from day one.
4. **Convert existing per-schedule seeds to `@Sql` opportunistically** as each schedule's tests are next touched (no dedicated stop-the-world refactor required).

### Tradeoffs / risks
- **Per-test load cost:** `@Sql` runs per class/method. Mitigate with class-level scripts + transactional rollback, and keep the shared Testcontainers container.
- **Shared fixtures:** rows several schedules depend on (e.g. `THE.MILL` context, code tables) stay in the Flyway baseline or a common `@Sql` include, not copied per schedule.
- **A loading convention** (`@Sql` vs a helper, teardown, isolation) needs to be agreed once so it stays consistent.

## Questions for the team
1. Agree Option 3 is the target, with Option 1 interim + Option 2 for constraints/reference?
2. `@Sql` directly, or a thin `FixtureLoader` test helper?
3. Where do genuinely shared fixtures (mill/year context, code tables) live — Flyway baseline, or a common `@Sql` include?
4. Incremental conversion (opportunistic, as recommended) vs a scheduled test-infra pass?
