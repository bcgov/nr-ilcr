# Defects and findings — UC-SCH5-001 Report Camp and Access Expenses (Schedule 5)

> New to these files? See [`defects-guide.md`](../../../defects-guide.md) at the e2e root for the five
> registers and their status lifecycles. Written for a BA/QA reader who does not know the codebase:
> every entry leads with plain language before any code.
>
> **BA/QA own triage.** Nothing here is adjudicated, assigned a ticket, or CLOSED by the authoring
> agent. `OPEN` means "found and evidenced", not "agreed".

**As of 2026-09-09**, this UC has **no Divergence and no Bug/Regression entries**. Five slices are
authored (S01–S05) and all are green: where the app and the legacy-derived Gherkin disagreed, the
Gherkin turned out to be wrong about legacy — see SPEC-2, which was settled by reading the legacy
source rather than by trusting either document.

---

## Divergence (app behaves differently from the legacy-derived spec)

*None found in S01.* The re-grounded happy path matched the source Gherkin in every respect that was
checkable: the panel title (`New Camp Details`), the five descriptor fields starting blank, BR-03's
propagation into exactly eleven volume fields, the four recomputed totals, and the success message.

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

- **GAP-2 — OPEN: S02–S25 are anchored and preflighted, but not yet authored.**
  - **What this means.** Every remaining slice now has a dedicated, verified anchor reserved and named
    for it (`fixtures/sch5/schedule5-test-data.ts`, one export per slice), and `preflight/sch5-anchors.setup.ts`
    proves all 22 resolve with no camps at rest plus both guard responses on every run. What is missing
    is the `.feature` / step / page-object work itself.
  - **Sizing note for planning.** The anchors were allocated per slice, so the remaining work is
    mechanical rather than exploratory: 19 mutating scenarios, 2 validate-only (sharing one anchor),
    1 read-only, 2 guards and 1 context-only case (S16, which needs no anchor).
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
  - **Fix:** correct S03 (and re-check S14) in the `ilcr-bmad` planning repo. The E2E test already
    follows legacy and is GREEN.
  - **Status:** OPEN — spec correction owed. Found 2026-09-09.

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
