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

   **Timestamp versions do NOT remove the race** — proven 2026-08-13, when three branches all reached
   for the same two days (`V20260814` → schedule 5 subpage fixtures, `V20260815` → schedule 9 write
   fixtures). Everyone picks today's or tomorrow's date, so a date is just as scarce as an integer.

1a. **Schema changes that must apply LAST: use a repeatable migration (`R__…`).** If your script adds a
   constraint or index over data that *other* migrations seed — rather than seeding its own fixtures —
   it does not want a version number at all. It wants to run after everything, which is precisely
   Flyway's guarantee for repeatable migrations. `R__cost_detail_bridge_culvert_fks.sql` is the worked
   example: it declares the delivery FKs on `ILCR_COST_REPORT_DETAIL` after every schedule's fixtures
   have populated their per-report column, and it cannot collide with anyone. `FlywayMigrationVersionUniquenessTest`
   ignores `R__` files by design. This is NOT a general escape hatch — a migration that inserts its own
   fixtures still takes a version, because re-running it on a reused container would duplicate rows.
2. **Fixture ID ranges.** Namespace seed entities by track so PKs can't overlap:

   | Track                     | `MILL_ID` block | Notes                                        |
   | ------------------------- | --------------- | -------------------------------------------- |
   | Schedule 1 / core context | 514–517         | seeded in `V2` (shared read context)         |
   | Schedule 3 / crown+admin  | 522 (prefill)   | `V5`                                         |
   | Other Costs               | 523–527         | `V6`                                         |
   | Home                      | (see `V8`/`V9`) |                                              |
   | Schedule 2                | **622–625**     | summaries in the `12xx` block                |
   | Schedule 5                | **670–676**     | reserved by `V34`, seeded by `V20260807`      |
   | Schedule 7B               | **680–681**     | `V20260811`                                  |
   | Schedule 5 sub-pages      | **690–693**     | `V20260814`                                   |
   | Schedule 9                | **700–706**     | `V20260815`                                  |

   **Schedule 5 sub-pages (`V20260814`, Story 7.4)** — a **timestamp version**, per convention 1 and
   the `V20260807` precedent. Seeds the first item-62 / item-68 rows the suite has ever held, on its
   own mills so no destructive test can touch Story 7.2's `670–676`: `690` the write playground
   (Draft 2016–2023, one destructive concern per year), `691` Submitted → the write-gate 409, `692`
   check-status against real sub-page rows, `693` owned solely by the authorization IT. The block was
   `680–683` until Schedule 7B's `V20260811` landed on `main` claiming `680–681`; both migrations
   `INSERT INTO THE.MILL` those ids, so the merge would have failed Flyway outright on ORA-00001.
   Per convention 1 the newer (unmerged) claim moved. PK ranges are
   a **new block**, verified above every value in use (the previous high-water mark was `8438`):
   `CAMP_REPORT_ID` **`8700–8719`** and `ILCR_COST_REPORT_DETAIL_ID` **`8720–8799`** — both below the
   sequence starts. It adds NO cost item (62/68/141/142 already exist via `V34`/`V31`).

   It carries **one DDL statement**, and it is a fidelity fix rather than a fixture: it widens
   `ILCR_COST_REPORT_DETAIL.ITEM_DESCRIPTION` from the `V1` snapshot's `VARCHAR2(30)` to delivery's
   **`VARCHAR2(120)`** (confirmed `CHAR_USED = 'B'` against the seeded real-data image). A snapshot
   narrower than delivery makes a green IT prove less than it appears to — a 30-character multi-byte
   description that production accepts would fail locally with ORA-12899.

   **Schedule 5 write (`V20260807`, Story 7.2)** — a **timestamp version, deliberately**. The next
   free integer was `V35`; it was not taken because this convention has now collided four times
   (schedules 2, 11, 6, and `V34`'s near-miss), and `V34`'s own header said the hatch was "worth
   taking next time". Flyway orders `20260807` after `34`, so it still applies immediately after the
   `V34` snapshot it seeds into, and no future merge can force a renumber of it or of anyone else's.
   It seeds Schedule 5's reserved mills **`670–676`**, which `V34` reserved and left empty: `670`
   write playground (Draft years 2016–2024, one destructive concern per year — 2016 is the
   cleared-value target added alongside 2017–2024), `671` non-Draft `S` →
   write-gate 409, `672` check-status all-met, `673` check-status issues/mixed, `674` zero camps →
   vacuously met, `675` neighbour (per-mill/year name scoping + the untouched-neighbour delete proof),
   `676` owned solely by the authorization IT. Its PK ranges are a **new block, `82xx`**, verified
   unused by every other migration: `CAMP_REPORT_ID` **`8200–8229`** and
   `ILCR_COST_REPORT_DETAIL_ID` **`8230–8299`** — both below the sequence starts and clear of
   Schedule 6's `8301–8399`, Schedule 5 read's `8401–8438`, and Schedule 8's `≥8500`. It adds NO
   table and NO cost item: all fifteen category-`'5'` items already exist (`V34` + `V31:79`).

   **Schedule 5 read (`V34`, Story 7.1)** reserves mills `670–676` but seeds **none of them yet** — the
   read fixtures reuse the shared `514`/`515`/`516`/`517` context from `V2`, which already supplies
   every guard and track-status case a summary-less schedule needs (`514` Draft, `515` status-row
   but no summary → the valid 200-with-no-camps case, `516` closed → 409, `517` Submitted → the
   read-only case). Its own PK ranges are `CAMP_REPORT_ID` `8401–8410` and
   `ILCR_COST_REPORT_DETAIL_ID` `8411–8480`. `V34` also `ALTER`s the shared
   `ILCR_COST_REPORT_DETAIL` to add the `CAMP_REPORT_ID` FK column, and registers fourteen
   category-`'5'` cost items — **not** item `68`, which `V31:79` already defines as Schedule 6's
   non-69 decoy (the shared-master-data rule above, in practice).

   **Schedule 7B (`V20260811`, Stories 13.1/13.2)** — the second **timestamp version**, for the reason
   the Schedule 5 note gives: the next free integer was `V35`, which the Schedule 5 entry above had
   already declined to take. Flyway orders `20260811` after both `34` and `20260807`. It `ALTER`s the
   shared `ILCR_COST_REPORT_DETAIL` to add the `CULVERT_REPORT_ID` per-report column (the
   `BRIDGE_REPORT_ID`/`CAMP_REPORT_ID` pattern), creates `THE.CULVERT_REPORT` and
   `THE.ILCR_CULVERT_TYPE_CODE`, and registers **only** cost items `77`/`78` — items `70–76`/`79–81`
   are Schedule 7A's, already defined by `V27` (the shared-master-data rule). PK ranges:
   `CULVERT_REPORT_ID` **`7801–7899`** and `ILCR_COST_REPORT_DETAIL_ID` **`7901–7999`**, both below the
   sequence starts and clear of Schedule 7A's `7601–7699`/`7701–7799`. Read fixtures reuse the shared
   `514`/`515`/`516`/`517` context from `V2`; it additionally owns mills **`680`** (two opened Draft
   reporting years, 2020 + 2021 — the only multi-year fixture in the snapshot, so the `REPORT_YEAR`
   predicate is falsifiable) and **`681`** (one culvert stored with a since-retired type code, for the
   unchanged-type exemption on save). Its culvert-type rows deliberately include a mid-year-effective
   and a mid-year-expiring code so the January-1 evaluation instant is falsifiable too.

   **Schedule 9 (`V20260815`, Story 9.2)** — a **timestamp version** (`V20260815`, renamed from
   `V20260814` to avoid collisions with PR #268). It seeds Schedule 9's write-side test fixtures. It
   additionally owns mills **`690–696`** for the write playground, Check-Status edge cases, and
   authorization tests. PK ranges: `CONTRACTUAL_WORK_REPORT_ID` **`9101–9199`** and
   `ILCR_COST_REPORT_DETAIL_ID` **`8481–8499`** (both below the sequence starts and clear of other
   schedules). Read fixtures reuse the shared `514`/`515`/`516`/`517` context from `V2`.

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
