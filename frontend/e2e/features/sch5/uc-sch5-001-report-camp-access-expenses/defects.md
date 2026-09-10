# Defects and findings — UC-SCH5-001 Report Camp and Access Expenses (Schedule 5)

> New to these files? See [`defects-guide.md`](../../../defects-guide.md) at the e2e root for the five
> registers and their status lifecycles. Written for a BA/QA reader who does not know the codebase:
> every entry leads with plain language before any code.
>
> **BA/QA own triage.** Nothing here is adjudicated, assigned a ticket, or CLOSED by the authoring
> agent. `OPEN` means "found and evidenced", not "agreed".

**As of 2026-09-10**, this UC has **no Divergence and no Bug/Regression entries**. Nineteen slices are
authored (S01–S19) and all are green: where the app and the legacy-derived Gherkin disagreed, the
Gherkin turned out to be wrong about legacy — see SPEC-2, which was settled by reading the legacy
source rather than by trusting either document.

That clean record comes with a caveat worth stating plainly: **the six slices still unauthored include
the two most likely to produce a divergence.** S24/S25 are the BR-12 "Check Status includes unsaved
edits" pair, where schedules 1, 2, 4 and 11 all carry an open `@discovered-divergence` (issue #359).
So "no divergences" describes what has been looked at, not a verdict on Schedule 5 as a whole.

---

## Divergence (app behaves differently from the legacy-derived spec)

*None found in S01–S19.* The re-grounded happy path matched the source Gherkin in every respect that
was checkable: the panel title (`New Camp Details`), the five descriptor fields starting blank,
BR-03's propagation into exactly eleven volume fields, the four recomputed totals, and the success
message.

Two places where the app and the source Gherkin visibly differ were examined and deliberately NOT
logged here, because in both the difference is mechanism rather than behaviour:

- **The guard messages moved out of the `p:messages` panel** (S16/S17/S18). The source expects a
  PrimeFaces business-exception panel; the rewrite renders the API's own `detail` inside a Carbon
  notification. Both error strings are byte-identical to the source's ERR-004/ERR-005 — confirmed
  against the running app 2026-09-10 — so the reporter reads exactly the same words.
- **S19's row actions collapse to a single `View`** instead of a `View` beside a disabled Delete and
  Copy — deviation (B), which the app's own source names and justifies (index.tsx:1193-1197). Legacy
  rendered a permanently-disabled Delete; neither system offers a reachable write action, so there is
  no user-visible difference to log. Recorded in coverage.md so a future reader does not "fix" the
  test back to the Gherkin's wording.

---

## Bug / Regression

*None found.*

---

## Coverage gaps

- **GAP-1 — RESOLVED 2026-09-09: the anchor blocker is cleared. 24 of 25 slices remain to be
  AUTHORED, but nothing structural stops them now.**
  - **What changed.** Reporting years **2022 and 2023** are open, and 21 dedicated anchors are seeded
    and verified — one per remaining scenario that needs exclusivity, plus a Submitted (non-Draft)
    document for S19 and a validate-only anchor for S12/S15. Two guard anchors need no capacity at all:
    S17 reuses the existing closed mill 25051/2017 (409) and S18 uses a deliberately-empty cell,
    16050/2022 (404). All 22 anchors and both guards were confirmed through the app's own API.
  - **Why a new year was the right lever.** Adding to `THE.ILCR_REPORTING_PERIOD` is purely additive —
    no existing row is modified — and it cannot shift any existing test's starting state, because the
    app has no default working context. Crucially, every mill-year any other suite pins is 2021 or
    earlier, so **"year ≥ 2022 belongs to Schedule 5" is now a structural fact**, not a convention
    someone has to remember: a cross-domain collision is not expressible in the new range.
  - **The gate earned its keep here.** The first attempt passed locally and would have failed in CI:
    `25051/2017` (S17's 409 guard) existed in the local extract but had no row in the CI migration
    chain, so CI would have answered 404 and S17 would have failed *only in CI*. The parity gate named
    it immediately; the row was added to `R__80` in the same change.
  - **Status:** RESOLVED (unblocked) 2026-09-09. Superseded by GAP-2 below, which tracks the authoring
    itself. Original text kept below for the record.
  - ---
  - **ORIGINAL ENTRY (raised 2026-09-09, before the fan-out): 24 of 25 slices are not yet automated,
    because Schedule 5 has no anchor capacity left.**
  - **What this means in plain language.** Each automated test needs its own "sandbox" — a mill and
    reporting year that no other test writes to, so two tests running at the same time cannot corrupt
    each other's data. Schedule 5 arrived last, and the six existing test suites have already claimed
    essentially every available sandbox in the test database.
  - **The numbers** (measured 2026-09-08 against the seeded image, and confirmed through the app's own
    API). The database holds 123 mill-year rows across 17 open mills and the years 2015–2021. The other
    suites already pin **119** mill-year combinations. Only **three** unclaimed combinations were in
    Draft status — 1/2017, 14050/2018 and 25051/2017 — and **all three belong to closed mills**, which
    the application refuses to open (HTTP 409). Usable free sandboxes: **zero**.
  - **What was done about it.** `real-test-data-patches/sch5/draft-anchors.sql` opens the single
    remaining empty cell (mill 9050, year 2016) and S01 uses it. That was the last one.
  - **What is needed next.** New capacity must be created rather than found. The recommended route is to
    open reporting year **2022** (adding a year is purely additive, and the app has no default
    mill/year context, so no existing test's starting state can shift). That yields 17 fresh sandboxes
    at once, and because every currently-pinned combination is 2021 or earlier, "year ≥ 2022 belongs to
    Schedule 5" becomes a rule the machine can enforce rather than a convention someone must remember.
    Roughly 10–12 of the remaining slices are validate-only, guard or read-only cases that need no
    dedicated sandbox at all, so the true requirement is nearer 12 than 24.
  - **Rejected alternative, for the record:** re-opening the closed mills. Schedule 2 and Schedule 4
    deliberately use closed mills to test the "mill is closed" error, so making one open would silently
    break their tests.
  - **Status:** RESOLVED — the decision was taken and applied the same day; see the summary above.

- **GAP-3 — RESOLVED 2026-09-10: S19's read-only anchor had no camp on it, so most of the slice was
  unassertable.**
  - **What this means in plain language.** S19 checks what a licensee sees once the report has been
    submitted and can no longer be edited: the camp is still listed, but the only thing you can do
    with it is *View* it — no Edit, no Delete, no Copy, no Add New Camp, no Check Status. Every one of
    those checks needs a camp to actually be on the schedule. The anchor reserved for S19
    (mill 16050, year 2023) was correctly set up as a Submitted report, but it was **empty**. On an
    empty read-only schedule the only thing S19 could have proved is that two buttons are greyed out
    — the weakest part of the slice, and the part least likely to break.
  - **How caught.** Probing the anchor through the app's own API before authoring:
    `GET /api/v1/schedule5?millId=16050&year=2023` returned `200`, `trackStatus "S"`, `editable false`
    and `camps: []`. Preflight had been asserting the first three and not the fourth.
  - **Why the test could not just create the camp itself.** Every write to a non-Draft document is
    refused with HTTP 409 — and that refusal *is* the condition S19 exists to prove. So the camp can
    only arrive as seed data. This is the identical wall Schedule 4 hit on its own read-only arm, and
    the fix follows that precedent deliberately (`real-test-data-patches/sch4/view-mode-amounts.sql`).
  - **What was done.** A new patch, `real-test-data-patches/sch5/view-mode-camp.sql`, seeds ONE camp
    with its twelve category rows onto that anchor, and it is folded into the CI seed
    (`db-e2e/R__80_e2e_anchor_seed.sql`) in the same change — a patch not folded in does not exist in
    CI, which is the mistake GAP-1 records being caught the hard way. Kept as a SEPARATE file from
    `draft-anchors.sql` on purpose: that file's header promises every anchor it opens holds no camps,
    and this is the single exception to that rule. Burying the exception inside the file that states
    the opposite would have been the wrong kind of tidy.
  - **The amounts are S01's, and that was the point.** Rather than invent figures, the seed reuses the
    happy path's numbers, so the totals S19 reads back (3,800 / 3,800 / 1,100 / 4,900 and $/m³ 0.76 /
    0.98) are ones the suite already proves the server derives. Confirmed through the API after
    seeding, not computed by hand.
  - **Preflight now states the exception rather than leaving it implicit:** every other anchor must
    hold no camps, this one must hold exactly `E2E View Camp`, and the failure messages distinguish
    "the patch was never applied" (zero camps) from "the single-View assertion is now ambiguous" (two
    or more).
  - **Status:** RESOLVED 2026-09-10. S19 is authored and GREEN.

- **VER-5 — the CI-seed parity gate reported the new camp's twelve rows as orphans. The gate was
  right to complain, and it was the gate that was incomplete.**
  - **What was seen.** Adding the Schedule 5 read-only camp to `R__80_e2e_anchor_seed.sql` immediately
    failed `seed parity: the seed's explicit ids are unique, unclaimed, and parented` with
    *"12 detail row(s) have no parent in the seed"*.
  - **Why it was not a real orphan.** `ILCR_COST_REPORT_DETAIL` carries one foreign key per report
    family, and they are mutually exclusive: a row belongs to a summary (schedules 1/2/3), a
    transportation report (schedule 4), or a camp (schedule 5). The gate knew the first two and had
    never seen the third, so it read "no parent column I recognise" as "no parent at all".
  - **Why it is recorded rather than just fixed.** A missing FAMILY and a genuine orphan produce the
    identical message, so the next person to add a schedule with its own report table will hit this
    and may reasonably conclude their ids are wrong. The gate now iterates a named list of parent
    columns with a comment saying exactly that, and naming `ROAD_MAINTENANCE_REPORT_ID` (V31's) as the
    likely next one.
  - **The gate earned its keep, for the second time on this UC.** GAP-1 records it catching a row that
    existed locally but not in CI. This time it caught a transcription that would have inserted twelve
    unreachable rows in CI — S19 would then have failed only in CI, on a read-only panel showing no
    amounts, which reads as an app defect.
  - **Status:** CLOSED as verified 2026-09-10 — no defect in the seed; the gate was extended.

- **GAP-2 — OPEN (narrowed 2026-09-10): S20–S25 are anchored and preflighted, but not yet authored.**
  - **Progress.** S01–S19 are now authored and green. **19 of 25 slices (76%).** Originally raised
    covering S02–S25.
  - **What this means.** Every remaining slice has a dedicated, verified anchor reserved and named
    for it (`fixtures/sch5/schedule5-test-data.ts`, one export per slice), and `preflight/sch5-anchors.setup.ts`
    proves all 22 resolve with no camps at rest plus both guard responses on every run. What is missing
    is the `.feature` / step / page-object work itself.
  - **Sizing note for planning.** The six remaining are all mutating scenarios with anchors already
    allocated: S20 (`check-missing`), S21 (`access-desc-blank`), S22 (`camp-desc-blank`),
    S23 (`subpage-cost`), S24 (`check-unsaved-violation`) and S25 (`check-unsaved-fix`). The two
    sub-page description cases (S21/S22) and the sub-page cost case (S23) should re-use the S04/S05
    page-object work; S20 re-uses S06's Check Status path.
  - **Watch item, restated because it is now imminent.** S24/S25 are the BR-12 pair below.
  - **Watch item.** S24/S25 are the BR-12 "Check Status includes unsaved edits" pair. Schedules 1, 2, 4
    and 11 all carry an OPEN `@discovered-divergence` there (issue #359 — Check Status judges the SAVED
    document and ignores the screen). Expect Schedule 5 to reproduce it; if it does, that is a fifth
    instance of one app-wide defect, not a new one.
  - **Status:** OPEN — ready to author. Raised 2026-09-09.

---

## Spec gaps (the source specification is wrong, ambiguous or incomplete)

- **SPEC-1 — OPEN: the Schedule 5 Gherkin README undercounts its own slices (says 23, there are 25).**
  - **What's wrong.** `UC-SCH5-001/gherkin/README.md` states "**Total feature files:** 23 (one per slice
    in the slice catalog)", but its own table lists S01–S25, 25 `.feature` files exist on disk, and
    `UC-SCH5-001-slices.md` defines 25 slices.
  - **Expected vs actual.** Expected: the count matches the catalog. Actual: it is short by two.
  - **How caught.** Reconciling the Gherkin against the slice catalog before authoring — the skill's
    rule that a `.feature` set is a lossy projection and must never be treated as the complete
    inventory.
  - **Why it matters more than a typo.** The two missing from the count are **S24 and S25**, the
    "Check Status includes unsaved edits" pair. That is the BR-12 family in which *every other schedule*
    (sch1, sch2, sch4, sch11) has an OPEN `@discovered-divergence`. Anyone sizing this UC from the
    header would have planned 23 slices and silently dropped the two most defect-prone ones.
  - **Verified harmless in one direction:** nothing is missing from the Gherkin itself. All 25 slices
    exist as files and all 25 are in the catalog; only the summary line is stale.
  - **Fix:** change "23" to "25" in that README (a file in the `ilcr-bmad` planning repo, not this one).
  - **Status:** OPEN — trivial doc fix, owned by whoever maintains the planning artifacts. Found
    2026-09-09.

- **SPEC-2 — OPEN: S03 says a copied camp keeps the source's name. Neither the new app nor LEGACY does
  that — the name is deliberately blanked.**
  - **What's wrong.** `UC-SCH5-001-S03.feature:29` asserts the copy panel opens "pre-filled with
    'North Camp''s descriptive fields and expense amounts, **including
    `schedule5Form:newCampName` set to 'North Camp'**". In the running app the Camp Name comes back
    **empty**; every other descriptor and all twelve category amounts are copied.
  - **Expected vs actual.** Expected (per the Gherkin): Camp Name = "North Camp". Actual: Camp Name = "".
  - **This is a SPEC gap, not a divergence — the app matches legacy.** Legacy's own copy constructor
    clones every field and then nulls the name: `CampReportType.java:120-121` in
    `docs/nr-ilcr-2.0.4` (`campName = null; campNameOriginalVal = null;`). The rewrite reproduces it
    deliberately and cites that line — `seedForm(camp, keepName=false)`,
    `components/schedule5/index.tsx:122-140`. So the *derived Gherkin* misdescribes the system it was
    derived from; the rewrite is correct.
  - **How caught.** S03 failed on its first run with `Expected: "North Camp" / Received: ""`. Rather
    than accept the Gherkin, the legacy source was opened and read — which is what turned a suspected
    app divergence into a spec correction.
  - **Why it matters beyond one assertion.** It changes what **S14** ("Save a Copied Camp Without
    Renaming It") can possibly assert. With the name blanked there is nothing to duplicate, so saving
    an untouched copy must raise the REQUIRED-name error (FLD-001), not the duplicate-name error
    (ERR/BR-02) that S14 predicts. S14 should be re-grounded on that basis when it is authored, and it
    is flagged in coverage.md.
  - **Also note:** the blank name is *why* WRN-001 exists — "provide a new Camp Name and invoke save"
    is an instruction, not a warning about a clash.
  - **Fix:** correct S03 **and S14** in the `ilcr-bmad` planning repo. The E2E tests already follow
    legacy and are GREEN.
  - **CONFIRMED DOWNSTREAM 2026-09-09, as predicted.** S14 has since been authored and it behaves
    exactly as this entry forecast: saving an unrenamed copy is rejected with **"Camp Name is
    required."**, not the duplicate-name error S14 scripts. The duplicate error is UNREACHABLE by that
    route — with a blank name there is nothing to duplicate. So S14 needs the same correction as S03,
    and it is a different message, not a re-worded one.
  - **Status:** OPEN — spec correction owed on TWO slices (S03 and S14). Found 2026-09-09, downstream
    effect confirmed the same day.

- **SPEC-3 — OPEN: three planning documents say Check Status shows a per-camp "requirements met" line
  on a pass. Neither the new app nor LEGACY does.**
  - **What's wrong.** `UC-SCH5-001-S06.feature:26-27` expects TWO messages when everything is complete:
    the schedule banner *and* "All requirements for North Camp have been met." Only the schedule banner
    appears. The same wrong expectation is in `UC-SCH5-001-detailed.md:151` and in the epic's AC.
  - **Expected vs actual.** Expected: two messages. Actual: one — "All requirements for this schedule
    have been met", with an empty `camps` array on the API response.
  - **The app matches legacy; the documents do not.** `Schedule5MB.java:324-326` adds
    `scheduleRequirementsMetMsg` in the PASS branch and returns; the loop that would add
    `campRequirementsMetMsg` sits in the `else` branch and is unreachable when the schedule passes. The
    per-camp "met" line is therefore only ever seen when the schedule FAILS and some individual camp
    passed. The rewrite reproduces this and its own source already names it — *"the legacy pass branch
    never enters the per-camp loop … which is deviation (C), contradicting both the epics AC and
    UC-SCH5-001-detailed.md:151"* (`Schedule5Service.java:788-791`).
  - **How caught.** Probing `POST /api/v1/schedule5/check-status` against a complete camp returned
    `outcome: MET`, one message, and `camps: []` — then the legacy managed bean was read to decide
    which side was wrong.
  - **Not a divergence, and already known to the implementer.** Logged here because the *test* has to
    take a side: `check-status.feature` asserts the per-camp line is ABSENT, so the distinction is
    regression-proof rather than resting on a code comment.
  - **Fix:** correct S06, `UC-SCH5-001-detailed.md:151` and the epic AC in the `ilcr-bmad` planning
    repo — three documents, one correction. **Or**, if the Ministry actually wants the per-camp
    confirmation, that is a product change to raise against the app, not a test fix. That call is
    BA/QA's, not the suite's.
  - **Status:** OPEN — spec correction owed (or a product decision). Found 2026-09-09.

---

## Verified — not a defect

- **VER-1 — the CI seed's decision to omit `ILCR_REPORT_CATEGORY` still holds, but its stated reason had
  expired and was corrected.**
  - **What was checked.** `db-e2e/R__80_e2e_anchor_seed.sql` deliberately does not replicate the
    `THE.ILCR_REPORT_CATEGORY` rows that the local seed patches create. Its justification read: *"no
    schedule 1/2/3/4/11 code path reads it (only reportingyear and **schedule5** do), so those rows
    change nothing here."* That reasoning was sound only while Schedule 5 was absent from the suite —
    which stopped being true with this change.
  - **Why it is still not a defect.** The omission survives, for a different and more durable reason:
    the CI Flyway schema has no FK to satisfy.
    `V34__the_schedule5_snapshot_and_read_fixtures.sql:34-35` says so at the point of creation —
    *"Delivery also has the composite FK `CMP_RPT_ILCR_RCAT_FK` → `ILCR_REPORT_CATEGORY`; the V1 test
    snapshot has no such table … not added here."* Schedule 5 never SELECTs the table either; it only
    inherits the FK. So the real database needs those eleven rows and CI does not.
  - **Evidence it is genuinely required in the real database:** the S01 anchor was probed end-to-end —
    a camp POST returned 200 `Data saved successfully` with the category rows present. Schedule 4 hit
    the opposite case on 9050/2015 and recorded it: without them the page opens but the first save
    fails `DataIntegrityViolationException`.
  - **Action taken:** the paragraph in `R__80` was rewritten to rest on the FK's absence rather than on
    Schedule 5's absence, and now states that if a future migration adds that FK to the test schema the
    exemption is void. No behaviour changed.
  - **Status:** CLOSED as verified 2026-09-09.

- **VER-4 — the Road Distance error text differs from the Gherkin by "0.9", and the APP is right. A
  Ministry ruling, already on the record.**
  - **What differs.** `UC-SCH5-001-S15.feature:29` expects *"Entered distance must be between 0 and
    999,999."* The app says *"…between 0 and **999,999.9**."*
  - **Why the app is right.** Legacy's message understated its OWN validator: the validator accepted up
    to 999,999.9 while the text said 999,999. The Ministry confirmed the BOUND and ruled the TEXT the
    defect (PR #370, 2026-08-27 — the value is in km). `validation.ts:23-27` records that decision at
    the constant, and the backend's `distanceValidatorErrorMsg` is kept identical.
  - **Not a spec gap needing correction, unlike SPEC-1/2/3:** this one was already adjudicated by the
    business and deliberately shipped. It is recorded only so a future reader diffing S15 against the
    suite does not "fix" the test back to the legacy wording.
  - **The scenario still exercises a genuine rejection** — the invalid value 1000000 is above the real
    bound either way, so the test does not depend on which text is correct to be meaningful.
  - **Status:** CLOSED as verified 2026-09-09.

- **VER-3 — one unreproduced S04 abort left a camp behind; preflight caught it, as designed.**
  - **What was seen.** During a `--repeat-each=3 --workers=1` run of the whole sch5 set, one S04
    instance failed with a duration of **0ms** — not an assertion failure but an aborted test — and its
    cleanup therefore never ran, leaving `"North Camp"` on 12050/2022.
  - **Why it is recorded rather than dismissed.** The NEXT run failed in `setup` with exactly the right
    message: *"Schedule 5 anchors already hold camps: subpage-existing (S04) (12050/2022) holds
    campId=25613 'North Camp'"*, naming the anchor, the camp and the remedy. That is
    `preflight/sch5-anchors.setup.ts` doing its job — an escaped row surfaced as one clear setup
    failure instead of a confusing red inside whichever scenario ran next.
  - **Not reproduced.** S04/S05 alone survived **5/5** serial repeats afterwards, and the full sch5 set
    then passed `--repeat-each=3` cleanly (193 passed). So this is not a flaky assertion; the scenario
    was cut short by something outside it.
  - **Honest limit of this entry:** the abort's cause was not identified. A 0ms Playwright failure is a
    test that never started or was killed, not one that failed — most likely worker-level, and this
    machine runs the backend in Docker over a Windows bind mount with the DB in a separate WSL distro,
    which has already produced load-induced 60s timeouts elsewhere in the suite.
  - **What to do if it recurs:** clear the residue (preflight prints the exact DELETE) and re-run. If it
    recurs *often*, that is worth a real investigation rather than a sweep.
  - **Status:** CLOSED as verified 2026-09-09 — no defect in the scenario; the guard worked.

- **VER-2 — `--repeat-each` in parallel fails every mutating scenario in this suite, S01 included. Not a
  flake and not specific to Schedule 5.**
  - **What was seen.** Stress-running S01 with `--repeat-each=5` failed 3 of 5, with the app reporting
    `Action failed Camp name already exists.`
  - **Why it is not a defect.** `--repeat-each` clones the scenario and runs the copies *concurrently
    against the same anchor with the same camp name*, so the first save wins and the rest hit BR-02's
    duplicate-name rule — which is the application behaving correctly. This suite's determinism rule is
    explicit (recorded in the sch2 and sch3 coverage files): *"no `Math.random()`/`Date.now()`; every
    mutating scenario owns a dedicated anchor, so fixed literals cannot collide."* Dedication is per
    **scenario**, not per **concurrent copy of a scenario**.
  - **Proved, not assumed:** the established `sch2` happy path was run the same way and failed
    identically (3 failures under `--repeat-each=3`). The property is suite-wide and pre-dates
    Schedule 5.
  - **The flake proof that IS valid here:** `--repeat-each=5 --workers=1` (serial repeats, so each
    repeat's cleanup completes before the next begins) — **5/5 green** — plus the full-suite parallel
    run, which exercises sch5 alongside every other domain.
  - **Worth knowing for the future:** anyone adding a mutating scenario and reaching for
    `--repeat-each` will hit this and may log it as a flake. It is not one.
  - **Status:** CLOSED as verified 2026-09-09.
