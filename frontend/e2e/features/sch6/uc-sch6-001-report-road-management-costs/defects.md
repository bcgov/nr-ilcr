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

**STATUS 2026-09-18 — IN PROGRESS.** S01–S20 authored and green (twenty-nine scenarios); **20 of 23
slices covered.** Seven verified-not-a-defect findings. **Still no divergences and no bugs found in the
app** — every red encountered so far has been the test being wrong, not the application.

**Three things need a human, all on this page.** None blocks the story:

1. **SPEC-2** — please accept the wording *"TSA or TFL: Value is required."* for a blank TSA/TFL
   submission. It cannot be checked against legacy (the old message came from a framework that is
   gone), so it is a decision rather than a lookup.
2. **VER-5** — a corrected field keeps showing its old error until Add Report is pressed again. Not a
   fault, and nothing is mis-stored; the question is whether you want it to clear as you type, which
   would be an enhancement.
3. **VER-7** — should a mill that saved only a general comment and no road records count as having met
   Schedule 6's requirements? The app says yes; legacy said no (recorded deviation (d)). A business
   rule question, not a defect.

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

### GAP-1 — 3 of 23 slices, and all accessibility coverage, not yet authored — OPEN

**What is missing.** S01–S20 are covered. **S21–S23** and the axe sweeps are still to be written.
(Was "12 of 23" when S01–S11 were the whole of it; the S12–S16 block, S17, and S18/S19/S20 all landed
2026-09-18. The accessibility half has not started, and by slice count alone it is now easy to read
this story as nearly done — which is exactly the GAP-5 trap this entry exists to prevent.)

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

### SPEC-2 — S12's required-field message text was `[UNKNOWN]` in the source — TEXT RECOVERED 2026-09-18, awaiting BA/QA acceptance

**What was missing.** The S01–S23 Gherkin's own "Open Items Carried Forward" records that the exact
JSF required-field validation message for a blank "TSA or TFL" submission could not be recovered — no
custom literal exists in `messages.properties` or `faces-config.xml`, so it was flagged rather than
fabricated.

**What it actually was.** There was nothing to find. Legacy did not *have* a literal for this: the JSF
framework generated the required-field message at runtime from `UIInput`, so the recovery work was
looking for a string that only ever existed inside the framework. That is why the search came up empty
— the `[UNKNOWN]` was correct, not an oversight.

**The text now asserted, and where it comes from.** The rebuilt app does not inherit a framework
default; it declares the message itself:

```
areaTypeRequired: 'TSA or TFL: Value is required.'
    src/components/schedule6/validation.ts:61
```

That module's header states its literals are transcribed *"verbatim from the backend bundle
(messages.properties) so an advisory message is byte-identical to the server's rejection for the same
field"* — so this is the app's own wording, client and server alike, taken from the running app exactly
as this entry said it would have to be. S12 asserts it byte-for-byte.

**What is being asked of BA/QA — the one open question.** Please accept
**"TSA or TFL: Value is required."** as the ILCR wording for this case. It **cannot** be confirmed
against legacy by anyone: the legacy string was produced by a framework that is gone, and no artifact
records what it rendered. So this is a decision, not a lookup. If you want different wording, it is a
one-line change in the bundle and a one-line change in the test.

**Disposition.** Text recovered and under test; the entry stays OPEN until BA/QA accept the wording.
Board item **#101**.

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

### VER-3 — an invalid TFL number is refused on Save, not while you type — 2026-09-17

**What changed for the user.** In the legacy screen, typing a TFL number that is not valid for interior
regions and leaving the field produced the error immediately, from a background round-trip. In the
rebuilt screen the same entry is accepted into the field and the error appears when you press
**Add Report**. The message is identical: *"Entered TFL number is not valid for Interior Regions."*

**Why it is not a defect.** "Valid" here means "this TFL number maps to a resource management
grouping", and that mapping lives on the server (`RoadGroupLookup`, a verbatim port of the legacy
lookup table). The browser genuinely cannot answer the question. The client does check what it *can* —
a blank or too-long entry is refused without a round-trip — but a plausible two-character code has to
be asked of the server, and the field only accepts two characters. So the entry is refused on submit,
nothing is stored, and the user sees the same words they saw before, one action later.

**The related correction to our own test, recorded because it is the kind of mistake that repeats.**
The scenario originally asserted that a rejected entry sends **no** request at all. That failed, and
correctly: one request is the right behaviour, for the reason above. The app was right and the
assertion was wrong. It now expects exactly one server round-trip — "exactly" rather than "at least"
so that a silent retry or double-submit would still be caught — and the claim that nothing was stored
is proved by reading the schedule back, which is the assertion that actually matters.

**For BA/QA.** Worth a glance only to confirm the later message is acceptable to reporters; it is a
timing change, not a behaviour change, and nothing is stored either way. If the immediate feedback is
considered important, that would be a new requirement rather than a defect, because it would mean
sending the TFL number to the server as the user leaves the field.

### VER-4 — the "select a mill and year" message has no trailing space here — 2026-09-17

**What looked wrong.** The S06 Gherkin quotes the message as
`"Please Select Mill and Reporting Year in the Home Page. "` — with a space after the full stop. The
rebuilt page shows it without one, so a byte-for-byte assertion on the quoted text fails.

**Why it is not a defect.** There are two literals, and the scenario reaches the other one. Legacy's
trailing-space version is the SERVER's ERR-001 text, returned when a request is made without a mill
and year. This guard never makes that request: with no working context the page suppresses the call
entirely and shows a client-side banner, whose literal carries no trailing space by the convention
every sibling schedule follows. The app states this at the point of definition
(`components/schedule6/index.tsx:52-55`), and notes that the server's spaced version still renders
verbatim when a request genuinely returns it — so neither literal has been lost.

**How handled.** The assertion uses the client literal, and the feature header records why. Nothing
was weakened: the message is still matched exactly, just the correct one of the two.

**For BA/QA.** No action. A reporter sees the same sentence; the difference is one invisible character
at the end, and only in the case where no request is sent.

### VER-5 — the Add panel's field errors appear on "Add Report", and stay until the next one — 2026-09-18

**What changed for the user.** In the legacy screen, typing a bad volume or cost and leaving the field
produced the error straight away, and correcting the field made it disappear again — both from
background round-trips. In the rebuilt screen there is no per-field checking at all: you can type
anything, and all five of these messages appear when you press **Add Report**:

| Field / case | Message |
|---|---|
| No TSA/TFL chosen (S12) | *TSA or TFL: Value is required.* |
| Volume not a number (S13) | *Entered volume entry is invalid.* |
| Volume outside 0–9,999,999 (S14) | *Entered volume must be between 0 and 9,999,999.* |
| Cost not a number (S15) | *Entered cost is invalid.* |
| Cost outside ±99,999,999 (S16) | *Entered cost must be between -99,999,999 and 99,999,999.* |

**Why it is not a defect.** It is the same deliberate choice already recorded in VER-3, applied to the
whole panel: the app validates when you submit, using messages taken verbatim from the backend's own
bundle, so what you read on a client refusal is word-for-word what the server would have said. The
four wordings above are *identical* to the legacy-derived Gherkin — only the moment they appear moved.

**And the refusal genuinely costs nothing, which is an improvement.** These five are decided in the
browser, so pressing Add Report with a bad value sends **no request at all** — nothing reaches the
database, and there is no round-trip to wait for. (Contrast S05's invalid TFL number, which must ask
the server and so costs exactly one request. The tests pin each at its own number so the two cannot be
confused.)

**The part worth your attention: a corrected field keeps showing the old error.** Fix the value and the
message stays on screen until you press Add Report again — so for one moment a reporter sees a valid
entry with a complaint under it. The figures behave correctly throughout (the `$ / m³` cell updates the
instant a valid value is entered, and refuses to move for an invalid one), and the record saves
normally on the next press. Nothing is lost or mis-stored.

This follows from validate-on-submit rather than being a separate mistake: with no per-field checking
there is no moment at which the app re-judges the field, so there is nothing to clear the message.
Making it clear as you type would mean adding live re-validation — **a new requirement, not a bug
fix**, which is the same conclusion VER-3 reached about the TFL number.

**How handled.** Asserted as it is, deliberately, in each correction arm (`the error ... is still shown
until the next submit`). That is not a weakened assertion: it states what the app does today, so if
anyone later makes the error clear on edit, that step fails and puts the change in front of a human
instead of letting it pass unnoticed.

**For BA/QA.** One decision, and it is a preference rather than a fault: is the lingering message
acceptable, or should correcting a field clear it? If the latter, raise it as an enhancement and these
assertions flip with it. Nothing is blocked either way.

### VER-6 — on a locked schedule the "Add" form is gone, not greyed out — 2026-09-18

**What looked wrong.** S17's scenario lists six fields of the *Add* form by name
(`tsaNumberOneMenu`, `tflNumber`, `tsbNumberOneMenu`, `vol`, `cos`, `comAdd`) and says each must be
**disabled** once the report leaves Draft. In the rebuilt screen none of those six exists to be
disabled, so read literally the scenario fails — and a reader comparing the Gherkin with our test will
see six named fields on one side and none on the other.

**Why it is not a defect.** The two screens put the add form in different places. Legacy showed it
permanently on the page, so greying it out was the only way to lock it. The rebuilt page keeps it
behind the **Add** button and only builds it when you press that button — and on a locked schedule the
**Add button itself is disabled**, so the form cannot be opened at all. The reporter cannot reach the
fields, which is a stronger lock than reaching them and finding them inert.

**How handled.** The test asserts the pair that actually describes this page: the Add button is present
and disabled, **and** the add form is absent. Asserting "disabled" on the six fields would fail against
things that do not exist; asserting only that they are absent would pass on *any* page and prove
nothing — the same empty-assertion trap recorded for the S06–S08 guards, in reverse.

**The six field names are still covered.** The rebuilt page builds each saved record's row from the
*same* component as the add form, so those six fields do appear on every stored record — and there the
test does assert each one is disabled, along with the row's **Delete** button. So the slice's real
claim, "a reporter cannot change anything", is checked on the surface where the fields exist.

**For BA/QA.** No action. Worth one glance only to confirm the expectation: on a submitted report there
is no greyed-out add form on screen — the Add button is simply unavailable. Recorded because, like
VER-1 (where the derived RMG figure moved from the add panel onto the saved row), this is a case where
comparing the old script to the new test otherwise looks like a dropped assertion.

### VER-7 — a comment-only schedule: the "$ / m³" total is blank, and Check Status passes — 2026-09-18

Two findings about the same situation: a mill/year where a reporter has saved a **general comment** and
never added a road record. Both were confirmed against the running app.

**Background, because this state is easy to misread.** There is no such thing as storing a bare
comment: the comment has to hang off a row, so the system quietly creates an otherwise-empty
"placeholder" record to carry it. That placeholder is deliberately hidden — it is not shown in the
records list, not counted in the totals, and not judged by Check Status. The screen therefore shows
"No records found." with the comment still in its box, which is exactly what the slice asks for.

**Finding 1 — the third total shows blank, not zero.** The scenario expects `totalVol`, `totalCos` and
`totalCal` to "show zero". Volume and Cost do show **0**. The **$ / m³** cell is **empty**.

*Why that is right.* $ / m³ is cost divided by volume, and here both are zero — zero divided by zero
has no answer, so the app shows nothing rather than inventing a figure. The code says so where the
formatting is decided: *"null (0/0 is undefined) while totalVolume/totalCost are real zeros that must
still show"*. Printing `0.00` there would be stating a rate that does not exist. The test asserts two
zeroes and a blank.

**Finding 2 — Check Status reports the schedule as MET.** Pressing Check Status on a comment-only
schedule gives *"All requirements for this schedule have been met"*, with no findings at all. **Legacy
reported problems here instead.**

*Why that is right, and deliberate.* This is a recorded parity deviation — deviation **(d)** — decided
when the backend was built, not something introduced by accident. The reasoning is that the hidden
placeholder is not a road record, so judging it would produce a complaint about a row the reporter
cannot see and cannot fix: it has no area type, no supply block and no cost, so it would fail three
checks at once. The app therefore ignores it, and a schedule with nothing but a comment has nothing
outstanding. Legacy's behaviour was arguably the bug.

**How handled.** Both are asserted as the app behaves, and Finding 2 is asserted **deliberately** even
though the original scenario never mentions Check Status — it is the check that proves the placeholder
stays hidden from end to end. If that ever broke, a reporter would see a phantom failing row appear out
of nowhere, and this is the assertion that would catch it.

**For BA/QA.** One thing worth confirming, and it is a business question rather than a defect: **should
a mill that has saved only a comment and no road records count as having met Schedule 6's
requirements?** The app says yes; legacy said no. If the Ministry expects "no records" to block
submission, that is a rule change rather than a bug fix — and it would belong with the submit gate, not
with this screen. Nothing is blocked either way.
