# UC-SCH4-001 — accessibility (NFR1 / Story 10.7 AC2: WCAG 2.1 AA, zero violations)
#
# axe-core runs with wcag2a + wcag2aa + wcag21a + wcag21aa (the 2.0 tags alone silently exclude the
# 2.1-only rules). Every Schedule 4 surface is swept: the location list, the New and Edit panels, an
# editable sub-page, the read-only View of all three, and the context-suppressed state.
#
# ONE SCAN PER SCENARIO on purpose. A scenario that scanned several surfaces in sequence would stop at the
# first violation and silently skip the rest — and two of these surfaces ARE red (BUG-1 and BUG-2, below),
# so the scans after them would have been lost. Sub-pages are a URL STATE of the same route, so each scan is
# taken after driving there, which is also how a reporter reaches them.
#
# THE POINTER IS PARKED before every scan (pages/common/axe.ts). axe measures the composited background, so
# a row the mouse happens to rest on is measured hovered — which made the result depend on which control the
# scenario clicked last. The one hover-state assertion here therefore hovers DELIBERATELY.

@UC-SCH4-001 @sch4 @a11y
Feature: Schedule 4 — accessibility

  As a Licensee using assistive technology
  I want every Schedule 4 surface to meet WCAG 2.1 AA
  So that I can report transportation costs without barriers

  @p1
  Scenario: The location list has no WCAG 2.1 AA violations
    Given the Schedule 4 anchor "a11y" is an editable Draft with no locations
    And the Schedule 4 location "E2E A11y Loc" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
      | Truck Barge/Ferry | 50       | 800    | 4000 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    Then the "Schedule 4 location list" view has no WCAG 2.1 AA accessibility violations

  @p1
  Scenario: The New Location panel has no WCAG 2.1 AA violations
    Given the Schedule 4 anchor "validation" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    Then the "Schedule 4 new location panel" view has no WCAG 2.1 AA accessibility violations

  # The editable sub-page adds the add-row form, the sortable headers and the per-row inputs.
  @p1
  Scenario: An editable sub-page has no WCAG 2.1 AA violations
    Given the Schedule 4 anchor "a11y-subpage" is an editable Draft with no locations
    And the Schedule 4 location "E2E A11y Sub" is already saved with only its name
    And that Schedule 4 location already has these "Towing Total" rows:
      | description  | distance | volume | cost |
      | E2E A11y row | 12.5     | 500    | 1500 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E A11y Sub" for edit
    And I open the Schedule 4 "Towing Total" sub-page from the saved location
    Then the "Schedule 4 Towing Total sub-page" view has no WCAG 2.1 AA accessibility violations

  @p1 @S18
  Scenario: The read-only location list has no WCAG 2.1 AA violations
    Given the Schedule 4 read-only anchor "verified"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    Then the "Schedule 4 read-only list" view has no WCAG 2.1 AA accessibility violations

  # The SAME defect as BUG-1 below, in View mode: opening the read-only panel highlights its row, and the
  # only action left enabled on it — "View" (#0f62fe on the #d0e2ff highlight, 3.81:1) — fails 1.4.3. Copy
  # and Delete are disabled here, and axe skips disabled controls, so this state reports one node where the
  # Draft panel reports three. Both flip green together when the highlight colour is fixed.
  #
  # It surfaced only after the pointer-parking fix (pages/common/axe.ts): the scan used to measure the View
  # button while the mouse was still resting on it, and a hovered ghost button gets its own background, which
  # passes. The resting state — what a reporter actually looks at — does not.
  @p1 @S18 @discovered-bug
  Scenario: The read-only location panel keeps its row action accessible [DISCOVERED BUG — the same editing-row highlight; defects.md Bug #1 → BA/QA/Jira]
    Given the Schedule 4 read-only anchor "verified"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E View Location" for viewing
    Then the "Schedule 4 read-only panel" view has no WCAG 2.1 AA accessibility violations

  @p1 @S18
  Scenario: The read-only sub-page has no WCAG 2.1 AA violations
    Given the Schedule 4 read-only anchor "verified"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E View Location" for viewing
    And I open the Schedule 4 "Towing Total" sub-page directly
    Then the "Schedule 4 read-only sub-page" view has no WCAG 2.1 AA accessibility violations

  # The guard states are their own render path (an InlineNotification instead of the page body), so they
  # get their own sweep — a notification-only page is exactly where a missing landmark or heading hides.
  @p2 @S15
  Scenario: The suppressed-context state has no WCAG 2.1 AA violations
    When I open Schedule 4 with no working context
    Then the Schedule 4 mill and reporting year guard message is shown
    And the "Schedule 4 context-suppressed state" view has no WCAG 2.1 AA accessibility violations

  # ---------------------------------------------------------------------------------------------------
  # BUG-1 — DELIBERATELY RED. See this UC's defects.md (Bug/Regression #1 → BA/QA/Jira).
  #
  # Opening a location panel highlights that row in the list — `.schedule-4__row--editing td
  # { background-color: var(--cds-highlight) }` (components/schedule4/index.scss:40), compositing to
  # #d0e2ff — and the highlight drops ALL THREE of that row's ghost action labels below WCAG 1.4.3:
  #   Edit   #0f62fe on #d0e2ff -> 3.81:1  (needs 4.5:1)
  #   Copy   #0f62fe on #d0e2ff -> 3.81:1
  #   Delete #da1e28 on #d0e2ff -> 3.81:1
  # Measured with axe-core 4.12 on 2026-08-17 WITH THE POINTER PARKED, so this is the resting state, not a
  # hover artefact. The same list scans CLEAN with no panel open (the first scenario above passes), so it is
  # specific to the editing-row highlight — the state a reporter is in the whole time they edit a location.
  # ---------------------------------------------------------------------------------------------------
  @p1 @discovered-bug
  Scenario: The open Edit panel keeps its row actions accessible [DISCOVERED BUG — the editing-row highlight fails contrast; defects.md Bug #1 → BA/QA/Jira]
    Given the Schedule 4 anchor "a11y-panel" is an editable Draft with no locations
    And the Schedule 4 location "E2E A11y Panel" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E A11y Panel" for edit
    Then the "Schedule 4 location panel" view has no WCAG 2.1 AA accessibility violations

  # ---------------------------------------------------------------------------------------------------
  # BUG-2 — DELIBERATELY RED, and APP-WIDE rather than Schedule 4's own. defects.md Bug #2 → BA/QA/Jira;
  # also recorded in deferred-work.md as cross-cutting.
  #
  # Hovering ANY data-table row composites Carbon's hover layer (#e0e0e0) behind that row's action labels,
  # and Carbon's own ghost tokens then fail WCAG 1.4.3:
  #   ghost        #0f62fe on #e0e0e0 -> 3.78:1  (needs 4.5:1)
  #   danger-ghost #da1e28 on #e0e0e0 -> 3.78:1
  # Reproduced on the Schedule 4 list AND its sub-page rows (axe-core 4.12, 2026-08-17). It is Carbon's
  # table-hover layer against Carbon's ghost button colour — nothing Schedule 4 styles — so every schedule
  # with row action buttons is affected; it simply went unnoticed because a scan only sees it when the
  # pointer happens to rest on a row. Hovering deliberately here is what makes it reproducible.
  # ---------------------------------------------------------------------------------------------------
  @p2 @discovered-bug
  Scenario: A hovered row keeps its action labels readable [DISCOVERED BUG — app-wide Carbon hover contrast; defects.md Bug #2 → BA/QA/Jira]
    Given the Schedule 4 anchor "a11y-hover" is an editable Draft with no locations
    And the Schedule 4 location "E2E A11y Hover" is already saved with only its name
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I hover the Schedule 4 location "E2E A11y Hover" row
    Then the "Schedule 4 hovered list row" view has no WCAG 2.1 AA accessibility violations in its current pointer state
