# Defects and findings — UC-SCH5-001 Report Camp and Access Expenses (Schedule 5)

> New to these files? See [`defects-guide.md`](../../../defects-guide.md) at the e2e root for the five
> registers and their status lifecycles. Written for a BA/QA reader who does not know the codebase:
> every entry leads with plain language before any code.
>
> **BA/QA own triage.** Nothing here is adjudicated, assigned a ticket, or CLOSED by the authoring
> agent. `OPEN` means "found and evidenced", not "agreed".
>
> The **2026-09-15/16** rounds below (DIV-1 ticketed; GAP-4 and all four SPEC gaps closed) were carried
> out **at BA/QA direction**, not on the authoring agent's own judgement — which is the only way an entry
> here changes status. SPEC-3's product question ("should a passing camp be confirmed by name on a full
> pass?") was answered by BA/QA on 2026-09-16: **no — keep legacy's behaviour and correct the documents.**

**As of 2026-09-11 the UC is COMPLETE: all 25 slices are authored.** All 25 are green as of
2026-09-22, when DIV-1 was fixed and S24/S25 stopped being deliberately red. No Bug/Regression
entries, and no open entries of any kind.

**UPDATED 2026-09-22 — DIV-1 IS CLOSED. #476 shipped the full fix and the UC has no open entries
left.** Check Status now posts the camp panel on screen and the service overlays it onto the stored
camps before running the same rule, so the verdict describes what the reporter is looking at — which
is what legacy's `ajax="false"` postback did. The availability gate that stood in for this is gone
with it. **`@S24` and `@S25` went green with NOT ONE assertion edited** — only their
`@discovered-divergence` tags and `[DISCOVERED …]` markers came off, both together. The green
companion was replaced rather than retired: it used to pin the gate, and now pins the unsaved-NEW-camp
case, which is the one an obvious implementation of this fix gets wrong. Details under DIV-1.
**#359 remains open for Schedules 1, 2, 4 and 11.**

**UPDATED 2026-09-15 — triage round.** DIV-1 is now ticketed as
[**bcgov/nr-ilcr#476**](https://github.com/bcgov/nr-ilcr/issues/476) (read the caveat under its **Ticket**
bullet before fixing it — the ticket's title describes the symptom, and fixing only that makes Schedule 5
*less* safe). **EVERY SPEC GAP IS NOW CLOSED** — SPEC-1, SPEC-2, SPEC-3 and SPEC-4, by correcting the planning
artifacts — and **GAP-4** by adding the one scenario that reaches the per-camp "met" line. Two entries
were open at that point, for different reasons (both have since closed):

**GAP-5 is also now CLOSED** — it was found 2026-09-16 while preparing the PR and closed the same day on
the review's request: Schedule 5 had **no accessibility coverage whatsoever** and was the only domain in
the suite without any. `accessibility.feature` now sweeps all seven surfaces, **all clean**. The reason it
was missed is the part worth keeping: **the 25-slice catalogue does not ask for accessibility** (it is an
NFR), so "all 25 slices authored" read as complete with half of issue #97 unverified. GAP-4 had the same
shape. **A slice catalogue is not a completeness test.**

**DIV-1 was the only entry still open, and it closed 2026-09-22** when #476 shipped the endpoint
change. Nothing in this UC is open now. The app-wide family (#359) is still open for the four other
affected schedules, but no Schedule 5 entry depends on it.

**A legacy screenshot supplied 2026-09-16 settled SPEC-3 on the facts.** With five camps and one
incomplete, legacy shows a RED panel of that camp's three missing fields **and** a BLUE panel carrying
`All requirements for <camp> have been met.` once per passing camp, with **no** schedule-level banner. That
is the mixed state, and it confirms what this file argued from source: the per-camp line is real and
belongs to the FAIL branch only, so S06's and S20's expectation of it on a PASS was the error. BA/QA chose
to correct the documents; the app keeps legacy's behaviour.

**The most useful thing SPEC-3 leaves behind is a process finding, not a text fix.** Three of its four
named targets had already been corrected on 2026-08-10 — the error survived only in the **Gherkin**, which
that audit never swept. Since the e2e suite is authored from the Gherkin, the refuted expectation stayed
live exactly where it did damage, and the same defect had to be diagnosed twice a month apart. The audit
notes now name the Gherkin as a fifth artifact. **Any future correction pass that stops at the planning
documents will repeat this.**

**One divergence, and it arrived exactly where it was predicted.** Every earlier entry here was a
SPEC gap — each time the app and the legacy-derived Gherkin disagreed, the **Gherkin** was wrong about
legacy: SPEC-2 (the copied camp's name), SPEC-3 (the per-camp "met" line, confirmed three times) and
SPEC-4 (the sub-page add-form's required attribute). All were settled by reading legacy source rather
than trusting either document. The single genuine app divergence is **DIV-1**, the BR-11 pair that
sch1, sch2, sch4 and sch11 were already known to fail — and the earlier editions of this file said to
expect it here. It is a fifth instance of one app-wide defect (#359), not a new one.

**What made Schedule 5's instance worth reading rather than skimming:** it did not fail the way the
other four do. They answer Check Status wrongly; Schedule 5 refused to answer at all. That was the
safer direction — an incomplete schedule could never look ready here — but it was still a change from
legacy, and a workflow cost the other four do not impose. DIV-1 evidenced it, BA/QA ruled on it, and
#476 fixed it on 2026-09-22 by giving the endpoint the screen rather than by re-enabling the button
over a database-only check. **The trade it left behind is the useful artifact: the "safe" divergence
was still a divergence, and the tempting one-line fix for the complaint would have converted it into
the unsafe one.**

---

## Divergence (app behaves differently from the legacy-derived spec)

- **DIV-1 — CLOSED 2026-09-22 (fixed): Check Status cannot judge what is on screen. Schedule 5 does not
  give a WRONG answer like its siblings — it gives NO answer.** Ticketed in its own right as
  **[bcgov/nr-ilcr#476](https://github.com/bcgov/nr-ilcr/issues/476)**, because the Schedule 5 instance
  presents differently from the other four and needs its own reproduction steps; the app-wide family is
  **bcgov/nr-ilcr#359**, which is where the shared endpoint change belongs.

  > **Everything from here to the closure bullets is the ORIGINAL 2026-09-11/15 diagnosis, kept
  > verbatim and in its original tense as the evidence of record.** It describes the app as it was
  > before 2026-09-22. What was actually done, and why the trap it warns about was avoided rather
  > than sprung, is in the **HOW IT WAS FIXED** bullet and the ones after it.
  - **What this means in plain language.** A reporter edits a required value, then asks Check Status
    whether the schedule is ready. On Schedules 1, 2, 4 and 11 they get an answer about the
    last-SAVED version, so it can be confidently wrong. On Schedule 5 they get no answer at all: the
    Check Status button is greyed out for as long as an unsaved edit could exist.
  - **Why the state is unreachable here, precisely.** Three facts compose:
    1. the button carries `disabled={!editable || saving || panelOpen}` (`index.tsx:1419`);
    2. all four descriptors Check Status tests — camp name, road distance, size of camp, associated
       camp volume — live INSIDE the camp panel, so editing one means the panel is open;
    3. closing the panel to re-enable the button raises the discard confirm and throws the edit away.
    So "an unsaved edit plus a clickable Check Status" is not a reachable screen state.
  - **Expected vs actual.** Expected (legacy, and BR-11): the check includes what is on screen.
    Actual: the check cannot be run at all until the camp is saved.
  - **It IS a divergence from legacy, which is why the tests stayed red.** Legacy's Check Status was a
    full JSF postback (`ajax="false"`) and the camp panels shared its form via `ui:include`, so
    `UPDATE_MODEL_VALUES` applied every on-screen value to the managed bean BEFORE the action ran
    (`schedule5.xhtml:40,257`; `Schedule5MB.java:321`). Legacy answered, and answered about the
    screen. The rewrite's `POST /api/v1/schedule5/check-status` carries **no request body at all**,
    so the endpoint cannot see the screen even in principle; disabling the button is what stops it
    answering wrongly.
  - **Which direction it fails in, because this matters for triage: the SAFE one.** An incomplete
    Schedule 5 can never be made to look ready, which is the actual harm #359 does elsewhere. What is
    lost is workflow, not correctness — the reporter must save before they can check, and legacy did
    not make them. Whether that trade is acceptable is a Ministry/BA call. **This entry does not
    adjudicate it.**
  - **How caught.** Authoring S24/S25 and probing the endpoint: `POST /check-status` takes no body,
    and the page object's own guard ("Check Status is disabled while a camp panel is open — close the
    panel first") fires at exactly the step where legacy would have answered. The red is therefore
    self-describing rather than cryptic.
  - **Both arms are red and both are needed.** S24 is the false-GREEN arm (an unsaved violation goes
    unreported) and S25 the false-RED arm (a correction keeps being reported). If #359 is fixed by
    giving the endpoint the screen's values, BOTH must go green — one alone means the fix is
    half-done.
  - **A GREEN companion pins the mechanism** (`check-status-unsaved.feature`, `@p2`). If a future
    change enables Check Status while a panel is open WITHOUT teaching the endpoint to read the
    screen, Schedule 5 would stop being the safe outlier and would start producing #359's
    confidently-wrong verdict. That scenario fails the moment it happens, which the two red ones
    cannot detect — they are already red.
  - **Fifth instance of one defect, not a fifth defect.** Schedules 1, 2, 4 and 11 carry it; Schedule
    6 is the only correct implementation. One fix turns them all green.
  - **Ticket:** [bcgov/nr-ilcr#476](https://github.com/bcgov/nr-ilcr/issues/476) — *"[BUGFIX]: Schedule 5
    - 'Check Status' button should be available when the camp is in edit mode"*. Raised from this entry;
    the issue body cites it by name.
  - **THE WARNING THAT WAS HEEDED — the ticket's title describes the SYMPTOM, and fixing only that
    would have made things worse.** #476 asks for the button to be enabled while a camp is in edit mode. Enabling it is
    *half* the fix: `POST /api/v1/schedule5/check-status` carries **no request body**, so an enabled
    button would run a check that still cannot see the screen — and Schedule 5 would stop being the safe
    outlier and start returning #359's confidently-wrong verdict about the last-SAVED camp. The endpoint
    has to be taught to read the screen's values in the same change, following Schedule 6's
    `Schedule6CheckRequest` (the one correct implementation). **The green companion scenario below is the
    tripwire for exactly this:** enable the button without changing the endpoint and it goes RED, which is
    the intended alarm, not a regression in the test. *(What shipped did change the endpoint, so the
    companion was replaced rather than tripped — see the **Test** bullet.)*
  - **HOW IT WAS FIXED (2026-09-22, #476), and it took the whole fix, not the symptom.** The warning
    above was heeded. `POST /api/v1/schedule5/check-status` now carries a body — the camp panel
    currently on screen (`Schedule5CheckRequest`) — which `Schedule5Service` overlays onto the stored
    camps before running the *identical* rule (`evaluateCamp` is untouched; the payload path and the
    stored path route through one private `evaluate(...)`, Schedule 6's arrangement). With the verdict
    describing the screen there is nothing left for the availability gate to protect against, so it
    was deleted: the button is now `disabled={!editable || saving}`, which is legacy exactly
    (`schedule5.xhtml:44`, `:257`).
  - **One camp, not all of them — the design choice worth knowing before touching this.** Schedule 6
    sends every record because every record is on screen. Schedule 5's table renders NAMES only and
    all four checked descriptors live in the single open panel, so the body carries just that panel.
    Sending the whole list would have made the verdict depend on the client's own, possibly stale,
    copy of camps the reporter is not even looking at. The itemized sub-page rows are never on this
    screen and stay database-sourced on both paths.
  - **The trap inside the fix, which is the part most worth keeping.** The payload is keyed on the
    panel being OPEN, never on it being DIRTY. An untouched new camp matches its empty baseline and is
    therefore *clean*, so a dirty-keyed send would omit it and report "requirements met" over a camp
    with four missing fields — the false-GREEN of #359, rebuilt by the fix for it. Mutation-proved
    both ways: coercing a cleared descriptor to `0` reddens 2 unit cases; sending only when dirty
    reddens the unsaved-new-camp case.
  - **The Story 15.1 sweep is unaffected and was kept that way deliberately.**
    `Schedule5CheckStatusResolver.checkStatus(millId, year)` was renamed `checkStatusStored` and the
    sweep re-pointed at it. The sweep has no screen to describe and must keep reading stored data, as
    legacy's own consolidated check page did. The endpoint and the sweep may legitimately disagree;
    that is the design. Naming them apart is not cosmetic — with both called `checkStatus`, a future
    caller picks the wrong one by autocomplete and the failure is silent.
  - **Priority / env:** p1 · local seeded DB · Chrome.
  - **Status:** **CLOSED 2026-09-22 — fixed, and verified by the two arms going green on their own.**
    NOT ONE assertion, step file or fixture was edited to make them pass; only the
    `@discovered-divergence` tags and the `[DISCOVERED …]` title markers came off, **both together**,
    which was the acceptance criterion rather than a nice-to-have. Found 2026-09-11; triaged
    2026-09-15; fixed 2026-09-22.
  - **The closure run, reproducible.** `cd frontend/e2e && npx playwright test --grep "@sch5"` on
    2026-09-22, Chrome, local stack on `jdbc:oracle:thin:@//host.docker.internal:1525/DBDOCK_01`
    (profile `local`): **217 passed / 0 failed** in 2.5 min. Narrowed to this file,
    `--grep "@check-status-unsaved"` minus the other domains: **181 passed / 0 failed** (178
    preflight + the three scenarios). Anchors 10050/2023 (S24), 12050/2023 (S25), 22051/2023 (`@p2`).
  - **Why `@S24` going green is conclusive and not merely encouraging.** It clears a STORED Size of
    Camp on screen, does not save, and expects the finding reported. The database still holds the
    value, so no database-only check can produce that verdict — green is reachable only if the
    payload path works end to end. `@S25` is the same argument inverted.
  - **⚠ THE STACK WAS SERVING PRE-FIX CODE AND WOULD HAVE PRODUCED A FALSE RED.** This is the #373
    stale-server trap, but the BACKEND this time rather than Vite. The `backend` container
    bind-mounts `./backend:/app` and runs `mvn spring-boot:run`, so the running JVM held classes
    compiled before the change — `GET /v3/api-docs` showed **no `requestBody`** on the endpoint. Run
    in that state, the frontend posts a body the old backend ignores, the verdict comes from the
    database, and S24/S25 fail **naming the right message on the right element** — i.e. by
    reproducing the defect just fixed. `docker restart backend` (~110 s to healthy) recompiles from
    the mount. **Prove both layers current before reading any result here:** a bodiless POST with
    valid params must return **400** (old code: 200), and `curl
    http://localhost:3000/src/components/schedule5/index.tsx` must show `screenCamp` and no
    `saving || panelOpen`. (`/v3/api-docs` is 404 on the `local` profile, so use the status probe.)
  - **⚠ #359 IS STILL OPEN.** This entry closes for Schedule 5 only. Schedules 1, 2, 4 and 11 carry the
    same defect and their own arms are still deliberately red; Schedule 6 was always correct. The
    split this fix introduced (`checkStatus` payload / `checkStatusStored`) is the shape the remaining
    four should follow.
  - **Test:** `check-status-unsaved.feature` ×3 — `@S24` and `@S25` (both now GREEN, unedited), plus a
    `@p2` scenario that replaces the retired panel-gate companion: *"Check Status includes an unsaved
    NEW camp that has never been saved"*. The old companion pinned the gate that no longer exists; the
    new one pins the case S24 and S25 cannot reach and that the obvious implementation gets wrong.

### Everything else that was checked, and found clean

*Nothing else in S01–S25.* The re-grounded happy path matched the source Gherkin in every respect that
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

- **GAP-2 — RESOLVED 2026-09-11: every slice is authored. 25 of 25.**
  - **Final state.** S01–S23 green; S24/S25 authored as deliberately-red `@discovered-divergence`
    scenarios tracking DIV-1, plus a green companion pinning the mechanism behind them. Originally
    raised on 2026-09-09 covering S02–S25, when only S01 existed.
  - **The watch item this entry carried was right.** While S24/S25 were still unauthored it predicted
    that Schedule 5 would reproduce the BR-11/BR-12 `#359` family — Check Status judging the SAVED
    document and ignoring the screen — and that this would be a fifth instance of one app-wide defect
    rather than a new one. It did, though not in the form expected: the prediction assumed a wrong
    VERDICT, and what Schedule 5 actually does is refuse to answer. See DIV-1.
  - **25 anchors are now pinned, not 22.** Three were minted after the original fan-out, each for the
    same structural reason — a scenario that writes cannot share a key under `fullyParallel`:
    17052/2023 (S12's saving arm), 22050/2023 (S23's ACCESS half) and 22051/2023 (S24's green
    companion). The last one was minted only after it was tried the other way and the two scenarios
    raced; the write-up is in the fixture.
  - **Status:** RESOLVED 2026-09-11.

- **GAP-4 — CLOSED 2026-09-15: the per-camp "requirements met" message is now exercised. A scenario was
  added rather than an existing one widened.**
  - **What this means in plain language.** When Check Status passes for the whole schedule, the app
    shows one banner. When it fails, it lists what is missing per camp — and any camp that is itself
    complete gets its own "All requirements for <camp> have been met." line. That per-camp line only
    ever appears in the second case: schedule failing, one camp passing.
  - **Why no test covered it.** S06 has one complete camp, so the schedule passes and the line is
    suppressed (that is SPEC-3). S20 has one incomplete camp, and once it is fixed the schedule
    passes — so the line is suppressed there too. Reaching it needs TWO camps on one anchor, one
    complete and one not, and **no slice in the 25-slice catalogue describes that state.**
  - **Why it was worth recording rather than quietly adding.** The message is real, live and
    user-facing (`campRequirementsMetMsg`), and it is pinned in the fixtures precisely so S06 and S20
    can assert its ABSENCE. An assertion that a string never appears is only as good as the knowledge
    that it CAN appear. Silently widening S20 to two camps would cover it while making S20 about
    something its own title does not describe.
  - **What was done.** A new scenario in `check-status.feature` — *"A failing schedule still reports the
    camps that passed, by name"* (`@p2 @S06 @SUC-005`) — seeds one complete camp and one with no road
    distance on a single anchor, then asserts all four facts that state produces: the passing camp's met
    line (with its trailing full stop), the failing camp's composed missing-field line, the ABSENCE of the
    schedule banner, and that the met line names the camp that passed rather than the one that failed.
    Both `Given` steps already existed and take a camp name, so no new step code was needed.
  - **It needed a new anchor, and only one.** `CHECK_MIXED_ANCHOR` = **23050/2023**, minted in sch5's own
    2023 range (nine cells there were still free) and folded into `db-e2e/R__80_e2e_anchor_seed.sql` in
    the same change, per this folder's rule. It is the only sch5 anchor that holds two camps mid-scenario;
    it is still empty AT REST, so preflight's "no camps" assertion is unchanged.
  - **Tagged `@S06`, not a new slice id.** The catalogue question — whether UC-SCH5-001 formally gains an
    S26 — is still BA/QA's, and nothing here presumes it: the scenario carries the slice tag of the
    message family it serves, exactly as S24's green companion does. If a slice is minted later, the tag
    is a one-line change.
  - **Status:** CLOSED 2026-09-15 — coverage exists and is GREEN. Raised 2026-09-10.

- **GAP-5 — CLOSED 2026-09-16: Schedule 5 had NO accessibility coverage at all, and was the only domain
  in the suite without any. Found while preparing the Story 28.4 PR; closed the same day on the PR
  review's request.**
  - **What was added.** `accessibility.feature` — **7 scenarios, all GREEN, zero WCAG 2.1 AA violations**
    on first run. It sweeps every surface issue #97 names, one scan per scenario (a scenario scanning
    several surfaces in sequence stops at the first violation and silently skips the rest — sch4's
    reasoning, followed here): the main page with its camp list, the NEW camp panel, the open EDIT panel,
    **both** expense sub-pages, the read-only View, and the context-suppressed guard state.
  - **No new step code and no new axe helper** — `pages/common/axe.ts` and the two common a11y steps are
    already domain-agnostic, and every navigation step this needed (`I start a new camp`, `I edit the
    {string} camp`, `I open the {string} sub-page`, `I view the {string} camp`) already existed. The gap
    was never a tooling gap.
  - **Four new anchors, and only four.** `a11y-list` (23051/2023), `a11y-panel` (23052/2023),
    `a11y-subpage-camp` (24050/2023) and `a11y-subpage-access` (24051/2023) — one per sweep that SAVES a
    camp to have something to scan, because a scenario that writes cannot share a key under
    `fullyParallel`. The other two sweeps need none: the new-panel scan saves nothing so it rides the
    validate-only anchor, and the read-only scan only GETs the camp S19 also reads. All folded into
    `db-e2e/R__80_e2e_anchor_seed.sql` in the same change; preflight is now 180 checks (was 176).
  - **No hovered-row scenario, deliberately.** The app-wide row-hover contrast defect is already tracked
    once on sch4 (BUG-1 / issue #314). Re-finding it per domain adds noise, not information.
  - **Status:** CLOSED 2026-09-16 — 7 sweeps, all clean. This was the second half of issue #97.
  - ---
  - **ORIGINAL ENTRY (raised 2026-09-16), kept because the reason it was missed is the reusable part:**
  - **What this means in plain language.** Every other subject area in this suite sweeps its screens with
    axe for WCAG 2.1 AA violations. Schedule 5 does not sweep anything. There is no
    `accessibility.feature`, no `@a11y` tag and no axe call anywhere under `features/sch5/` — 15 feature
    files, 32 scenarios, zero accessibility assertions.
  - **Measured, not assumed** (2026-09-16): `--grep "@sch5.*@a11y"` lists **0 tests**, and
    `grep -rn "@a11y\|axe" features/sch5/` returns nothing. By comparison every sibling has coverage —
    sec 4 feature files, sch4 3, sch11 3, sch1 2, sch2 2, sch3 1.
  - **It contradicts the suite's own stated rule.** `e2e/README.md` § Scenario tags says: *"**Every
    accessibility scenario is `@a11y`**, in every domain."* Schedule 5 is the exception that sentence does
    not allow for.
  - **And it is half of what the story asks for.** The tracking issue is
    **[#97 — \[28.4\] End-to-End *and Accessibility* Verification for Schedule 5](https://github.com/bcgov/nr-ilcr/issues/97)**.
    The end-to-end half is complete (25 of 25 slices); the accessibility half has not been started. Story
    28.3's PR (#402) swept four structurally distinct Schedule 3 renders and reported all four clean — that
    is the standard this domain has not yet met.
  - **Why it went unnoticed:** no slice in the 25-slice catalogue asks for it. Accessibility is an NFR
    (NFR1 / the epic's AC4), not a use-case slice, so a catalogue-driven completeness check —
    "all 25 slices authored" — reports DONE with this missing. GAP-4 had the same shape: real behaviour
    that no slice describes.
  - **What it would take.** The renders are already identified by `render-states.feature` and the panel
    work: the Schedule 5 main page with the camp list, the open camp panel (new and edit are the same
    panel in different modes), the two expense sub-pages, and the read-only non-Draft view. Each needs an
    axe sweep with the pointer parked before measuring, so contrast is judged at rest and the tracked
    app-wide hover defect (#314, now closed) is not re-found here — the technique PR #402 established.
  - **Suggested fix:** one `accessibility.feature` in this UC folder, following sch4's three-file pattern,
    on existing anchors — the sweeps read, they do not write, so no new anchor capacity is needed.
  - **Status:** superseded by the CLOSED summary above.

---

## Spec gaps (the source specification is wrong, ambiguous or incomplete)

- **SPEC-1 — CLOSED 2026-09-15: the Schedule 5 Gherkin README undercounted its own slices (said 23,
  there are 25).**
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
  - **Fix APPLIED 2026-09-15** in the `ilcr-bmad` planning repo:
    - `UC-SCH5-001/gherkin/README.md` — "**Total feature files:** 23" → **25**.
    - **A second copy of the same stale count was found while fixing it:**
      `UC-SCH5-001-slices.md` said "Total slices after gap analysis: **23**" while listing 25 rows and 25
      detail sections. Corrected to 25, and the breakdown above it now accounts for where the last two
      came from — S24/S25 were added by a later cross-schedule rule sweep, after the original gap
      analysis, which is exactly why both totals went stale rather than either being a typo.
    - Verified by counting: 25 table rows = 25 `.feature` files on disk = 25 slice sections = the stated
      totals in both documents.
  - **Status:** CLOSED 2026-09-15. Found 2026-09-09.

- **SPEC-2 — CLOSED 2026-09-15: S03 said a copied camp keeps the source's name. Neither the new app nor
  LEGACY does that — the name is deliberately blanked.**
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
  - **Fix APPLIED 2026-09-15** in the `ilcr-bmad` planning repo, on both slices plus the documents that
    name them:
    - `UC-SCH5-001-S03.feature` — the copy panel now opens with `schedule5Form:newCampName` left **BLANK**
      instead of "set to 'North Camp'", with a header note citing `CampReportType.java:120-121` so the
      next reader does not "correct" it back.
    - `UC-SCH5-001-S14.feature` — **re-grounded onto the right error, not just re-worded.** Retitled
      *"Save a Copied Camp Without **Naming** It (**Required** Name Error)"*; both scenarios now expect
      FLD-001 *"Camp Name is required."* rather than ERR-001 *"Camp name already exists."*, and the header
      records that the duplicate error is UNREACHABLE by this route and that BR-02's duplicate rule is
      S13's subject instead — so closing this does not quietly drop coverage of the duplicate rule.
    - `UC-SCH5-001-slices.md` — the S14 section, its summary row, its gap-analysis line, its Controls note
      ("arrives BLANK") and its Messages row (ERR-001 → FLD-001).
    - `gherkin/README.md` — the S14 row's name.
    - The E2E tests already followed legacy and are GREEN; nothing in the suite changed.
  - **CONFIRMED DOWNSTREAM 2026-09-09, as predicted.** S14 has since been authored and it behaves
    exactly as this entry forecast: saving an unrenamed copy is rejected with **"Camp Name is
    required."**, not the duplicate-name error S14 scripts. The duplicate error is UNREACHABLE by that
    route — with a blank name there is nothing to duplicate. So S14 needs the same correction as S03,
    and it is a different message, not a re-worded one.
  - **Status:** CLOSED 2026-09-15 — both slices corrected. Found 2026-09-09, downstream effect confirmed
    the same day.

- **SPEC-3 — CLOSED 2026-09-16: three planning documents said Check Status shows a per-camp "requirements
  met" line on a pass. Neither the new app nor LEGACY does.**
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
  - **CONFIRMED A THIRD TIME 2026-09-10, by S20.** S20's second arm scripts the same per-camp line
    (`SUC-005`) after the missing value is supplied. It does not appear: once the only camp passes,
    the SCHEDULE passes, so the pass branch returns the banner with `camps: []`. Probed directly —
    `POST /check-status` answered `outcome: MET`, one message, `camps: []`. So the correction is owed
    on **S06, S20, `UC-SCH5-001-detailed.md:151` and the epic AC** — four documents, one root cause.
  - **A knock-on worth its own entry:** because the per-camp line is unreachable from both S06 and
    S20, nothing in the catalogue exercises it at all. See GAP-4.
  - **CONFIRMED A FOURTH TIME 2026-09-16 — and this time from a RUNNING LEGACY SCREEN, not from source.**
    BA/QA supplied a screenshot of legacy Schedule 5 (DLVR data, mill 727 / year 2017) taken after
    pressing Check Status with **five** camps present, one of them incomplete. It shows, verbatim:
    - a RED error panel — three lines, all for the failing camp `test1`:
      `Camp Report Name : test1 - Road Distance to Operating Area: Value Required`, and the same shape for
      `Size of Camp` and `Associated Camp Volume`;
    - a BLUE info panel — one line per PASSING camp:
      `All requirements for camp 2 have been met.` and likewise for `camp 3`, `camp 1`, `test 8`;
    - **no schedule-level banner at all.**

    That is decisive, and it settles the question in the direction this entry already argued: the per-camp
    "met" line is real in legacy and appears **only when the schedule FAILS while individual camps pass** —
    exactly the branch structure read out of `Schedule5MB.java:324-326`. It does **not** appear on a pass,
    which is what S06 and S20 script and what makes them wrong. So the app is right, the four documents are
    still wrong, and the correction owed is unchanged.

    Three incidental confirmations worth keeping, all byte-level:
    - the composed finding's shape — `Camp Report Name : <name> - <field>: Value Required`, **no space
      before the final colon** — matches `CHECK_MISSING_MESSAGE` exactly, independently of the resolver's
      own comment;
    - the met line carries a **trailing full stop** (`… have been met.`) while the schedule banner does
      not, which is what `CHECK_STATUS_MESSAGES.campMet` encodes;
    - legacy emits a met line for **every** passing camp (four of them here), which is the loop
      `Schedule5Service.checkStatus` reproduces.
  - **It also independently validates GAP-4's new scenario**, which asserts precisely this state (a failing
    schedule, a passing camp named in its own met line) — authored from the source branch structure one day
    before the screenshot arrived, and matching it.
  - **One cosmetic difference the screenshot exposes, NOT logged as a defect:** legacy splits the two
    panels by severity as **error (red)** and **info (blue)**; the rewrite renders them as Carbon
    `warning` and `success` respectively (`schedule5/index.tsx:1444, 1457`, keyed off `outcome` and
    `requirementsMet`). The message TEXT is identical and severity is carried by a title word rather than
    colour alone, which is this suite's accessibility standard — so it is mechanism, not behaviour.
    Flagged here only so a future reader comparing the screenshot to the app does not re-open it as a
    finding. If the Ministry wants the legacy info/error pairing exactly, that is a styling decision.
  - **DECIDED 2026-09-16 (BA/QA): correct the documents — option (a). The app keeps legacy's behaviour.**
  - **AND THE FIX WAS SMALLER THAN THIS ENTRY CLAIMED, because most of it had already been done.** Before
    changing anything, all four named targets were re-read. Three of them were **already correct**,
    corrected on **2026-08-10** (PR #242 review, Scho sign-off) — months before this entry was raised:
    - `epics.md` Story 7.2 AC — already reads "SUC-004 **alone** … with SUC-005 appearing **only** for the
      camps that individually pass inside that mixed result";
    - `UC-SCH5-001-detailed.md` — Basic Flow step 7 already reads "**and nothing else**", and its SUC-005
      row already reads "**only when the SCHEDULE has failed**";
    - `UC-SCH5-001-slices.md` S06 — already reads "**No per-camp lines are emitted**";
    - `UC-SCH5-001-technical.md` SUC-005 row — already corrected too.

    So this entry's own "four documents, one correction" fix line was **stale when it was written**: it
    named documents that no longer carried the error.
  - **What was ACTUALLY still wrong was the GHERKIN, which that 2026-08-10 audit never covered.** Its own
    sign-off line lists the four *planning* documents and stops there —
    `implementation-artifacts/tests/UC-SCH5-001/gherkin/` was not swept. So:
    - `UC-SCH5-001-S06.feature:27` still asserted the per-camp line on a pass;
    - `UC-SCH5-001-S20.feature:35` still asserted SUC-005 after its only camp was completed — doubly
      unreachable, since one camp passing makes the SCHEDULE pass.

    **That is the whole reason this was found twice.** The e2e suite is authored FROM the Gherkin, so the
    refuted expectation was still live where it mattered most, and it had to be settled a second time a
    month later. Both feature files are now corrected and carry the reasoning inline, and the audit note
    in all three planning documents has been extended to say the Gherkin is a fifth artifact — the one
    downstream automation is actually written from. That is the durable fix; the two feature edits are not.
  - **Fix APPLIED 2026-09-16:** `UC-SCH5-001-S06.feature` (asserts the per-camp line's ABSENCE, matching
    the suite), `UC-SCH5-001-S20.feature` (asserts the SCHEDULE banner plus the absence of both the
    finding and the per-camp line), and the extended audit notes in `UC-SCH5-001-detailed.md`,
    `UC-SCH5-001-slices.md` and `UC-SCH5-001-technical.md` — each also recording the legacy screenshot as
    observed evidence.
  - **No suite change:** `check-status.feature` and `check-status-missing.feature` already asserted exactly
    this, which is what made the Gherkin's drift visible in the first place.
  - **Still open, and deliberately not decided here:** whether the 25-slice catalogue should gain a slice
    for the mixed state that GAP-4's scenario now covers. Schedule 4 carries the direct precedent in its
    own **S31** ("mixed per-location results"), so there is a house pattern to follow if BA/QA want it.
  - **Status:** CLOSED 2026-09-16. Found 2026-09-09; confirmed 2026-09-10 (S06), 2026-09-10 (S20), and
    2026-09-16 against a running legacy screen.

- **SPEC-4 — CLOSED 2026-09-16: S22 was built on a premise that is false in BOTH systems. The Other Camp
  add-form DOES require a description.**
  - **What's wrong.** `UC-SCH5-001-S22.feature` scripts "a source-confirmed asymmetry — the
    add-form's description field has no `required="true"`, unlike the Access Expenses equivalent, so
    Add succeeds and Save is what blocks it". Its first scenario therefore expects a blank-description
    row to LAND IN THE GRID and be rejected later, at Save.
  - **Expected vs actual.** Expected: Add succeeds, a blank row appears, Save rejects it. Actual: Add
    is rejected immediately with `Value Required`, exactly as on the Access page, and no row is
    created. The blank row is unreachable by that route.
  - **The app matches legacy; the slice does not.** Both add-forms carry `required="true"` —
    `schedule5CampExpenses.xhtml:39` and `schedule5AccessExpenses.xhtml:32`. The rewrite reproduces
    that (`validateAddForm` requires a description for both `kind`s) and its own source already flags
    the discrepancy against the committed AC3 as deviation (A)
    (`components/schedule5SubPage/validation.ts:113-116`).
  - **There IS a real S21/S22 asymmetry — it is just in a different control.** The GRID row's
    description input differs: the Access one carries `<f:ajax event="change">` (:63) so a cleared
    description reports immediately, and the Camp one does not (:64-67), so its check is deferred to
    Save. That is the behaviour S22's TITLE describes ("Blocked at Sub-Page Save") and it is
    genuinely there. The slice names the wrong control, not the wrong behaviour.
  - **How caught.** Reading `validation.ts` before authoring, then confirming in the browser: the
    Camp add-form rejects a blank description identically to the Access one, and the deferred check
    fires only when a STORED row's description is cleared in the grid.
  - **How the test was re-grounded.** S22 now asserts both halves: the add-form rejection (so the
    corrected premise is itself pinned) AND the real deferred-to-Save path via the grid row,
    including the negative — no error on change — that distinguishes it from S21.
  - **RE-VERIFIED FIRST-HAND before correcting anything, 2026-09-16.** All four description inputs were
    read straight out of the legacy source rather than trusted from this entry:
    `schedule5CampExpenses.xhtml:39` (add) and `:66` (row); `schedule5AccessExpenses.xhtml:32` (add) and
    `:60` (row) — **every one carries `required="true"`**. The `f:ajax event="change"` sits on the Access
    row only (`:63`); the Camp row has none (`:64-67`). So the premise really is false, and the real
    asymmetry really is the grid row's TIMING.
  - **Fix APPLIED 2026-09-16** in the `ilcr-bmad` planning repo — the four-document pass Story 7.4's Open
    Question 1 had already asked for, plus the Gherkin:
    - `UC-SCH5-001-S22.feature` — retitled *"…Description Cleared in the Grid, Blocked at Sub-Page Save"*
      and rewritten to three scenarios: the add-form refusing a blank outright (so the false premise
      cannot quietly return), the cleared-stored-row path with the **negative** that distinguishes it from
      S21 (no error on change), and the restore-and-save recovery.
    - `UC-SCH5-001-slices.md` — the S22 section (trigger, outcome, Controls, Messages, Fields), its
      summary row, and its gap-analysis line; plus a note on S21's row control recording the `f:ajax`
      immediacy that S22 lacks, since the contrast is the point.
    - `UC-SCH5-001-technical.md` — both control rows and the Field Reference row, with a dated correction
      note in the same house style as the 2026-08-11 cost-bounds correction.
    - `UC-SCH5-001-detailed.md` — the Field Reference row, and the Assumptions bullet that asserted the
      asymmetry is now **withdrawn** rather than silently deleted.
    - `epics.md` — Story 7.4's sub-page validation AC (the "AC3" this entry originally named; the
      implementation story had already superseded it with AC10).
    - `gherkin/README.md` — S22's row name.
  - **The suite needed no change:** `sub-page-validation.feature`'s `@S22` scenario already asserted both
    halves — the add-form rejection AND the deferred grid-row check — and is GREEN. The documents have
    caught up to the test, not the other way round.
  - **Story 7.4 had independently reached the same conclusion** in implementation (deviation (A), AC10),
    so this closes a documentation gap that was already known to the implementer and left deliberately
    for a separate pass.
  - **Status:** CLOSED 2026-09-16. Found 2026-09-10.

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
