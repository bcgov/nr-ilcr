# Test-scope Flyway migrations (`classpath:db`)

These `V*__*.sql` scripts are **test fixtures only**. They are applied by Flyway's Java API to a
throwaway Oracle-Free Testcontainer when the `*IT` acceptance suite boots
(`support/AbstractOracleIT.java`) — they are **never** run against the shared dev/prod THE schema
(AD-2: no runtime DDL). So editing them is safe: the only thing they touch is a disposable container
created fresh per test run.

## Why this file exists

Multiple backend feature branches are usually in flight at once (schedule 1/other-costs, schedule 3,
home, schedule 2, …). Each cuts from `main`, so each sees the same "next free" Flyway version and the
same empty stretch of fixture IDs — and independently claims them. The collision only appears when
two such branches merge:

- **Version collisions** — two files named `V5__…`. Flyway refuses to load and the whole `*IT` suite
  dies at boot with a `FlywayException`. Guarded by
  `support/FlywayMigrationVersionUniquenessTest` (a fast `surefire` unit test — no container — so the
  clash is caught at PR time, not at IT boot).
- **Seed-data collisions** — two branches `INSERT` the same `MILL_ID` or `ILCR_REPORT_COST_ITEM_ID`
  primary key → `ORA-00001` at migrate time. Not machine-guarded yet; avoided by the ID ranges below.

## Conventions

1. **Version numbers.** Take the next free integer after the highest `V` currently on `main` **plus
   any in-flight PRs you know about**. When you rebase/merge `main` and hit a duplicate, bump *your*
   (newer) migration to the next free slot — never renumber someone else's merged migration. If we
   keep colliding, switch to timestamp versions (`V20260728__…`), which removes the race entirely.
2. **Fixture ID ranges.** Namespace seed entities by track so PKs can't overlap:

   | Track                     | `MILL_ID` block | Notes                                        |
   | ------------------------- | --------------- | -------------------------------------------- |
   | Schedule 1 / core context | 514–517         | seeded in `V2` (shared read context)         |
   | Schedule 3 / crown+admin  | 522 (prefill)   | `V5`                                         |
   | Other Costs               | 523–527         | `V6`                                         |
   | Home                      | (see `V8`/`V9`) |                                              |
   | Schedule 2                | **622–625**     | summaries in the `12xx` block                |

   Cost-item IDs (`ILCR_REPORT_COST_ITEM_ID`) are a **shared** master-data space across schedules.
   Define each item **once**; if another track already seeds it (identical row), reference it, don't
   re-`INSERT` it.

## History: Schedule 2 collisions (resolved)

When schedule 3 (`V5`, #182), other costs (`V6`, #182) and home (`V8`/`V9`, #190) merged into `main`,
this branch's schedule 2 fixtures collided on both version numbers and seed IDs. All resolved:

- **Version clash** — schedule 2's `V5`/`V6` → renumbered to `V10`/`V11`.
- **Mill PKs** — schedule 2's mills `522–525` → `622–625` (schedule 3 keeps 522; other costs keeps
  523–525). All references in the `schedule2` `*IT`/service tests moved in lockstep.
- **Summary PKs** — schedule 2's colliding summaries `1022/1024/1025` (write) and `1028` (read) →
  the `12xx` block (`1222–1225`, `1228`); non-colliding `1002`/`1023` left as-is.
- **Cost-item 135** — was `INSERT`ed identically by both schedule 3 (`V5`) and schedule 2 (`V10`);
  the duplicate `INSERT` was dropped from `V10`, which now references schedule 3's definition.

## History: Schedule 6 version collision (resolved)

Schedule 6 (#225) claimed `V30`/`V31` while open, yielding once already because schedule 11 had taken
`V28`/`V29`. `main` then merged `V30__ilcr_mill_user_profile_xref.sql`, so the pair moved again to
`V31`/`V32` — both files renumbered together to keep the write fixtures immediately after the
snapshot they seed into, with the `V3x` references in the `schedule6` `*IT`s and service moved in
lockstep. Version numbers only; no seed-ID clash (schedule 6 owns mills `660–666`).

This is the third version collision on this convention (schedule 2, schedule 11, schedule 6), and
each one was caught only after CI went red on a branch that was otherwise green. The
timestamp-version escape hatch in convention 1 above is worth taking.
