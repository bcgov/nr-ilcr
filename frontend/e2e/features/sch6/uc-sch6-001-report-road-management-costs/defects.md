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

**STATUS 2026-09-18 — COMPLETE.** All **23 of 23 slices** plus the **accessibility sweeps** are
authored: thirty-nine scenarios, of which **37 pass** and **2 are deliberate, tagged reds** excluded
from `npm run test:gate`. Seven verified-not-a-defect findings.

**Across the 23 functional slices, no divergence and no bug was found in the application** — every red
encountered there turned out to be the test being wrong, and each is recorded where it happened.

**The accessibility work found two**, which is the point of having done it:

- **BUG-1** — validation errors are not announced to screen readers. **App-wide Carbon issue, already
  tracked** elsewhere; confirmed present here.
- **BUG-2** — a live character counter is still shown on the disabled General Comments field of a
  submitted schedule, at 1.73:1. **New.** Probably WCAG-exempt (inactive component) but pointing at a
  real oddity; needs a BA/QA choice. See section 2.

**Worth stating plainly for triage:** S22/S23, the two slices that are a live defect on Schedule 5
(issue #476 / app-wide #359), are **green here**. Schedule 6 already judges what is on screen rather
than what is in the database. They stand as a regression guard — see section 1.

**One thing needs a human, and it is the only code change on this page.** It does not block the gate:

1. **BUG-2** — hide the character counter on the disabled General Comments field (recommended), or
   accept the contrast as WCAG-exempt and record a node exclusion. Either closes it.

**Everything else that was awaiting a decision has been settled by BA/QA on 2026-09-23**, all three in
favour of the app as built, and none of them needing a code or test change:

- **SPEC-2** — the required-field wording *"TSA or TFL: Value is required."* is **accepted** as the
  ILCR text. CLOSED; see section 4.
- **VER-5** — the lingering validation message (a corrected field keeps its old error until the next
  **Add Report**) is **accepted**; no enhancement is being raised. See section 5.
- **VER-7** — a mill that saved only a general comment and no road records **does** count as having met
  Schedule 6's requirements; parity deviation **(d)** stands. See section 5.

The VER entries stay on this page permanently — that is what the register is for (`defects-guide.md`:
*"Verified-not-a-defect: permanent"*) — so what closed is the question each one carried, not the record.

---

## 1. Divergences (app behaves differently from the spec — suspected defect)

*None.*

> Note for triage, because Schedule 5's experience sets the wrong expectation here. On **Schedule 5**
> the Check-Status-includes-unsaved-edits pair (its S24/S25) is a live divergence — issue #476, app-wide
> #359 — because that endpoint reads the database rather than the screen. **Schedule 6 already has the
> fix**: `POST /api/v1/schedule6/check-status` takes the ON-SCREEN values (`Schedule6CheckRequest`,
> whose own javadoc calls itself "the fix"), `Schedule6Service.checkStatus` evaluates
> `payloadCandidates(request)` and nothing else, and an earlier DB-reading implementation was
> deliberately retired in Task 8 for exactly the reason Schedule 5 is still broken.
>
> **Now confirmed by test, 2026-09-18.** S22 and S23 are authored and **both pass** — see
> `check-status-unsaved.feature`. Schedule 6 reports a value cleared on screen but not saved, and stops
> reporting one corrected on screen but not saved, and writes nothing either way. So this pair is a
> **regression guard** here, not a defect: a red in future is a real regression rather than the known
> cross-schedule gap. Do not "align" Schedule 6 with the other schedules.
>
> If the app-wide #359 fix is ever rolled out to the other schedules, Schedule 6's implementation is the
> reference — and these two scenarios are the ones to copy.

## 2. Bugs / regressions

### BUG-1 — validation errors are not announced to screen readers — OPEN (app-wide, already tracked)

**Not Schedule 6's defect, and not new.** When a field is rejected, Carbon's text input points assistive
technology at an error message it never actually announces. A screen-reader user presses **Add Report**,
hears nothing, and the form simply does not submit — they are given no way to know why.

**It affects every schedule's validation-error state**, not this page: it is a wiring problem inside the
`@carbon/react` component. Already found, triaged and recorded as **BUG-1** in
`features/sch11/uc-sch11-001-report-costs/defects.md`, and listed in the suite's `KNOWN_A11Y_RULES`
(rule `aria-valid-attr-value`, impact **critical**) so a known red reports one line instead of a full
dump.

**Confirmed present on Schedule 6** by the accessibility sweep of the Add panel's error state
(2026-09-18), on the Volume field. Recorded here so this UC's own ledger is complete; **the fix belongs
to the app-wide item, not to this story.**

**Test:** `accessibility.feature` `@discovered-bug` — deliberately RED, and excluded from
`npm run test:gate`. It goes green on its own when the app-wide fix lands.

### BUG-2 — a live character counter on a field nobody can type into — OPEN (new, found by the sweep)

**This is the one finding the accessibility work turned up.**

**What's wrong.** On a **submitted** (non-Draft) Schedule 6, everything is correctly locked — but the
General Comments box still displays its live character counter, *"372 characters remaining"*, and it is
rendered so faintly as to be effectively invisible.

**Expected vs actual.** Expected: a field that cannot be typed into does not advertise how much room is
left in it. Actual: the counter is still there, greyed to the point of being unreadable.

**How caught.** The read-only accessibility sweep (`accessibility.feature`), then measured directly from
the rendered pixels rather than taken on the scanner's word:

| Text | Colour | Effective over white | Ratio | WCAG 1.4.3 (4.5:1) |
|---|---|---|---|---|
| "372 characters remaining", field **disabled** | `rgba(22, 22, 22, 0.25)` | `rgb(197, 197, 197)` | **1.73:1** | fails on its face |
| the same helper text **enabled**, for reference | `#525252` | — | 7.81:1 | passes |

**Is it actually a WCAG failure? Probably not — and that matters.** WCAG 1.4.3 carries an explicit
exception: text that is *"part of an inactive user interface component"* has **no contrast
requirement**. The field is genuinely disabled, so its own helper text is exempt by the letter of the
standard. The scanner flags it only because the counter is a separate element beside the field rather
than the field itself, so the rule cannot tell it belongs to something inactive. That is a known
limitation of the check, not a verdict about this page.

**So why is it recorded as a bug, and left failing?** Two reasons:

1. **The contrast question is BA/QA's to settle, not the test author's.** Waving away a critical-looking
   scanner result on a WCAG interpretation is exactly the sort of call that should be made by the people
   who own the standard for this product.
2. **The finding points at something real regardless of the contrast.** A live "characters remaining"
   counter on a field nobody can edit is simply wrong — it is telling a reporter about capacity they
   cannot use. **Not rendering the counter when the field is disabled fixes the odd behaviour and
   clears the scanner finding as a side effect**, which is why it is the recommended direction.

**What is being asked of BA/QA.** Choose one:

- **(recommended)** hide the character counter when the General Comments field is disabled — a small
  frontend change that resolves both halves; or
- accept the contrast as WCAG-exempt and record a node-level exclusion for this one element, leaving the
  counter as it is.

Either way this closes. **Nothing else on the read-only page is affected** — the sweep reports this one
element and nothing more, which is what makes the finding precise rather than a blanket red.

**Why the rule is not silenced suite-wide.** `color-contrast` carries *genuine* failures elsewhere in
this suite — the authored Home welcome message (sec BUG-1) and Schedule 4's row-hover contrast — so
adding it to `KNOWN_A11Y_RULES` would hide those. The louder logging is the correct trade.

**Test:** `accessibility.feature` `@p1 @S17 @discovered-bug` — deliberately RED, excluded from
`npm run test:gate`.

## 3. Coverage gaps (something the suite does not yet prove)

### GAP-1 — accessibility coverage — CLOSED 2026-09-18

**What was missing.** All 23 slices were covered while `accessibility.feature` did not exist at all.
This entry existed because accessibility is an NFR that **no slice in the catalogue asks for**, so the
slice count would otherwise have read as completeness — which is exactly what happened on Story 28.4
(Schedule 5 reached "all 25 slices authored" with zero accessibility coverage; its GAP-5, whose lesson
was *"a slice catalogue is not a completeness test"*).

**Now closed.** `accessibility.feature` sweeps seven surfaces, one scan per scenario, pointer parked:

| Surface | Result |
|---|---|
| the empty Draft schedule | green |
| the blank Add panel | green |
| the Add panel showing validation errors | **red — BUG-1**, app-wide, already tracked |
| a saved record's expanded row | green |
| a Check Status verdict with findings | green |
| the read-only (non-Draft) schedule | **red — BUG-2**, new (see above) |
| the context-suppressed guard state | green |

**One sweep covers all three context guards** — S06/S07/S08 render through the same shared
`ScheduleLoadState` component and differ only in message text, so sweeping one exercises the whole
render path. The context-suppressed one is used because it needs no anchor at all.

**Five of seven green; both reds are tagged `@discovered-bug`** and therefore excluded from
`npm run test:gate`, so neither blocks the gate while it is open. Stability: `--repeat-each=5` over the
five green sweeps → 25/25.

**Disposition.** Closed. Board item **#101** — as of 2026-09-23 the only remaining open items on this
page are **BUG-2** (new, needs a BA/QA choice) and **BUG-1** (app-wide, not this story's). Every
judgement item is settled: SPEC-2, VER-5 and VER-7 all closed 2026-09-23.

### GAP-2 — one scenario in the source cannot happen on this screen — OPEN (informational)

**What is not covered.** Slice S22 has two scenarios. The first — clearing a required amount on screen
and running Check Status without saving — is covered. The second asks for *"a well-formed, in-range
amount that still fails its Check Status requirement"*. **There is no such value on Schedule 6.**

**Why not.** Everything Check Status examines on this screen is simply *present or absent*: the
TSA/TFL area type, the TFL number (on the TFL branch), the Supply Block (otherwise), and the Cost.
There is no minimum, no maximum, and no rule comparing one field against another — and Volume is not
checked at all. So any cost a reporter can legitimately type satisfies the requirement, **including
zero**. The only way the Cost check can fail is for the field to be empty, which is the first
scenario.

The original scenario was narrowed for a reason that does not apply to the rebuilt screen: it assumed
the old framework's field-by-field validation would reject a malformed value before Check Status ran,
so it deliberately asked for a value that was *valid* but still *failing*. The rebuilt screen validates
when you press a button and sends the values as data, so that distinction has no counterpart here.

**Why this is recorded rather than dropped.** A reader comparing the source scenarios with our tests
would otherwise count one short and reasonably assume it was missed. It was not: the state it describes
cannot be reached on this screen, so there is nothing to automate and nothing to skip.

**For BA/QA.** No action, unless the answer to VER-7's question changes. If the Ministry ever adds a
*range* or *relationship* rule to Schedule 6's Check Status — a minimum cost, say, or a cost that must
agree with another schedule — this scenario becomes reachable and should be written then.

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

### SPEC-2 — S12's required-field message text was `[UNKNOWN]` in the source — CLOSED 2026-09-23

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

**What was asked of BA/QA, and the answer.** BA/QA were asked to accept
**"TSA or TFL: Value is required."** as the ILCR wording for this case, because it **cannot** be
confirmed against legacy by anyone: the legacy string was produced by a framework that is gone, and no
artifact records what it rendered. So it was a decision, not a lookup.

**Accepted by BA/QA 2026-09-23.** The wording stands as the ILCR text for a blank TSA/TFL submission,
client and server alike, and S12 continues to assert it byte-for-byte. Nothing changed in the app or
in the test — the acceptance is the whole of what this entry was waiting on, and the assertion that
was already passing is now a ratified one rather than a provisional one.

**Disposition.** CLOSED 2026-09-23. Board item **#101**. If the Ministry later wants different
wording, that is a change request rather than a reopening of this entry: a one-line change in the
backend bundle and `src/components/schedule6/validation.ts:61`, and a one-line change in
`required-field.feature`.

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

### VER-5 — the Add panel's field errors appear on "Add Report", and stay until the next one — 2026-09-18 (BA/QA question SETTLED 2026-09-23)

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

**For BA/QA — SETTLED 2026-09-23.** The lingering message is **accepted as the ILCR behaviour**. No
enhancement is being raised: validate-on-submit stands, and a corrected field will continue to show its
previous message until the next **Add Report**.

Nothing changes in the app or in the tests. The assertions stay exactly as written, which means they
keep their second job: they state what the app does today, so if anyone later makes the error clear on
edit, the step fails and puts that change in front of a human instead of letting it pass unnoticed.
Should the Ministry want live re-validation after all, it is a **new requirement** (the conclusion
VER-3 already reached about the TFL number) and these assertions flip with it.

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

### VER-7 — a comment-only schedule: the "$ / m³" total is blank, and Check Status passes — 2026-09-18 (BA/QA question SETTLED 2026-09-23)

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

**For BA/QA — SETTLED 2026-09-23.** Confirmed: **a mill that has saved only a general comment and no
road records counts as having met Schedule 6's requirements.** Parity deviation **(d)** stands as
decided when the backend was built. The hidden placeholder is not a road record, so Check Status does
not judge it, and legacy's contrary behaviour is deliberately not restored — judging the placeholder
would raise three complaints about a row the reporter can neither see nor fix.

Both findings are asserted as the app behaves and need no change.

**What this does NOT decide — flagged for whoever builds the submit gate.** This settles what the
*Schedule 6 screen* reports. Whether an otherwise-empty report should be allowed to be **submitted** is
a separate question, and it belongs with Epic 15's submit gate (Story 15.3 — Submit Schedules 1–10),
where the all-schedule rule lives. If the Ministry ever wants "no records" to block submission, that is
a new rule there; it does not reopen this entry.
