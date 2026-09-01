# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SEC-001/gherkin/UC-SEC-001-S01..S08.feature
# (legacy JSF home.xhtml + UserSessionMB). RE-GROUNDING NOTES (see defects.md):
#  - ROLE: the legacy Gherkin authenticates as "ILCR_LICENSEE"; the new app has no such role (ratified
#    two-group model ILCR_ADMIN + ILCR_SUBMITTER, PRD DL-23) and runs security-off with a fixed mock
#    authority — so "authenticated" is just opening the app. Same rename already logged under UC-SCH1-001.
#  - ROLE-SPECIFIC NOTICE: legacy S01 asserts a role-specific notice on Home (BR-07). The new Home
#    (components/home/index.tsx) renders no such section — dropped in the rebuild (logged as a Divergence).
#  - SAVE = READ/RESOLVE: legacy Save persisted session state; the new Save is GET /v1/mill-context and
#    writes nothing. The observable outcome is SUC-001 + the working-context banner (ContextBanner.tsx),
#    the modern #subMenu. Nothing to clean up; scenarios are parallel-safe.
#  - VALIDATION is backend-authoritative (the app posts empty params, the server returns the 400). The
#    empty-dropdown slices S04/S05/S08 are NOT UI-reproducible on current data because the mount default
#    (13050/2017) is present in both lists, so both dropdowns pre-select and Carbon has no clear control
#    — covered at the contract level instead (see coverage.md / defects.md). S02 (single-mill pre-select)
#    is not-applicable on the 21-mill delivery data.

@sec @UC-SEC-001
Feature: Establish Working Context (Home) — select a mill and reporting year
  As a mill reporter
  I want to select a mill and an opened reporting year and save them
  So that the system holds this pair as my working context for every schedule and report page

  Background:
    Given I am on the Home page

  @S01 @landing @p1
  Scenario: Landing populates the lists and pre-selects the default context
    Then the mill and reporting-year option lists are populated
    And the working context is pre-selected on landing

  @S01 @SUC-001 @p0
  Scenario: Select a mill and an opened reporting year and save successfully
    When I select the working context "open with status"
    And I save the working context
    Then I should see the message "Data saved successfully"
    And the working-context banner shows the "open with status" context
    And the working-context banner no longer shows the "default" context

  # ---------------------------------------------------------------------------------------------------
  # The two Home axe sweeps below used to be the LAST STEP of the two journey scenarios above. They were
  # split out 2026-08-24 because they are red for a reason those journeys have nothing to do with, and a
  # scan failing at the end of a scenario takes the whole scenario down with it: `@p0` "select a mill and
  # save" — the core working-context journey — was failing on a colour, and `npm run test:gate` was red
  # because neither scenario carried a `@discovered-*` tag to exclude it.
  #
  # Split, the journeys stay in the gate and green, and the contrast stays tracked instead of skipped.
  # Nothing was deleted: the same two scans run against the same two states.
  #
  # WHY TWO SCANS AND NOT ONE: a page can be accessible in one state and not another, so Home is swept
  # both before any context is selected and after a Save has populated the banner. Today both report the
  # IDENTICAL two nodes, because the offending markup sits below the banner in both.
  #
  # READ defects.md BUG-1 BEFORE "FIXING" EITHER: the failing nodes are ADMIN-AUTHORED CONTENT (the
  # welcome message stored in THE.ILCR_ROLE.MESSAGE_TEXT), not app CSS. That has a consequence for how a
  # green here should be read — editing the welcome message also turns these green, without anything
  # being fixed. Only a contrast constraint on authored content settles it.
  # ---------------------------------------------------------------------------------------------------
  @S01 @landing @a11y @p1 @discovered-bug
  Scenario: The Home landing view is accessible [DISCOVERED BUG — authored-content contrast; defects.md BUG-1]
    Then the "Home (landing)" view has no WCAG 2.1 AA accessibility violations

  @S01 @SUC-001 @a11y @p1 @discovered-bug
  Scenario: Home with a populated banner is accessible [DISCOVERED BUG — authored-content contrast; defects.md BUG-1]
    When I select the working context "open with status"
    And I save the working context
    Then the working-context banner shows the "open with status" context
    And the "Home (banner populated after Save)" view has no WCAG 2.1 AA accessibility violations

  @S03 @p1
  Scenario: Change the working context later and re-save — the banner replaces
    When I select the working context "open with status"
    And I save the working context
    Then the working-context banner shows the "open with status" context
    When I select the working context "open alternate"
    And I save the working context
    Then I should see the message "Data saved successfully"
    And the working-context banner shows the "open alternate" context
    And the working-context banner no longer shows the "open with status" context

  @S06 @p1
  Scenario: A closed mill still saves and banners like an open mill (Home screen boundary)
    When I select the working context "closed"
    And I save the working context
    Then I should see the message "Data saved successfully"
    And the working-context banner shows the "closed" context

  @S07 @p1
  Scenario: A mill/year with no report-status row saves; banner shows the mill line only
    When I select the working context "no status"
    And I save the working context
    Then I should see the message "Data saved successfully"
    And the working-context banner shows only the mill line for the "no status" context
