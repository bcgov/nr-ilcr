# Defects — UC-SCH4-001 Report Special Log Transportation Costs (Schedule 4)

> How this log works (registers, tags, glossary): [defects-guide.md](../../../defects-guide.md)

All findings below were reproduced against the running local stack (frontend `:3000`, backend `:8080`,
seeded delivery Oracle) on **2026-08-17**, branch `test/schedule-4-e2e`, app commit `9632f7f`.

**Bug / Regression:**

- **#1 — While a location is open for editing (or viewing), the buttons on that row are too faint to read.**
  - **What's wrong:** opening a location highlights its row in the Existing Locations list so you can see
    which one you are working on. That highlight is dark enough that the row's own **Edit**, **Copy** and
    **Delete** labels no longer stand out from it clearly enough to meet the accessibility standard the
    project commits to (WCAG 2.1 AA). It is the state a reporter is in for the whole time they edit a
    location, and it also happens in read-only View mode.
  - **Expected vs actual:** text must have a contrast ratio of at least **4.5:1** against its background;
    these labels measure **3.81:1**.
  - **How we caught it (verified on real data 2026-08-17):** the axe-core sweep of the open Edit panel, with
    the mouse pointer deliberately parked away from the page so the measurement is of the resting state, not
    a hover. Measured values:
      - Edit `#0f62fe` on `#d0e2ff` → 3.81:1
      - Copy `#0f62fe` on `#d0e2ff` → 3.81:1
      - Delete `#da1e28` on `#d0e2ff` → 3.81:1
      - View (read-only mode) `#0f62fe` on `#d0e2ff` → 3.81:1 (Copy/Delete are disabled there, and
        accessibility tools skip disabled controls, so that state reports one label instead of three)
    The same list scans **clean** with no panel open, which is what pins the cause to the highlight.
  - **Why (technical):** `components/schedule4/index.scss:40` —
    `.schedule-4__row--editing td { background-color: var(--cds-highlight); }`. `--cds-highlight` resolves to
    a mid-blue that composites to `#d0e2ff` behind Carbon's `ghost` / `danger--ghost` button label colours.
    Schedule 4 is the only schedule that highlights a row this way.
  - **Action:** **BA/QA to raise a Jira ticket.** Kept as two genuinely-failing `@discovered-bug` tests (the
    Draft panel and the read-only panel), RED until fixed. They flip green together when the highlight colour
    is changed — a lighter token, or a darker label colour on that row.
  - **Priority / env:** p1 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN. Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/accessibility.feature`
    ("The open Edit panel keeps its row actions accessible" and "The read-only location panel keeps its row
    action accessible", both `@discovered-bug`).

- **#2 — Hovering any table row makes that row's action buttons too faint to read (APP-WIDE, not just Schedule 4).**
  - **What's wrong:** moving the mouse over a row in any data table shades the row, and the shading makes the
    row's action labels (e.g. **Delete**) fail the same contrast standard as #1. It is not caused by anything
    Schedule 4 does — it is the design system's own row-hover shade combined with its own button colours — so
    every screen with buttons inside table rows is affected.
  - **Expected vs actual:** at least **4.5:1** required; measures **3.78:1** while hovered.
  - **How we caught it (verified on real data 2026-08-17):** an axe-core sweep taken with a list row
    deliberately hovered. Reproduced in two places — the Schedule 4 location list (`ghost` #0f62fe and
    `danger--ghost` #da1e28 on `#e0e0e0`) and a Schedule 4 sub-page's row Delete. Every other sweep in this
    suite now parks the pointer first, so this state is only ever measured on purpose.
  - **Why (technical):** Carbon's data-table hover layer (`#e0e0e0`) behind `@carbon/react`'s `ghost` /
    `danger--ghost` label tokens. Schedule 11 renders the same `danger--ghost` control inside a table row and
    is equally affected; its sweep passes today only because its pointer happens to rest elsewhere.
  - **Action:** **BA/QA to raise a Jira ticket**, and treat it as cross-cutting rather than a Schedule 4 fix
    (also recorded in `deferred-work.md`). Kept as one genuinely-failing `@discovered-bug` test.
  - **Priority / env:** p2 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN. Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/accessibility.feature`
    ("A hovered row keeps its action labels readable", `@discovered-bug`).

- **#3 — A sub-page's rows table carries a descriptive name that assistive technology never uses.**
  - **What's wrong:** each sub-page's rows table is given the name "Towing Total rows" (or the equivalent for
    the other two sub-pages), but a second, competing name is also set, and that one wins. A screen-reader
    user hears only "Towing Total" — the same words as the heading right above it — so the extra description
    the developer intended is silently discarded. Nothing is unusable; the table still has a name.
  - **Expected vs actual:** expected the table to be announced as "Towing Total rows"; actual "Towing Total".
  - **How we caught it (verified on real data 2026-08-17):** Playwright's accessibility snapshot of the
    sub-page reports `- table "Towing Total"`, and matching on the intended name resolves nothing. The suite
    therefore addresses these tables by the name that is actually in force.
  - **Why (technical):** `SubPage.tsx` sets `aria-label={`${def.label} rows`}` on `<Table>`, while Carbon's
    `<TableContainer title=…>` also sets `aria-labelledby` on the same element. Per the accessible-name
    spec, `aria-labelledby` takes precedence, so the `aria-label` is dead code.
  - **Action:** **BA/QA to raise a Jira ticket** (low priority — cosmetic/maintenance). No test: nothing
    user-facing is broken, and asserting the currently-dead name would assert a behaviour nobody guarantees.
  - **Priority / env:** p3 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN. Found 2026-08-17.
  - **Test:** none (recorded here so the next author does not re-derive it; the locator note lives in
    `pages/sch4/schedule4SubPage.ts`).

**Divergences:**

- **#1 — Check Status can still be run after the report has been submitted; the old system did not allow it.**
  - **What's wrong:** once the Schedules 1–10 report leaves Draft (Submitted or Verified), Schedule 4 becomes
    read-only — Add New Location, Copy and Delete are all correctly disabled — but the **Check Status** button
    stays clickable.
  - **Expected vs actual:** Expected the button to be disabled outside Draft (legacy STA-001/BR-03; the source
    scenario S18 asserts it in as many words: *"the Check Status button (top) is disabled"*). Actual: enabled,
    and clicking it runs the check.
  - **How we caught it (verified on real data 2026-08-17):** on the Submitted anchor (mill 20171 / 2015), the
    browser reports Add New Location `disabled=true` and Check Status `disabled=false`. Nothing is written —
    Check Status is read-only by contract — so the practical impact is a control that should not be offered
    rather than data damage.
  - **Why (technical):** `components/schedule4/index.tsx` gives the button `disabled={saving}` where the
    sibling schedules use `disabled={!editable || saving}` (`schedule2/index.tsx:319`,
    `schedule11/index.tsx:881`). Legacy bound it to `disableReportEdits()` (`schedule4.xhtml:43`). So Schedule
    4 differs from the old system **and** from both of its siblings.
  - **Is it a defect?** Can't tell from the test alone — the team may have decided a read-only check is
    harmless to offer. BA/QA to decide.
  - **Action:** **BA/QA to raise a Jira ticket.** Kept as a genuinely-failing `@discovered-divergence` test
    that asserts the legacy behaviour — RED until the app matches (or the spec is updated and the test
    retired).
  - **Priority / env:** p2 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN. Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/render-states.feature` (S18,
    `@discovered-divergence`).

- **#2 — Check Status says a value is required but not WHICH figure is missing.**
  - **What's wrong:** when a location is missing a required Cost, Check Status reports the location name and
    the words "Value Required" — but not the transportation category the Cost belongs to. A location missing
    two Costs produces two identical messages, so the reporter cannot tell which lines to go and fix.
  - **Expected vs actual:** Expected the old system's wording, which named the field —
    `"Location : <name> - Lakeside Dry Dump (Cost $) "` followed by `"Value Required"` (EF3). Actual: a
    notification titled `"<location name> — required"` with the message "Value Required", repeated once per
    missing figure with nothing to distinguish them.
  - **How we caught it (verified on real data 2026-08-17):** seeded one location with two categories that have
    a Volume but no Cost, then ran Check Status. The screen showed exactly two notifications, both reading
    `"E2E Two Gaps — required / Value Required"`. The API response underneath DOES identify each one (it
    returns the cost-item code per issue), so the information exists and is dropped on the way to the screen.
  - **Why (technical):** the backend returns `FieldIssue { code, message }` per issue — Story 10.4 §Decision 4
    added the per-field shape for precisely this purpose — but `components/schedule4/index.tsx` renders only
    `issue.message.text`, never mapping `issue.code` to its category label (the label map already exists in
    `validation.ts` `ALL_CATEGORIES`).
  - **Is it a defect?** Can't tell from the test alone. The notification layout was deliberately re-grounded
    (title carries the location, subtitle the message), so this may be an accepted simplification — but the
    lost field identity looks unintended given the backend still supplies it. BA/QA to decide.
  - **Action:** **BA/QA to raise a Jira ticket.** Kept as a genuinely-failing `@discovered-divergence` test.
    It asserts that the category is named *somewhere* in the Check Status output rather than pinning the old
    JSF sentence, because the notification shape itself is not coming back.
  - **Priority / env:** p1 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN. Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/check-status.feature` (S28,
    `@discovered-divergence`).

- **#3 — Unsaved changes to a location are thrown away with no warning.**
  - **What's wrong:** type a change into an open location, then press **Back**, or click **Edit** on another
    location, or **Add New Location** — the change disappears immediately, with no "are you sure" prompt. The
    old system asked first, and the story for this screen requires the prompt in as many words.
  - **Expected vs actual:** Expected the confirmation *"Any unsaved data will be lost. Are you sure you would
    like to continue?"* before the entered data is dropped (legacy NAV-001, source scenario S12, and
    `epics.md` Story 10.5: *"Then the dirty-panel discard confirm fires (NAV-001 …) before entered data is
    dropped (S12)"*). Actual: no dialog at all; the panel just closes or switches.
  - **How we caught it (verified on real data 2026-08-17):** with a saved location open, changed a category
    Cost from 3600 to 9999 and pressed Back — zero dialogs appeared, the panel closed, and reading the record
    back showed the stored Cost still 3600 (so nothing was saved; the edit was simply lost). Repeated with
    **Add New Location** instead of Back: again zero dialogs, and the panel switched straight to "New
    Location".
  - **Why (technical):** `closePanel` is `() => setPanelMode('closed')`, and `openNew` / `openEditOrView`
    switch the panel unconditionally — no dirty check anywhere. The app DOES implement the same confirmation
    for sub-page navigation (NAV-002/NAV-003, both covered and passing), so this is a missing case rather than
    a missing feature.
  - **Is it a defect?** Can't tell from the test alone, but note the story AC explicitly requires it. BA/QA to
    decide.
  - **Action:** **BA/QA to raise a Jira ticket.** Kept as two genuinely-failing `@discovered-divergence` tests
    (Back, and Add New Location). A third test — passing — asserts the compensating guarantee: whatever is
    decided about the prompt, a discarded edit is never written to the database.
  - **Priority / env:** p1 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN. Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/nav-and-recompute.feature` (S12,
    `@discovered-divergence` ×2).

- **#4 — After saving, the cost-per-cubic-metre column still shows the OLD value until you reopen the location.**
  - **What's wrong:** `$/m³` is calculated by the system from the Cost and Volume entered. Save a location and
    the success message appears, the amounts are stored correctly — but the `$/m³` cell on the panel you just
    saved still shows what it showed before (an em dash for a brand-new location, or the previous rate for an
    edit). Close and reopen the location and the correct figure appears.
  - **Expected vs actual:** Expected the recalculated rate on screen after the save — both source scenarios
    say so (S01 *"shows the recomputed cost-per-volume"*, S02 *"shows the recalculated cost-per-volume"*), and
    BR-05 makes `$/m³` a system-computed display figure. Actual: the stale value until the location is
    reopened.
  - **How we caught it (verified on real data 2026-08-17):** entered Volume 1,200 and Cost 3,600 on a new
    location and saved. The success message appeared and the API read-back confirmed the server had computed
    `perUnit = 3`; the on-screen `$/m³` cell read `—`. Pressing Back and reopening the same location showed
    `3.00`. So the figure is right in the database and stale on screen.
  - **Why (technical):** `handleSave` re-seeds `panelMode` / `panelEditId` / `panelRevision` from the save
    response but never calls `setPanelPerUnit`, so the panel keeps the per-unit map it was opened with.
  - **Is it a defect?** Reads as an oversight rather than a decision (the server response already carries the
    new values), but BA/QA to decide.
  - **Action:** **BA/QA to raise a Jira ticket.** Kept as a genuinely-failing `@discovered-divergence` test,
    paired with a passing test that proves reopening shows the right figure — so the ticket's scope is clearly
    "refresh the panel after save", not "the calculation is wrong".
  - **Priority / env:** p1 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN. Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/nav-and-recompute.feature` (S01/S02,
    `@discovered-divergence`).

- **#5 — A rejected duplicate name stays in the field instead of being reset.**
  - **What's wrong:** nothing is broken. When a save is refused because the location name already exists, the
    old system put the previous name back in the field; this app leaves what you typed there so you can edit
    it. Recorded so the difference is a decision on the record rather than a surprise.
  - **Expected vs actual:** Expected (legacy ERR-002) the name field reset to its prior value; actual the
    entered name is kept, with the error shown above.
  - **How we caught it (verified on real data 2026-08-17):** saved "E2E LAKESIDE" against an existing
    "E2E Lakeside"; the banner showed the API's own *"Location Name already exists."* and the field still read
    "E2E LAKESIDE". Nothing was stored (the anchor still held exactly one location).
  - **Why (technical):** `putLocation`'s catch keeps the panel and its entered values, per Story 10.5's
    "entered values retained on failure" acceptance criterion.
  - **Is it a defect?** Very likely intentional — it is an explicit story AC and an improvement for the user.
    BA/QA to confirm parity is acceptable.
  - **Action:** **BA/QA to raise a Jira ticket** only if they disagree with the change. This is an ACCEPTED
    re-grounding: the test is GREEN and asserts the as-built behaviour, not `@discovered-divergence`.
  - **Priority / env:** p2 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN (awaiting BA/QA acknowledgement). Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/duplicate-name.feature` (S14, green).

- **#6 — Check Status does not flag a missing Distance, and the source scenario for it describes a rule that never existed.**
  - **What's wrong:** nothing is broken. Source scenario S29 expects Check Status to report a missing Distance
    as "Value Required". It does not — and the old system did not either: that check is commented out in the
    legacy code, and the slice catalogue itself flagged S29's premise as *inferred* rather than sourced.
  - **Expected vs actual:** Expected (per S29) a missing Distance to fail the location; actual only a missing
    **Cost** fails it.
  - **How we caught it (verified on real data 2026-08-17):** a location whose distance-based categories are
    absent passes Check Status, and the whole-schedule "all requirements met" banner appears. Note the state
    S29 describes is unreachable anyway: a distance category with amounts but no Distance cannot be saved
    (BR-04 blocks it), and a fully empty one is not stored at all.
  - **Why (technical):** Story 10.4 §Decision 2 pinned "Distance NOT enforced" to legacy parity and recorded
    it; `Schedule4Service.checkStatus` tests `category.cost() == null` only.
  - **Is it a defect?** No — a documented, delivery-confirmed decision. Raised here only so the S29 scenario
    is not read later as missing coverage.
  - **Action:** **BA/QA to raise a Jira ticket** only if the ministry actually wants a distance check (that
    would be new behaviour, not a fix). This is an ACCEPTED re-grounding: the test is GREEN and asserts the
    app's real behaviour.
  - **Priority / env:** p1 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN (awaiting BA/QA acknowledgement). Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/check-status.feature` (S29, green).

- **#7 — Check Status does not flag missing Comments either (same story as #6).**
  - **What's wrong:** nothing is broken. Source scenario S30 expects Check Status to report a location with no
    Comments as "Value Required". It does not, and there is no such message in the system's message file to
    render — the legacy comment check is commented out too.
  - **Expected vs actual:** Expected (per S30) blank Comments to be flagged; actual Comments never affect the
    outcome.
  - **How we caught it (verified on real data 2026-08-17):** a location with complete Costs and an empty
    Comments box (confirmed empty through the UI) passes, and the whole-schedule banner appears.
  - **Why (technical):** Story 10.4 §Decision 3 ("Comments soft — no comments-required key exists; never
    blocks MET").
  - **Is it a defect?** No — a documented decision. If a soft advisory message is wanted, it needs a product
    decision and a new message key; it must not be invented by the test suite.
  - **Action:** **BA/QA to raise a Jira ticket** only if the ministry wants the soft prompt. ACCEPTED
    re-grounding: the test is GREEN.
  - **Priority / env:** p1 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN (awaiting BA/QA acknowledgement). Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/check-status.feature` (S30, green).

- **#8 — The delete confirmation's punctuation differs by one character.**
  - **What's wrong:** nothing is broken. The old system built the confirmation from two message fragments,
    which joined as *"This will delete the current record,"* + *"Do you want to continue?"* (note the comma).
    The app shows *"This will delete the current record. Do you want to continue?"* (a full stop).
  - **Expected vs actual:** as above — one sentence-joining character.
  - **How we caught it (verified on real data 2026-08-17):** the delete-confirmation assertions in
    `delete.feature` / `subpage-rows.feature` pin the app's wording verbatim.
  - **Why (technical):** the two legacy fragments (`confirmDeleteMsgPart1`/`Part2`) were merged into one
    client string in `index.tsx` / `SubPage.tsx`.
  - **Is it a defect?** No — a deliberate tidy-up of a legacy two-part message.
  - **Action:** none expected; noted for BA/QA completeness. ACCEPTED re-grounding: the tests are GREEN and
    assert the app's wording.
  - **Priority / env:** p3 · branch `test/schedule-4-e2e` · local seeded DB · commit `9632f7f`.
  - **Status:** OPEN (awaiting BA/QA acknowledgement). Found 2026-08-17.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/delete.feature` (S10, green).

**Coverage gaps (not tested yet — no app problem):**

- **#1 — "A user without edit rights sees Schedule 4 read-only" cannot be produced here (S18/BR-03).**
  - **Why not:** whether the page is editable is decided server-side from the caller's `EDIT_SCHEDULE`
    permission AND the Draft status. The local environment's mock authentication grants a single fixed
    admin-ish user, so a user *without* that permission cannot be produced from the browser. The DRAFT half of
    the same gate IS covered end-to-end (both non-Draft codes, S18), and the endpoint-level permission checks
    are covered by the backend's own `Schedule4WriteAuthorizationIT`.
  - **Future action:** revisit as E2E once the app has real role switching (the FAM/Cognito work); until then
    treat as waived at the gate, per the coverage guide's `blocked` rule.
  - **Status:** OPEN.
  - **Test:** none — tracked as the role/permission line in `coverage.md`.

- **#2 — ERR-003, the generic "something went wrong while saving" message, is not exercised.**
  - **Why not:** the message text is whatever the service layer's exception resolves to, which the source
    documents themselves record as `[UNKNOWN]`. Forcing a real server failure from a browser test needs the
    request to be intercepted and failed at the browser edge (the technique Schedule 2 uses for its own
    save-error slice). Schedule 4 has no source slice for it, so no scenario was invented.
  - **Future action:** add one interception-based scenario if BA/QA want the failure path covered, mirroring
    `features/sch2/uc-sch2-001-report-costs/save-error.feature`.
  - **Status:** OPEN.
  - **Test:** none — tracked as a `deferred` row in `coverage.md`.

- **#3 — The "changed by another user" conflict (a stale save token) is not exercised.**
  - **Why not:** the app refreshes its optimistic-lock token from every save response, so producing a stale
    one needs two concurrent sessions on the same location — outside what a single-browser scenario can do
    honestly. The API-level behaviour was verified by hand during authoring (a stale token returns HTTP 409
    with *"This schedule was changed by another user. Please reload and try again."*), and the message is
    pinned in the fixture so a UI scenario can be added cheaply later.
  - **Future action:** cover with two browser contexts if BA/QA want the conflict path end-to-end.
  - **Status:** OPEN.
  - **Test:** none — tracked as a `deferred` row in `coverage.md`.

- **#4 — The validation-error state is not swept by axe here, deliberately.**
  - **What's missing:** Schedule 4's accessibility sweeps cover nine renders (the location list, the New
    panel, an editable sub-page, the read-only list/panel/sub-page, the context-suppressed state, and the two
    contrast states filed as Bug #1/#2) but omit the validation-error state.
  - **Why:** sweeping it would re-find a single already-triaged, **app-wide** defect — Carbon's `TextInput`
    invalid state wires `aria-errormessage` to an element it never announces (axe rule
    `aria-valid-attr-value`, impact critical), so a field error never reaches assistive technology. It
    affects every schedule page, is recorded in `deferred-work.md`, and is already carried as the standing
    red in `features/sch11/uc-sch11-001-report-costs/accessibility.feature` (that UC's BUG-1).
  - **Why a Coverage gap here, when Schedule 11 files it as a Bug.** The defect is owned by
    `deferred-work.md` → *"Deferred (cross-cutting): validation errors are never announced to assistive
    technology (app-wide WCAG 4.1.2)"*, whose original note specifically asked for a deliberately-RED check
    **on Schedule 11**. Every other page records the item as a Coverage gap pointing at that section instead
    — logged so far as `sch2` GAP-4 and now this. Do not re-litigate it per page.
  - **What is genuinely NOT covered:** Schedule 4's validation-error state is **unswept**. A
    Schedule-4-specific accessibility problem in that state would not be caught today. This entry is the
    record of that, not a waiver.
  - **Does it block the AC?** No. Story 10.7 AC2 is "zero violations **or** triaged exceptions" — the
    `deferred-work.md` entry is that disposition, and Schedule 4's other seven swept renders are clean (the
    two that are not are Bug #1/#2, filed with measurements).
  - **Future action:** per that `deferred-work.md` entry — once the app-wide announcement fix lands, QA sweeps
    the validation-error state on every page that skipped it, including this one, and closes this gap.
  - **Status:** OPEN.
  - **Test:** none today, by filing convention rather than by oversight.

**Spec gaps (the Gherkin is missing scenarios its own docs list):**

- **#1 — No scenario was ever written for editing a sub-page row in place, though the source documents list the control.**
  - **What's missing:** the technical sidecar's Control Reference documents the sub-page rows table as having
    **editable per-row Description / Distance / Volume / Cost cells** with its own Save button
    (`UC-SCH4-001-technical.md` Control Reference, `schedule4TowingTotal.xhtml:91-164`), and the sub-page
    Validation Rules row covers those cells' bounds. None of the 31 slices exercises them: S11 only deletes a
    row, and S03–S06 only add one.
  - **The app is correct:** it implements the in-place edit (`SubPage.tsx` `putRow` → `PUT
    /v1/schedule4/locations/{id}/rows/{rowId}`) and validates the edited cells on Save with the same bounds as
    the add-row form. We covered it anyway. A paperwork mismatch, not a bug.
  - **Future action:** a BA regenerates the UC-SCH4-001 slice set to include an "edit a sub-page row" slice
    (happy path + one out-of-range rejection).
  - **Status:** OPEN. Found 2026-08-17.
  - **Test (covers it anyway):** `features/sch4/uc-sch4-001-report-transportation/subpage-rows.feature`
    ("Correct a row in place and save the sub-page" and "An out-of-range in-place edit blocks the sub-page
    save").

- **#2 — Two story acceptance criteria describe count columns in the Existing Locations table that neither the old system nor the app has.**
  - **What's missing:** Story 10.5 AC1 (as built) and Story 10.7 AC1 both describe the Existing Locations list
    as showing a *category count and the three sub-page counts*. The app's list has two columns — Location
    Name and Actions — and the legacy table had one (`schedule4.xhtml:50-104`, column "Location Name"). The
    counts that DO exist are the ones legacy had: they are interpolated into each sub-page link inside the
    open location panel (CNT-001), e.g. "Towing Total (2):", and those are covered.
  - **The app is correct:** it matches legacy. It is the two story ACs that overstate the list's contents.
  - **Future action:** a BA corrects Story 10.5 AC1 / Story 10.7 AC1 to describe the counts where they
    actually live (the panel's sub-page links), so a future reader does not record this as missing coverage.
  - **Status:** OPEN. Found 2026-08-17.
  - **Test (covers what exists):** `features/sch4/uc-sch4-001-report-transportation/update.feature`,
    `subpages.feature`, `subpage-rows.feature` (the `sub-page link "…" shows N rows` assertions).

**Verified — not a defect:**

- **The extract has no Schedule 4 amounts at all, which is a test-data gap and not an app fault.** Every one of
  the 68 locations in the seeded database has an empty category grid and no sub-page rows; at the database,
  `ILCR_COST_REPORT_DETAIL` holds 1316 rows and not one of them carries a Schedule 4 cost item (40–55) against
  289 category-"4" transportation reports. The read model is correct — there is simply nothing to read. Draft
  scenarios therefore create their own data through the app's own endpoints and delete it again; the read-only
  scenarios need the one seed patch (`real-test-data-patches/sch4/view-mode-amounts.sql`), because the Draft
  gate makes it impossible to put amounts on a Submitted/Verified mill-year through the app. (Verified
  2026-08-17.)

- **A location's `$/m³` is computed by the server and is correct in storage.** Divergence #4 is about the panel
  not refreshing after a save — not about the calculation. Read back through the API, every saved location
  carries the right `perUnit` (3,600 ÷ 1,200 = 3; 4,000 ÷ 800 = 5; and 7,200 ÷ 1,200 = 6 after an edit), and
  reopening the location displays it. (Verified 2026-08-17.)

- **A blank data-table cell in these scenarios means "no value", never zero.** Both matter to Check Status: a
  null Cost fails a location, a stored 0 passes it. The step layer maps an empty cell to `null` and asserts
  the zero case separately, so the two can never be conflated. (Verified 2026-08-17.)

- **Opening a sub-page from a saved location genuinely discards unsaved panel edits — that is legacy NAV-002, not data loss.**
  It was worth confirming rather than assuming: after continuing past the prompt, the panel's unsaved Cost
  change is gone and the stored record still holds its previous value. Covered as a passing scenario.
  (Verified 2026-08-17.)

- **The suite is stable by design; the LOCAL DEV STACK is what buckles under a full-worker stress run.**
  Measured over three 5× parallel stress runs of the whole Schedule 4 suite (1,542 executions total):
    - **run 1** (default workers, 24.9 min) — 512/514 passed. One failure was Chrome itself taking >60 s to
      launch (the test never ran); the other was Home's Mill dropdown not visible within 10 s.
    - **run 2** (default workers, 20.4 min, after the fix below) — 512/514 passed. Different tests this time:
      one app shell that never painted within 30 s, and one where Home actually rendered "Unable to load",
      i.e. an API fetch genuinely failed.
    - **run 3** (4 workers, 20.6 min) — 512/514 passed. Again two different tests: the context-suppression
      banner not rendered within 10 s (a scenario that passed 15 consecutive times across the other runs),
      and a Home dropdown click that timed out with the element present but not yet stable.
  **Every single failure was at the ENTRY POINT** (app shell paint, or Home's first fetch), never inside
  Schedule 4's own behaviour, and never the same test twice. Crucially there were **zero data-contention
  failures**: no anchor collision, no shared-state race, no ordering dependency — which is what the
  one-anchor-per-mutating-scenario design exists to guarantee.
  **Two readiness waits were stabilised** (not retried away, and no further timeout inflation): the app-shell
  check in `pages/common/authNav.ts` and Home's first-fetch check in `pages/common/homePage.ts` now get the
  NAVIGATION budget (30 s) instead of the default 10 s `expect` timeout, because both are first-paint /
  first-fetch readiness checks rather than state assertions. Every assertion after them keeps the strict
  default, so a genuine hang still fails fast. Re-running the two affected specs 5× each after that fix:
  249/249 passed.
  **Recommendation for stress runs on a developer box:** pass `--workers=4`. A full-worker `--repeat-each=5`
  run puts ~24 concurrent browsers through one on-demand-compiling Vite dev server and one backend for 20+
  minutes; the resulting entry-point failures say nothing about the app or the tests. (Verified 2026-08-18.)
