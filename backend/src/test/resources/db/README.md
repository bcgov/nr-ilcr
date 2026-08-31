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
  primary key → `ORA-00001` at migrate time. **Still not machine-guarded**; avoided by the ID ranges
  below and by nothing else. Same blast radius as a version collision, from a different cause —
  do not read the guards below as covering it.

### What is machine-checked, and what is still discipline

Two fast `surefire` classes in `support/`, no container, both failing at PR time:

| check | class | test |
| --- | --- | --- |
| no two migrations claim one version | `FlywayMigrationVersionUniquenessTest` | `everyFlywayVersionIsClaimedByExactlyOneMigration` |
| a NEW `V__` carries no seed rows | `FlywayMigrationConventionTest` | `newVersionedMigrationsCarryNoSeedData` |
| every `R__` has a two-digit prefix | `FlywayMigrationConventionTest` | `everyRepeatableMigrationCarriesATwoDigitOrderingPrefix` |
| the grandfathering list has not rotted | `FlywayMigrationConventionTest` | `theGrandfatheringManifestDoesNotRot` |
| every `.sql` here is a real migration | `FlywayMigrationConventionTest` | `everySqlFileIsEitherVersionedOrRepeatable` |
| `apply-local-ddl.sh` names real files\* | `FlywayMigrationConventionTest` | `applyLocalDdlScriptNamesOnlyMigrationsThatExist` |
| the detectors themselves still work | `FlywayMigrationConventionTest` | `DetectorContracts` (8 cases) |

\* **This one does not run everywhere.** It self-skips when `../scripts` is absent, which is the
case inside the backend Docker image build — `backend/Dockerfile` copies only `pom.xml`, `.mvn` and
`src`, then runs `mvn package` with unit tests on. Everywhere with a full checkout (your machine,
`analysis.yml`, the CI test job) it runs.

`DetectorContracts` is the answer to "what checks the checker?" Every filesystem check above passes
**vacuously** on a compliant tree, so a regex edit that quietly narrows one leaves the suite green
and protecting nothing. The 2026-08-27 review of #367 found four live bypasses exactly that way. The
nested class asserts the predicates directly — no files, no I/O — so each fix stays fixed.

**Still discipline, checked by nobody:** the fixture ID ranges in convention 2; whether an `R__`
prefix is in the *right* band — the check enforces two digits from **10 to 99**, so `81`–`89`, which
belongs to neither documented band, passes just as a wrong band does; whether a *grandfathered*
`V__` file grows new `INSERT`s (the check reads the tree, not the diff); **whether the manifest
grows** — "shrink-only" is a review rule, and appending a line is a legitimate, deliberately visible
way to turn a red build green; seed rows written as `CREATE TABLE … AS SELECT`; and whether an `R__`
migration is genuinely idempotent. All of these are spelled out in `FlywayMigrationConventionTest`'s
class docstring — read it before assuming coverage.

**The guards read `src/`; Flyway reads `target/`. Run `mvn clean` after ANY migration rename.**
`AbstractOracleIT` loads `classpath:db`, which resolves to `target/test-classes/db` — a build output,
not this directory. Maven's resource copy adds files but never removes them, so a rename leaves BOTH
names behind and a delete leaves the deleted one. Two real instances:

- After PR #356 deleted `V30__ilcr_mill_user_profile_xref.sql`, a stale `target/…/V30__…` survived on
  developer machines. Harmless in effect — that file's DDL is additive and guarded.
- **Worse:** #367's rename of `R__cost_detail_bridge_culvert_fks.sql` → `R__90_…` left *both* copies
  in `target/`. Flyway treats them as two different repeatables and applies **both**, and the file
  issues four named `ADD CONSTRAINT` statements — so the second application fails and takes the whole
  IT suite down at boot. Nothing in `src/` is wrong; only the stale build output is.

Reading `src/` is the right call for a check that runs at PR time. But if a local IT failure makes no
sense against this directory, `mvn clean` before believing it.

## Conventions

1. **Seed data goes in a repeatable migration (`R__`), not a versioned one.** Decided 2026-08-20 —
   see `docs/decisions/flyway-test-fixture-strategy.md`. Name the file for **what it seeds**, with a
   numeric ordering prefix: `R__<10-80>_<what_it_seeds>.sql` for data,
   `R__<90+>_<name>.sql` for constraints, indexes and FKs that must land after the data.

   Why: a version number is a **shared, sequential** resource, and two branches claiming the same one
   produce two *differently named files* — so **git merges them cleanly and the failure only appears
   when Flyway loads**, taking the whole `*IT` suite down at boot. Five collisions on record. With a
   content-derived `R__` name, a shared prefix is harmless (both files run, ordered by the rest of the
   name) and an identical name is the same path, which git reports as an ordinary conflict.

   Verified on Flyway 12.4.0: repeatables apply **after every versioned migration**, in
   **lexicographic order of description** — so the numeric prefix is what fixes FK ordering, and
   digits sort before letters. The prefix is **enforced**: `FlywayMigrationConventionTest` fails any
   `R__` file that does not match `R__<two digits>_<lower_snake>.sql` with the prefix at 10 or above.

   Two digits, not one, and here is the actual reason: a one-digit prefix sorts **after** every
   two-digit prefix from `10` to `49`, because `"5 seed"` > `"10 seed"` on the first character. So
   `R__5_…` would jump past the `10`–`49` seeds it was probably meant to precede. *(An earlier
   revision of this paragraph said `R__5_` sorts after `R__90_`. That is false — `'5'` < `'9'`, so it
   sorts before. The rule was right and its worked example was inverted; corrected 2026-08-27 after
   code review, and now asserted in `DetectorContracts` so prose cannot drift from arithmetic again.)*

1a. **Only DDL keeps a version.** `V<next>__<name>.sql` for adding or altering tables. Take the next
   free integer after the highest `V` on `main` plus any in-flight PR you know about; on a duplicate,
   bump *your* (newer) migration and never renumber someone else's merged one.
   `FlywayMigrationVersionUniquenessTest` catches a clash at PR time rather than at IT boot. **Keep
   `INSERT`s out of these files** — putting seed rows in a new `V__` reopens the collision this
   convention exists to close, and `FlywayMigrationConventionTest` now **fails the build** if you do.

   The 45 files that already carried seed rows when the guard was written are grandfathered by name
   in `grandfathered-seeded-versions.txt`, beside this README. That list is **shrink-only**: removing
   a line is the record that a fixture moved to `R__`, and a line naming a file that no longer exists
   — or no longer contains `INSERT`s — fails the build rather than sitting there as cover. Adding a
   line also turns a red build green; that is deliberately possible and deliberately conspicuous, an
   exemption to argue for in review rather than an enforced prohibition.

   **Three of the 45 are not pre-decision, and that is worth knowing.** `V20260821` landed 38 minutes
   before the decision merged; `V20260822` (53 `INSERT`s) and `V20260823` (11) landed four and five
   days *after* it, in breach of convention 1a as already written. They are grandfathered anyway —
   the guard was built to stop the *next* one, not to force a retroactive conversion of fixtures the
   IT suite depends on — but they are precisely the files that proved the convention needed a machine
   check, so the baseline includes the violations that motivated it. Converting them is the obvious
   first withdrawal from this list.

   *(Historical note, kept because it is the reason for the rule above: timestamp versions were tried
   and did NOT remove the race. Proven 2026-08-13, when three branches reached for the same two days
   (`V20260814`, `V20260815`), and again on 2026-08-19 with `V20260819`. Everyone hand-picks today's
   date, so a date is exactly as scarce as an integer.)*

1b. **`R__` files are safe to re-run here, and this is not the escape hatch it once looked like.** An
   earlier revision of this README said a fixture-inserting migration "still takes a version, because
   re-running it on a reused container would duplicate rows." That was wrong on both halves. Flyway
   re-runs a repeatable migration **only when its checksum changes**, and `AbstractOracleIT` creates
   the container **fresh per JVM** (no `withReuse`), so every repeatable applies exactly once per run.
   The only residual case is editing an `R__` file against a container you are deliberately reusing —
   which needs a clean container, the same caveat every DDL fixture here already carries.
   `R__90_cost_detail_bridge_culvert_fks.sql` is the worked example of the `90+` band: it declares the
   delivery FKs on `ILCR_COST_REPORT_DETAIL` after every schedule's fixtures have populated their
   per-report column. It predates the prefix convention and **carried no number until #367 renamed
   it** — it had been sorting last among repeatables by ASCII accident (`c` sorts after any digit),
   which happened to be the order it needs. The prefix makes that explicit, and it is what lets the
   `R__` check above apply with no exceptions.
2. **Fixture ID ranges.** Namespace seed entities by track so PKs can't overlap:

   **The e2e anchor seed claims ids too.** `../db-e2e/R__80_e2e_anchor_seed.sql` (applied only by
   the CI e2e job's Flyway run, never by the `*IT` suite — see its header) owns mills **13** and
   **9050–25054** (sparse — the exact list is in its header), summaries **3001–3199**, cost-report
   details **4001–4499**, transportation reports **4801–4899**, silviculture reports **9351–9399**,
   biogeo catalogue ids **40, 171, 8850–8869**, and reporting years **2015–2019**. Treat those
   ranges as taken when claiming blocks here, and vice versa.

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
   | Schedule 10               | **710–716**     | `V20260817`                                  |
   | Schedule 10 write         | **717–723**     | `V20260818`                                  |
   | Schedule 6 correction     | **724–726**     | `V20260822`                                  |
   | Mill Information report   | **730–733**     | `R__40`; 733 is ACT-in-year / CLS-now         |

   **Schedule 5 sub-pages (`V20260814`, Story 7.4)** — a **timestamp version**, per the historical
   note in convention 1a and the `V20260807` precedent. Seeds the first item-62 / item-68 rows the suite has ever held, on its
   own mills so no destructive test can touch Story 7.2's `670–676`: `690` the write playground
   (Draft 2016–2023, one destructive concern per year), `691` Submitted → the write-gate 409, `692`
   check-status against real sub-page rows, `693` owned solely by the authorization IT. The block was
   `680–683` until Schedule 7B's `V20260811` landed on `main` claiming `680–681`; both migrations
   `INSERT INTO THE.MILL` those ids, so the merge would have failed Flyway outright on ORA-00001.
   Per convention 1a the newer (unmerged) claim moved. PK ranges are
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

   **Schedule 10 (`V20260817`, Story 11.1)** — a **timestamp version**; `V20260816` was the
   high-water mark on disk when it was claimed. This migration **creates the Schedule 10 tables for
   the first time** — no DDL for them existed anywhere in the repo before it (the repeatable FK
   script previously recorded "S10 is not built"). Shape is delivery-faithful throughout: every
   column type, precision and nullability was read from `ALL_TAB_COLUMNS` on the real-data image.

   It creates `ROAD_CONSTRUCTION_REPRT`, `ROAD_CONSTRUCTION_REPRT_DTL` and five code tables
   (`ILCR_ROAD_LIFETIME_CODE`, `ILCR_ROAD_BALLAST_METHOD_CODE`, `ILCR_ROAD_BALLAST_MATERL_CODE`,
   `ILCR_RL_SOIL_MOIS_RGM_CLS_CODE`, `ILCR_BEC_SOIL_MOISTUR_XREF`), adds
   `ROAD_CONSTRUCTION_REPRT_DTL_ID` to `ILCR_COST_REPORT_DETAIL` (guarded), and widens the
   pre-existing `ILCR_FOREST_REGION_CODE` with `EFFECTIVE_DATE`/`EXPIRY_DATE` (guarded) so the legacy
   year filter applies. `BIOGEOCLIMATIC_CATALOGUE` and the TSA/TSB/TFL code tables are **reused**
   from `V20`/`V22`, not recreated.

   **Unlike Schedule 6, all five classification columns carry ENABLED foreign keys in delivery** —
   verified against `ALL_CONSTRAINTS`, and mirrored here so an unknown code fails in tests exactly as
   it would in delivery. Do not inherit Schedule 6's no-FK finding by analogy.

   Mills **`710–716`**: `710` the rich Draft fixture (2 pages, 2 details, full cost lines), `711`
   TFL-located, `712` unmapped TSA/TSB, `713` unmapped TFL, `714` a page with zero details,
   `715` valid context with zero pages, `716` track `S`. PK ranges (**corrected 2026-08-17** — the
   previously recorded `8900–8907` / `8910–8917` / `8920–8931` understated all three, and an
   understated range is exactly what invites the next story to claim a taken id):
   `ROAD_CONSTRUCTION_REPRT_ID` **`8900–8909`**, `ROAD_CONSTRUCTION_REPRT_DTL_ID`
   **`8910–8919` plus `8940`**, `ILCR_COST_REPORT_DETAIL_ID` **`8920–8932`** (all below the sequence
   starts). Read fixtures reuse `516` (closed → 409) and unseeded `999999` (→ 404) from `V2`.

### `V20260818__seed_schedule10_write_fixtures.sql` — Schedule 10 write path (Story 11.2)

Adds the schema the write path needs and the fixtures that make its defects fail.

**Schema.** Creates `THE.ILCR_SOIL_MOISTURE_XREF`, which `V20260817` never needed because the read
never touches the moisture cross-reference. Widens `ILCR_BEC_SOIL_MOISTUR_XREF` with the
`SOIL_MOISTURE_XREF_ID` join key and `ACTIVE_IND` (added nullable, backfilled, then tightened — an
existing row cannot satisfy a NOT NULL column added in one step). Creates
**`THE.ROAD_CONSTRUCTION_REPORT_SEQ`** (`START WITH 9600`), the sequence legacy declares for the
master table; it does not exist in the seeded delivery image either, where it sits un-advanced at 1
against real ids of 90–184 because those rows were bulk-loaded rather than written through the
application. That is an environment defect to be fixed by advancing the sequence, **not** a reason to
repoint the code at the shared `ILCR_REPORT_COMMON_SEQ`.

**Codes.** Adds the REAL delivery moisture codes — `Dry`/`Moist`/`Wet` and the eight-code ASM
gradient `ED`/`VD`/`MD`/`SD`/`F`/`M`/`VM`/`W`. `V20260817`'s single `SM1`/`ASM1` placeholders exist
nowhere in delivery, so an insert test written against them would pass here and raise `ORA-02291`
there. They are **retained**, not replaced, because Story 11.1's detail rows reference them by
foreign key.

**Claimed:** mills **`717–723`**, `ROAD_CONSTRUCTION_REPRT_ID` **`8950–8959`**,
`ROAD_CONSTRUCTION_REPRT_DTL_ID` **`8960–8979`**, `ILCR_COST_REPORT_DETAIL_ID` **`8980–8999`**,
`SOIL_MOISTURE_XREF_ID` **`9001–9004`**, `ILCR_BEC_SOIL_MOISTUR_XREF_ID` **`8803–8805`**. Mill `717`
carries six Draft years (2019–2024) so each destructive test method claims its own `(mill, year)` and
the suite stays order-independent. Story 11.1's mills `710–716` are read-only here and never mutated.

The derivation fixtures deliberately cover all three outcomes: BEC `8801` + RSMR `'1'` resolves to
exactly one pair, BEC `8802` + RSMR `'2'` to two (forcing the tie-break to be exercised), and BEC
`8801` + RSMR `'2'` to none via an inactive link. Row `9004` is an inactive xref sharing a BEC and
RSMR class with `9001`, so dropping `ACTIVE_IND` from the join would turn the single-candidate case
into a two-candidate one — the flag is falsifiable rather than decorative.

   It registers cost items **3, 4, 5, 6, 7, 8, 9, 10, 11, 20, 21, 22** — all twelve verified absent
   from every existing migration first, and verified present in delivery with exactly these
   category/subcategory pairs. Note the deliberately populated cost lines on detail `8910`:
   **delivery holds ZERO Schedule 10 cost rows**, so the cost-reassembly assertions cannot be proven
   against real data and need a constructed fixture. Detail `8911` deliberately has none, which is
   the shape real data actually has.

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
each one was caught only after CI went red on a branch that was otherwise green. Under the 2026-08-20 decision this class of
clash no longer arises for seed data at all: it goes in an `R__` file, which has no version to claim
(convention 1 above).
