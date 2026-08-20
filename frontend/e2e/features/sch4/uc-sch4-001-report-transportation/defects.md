# Defects — UC-SCH4-001 Report Special Log Transportation Costs (Schedule 4)

> How this log works (registers, tags, glossary): [defects-guide.md](../../../defects-guide.md)

All findings below were reproduced against the running local stack (frontend `:3000`, backend `:8080`,
seeded delivery Oracle) on **2026-08-17**, branch `test/schedule-4-e2e`, app commit `9632f7f`.

**Bug / Regression:**

- **BUG-1 - Hovering any table row makes that row's `ghost` controls too faint to read (APP-WIDE).**
  - **What's wrong:** hovering a row in any data table shades the row, and the shading drops that row's
    `ghost` / `danger--ghost` control labels below the WCAG 2.1 AA 4.5:1 minimum. It affects both row-action
    buttons (Edit / Copy / Delete / View) and the in-row "(n):" sub-page navigation links, so the failing state
    sits on the main path through the form, not just in a trailing actions column.
  - **Measured** (rendered pixels, after Carbon's 70ms hover transition settles):

    | pointer state | painted behind the label | `ghost` #0f62fe | `danger--ghost` #da1e28 |
    | --- | --- | --- | --- |
    | parked (resting) | `#ffffff` | 5.00:1 pass | 5.00:1 pass |
    | **anywhere on the row** | **`#e0e0e0`** | **3.79:1 FAIL** | **3.79:1 FAIL** |
    | directly on the control | `#d6d6d6` / `#b81921` | 5.36:1 pass | 6.56:1 pass |

    Carbon's own *button* hover passes because it moves the label with the background. The failure is the
    **row** hover, where the background darkens and the label does not. Ordinary row text is unaffected
    (a location name measures 13.71:1).
  - **App-wide, verified on six pages:** Schedule 1 4.08:1, Schedule 3 4.08:1, Schedule 4 3.79:1,
    Schedule 5 4.08:1, Schedule 8 3.79:1, Schedule 11 4.08:1 - all FAIL. Two bases: `#e0e0e0` where the
    `.schedule-page` hover tint applies (Schedules 2/4/8, codeTables), `#e8e8e8` elsewhere. 14 components
    render the pattern and only `core/RowActionButtons` is shared, so it needs a theme-level fix.
  - **Why (technical):** two app-level causes stack. (1) `context/theme/ThemeProvider.tsx:43` wraps the tree in
    `<CarbonTheme>`, whose `div.cds--g10` re-declares Carbon's stock tokens on a descendant and so shadows the
    bcgov accessible palette applied at `:root` - 154 of 376 `--cds-*` tokens differ, including
    `--cds-link-primary` `#005CB8` to `#0f62fe` and `--cds-button-danger-secondary` `#B32001` to `#da1e28`.
    (2) `styles/index.scss:94-100` tints `.schedule-page ... tr:hover td/th` with `rgba(22,22,22,0.04)`, which
    stacks on Carbon's hover instead of replacing it, darkening it (`#e8e8e8` to `#e0e0e0`) rather than
    softening it as its comment claims. Restoring the shadowed tokens fixes it live - 5.13:1 / 5.29:1 with the
    hover shade untouched. Full analysis and component inventory are in the ticket.
  - **Test note:** the hover step aims at the row's NAME CELL and waits out the transition. Aiming at the row
    centre landed the pointer on a button - a different, passing state - and reported two failing labels
    instead of three.
  - **Ticket:** [bcgov/nr-ilcr#314](https://github.com/bcgov/nr-ilcr/issues/314).
  - **Priority / env:** p2 - local seeded DB - Chrome.
  - **Status:** TRIAGED, ticket raised. Dev to fix when capacity allows; QA re-verifies and closes this entry
    then. The `@discovered-bug` test stays RED until the fix lands, at which point it goes green on its own and
    the tag comes off.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/accessibility.feature`
    ("A hovered row keeps its action labels readable", `@discovered-bug`).

- **BUG-2 - Eight data tables declare an `aria-label` that the `TableContainer` title overrides (dead `aria-label`, APP-WIDE).**
  - **What's wrong, in plain terms:** a data table can carry a *name* that screen readers read out. A Schedule 4
    sub-page's rows table is given **two** - "Towing Total" (from the visible heading) and "Towing Total rows"
    (typed onto the table). The heading wins, so screen readers say "Towing Total", which is the correct name.
    **Nothing is wrong for any user;** the defect is that "Towing Total rows" is dead code that looks as though
    it does something.
  - **Expected vs actual:** expected one name per table with no ignored attribute in the markup; actual two
    names declared, one silently discarded.
  - **NOT a WCAG/accessibility item.** Every affected table has a valid accessible name, no success criterion
    fails, and the axe sweeps pass all of them. It involves ARIA attributes, but both the visible and the
    audible behaviour are already correct, so this is a trivial cleanup rather than an accessibility defect.
  - **Why (technical):** `SubPage.tsx:338` sets an `aria-label` of "<label> rows" on `<Table>`, while
    `<TableContainer title={def.label}>` (line 337) sets `aria-labelledby` on the same element pointing at the
    `<h2>` it renders. `aria-labelledby` takes precedence per the accessible-name spec. Fix: delete line 338's
    `aria-label` - one line, no behaviour change.
  - **App-wide, swept 2026-08-19 - 8 of the app's 18 `<Table>`s carry a dead `aria-label`:**
      - **Two where the dead string has drifted from the live one:** `schedule4/SubPage.tsx:338`
        ("Towing Total" wins, "Towing Total rows" dead) and `schedule8/SamplePage.tsx:336` ("Samples (n)" wins,
        "Samples" dead). In both the name announced is the CORRECT one, so nothing of value is lost - the drift
        just shows the attribute is unmaintained. Schedule 4's case is verified live; Schedule 8's is by code
        inspection (same deterministic precedence rule, that page was not navigated to).
      - **Six where the two strings still match,** so the dead attribute is harmless: `schedule1/index.tsx:645`,
        `schedule3/index.tsx:583`, `schedule4/index.tsx:576`, `schedule5/index.tsx:933`,
        `schedule5SubPage/index.tsx:445`, `schedule8/index.tsx:594`.
      - **Ten tables use `aria-label` CORRECTLY and must be left alone** - their `TableContainer` has no
        `title`, so the `aria-label` is the only name the table has: `codeTables:213`, `schedule1:618`,
        `schedule11:981`, `schedule1OtherCosts:214`, `schedule2:356`, `schedule3:521`, `schedule3SubPage:300`,
        `schedule4:700`, `schedule5:300`, `schedule8/RatesPage:269`.
  - **How to verify - read the attributes, not the screen.** `aria-label` has no visual rendering, so
    "Towing Total rows" appears nowhere in the UI and never would; looking for it on the page is the natural
    way to misread this. On the Towing Total sub-page of a Draft location:

    ```
    aria-label      = "Towing Total rows"                          <- ignored
    aria-labelledby = "tc-_r_1l_-title"  ->  <h2> "Towing Total"   <- wins
    ```

    Decisive by query: `role=table` `name="Towing Total rows"` resolves **0** elements, `name="Towing Total"`
    resolves **1**.
  - **Ticket:** [bcgov/nr-ilcr#321](https://github.com/bcgov/nr-ilcr/issues/321).
  - **Priority / env:** p3 - local seeded DB - Chrome.
  - **Status:** TRIAGED, ticket raised. Dev to delete the eight dead attributes when capacity allows; QA
    re-verifies and closes this entry then. No test: nothing user-facing is broken, and asserting the
    currently-dead name would assert a behaviour nobody guarantees.
  - **Test:** none (the locator note lives in `pages/sch4/schedule4SubPage.ts`).

- **BUG-3 - A hovered button is almost indistinguishable from the row it sits in (APP-WIDE, WCAG 1.4.11).**
  - **What's wrong:** moving onto a row-action button normally gives it a slightly darker background so the
    user can tell which control they are about to click. Inside an already-shaded hovered row that feedback
    effectively disappears.
  - **Expected vs actual:** WCAG 2.1 AA **1.4.11 Non-text Contrast** asks **3:1** between the visual
    information identifying a component or its state and the adjacent colour. Measured **1.10:1** - hovered
    button `#d6d6d6` against hovered row `#e0e0e0` (`#dddddd` on `#e8e8e8`, 1.11:1, on untinted pages).
  - **How we caught it 2026-08-18:** raised by the QA reviewer from direct observation, then measured from the
    rendered pixels.
  - **Why (technical):** the row hover and the button hover each darken by a small amount and stack - white to
    `#e0e0e0` (row) to `#d6d6d6` (button). The *increment* is what identifies the targeted control, and it is
    about 4% of the range.
  - **BUG-1's fix does NOT close this one - measured.** With the bcgov palette un-shadowed it stays at
    **1.10:1** (`#dadadc` on `#e4e4e6`), because what fails is the SIZE of the hover increment, not the colours
    (`--cds-background-hover` is effectively identical in both palettes). Closing it needs a deliberately
    stronger hover treatment, e.g. a border or outline, so it carries its own acceptance criterion in the
    ticket.
  - **Confidence note - applicability needs a specialist's call, unlike BUG-1.** A reviewer could hold that the
    text labels already identify the controls (they pass at 5.36:1 in this state) and that pointer position
    indicates the target, so the hover increment is not *required* information. The measurement is not in doubt;
    the interpretation is. BUG-1 has no such ambiguity.
  - **Ticket:** [bcgov/nr-ilcr#314](https://github.com/bcgov/nr-ilcr/issues/314) - the same ticket as BUG-1
    (same interaction), tracked there as a separate acceptance criterion.
  - **Priority / env:** p3 - local seeded DB - Chrome.
  - **Status:** TRIAGED, ticket raised. Dev to fix when capacity allows; QA re-verifies and closes this entry
    then. No test: no automated rule detects it (axe does not evaluate 1.4.11 for hover states), so this entry
    and the ticket are its only tracking.
  - **Test:** none - see Status.

**Divergences:**

- **DIV-1 — Check Status can still be run after the report has been submitted; the old system did not allow it.**
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
  - **Why (technical):** `components/schedule4/index.tsx:821` gives the button `disabled={saving}`, omitting the
    `!editable` term every other schedule includes. Legacy bound it to `disableReportEdits()`
    (`schedule4.xhtml:43`), which is what `editable` corresponds to in the new app — so the fix is a
    restoration, not a new rule.
  - **SCHEDULE 8 HAS THE SAME DEFECT (swept 2026-08-19, confirmed by QA in the browser).** Exactly 2 of the 9
    schedules are wrong; the other 7 all include `!editable`:
      - **wrong:** Schedule 4 (`schedule4/index.tsx:821`) and Schedule 8 (`schedule8/index.tsx:780`), both
        `disabled={saving}`. Legacy disabled BOTH — `schedule4.xhtml:43` and `schedule8.xhtml:48` each bind
        `disabled="#{scheduleNMB.disableReportEdits()}"`.
      - **correct:** Schedules 1 and 3 via `core/ScheduleActions/index.tsx:44`
        (`!editable || saving || checking`); Schedule 2 (`schedule2/index.tsx:319`) and Schedule 11
        (`schedule11/index.tsx:881`) (`!editable || saving`); Schedule 5 (`schedule5/index.tsx:1084`)
        (`!editable || saving || panelOpen`); Schedules 7A and 7B via `core/SaveCheckActions` with
        `checkDisabled={controlsDisabled}` where `controlsDisabled = !editable || saving`
        (`schedule7a/index.tsx:377`, `schedule7b/index.tsx:395`).
  - **Is it a defect?** Yes — confirmed. Legacy disabled it on both pages and 7 of the 9 schedules in the new
    app already do, so this is two pages having drifted rather than a deliberate product decision.
  - **Ticket:** [bcgov/nr-ilcr#322](https://github.com/bcgov/nr-ilcr/issues/322).
  - **Priority / env:** p2 · local seeded DB · Chrome.
  - **Status:** TRIAGED, ticket raised. Dev to add the `!editable` term on both pages when capacity allows; QA
    re-verifies and closes this entry then. The `@discovered-divergence` test already asserts the CORRECT
    behaviour (the button IS disabled), so it is RED today and goes green on its own when the fix lands, at
    which point its tag comes off. No test change is needed. Schedule 8 has no E2E coverage of its own yet, so
    QA's close-out check there is manual until a Schedule 8 suite exists.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/render-states.feature` (S18,
    `@discovered-divergence`).

- **DIV-2 — Check Status says a value is required but not WHICH figure is missing.**
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
  - **Schedule 4 is the ONLY page that drops the field identity (swept 2026-08-19).** Legacy named the field on
    every schedule: `FacesUtil.addCheckStatusErrorMessage(label, code)` composed `label + ": " + message`
    (`FacesUtil.java:131-139`), and Schedule 4 called it as `"Location : " + reportID + fieldMissing`
    (`Schedule4MB.java:688`). Every other schedule in the new app still names it, by one of two routes:
      - **backend composes the label into the message text** — Schedule 1 (`Schedule1Service.java:594,713`),
        Schedule 2 (`Schedule2Service.java:78,396`), Schedule 3 (`Schedule3Service.java:969`), Schedule 5
        (`Schedule5Controller.java:53-54`), Schedule 11 (`Schedule11Service.java:370`).
      - **backend sends the field separately and the FRONTEND renders it** — Schedule 8
        (`Schedule8Service.java:579` returns `field` + a null message text, exactly Schedule 4's shape, and
        `schedule8/CheckStatusResult.tsx:26,36` renders `title={`Page — ${issue.field}`}`). This is the
        in-repo precedent for the fix, not just a legacy one.
  - **Is it a defect?** Yes — confirmed. The re-grounded notification layout is not the problem; Schedule 8
    proves the same layout can carry the field name. Schedule 4 is alone in discarding what the API sends.
  - **Fix (frontend only):** map `issue.code` to its label and show it, following Schedule 8's component. The
    label map already sits beside the page — `components/schedule4/validation.ts` `ALL_CATEGORIES`
    (`{ code: 40, label: 'Lakeside Dry Dump', … }`). Not blocked by AD-8 ("every line is the API's own text"):
    AD-8 forbids inventing messages the API never sent, whereas here the API sends the identity as a code and
    the label is already the client's display name for that code — which is what Schedule 8 does.
  - **Action:** ticket raised (below). Kept as a genuinely-failing `@discovered-divergence` test. It asserts
    that the category is named *somewhere* in the Check Status output rather than pinning the old JSF sentence,
    because the notification shape itself is not coming back.
  - **Ticket:** [bcgov/nr-ilcr#326](https://github.com/bcgov/nr-ilcr/issues/326).
  - **Priority / env:** p1 · local seeded DB · Chrome.
  - **Status:** TRIAGED, ticket raised. Dev to surface the category label when capacity allows; QA re-verifies
    and closes this entry then. The `@discovered-divergence` test asserts the CORRECT behaviour, so it is RED
    today and goes green on its own when the fix lands, at which point its tag comes off. No test change is
    needed.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/check-status.feature` (S28,
    `@discovered-divergence`).

- **DIV-3 — Unsaved changes to a location are thrown away with no warning.**
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
  - **FOURTH PATH: the SUB-PAGE's Back button (found 2026-08-19 during the app-wide sweep for #324).** Legacy
    attached the same confirm to each sub-page's Back button **unconditionally** — no dirty check, so it fired
    whether or not anything had been typed: `schedule4TowingTotal.xhtml:173-175`, and the same in
    `schedule4TruckRehaul.xhtml` and `schedule4OtherTransportation.xhtml`. `schedule4/SubPage.tsx` has no
    confirm state at all. Verified in the browser 2026-08-19: typed "E2E unsaved text" into the add-row form,
    pressed Back, and the app returned to the location list immediately — 0 dialogs, typed input gone.
  - **Schedule 4 is the ONLY page that does this wrong (swept 2026-08-19).** 16 legacy pages raised
    `confirmNavigationMsg` (`resources/ca/bc/gov/mof/ilcs/common/messages.properties:30`); every one has a
    working counterpart except Schedule 4's five:
      - **correct:** `schedule1/index.tsx:44`; `schedule1OtherCosts` and `schedule3SubPage` via
        `core/EditableSubPageLayout`; `schedule3/index.tsx:44`; `schedule5/index.tsx:63` (panel close, fired
        unconditionally like legacy); `schedule5SubPage/index.tsx:52,130` (Back);
        `schedule8/RatesPage.tsx:90` (Back, dirty-gated).
      - **wrong:** `schedule4.xhtml` + `schedule4ExistingLocation.xhtml` → the panel paths above, and
        `schedule4TowingTotal/TruckRehaul/OtherTransportation.xhtml` → the sub-page Back.
      - **NOT a defect, do not "fix" it:** Schedule 8's main page has an unguarded `closePanel`
        (`schedule8/index.tsx:222`) exactly like Schedule 4's, but legacy `schedule8.xhtml` never raised the
        confirm there — only its Additions-and-Deductions sub-page did, and `RatesPage.tsx` honours that. So
        Schedule 8 matches legacy and must be left alone. `schedule10.xhtml` used the confirm too, but
        Schedule 10 is not implemented.
  - **Is it a defect?** Yes — confirmed. Legacy raised the prompt on all five Schedule 4 paths, Story 10.5's AC
    requires it verbatim, and every other page already implements it. This is behaviour lost in the rebuild.
  - **Ticket:** [bcgov/nr-ilcr#324](https://github.com/bcgov/nr-ilcr/issues/324).
  - **Priority / env:** p1 · local seeded DB · Chrome.
  - **Status:** TRIAGED, ticket raised. Dev to add the confirm on the four navigation paths when capacity
    allows; QA re-verifies and closes this entry then. All three `@discovered-divergence` tests assert the
    CORRECT behaviour (the prompt appears), so they are RED today and go green on their own when the fix lands,
    at which point their tags come off. No test change is needed.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/nav-and-recompute.feature` (S12,
    `@discovered-divergence` ×3 — panel Back, Add New Location, and sub-page Back). A fourth, passing scenario
    asserts the compensating guarantee: whatever is decided about the prompt, a discarded edit is never written
    to the database.

- **DIV-4 — After saving, the cost-per-cubic-metre column still shows the OLD value until you reopen the location.**
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

- **DIV-5 — A rejected duplicate name stays in the field instead of being wiped.**
  - **What's wrong:** nothing is broken. When a save is refused because the location name already exists, the
    old system **discarded what you typed**; this app leaves it there so you can correct it. Recorded so the
    difference is a decision on the record rather than a surprise.
  - **Read this carefully — the restored value is the PANEL's own prior name, not the colliding location's.**
    It is easy to read "reset to the previous name" as "put back the duplicate", which would make the whole
    entry meaningless (the two strings would be the same). It is not that. Traced through the legacy source:
    `locationDescriptionOriginalVal` starts as `""` (`TransportationReportType.java:86,123`) and is only ever
    filled when a STORED location is loaded (`Schedule4DAO.java:142`). So:
      - **New Location panel** (the flow the test drives): original value is `""`, so legacy's
        `setLocationDescription(getLocationDescriptionOriginalVal())` **emptied the field** — the reporter
        retyped the name from scratch. The app keeps "E2E LAKESIDE" so only the case needs fixing.
      - **Edit panel:** the original value IS the location's stored name, so legacy silently reverted the
        attempted rename.
    Legacy also ran this on the field's own change listener (`Schedule4MB.java:236-250`), not just on Save, so
    the field blanked itself as soon as focus left it. Both call sites do the same reset
    (`Schedule4MB.java:249` and `:619`).
  - **Expected vs actual:** Expected (legacy ERR-002) the typed name discarded — blank on a new location,
    reverted on an edit; actual the entered name is kept, with the error shown above.
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

- **DIV-6 — The delete confirmation's punctuation differs by one character.**
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

- **DIV-7 - Schedules 4 and 8 highlight the row being edited; the old system highlighted it nowhere.**
  - **What's wrong:** opening a row for editing (a location in Schedule 4, a report row in Schedule 8) turns
    that row's background blue. The legacy app never highlighted the selected row on any schedule, and no other
    schedule in the new app does either - Schedule 5 has the same list-plus-edit-panel interaction and applies
    no highlight. The highlight is an addition made during the rebuild, affecting these two pages only.
  - **Expected vs actual:** expected no highlight (legacy, and Schedules 1/3/5/11); actual the edited row is
    filled with `var(--cds-highlight)`, composited `#d0e2ff`.
  - **How we caught it (verified on real data 2026-08-17, extended to Schedule 8 on 2026-08-18):** the
    accessibility sweep of the open Edit panel failed on contrast, which drew attention to the highlight;
    checking the legacy source then showed the highlight itself has no basis.
  - **Legacy evidence:** no `rowStyleClass` anywhere in the legacy app, no `selected` / `ui-state-highlight`
    CSS rule, and legacy Schedules 4, 5 and 8 all render their lists with the identical plain
    `<p:dataTable styleClass="center">`. Legacy also showed the list and the edit panel together on one page
    (`schedule4.xhtml:167` includes `schedule4ExistingLocation.xhtml`), so it had exactly this interaction and
    still applied no highlight.
  - **Why (technical):** `components/schedule4/index.scss:40` and `components/schedule8/index.scss:34`, each
    setting `background-color: var(--cds-highlight)` on the editing row. Schedule 8's Sample page reuses the
    same class (`schedule8/SamplePage.tsx:356`).
  - **Is it a defect?** Yes - a divergence to remove, not a re-grounding to accept. Removing the highlight also
    clears the contrast failure it caused: the row's own Edit / Copy / Delete labels measured **3.81:1** against
    the 4.5:1 minimum on both Schedule 4 and Schedule 8, with the pointer parked, so it was a resting state that
    persisted for as long as the record stayed open. No separate contrast fix is needed once the highlight goes.
  - **Ticket:** [bcgov/nr-ilcr#319](https://github.com/bcgov/nr-ilcr/issues/319).
  - **Priority / env:** p1 - local seeded DB - Chrome.
  - **Status:** TRIAGED, ticket raised. Dev to remove the highlight when capacity allows; QA re-verifies and
    closes this entry then. Two `@discovered-divergence` accessibility tests are RED because of this highlight and go
    green together once it is removed, at which point their tags come off.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/accessibility.feature`
    ("The open Edit panel keeps its row actions accessible" and "The read-only location panel keeps its row
    action accessible", both `@discovered-divergence`).

**Coverage gaps (not tested yet  — no app problem):**

- **GAP-1 — There is no role-dependent Schedule 4 behaviour reachable from a browser.**
  - **Why not:** whether the page is editable is decided server-side from the caller's `EDIT_SCHEDULE`
    permission AND the Draft status, and the E2E environment's mock auth stamps a single authority per
    process — so "a user without the Schedules permission is denied" cannot be produced from a browser.
    Separately, `SchedulePermissions.ROLE_ACTIONS` still grants `ILCR_ADMIN` and `ILCR_SUBMITTER` the **same**
    two actions (`VIEW_SCHEDULE`, `EDIT_SCHEDULE`), so on this screen there is no role branch to test even in
    principle yet.
  - **Already covered where it belongs:** server-side enforcement **is** present and is covered by the
    backend's own `Schedule4WriteAuthorizationIT`.
  - **The DRAFT half of the same gate IS covered end-to-end** — S18 proves the read-only render on both
    non-Draft codes (Submitted and Verified), on both the location panel and a sub-page.
  - **This is the cross-cutting deferral, not a Schedule 4 finding.** It is owned by `deferred-work.md`
    → *"Deferred (cross-cutting): role-gated behaviour cannot be E2E-tested under single-role mock auth
    (2026-08-12)"*, which lists this entry alongside `sch1` GAP-1, `sch2` GAP-1, `sch11` GAP-6 and `sec`
    GAP-4. Do not re-litigate it per page. Note the role work is still in progress overall — FAM sign-in and
    the role switcher landed in upstream `2754399` (Stories 1.2/1.3), but the two `ROLE_ACTIONS` sets have not
    yet diverged — so it is too early to call the difference from legacy a divergence.
  - **When it is done** (per that entry): QA authors E2E tests for these coverage gaps and runs them against
    the running app, once the role-specific behaviours are actually implemented — then this `Status:` moves.
    The legacy target behaviour QA will be covering is recorded in that cross-cutting entry.
  - **Status:** OPEN — `blocked` in coverage.md. A gate should treat this as **waived**, not failing.
  - **Test:** none today, by environment limitation rather than by choice.

- **GAP-2 — ERR-003, the generic "something went wrong while saving" message, is not exercised.**
  - **Why not:** the message text is whatever the service layer's exception resolves to, which the source
    documents themselves record as `[UNKNOWN]`. Forcing a real server failure from a browser test needs the
    request to be intercepted and failed at the browser edge (the technique Schedule 2 uses for its own
    save-error slice). Schedule 4 has no source slice for it, so no scenario was invented.
  - **Future action:** add one interception-based scenario if BA/QA want the failure path covered, mirroring
    `features/sch2/uc-sch2-001-report-costs/save-error.feature`.
  - **Status:** OPEN.
  - **Test:** none — tracked as a `deferred` row in `coverage.md`.

- **GAP-3 — The "changed by another user" conflict (a stale save token) is not exercised.**
  - **Why not:** the app refreshes its optimistic-lock token from every save response, so producing a stale
    one needs two concurrent sessions on the same location — outside what a single-browser scenario can do
    honestly. The API-level behaviour was verified by hand during authoring (a stale token returns HTTP 409
    with *"This schedule was changed by another user. Please reload and try again."*), and the message is
    pinned in the fixture so a UI scenario can be added cheaply later.
  - **Future action:** cover with two browser contexts if BA/QA want the conflict path end-to-end.
  - **Status:** OPEN.
  - **Test:** none — tracked as a `deferred` row in `coverage.md`.

- **GAP-4 — The validation-error state is not swept by axe here, deliberately.**
  - **What's missing:** Schedule 4's accessibility sweeps cover nine renders (the location list, the New
    panel, an editable sub-page, the read-only list/panel/sub-page, the context-suppressed state, and the two
    contrast states filed as DIV-7 and BUG-1) but omit the validation-error state.
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
    two that are not are DIV-7 and BUG-1, filed with measurements).
  - **Future action:** per that `deferred-work.md` entry — once the app-wide announcement fix lands, QA sweeps
    the validation-error state on every page that skipped it, including this one, and closes this gap.
  - **Status:** OPEN.
  - **Test:** none today, by filing convention rather than by oversight.

**Spec gaps (the Gherkin and its source docs disagree with reality — missing scenarios, or scenarios whose premise never existed):**

- **SPEC-1 — No scenario was ever written for editing a sub-page row in place, though the source documents list the control.**
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

- **SPEC-2 — Two story acceptance criteria describe count columns in the Existing Locations table that neither the old system nor the app has.**
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

- **SPEC-3 — Source scenario S29 requires a missing-Distance check that no version of the system ever had.**
  - **What's missing / wrong in the spec:** S29 expects Check Status to report a missing Distance
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
  - **Why this is a SPEC GAP, not a divergence (reclassified 2026-08-20).** It was first filed as DIV-6
    because the register is "app vs spec" (`defects-guide.md:45`). But once the legacy sweep below showed the
    rule never existed anywhere, the only thing wrong is the SPEC: the app matches legacy, and no dev work is
    implied. Note this is the INVERSE of the register's summary line ("the Gherkin is missing scenarios its
    own docs list") — here the Gherkin CARRIES a scenario whose premise is false. Same owner and same fix
    (a BA corrects the source), so it belongs here rather than in the divergence register.
  - **NO legacy schedule has a live distance check — swept 2026-08-19.** Of the 13 legacy `*CheckStatus`
    services, Schedule 4's is the only one that mentions distance at all, and it is disabled twice over:
      - the only code that ever sets the flag sits inside a `/* … */` block marked *"Commented code section
        must be deleted once code stabilized"* (`Schedule4CheckStatus.java:88-94`), so
        `missingDistanceOnCheck` is never true;
      - `Schedule4MB.java:456` still READS that flag, so legacy carries a dead branch that could never fire;
      - the helper whose name suggests otherwise, `CheckStatusUtil.checkRequiredDistanceCycleTimeType`
        (called live at `Schedule4CheckStatus.java:97,110,129`), validates **volume and cost only** — never
        distance (`CheckStatusUtil.java:141-155`), and the volume flag it sets is itself only read inside
        another commented-out block (`:101-104`).
    So "legacy did not enforce Distance" is not an inference from one commented line; it holds across the
    whole legacy app.
  - **The app is correct:** it matches legacy and the delivery-confirmed Story 10.4 §Decision 2. Raised so
    the S29 scenario is not read later as missing coverage.
  - **Future action:** a BA corrects S29 in the UC-SCH4-001 slice set — either delete it or restate it as
    the app's (and legacy's) actual rule, Cost-only. A Jira ticket is needed ONLY if the ministry actually
    wants a distance check, which would be new behaviour rather than a fix.
  - **Status:** OPEN. Found 2026-08-17, reclassified from DIV-6 on 2026-08-20.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/check-status.feature` (S29, green).

- **SPEC-4 — Source scenario S30 requires a missing-Comments check that Schedule 4 never had (same shape as SPEC-3).**
  - **What's missing / wrong in the spec:** S30 expects Check Status to report a location with no
    Comments as "Value Required". It does not, and neither did legacy Schedule 4 — its comments check is
    commented out inline: `Schedule4CheckStatus.java:22` reads
    `if (transportation.isMissingLocationDescriptionOnCheck() ) //|| transportation.isMissingCommentsOnCheck())`.
  - **Expected vs actual:** Expected (per S30) blank Comments to be flagged; actual Comments never affect the
    outcome.
  - **How we caught it (verified on real data 2026-08-17):** a location with complete Costs and an empty
    Comments box (confirmed empty through the UI) passes, and the whole-schedule banner appears.
  - **Why (technical):** Story 10.4 §Decision 3 ("Comments soft — no comments-required key exists; never
    blocks MET").
  - **Why this is a SPEC GAP, not a divergence (reclassified 2026-08-20).** Same reasoning as SPEC-3: the
    app matches legacy Schedule 4, so the only thing wrong is the source scenario, and the fix is a BA's.
  - **BUT legacy DID enforce Comments on ANOTHER page — swept 2026-08-19.** Unlike Distance (which no legacy
    schedule checked), Comments were a real check on **Schedule 7B**: when a culvert's type is "O" (Other),
    `Schedule7bCheckStatus.java:16` sets `missingCommentsOnCheck` from a blank Comments field, `:32` counts it
    against the schedule, and `Schedule7bMB.java:137` emits
    `"Culvert Report Id : <id> - Culvert Type Others - Comments: Value Required"`. So the concept existed in
    legacy — conditionally, on one page — which is the likeliest origin of S30's premise. It was never a
    Schedule 4 rule.
    Note also that the "no comments-required key" wording above means no DEDICATED key: 7B used the shared
    `missingRequiredFieldMsg` with a composed label, which is how legacy named every field (see DIV-2). So a
    soft prompt here would need a product decision about the RULE, not a new message key.
  - **The app is correct:** it matches legacy Schedule 4 and the delivery-confirmed Story 10.4 §Decision 3.
    If a soft advisory message is wanted, it needs a product decision; it must not be invented by the suite.
  - **Future action:** a BA corrects S30 in the UC-SCH4-001 slice set — delete it, or restate it as the
    conditional Schedule 7B rule it appears to have come from. A Jira ticket only if the ministry wants a
    Schedule 4 prompt, which would be new behaviour.
  - **Status:** OPEN. Found 2026-08-17, reclassified from DIV-7 on 2026-08-20.
  - **Test:** `features/sch4/uc-sch4-001-report-transportation/check-status.feature` (S30, green).

**Verified — not a defect:**

- **VER-1 — The extract has no Schedule 4 amounts at all, which is a test-data gap and not an app fault.** Every one of
  the 68 locations in the seeded database has an empty category grid and no sub-page rows; at the database,
  `ILCR_COST_REPORT_DETAIL` holds 1316 rows and not one of them carries a Schedule 4 cost item (40–55) against
  289 category-"4" transportation reports. The read model is correct — there is simply nothing to read. Draft
  scenarios therefore create their own data through the app's own endpoints and delete it again; the read-only
  scenarios need the one seed patch (`real-test-data-patches/sch4/view-mode-amounts.sql`), because the Draft
  gate makes it impossible to put amounts on a Submitted/Verified mill-year through the app. (Verified
  2026-08-17.)

- **VER-2 — A location's `$/m³` is computed by the server and is correct in storage.** DIV-4 is about the panel
  not refreshing after a save — not about the calculation. Read back through the API, every saved location
  carries the right `perUnit` (3,600 ÷ 1,200 = 3; 4,000 ÷ 800 = 5; and 7,200 ÷ 1,200 = 6 after an edit), and
  reopening the location displays it. (Verified 2026-08-17.)

- **VER-3 — A blank data-table cell in these scenarios means "no value", never zero.** Both matter to Check Status: a
  null Cost fails a location, a stored 0 passes it. The step layer maps an empty cell to `null` and asserts
  the zero case separately, so the two can never be conflated. (Verified 2026-08-17.)

- **VER-4 — Opening a sub-page from a saved location genuinely discards unsaved panel edits — that is legacy NAV-002, not data loss.**
  It was worth confirming rather than assuming: after continuing past the prompt, the panel's unsaved Cost
  change is gone and the stored record still holds its previous value. Covered as a passing scenario.
  (Verified 2026-08-17.)

- **VER-5 — The suite is stable by design; the LOCAL DEV STACK is what buckles under a full-worker stress run.**
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
