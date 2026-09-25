# UC-SCH6-001 — accessibility (NFR1: WCAG 2.1 AA, zero violations). Board item #101.
#
# WHY THIS FILE EXISTS SEPARATELY FROM THE 23 SLICES, and why it was in scope from the first commit of
# this story rather than bolted on at the end: accessibility is an NFR, so NO slice in the catalogue
# asks for it. Story 28.4 reached "all 25 slices authored" on Schedule 5 with zero accessibility
# coverage precisely because the slice count read as completeness — recorded there as GAP-5, whose
# lesson was "a slice catalogue is not a completeness test". This file is the carry of that lesson.
#
# axe-core runs with wcag2a + wcag2aa + **wcag21a + wcag21aa** (the 2.0 tags alone silently exclude the
# 2.1-only rules), via the shared gate in `pages/common/axe.ts`.
#
# ONE SCAN PER SCENARIO, on purpose. A scenario that swept several surfaces in sequence would stop at
# the first violation and silently lose the rest — and one of these surfaces IS red (the
# validation-error state, below), so the scans after it would have been lost.
#
# THE POINTER IS PARKED before every scan (`pages/common/axe.ts`). axe measures the composited
# background, so a control the mouse happens to rest on is measured HOVERED, which made results depend
# on whatever the scenario clicked last. No sweep here deliberately hovers, so all of them use the
# parking step.
#
# ---------------------------------------------------------------------------------------------------
# WHICH SURFACES, AND WHY THESE
#
# Schedule 6's distinct render paths, each swept once:
#   1. the empty Draft schedule — the record-list placeholder, the totals strip, the general comment;
#   2. the Add panel open and blank — six entry controls including two filterable comboboxes;
#   3. the Add panel showing VALIDATION ERRORS — the inline `invalidText` state (the red one);
#   4. a saved record's row expanded — the row editor, its derived cells and its Delete button;
#   5. a Check Status verdict with FINDINGS on screen — the notification list;
#   6. the read-only (non-Draft) render — every control disabled, records still shown;
#   7. the context-suppressed state — a notification INSTEAD of the page body.
#
# ONE GUARD SWEEP COVERS ALL THREE GUARDS. S06/S07/S08 render through the SAME shared component
# (`components/core/ScheduleLoadState`) and differ only in their message text and title, so sweeping
# one exercises the whole render path; three sweeps would re-scan identical structure. The
# context-suppressed one is chosen because it needs no anchor at all.
#
# MOST SWEEPS NEED NO ANCHOR OF THEIR OWN. Scenarios 1-3 ride the shared VALIDATE-ONLY cell
# (22050/2024), whose contract is that nothing is ever written there — three more pure readers cannot
# collide with each other or with S05/S12-S16. Scenario 6 reads S17's seeded read-only cell, which S17
# also only reads. Scenario 7 needs none. Only 4 and 5 put a SAVED RECORD on screen, so only those two
# have cells of their own (10050/2025, 12050/2025 — 2024's ACT mills are exhausted; see the fixture).
# ---------------------------------------------------------------------------------------------------

@sch6 @UC-SCH6-001 @a11y
Feature: Report Road Management Costs (Schedule 6) — accessibility
  As a mill reporter using assistive technology
  I want every Schedule 6 surface to meet WCAG 2.1 AA
  So that I can report road maintenance costs without barriers

  @p1
  Scenario: The empty Draft schedule has no WCAG 2.1 AA violations
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    Then the Schedule 6 record list shows no records
    And the "Schedule 6 empty Draft schedule" view has no WCAG 2.1 AA accessibility violations

  # The Add panel is a labelled region containing two filterable CodeComboBoxes, three text inputs and a
  # character-counted TextArea — the densest control cluster on the page, and the one a reporter spends
  # the most time in.
  @p1
  Scenario: The blank Add panel has no WCAG 2.1 AA violations
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    Then the Add panel is shown with its fields blank
    And the "Schedule 6 Add panel" view has no WCAG 2.1 AA accessibility violations

  # -------------------------------------------------------------------------------------------------
  # DELIBERATELY RED — and NOT Schedule 6's own defect.
  #
  # `aria-valid-attr-value` (impact: CRITICAL). Carbon's `TextInput` invalid state renders
  # `aria-errormessage` pointing at an element it never announces, so a validation error never reaches
  # assistive technology: a screen-reader user presses "Add Report", nothing is announced, and the form
  # simply does not submit. It is a `@carbon/react` wiring issue present in EVERY schedule page's
  # validation-error state — already found, triaged and recorded as BUG-1 in
  # `features/sch11/uc-sch11-001-report-costs/defects.md`, and listed in `KNOWN_A11Y_RULES`
  # (`pages/common/axe.ts`) so a known red logs one line instead of a full dump.
  #
  # IT IS LEFT FAILING ON PURPOSE. The failing state IS the tracking signal, and Playwright isolates
  # tests so an honest red costs nothing else. `npm run test:gate` filters it out
  # (`--grep-invert "@discovered-bug|@discovered-divergence"`), so it does not block the gate while it
  # is open. It goes green on its own the moment the app-wide fix lands, at which point the
  # `@discovered-bug` tag comes off and the rule id leaves `KNOWN_A11Y_RULES`.
  #
  # NOTE the quiet logging is scoped BY RULE ID, not by the tag: a DIFFERENT violation appearing in this
  # same state still gets the full triage dump, so this scenario cannot quietly absorb a fresh defect.
  #
  # The state is reached exactly as S13 reaches it — a non-numeric volume with a valid cost, submitted —
  # so it writes nothing and rides the validate-only cell.
  # -------------------------------------------------------------------------------------------------
  @p1 @discovered-bug
  Scenario: The Add panel's validation errors reach assistive technology [DISCOVERED BUG — app-wide Carbon aria-errormessage wiring; sch11 defects.md BUG-1]
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I enter the volume "abc" with a valid cost
    And I submit the Add panel
    Then I should see the error "Entered volume entry is invalid."
    And the "Schedule 6 Add panel validation errors" view has no WCAG 2.1 AA accessibility violations

  # The row editor is the same `RoadRecordFields` component as the Add panel, but inside a Carbon
  # AccordionItem and with its derived RMG / $ per m³ cells populated and a danger-tertiary Delete
  # button — enough structural difference to be worth its own scan.
  @p1
  Scenario: A saved record's expanded row has no WCAG 2.1 AA violations
    Given the Schedule 6 anchor "a11y-row" is an editable Draft with no road records
    And a road maintenance record commented "E2E a11y row record" already exists on that anchor
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I expand the saved record
    Then the "Schedule 6 expanded record row" view has no WCAG 2.1 AA accessibility violations

  # A verdict with FINDINGS rather than the met banner: the findings are the richer surface (one
  # notification per issue, each carrying a severity word) and the state a reporter has to act on.
  @p1
  Scenario: A Check Status verdict with findings has no WCAG 2.1 AA violations
    Given the Schedule 6 anchor "a11y-check" is an editable Draft with no road records
    And a road maintenance record with no cost commented "E2E a11y check record" already exists on that anchor
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I run Schedule 6 Check Status
    Then Check Status reports "Road : 1 - TSA or TFL (Cost $) : Value Required"
    And the "Schedule 6 Check Status findings" view has no WCAG 2.1 AA accessibility violations

  # -------------------------------------------------------------------------------------------------
  # RED, AND THE ONE FINDING THIS WHOLE FILE TURNED UP. See defects.md BUG-2 for the full write-up.
  #
  # axe reports `color-contrast` [serious] on ONE node — the General Comments textarea's helper text,
  # `#text-area-helper-text-id-*`, which reads "372 characters remaining". On the read-only page that
  # textarea is DISABLED, and Carbon renders a disabled field's helper text at `rgba(22, 22, 22, 0.25)`:
  # composited over the white content background that is effectively rgb(197, 197, 197), which measures
  # **1.73:1** against 4.5:1. (Measured directly from the rendered pixels 2026-09-18, not from axe
  # alone; the same text ENABLED uses #525252 and passes at 7.81:1.)
  #
  # WHY THIS IS PROBABLY NOT A WCAG VIOLATION, and why the scenario is still red rather than quietly
  # excluded. WCAG 1.4.3 has an explicit exception: text that is "part of an inactive user interface
  # component" has NO contrast requirement. This textarea is genuinely disabled, so its own helper text
  # is exempt on the letter of the standard — axe flags it only because the helper text is a sibling
  # <div> rather than the disabled control itself, so the rule cannot tell it belongs to an inactive
  # component. That is a known limitation of the rule, not a finding about this page.
  #
  # BUT IT IS NOT MINE TO DISMISS, for two reasons. First, a WCAG interpretation is BA/QA's call, not
  # the test author's. Second, the finding points at something real even if the contrast is exempt: the
  # page renders a LIVE CHARACTER COUNTER on a field nobody can type into. Not showing the counter when
  # the field is disabled would be the better behaviour AND would clear the axe finding as a side
  # effect. So the red stays as the tracking signal until BA/QA choose between that fix and a recorded
  # node exclusion.
  #
  # `@discovered-bug` keeps it out of `npm run test:gate`
  # (`--grep-invert "@discovered-bug|@discovered-divergence"`), so it does not block the gate. The rule
  # id is deliberately NOT added to `KNOWN_A11Y_RULES`: `color-contrast` carries GENUINE reds elsewhere
  # in this suite (sec BUG-1's authored welcome message, sch4's row-hover BUG-1), and silencing it by
  # rule id would hide those. The logging noise is the correct trade.
  #
  # THE REST OF THIS SURFACE IS CLEAN, which is what makes the finding precise rather than a blanket
  # red: every control disabled, the Add panel absent, both records expandable, the totals strip and the
  # comment all scanned, and only this one node reported.
  # -------------------------------------------------------------------------------------------------
  @p1 @S17 @discovered-bug
  Scenario: The read-only schedule has no WCAG 2.1 AA violations [DISCOVERED BUG — a live character counter on a disabled field, at 1.73:1; defects.md BUG-2]
    Given the Schedule 6 report for that mill and year is not in Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    Then every Schedule 6 entry control is disabled
    And the "Schedule 6 read-only schedule" view has no WCAG 2.1 AA accessibility violations

  # The guard states replace the page BODY with a notification, which is exactly where a missing
  # landmark or heading hides. One sweep covers all three guards — they share
  # `components/core/ScheduleLoadState` and differ only in message text and title.
  @p2 @S06
  Scenario: The context-suppressed state has no WCAG 2.1 AA violations
    When I open Schedule 6 with no working context
    Then the Schedule 6 mill and reporting year guard is shown
    And the "Schedule 6 context-suppressed state" view has no WCAG 2.1 AA accessibility violations
