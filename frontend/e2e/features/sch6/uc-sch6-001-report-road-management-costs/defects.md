# UC-SCH6-001 — Report Road Management Costs (Schedule 6) — defects and findings

**New to these files?** `e2e/defects-guide.md` explains the five registers and each one's status
lifecycle; the sibling `coverage.md` is the slice-by-slice ledger. Entries here are written for a
BA/QA reader who does not know the codebase — plain language first, code references after.

**Where the source documents live.** The Gherkin, the slice catalogue and the UC sidecars are in the
**`ilcr-bmad` planning repo**, not here, so no relative link from this file resolves:

- Gherkin — `_bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S<nn>.feature`
- Slice catalogue — `_bmad-output/planning-artifacts/requirements/use-cases/UC-SCH6-001/UC-SCH6-001-slices.md`

**Who closes what.** The authoring agent files and organises these entries; **BA/QA own triage**.
`OPEN` means "found and evidenced", not "agreed". Nothing here is closed or ticketed without your
confirmation.

**STATUS 2026-09-17 — IN PROGRESS.** S01 and S02 authored and green; 2 of 23 slices covered. Two
verified-not-a-defect findings. No divergences and no bugs found in the app so far.

---

## 1. Divergences (app behaves differently from the spec — suspected defect)

*None.*

> Note for triage, because Schedule 5's experience sets the wrong expectation here. On **Schedule 5**
> the Check-Status-includes-unsaved-edits pair (its S24/S25) is a live divergence — issue #476, app-wide
> #359 — because that endpoint reads the database rather than the screen. **Schedule 6 already has the
> fix**: `POST /api/v1/schedule6/check-status` takes the ON-SCREEN values (`Schedule6CheckRequest`,
> whose own javadoc calls itself "the fix"), `Schedule6Service.checkStatus` evaluates
> `payloadCandidates(request)` and nothing else, and an earlier DB-reading implementation was
> deliberately retired in Task 8 for exactly the reason Schedule 5 is still broken. So when S22/S23 are
> authored here they are expected **green**, and a red would be a real regression rather than the known
> cross-schedule gap.

## 2. Bugs / regressions

*None.*

## 3. Coverage gaps (something the suite does not yet prove)

### GAP-1 — 21 of 23 slices, and all accessibility coverage, not yet authored — OPEN

**What is missing.** S01 and S02 are covered. S03–S23 and the axe sweeps are still to be written.

**Why it is recorded rather than left implicit.** Story 28.4's GAP-5 is the precedent: Schedule 5
reached "all 25 slices authored" with **zero** accessibility coverage, because accessibility is an NFR
that no slice in the catalogue asks for, so the slice count read as completeness while half of board
item #97 was unverified. The recorded lesson was *"a slice catalogue is not a completeness test — carry
this into the remaining 28.x stories."* This entry is that carry: accessibility is in scope for Story
28.5 from the start and is tracked here until `accessibility.feature` exists, so it cannot be missed by
counting slices.

**Disposition.** Expected to close within Story 28.5. Board item **#101**.

## 4. Spec gaps (the source documents are silent, ambiguous, or wrong)

### SPEC-1 — the slice catalogue said 21 slices; there are 23 — CLOSED 2026-09-17

**What was wrong.** `UC-SCH6-001` was described as having 21 slices in three places — `epics.md`'s
Epic 8 summary line, Story 28.5's acceptance criteria (which enumerated S01–S21 and named neither
S22 nor S23), and `UC-SCH6-001-slices.md`'s own gap-analysis total — while the Gherkin folder has
carried 23 feature files all along. The catalogue even contradicted itself: its header said 23 and its
total said 21.

**How caught.** Counting the Gherkin folder before authoring, rather than trusting the stated total.

**Consequence if it had shipped.** The two uncounted slices are S22/S23, the
Check-Status-includes-unsaved-edits pair — the most defect-prone family in the suite on every other
schedule. Authoring to the stated 21 would have silently dropped exactly the pair most worth having.

**Fix.** Corrected on the planning branch `docs/story-28-5-schedule-6-e2e` before any test was written.
The two slices are now attributed to a cross-schedule rule sweep, mirroring
`UC-SCH5-001-slices.md:26`, and grounded in source rather than inferred: the legacy Check Status button
is `ajax="false"` (`schedule6.xhtml:226,523`), so a full JSF postback applied the screen to the model
before `checkStatus()` ran.

**Note for the next 28.x story.** This is the same drift class as Story 28.4's own SPEC-1, and 28.4's
SPEC-3 is the reason to sweep the Gherkin *and* the sidecars together: three of SPEC-3's four targets
had already been fixed a month earlier and the error survived only in the Gherkin, so the same defect
was diagnosed twice.

### SPEC-2 — S12's required-field message text is `[UNKNOWN]` in the source — OPEN

**What is missing.** The S01–S23 Gherkin's own "Open Items Carried Forward" records that the exact
JSF required-field validation message for a blank "TSA or TFL" submission could not be recovered — no
custom literal exists in `messages.properties` or `faces-config.xml`, so it was flagged rather than
fabricated.

**Why it matters now.** S12 asserts that message. It cannot be asserted byte-for-byte against an
`[UNKNOWN]`, so when S12 is authored the text will be taken from the **running app** and recorded here
as its provenance, with BA/QA asked to confirm it matches legacy.

**Disposition.** Blocks nothing today; carried to S12.

### SPEC-3 — pagination scope of the running totals is unresolved — OPEN

**What is ambiguous.** `slices.md` excluded item 10 flags that it is not clear from legacy source
whether `totalVol` / `totalCos` / `totalCal` cover only the current page (the list paginates at 5 rows)
or the whole record set. It was flagged rather than sliced.

**Why it matters now.** S01 has a single record, so page and record set coincide and the question does
not arise. It becomes a real assertion the moment a slice holds six or more records.

**Disposition.** Carried to the multi-record slices (S19–S21). Will need a BA/QA or legacy ruling.

## 5. Verified — not a defect

### VER-1 — the Add panel does not display RMG; the saved row does — 2026-09-17

**What looked wrong.** The S01 Gherkin asserts that `schedule6AddForm:RMG` "shows the derived Resource
Management Grouping" after the record is added. In the React app that field in the **Add panel** is
always blank, so read literally the scenario fails.

**Why it is not a defect.** The Resource Management Grouping is derived **server-side** from the supply
block, and the Add panel is passed `rmg=""` deliberately — `components/schedule6/index.tsx:401`, with
the reason stated at the call site ("`rmg` stays server-derived — see derived.ts for why it is not
mirrored"). The value is still shown to the user, on the saved record's row, and it is still correct:
supply block `01B` derives RMG `15`, confirmed both through the API and on screen. Legacy had no
separate add form — its add row *was* a row — so "the add panel" and "the row" were the same surface
there and are two surfaces here.

**How handled.** The assertion is re-grounded onto the row (`Then the new record row shows its derived
figures`) and the change is documented in the feature file's own re-grounding header. The panel's
`$ / m³` **is** mirrored from the blurred inputs and is asserted where legacy put it, so only RMG moves.

**Nothing is asked of BA/QA here** beyond awareness that the derived figure moved surfaces. Recorded
because a future reader comparing the Gherkin to the test will otherwise see a dropped assertion.

### VER-2 — record rows are in the page even when their accordion is collapsed — 2026-09-17

**What looked wrong.** Every saved record renders inside a Carbon accordion panel that starts
**closed**, yet the row's Volume and Cost fields can be read by an automated check without opening it.
That looks like a rendering leak.

**Why it is not a defect.** It is how Carbon's `Accordion` works: it places every panel's contents in
the page and hides the closed ones, rather than building them on expand. The app already relies on
this and says so at the point where it works around a consequence — each row's Delete button has the
record's ordinal added to its accessible name "because Carbon renders every AccordionItem's children
into the DOM regardless of which panel is expanded" (`components/schedule6/index.tsx:479`). A reporter
sees nothing until they expand the row, which is correct.

**Why it is recorded anyway — it changed how this suite asserts.** A check that reads a field's value
without expanding the row **passes while the value is invisible to the user**: it is testing the page's
internals rather than the screen. S01 was originally written that way and passed for that reason. Both
S01 and S02 now expand the record first and wait for the field to be genuinely **visible**, so the
assertion is a claim about what a reporter can see. Any future slice that reads a row must do the same
— `schedule6Page.expandRecord(ordinal, recordId)` exists for it, and its own comment explains why.

**One trap inside the fix, worth stating once.** The accordion title is
`Road Maintenance report Id: <ordinal>` where the ordinal is the **1-based position in the list**, not
the database `recordId`. Legacy's `rowCounter` means the same thing and the Check Status lines key on
it. Confusing the two produces an off-by-one that reads as an application bug.

**Nothing is asked of BA/QA here.**
