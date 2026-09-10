# UC-SCH5-001-S16 / S17 / S18 — the three EF2 guards, and S19 — the read-only "View" mode.
#
# RE-GROUNDING NOTE — what changed between the source Gherkin and the running app, and why.
#
#   * ALL FOUR SLICES NAVIGATE TO `/ILCR/schedule5.xhtml` IN THE SOURCE. That page no longer exists;
#     the route is `/schedule-5`, reached through Home + the side-nav so the working context saved on
#     Home survives the navigation. S16 is the exception and loads the route DIRECTLY, because its
#     whole fixture is the absence of that context.
#
#   * "THE BUSINESS-EXCEPTION PANEL" IS GONE. S16/S17/S18 each expect their message in a PrimeFaces
#     `p:messages` panel driven by an `isScheduleNotFound()`-style flag. The rewrite has no such
#     panel: S17 and S18 render the API's OWN `detail` inside a Carbon notification titled "Unable to
#     load Schedule 5" (index.tsx:1151-1161), and S16 never issues a request at all — it is a
#     client-side guard on the missing context (index.tsx:1130-1141). The MESSAGES survive verbatim;
#     only the mechanism moved. Both server details were confirmed against the running app
#     (2026-09-10): 25051/2017 -> 409 and 16050/2022 -> 404, each carrying the exact ERR-004 / ERR-005
#     string the source Gherkin predicts.
#
#   * S19 AND DEVIATION (B). The source expects the row's "Delete" and "Copy" to be rendered and
#     DISABLED alongside a "View". The rewrite drops them from the DOM entirely and leaves View alone
#     (index.tsx:1193-1208, which cites the epics AC and Schedule 6's precedent). Legacy rendered a
#     permanently-disabled Delete; net user-reachable behaviour is identical, so this file asserts the
#     ABSENCE of every write action rather than a disabled state. Same call sch4 made on its own
#     read-only arm.
#
#   * S19'S READ-ONLY MECHANISM IS NOT SCHEDULE 4'S, and the difference is asserted rather than
#     assumed. Schedule 4 renders its view-mode values as text throughout. Schedule 5 keeps the
#     descriptors as Carbon `TextInput`s and sets `readOnly` on them (index.tsx:438-478) — the value
#     is still in `inputValue()` — while `Isolated Camp`, being a `Select`, is `disabled` instead
#     (:479-483), and only the CATEGORY GRID collapses to bare cells (`AmountCell`, :202-203). So the
#     grid assertion ("no inputs") and the descriptor assertion ("readonly attribute") are genuinely
#     different checks, not two spellings of one.
#
# THE READ-ONLY ANCHOR CARRIES A SEEDED CAMP, and it has to. 16050/2023 is the suite's only Submitted
# Schedule 5 document, and every write to a non-Draft document is refused with HTTP 409 — which IS the
# condition S19 proves. So the camp cannot be created by the scenario; it is supplied by
# `real-test-data-patches/sch5/view-mode-camp.sql` (that file explains the rest) and folded into the CI
# seed in the same change. Its amounts are S01's, so the figures asserted below are ones the suite
# already proves the server derives on the WRITE path — S19 reads the same arithmetic back off the READ
# path. Nothing here writes: preflight re-verifies the seeded camp so a drifted patch fails fast
# instead of surfacing as an opaque panel mismatch.
#
# NO RECOVERY SCENARIOS ARE SCRIPTED, deliberately and per the source's own notes: correcting S16/S17
# means picking a mill and year on Home (UC-SEC-001's territory), correcting S18 means an Administrator
# opening the reporting year, and S19's editability is a submission-workflow concern. All four are
# outside this UC's actor scope.

@sch5 @UC-SCH5-001 @render-states
Feature: Report Camp and Access Expenses (Schedule 5) — guard states and read-only view
  As a mill reporter
  I want Schedule 5 blocked or read-only when it should be
  So that I am told why I cannot enter data instead of being left with an empty screen

  # S16 / ERR-003 — no working mill and year. The one slice needing no anchor at all.
  @p1 @S16 @ERR-003
  Scenario: With no working mill and reporting year the page is suppressed
    When I open Schedule 5 with no working context
    Then the Schedule 5 mill and reporting year guard message is shown
    And the Schedule 5 data-entry panel is suppressed

  # S17 / ERR-004 and S18 / ERR-005 — the document GET fails, so nothing renders and the API's own
  # detail is what the reporter reads. Both anchors are proved at the API before the browser is driven.
  @p1 @S17 @S18
  Scenario Outline: <name>
    Given the Schedule 5 guard anchor "<guard>"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5 expecting a guard message
    Then the Schedule 5 page is blocked with "<detail>"
    And the Schedule 5 data-entry panel is suppressed

    Examples:
      | name                                                       | guard       | detail                                                                                                |
      | S17 A mill that is not active for the year is blocked      | closed-mill | This Mill is not active for the current Reporting Year. Please select another mill from the Home Page. |
      | S18 A mill/year with no Schedule 5 record is blocked       | not-found   | Schedule not found.                                                                                   |

  # S19 / STA-001, BR-06 — the report is Submitted, so the whole schedule renders read-only.
  @p1 @S19 @STA-001 @BR-06
  Scenario: Schedule 5 renders read-only when the report is not in Draft
    Given the Schedule 5 read-only anchor holds the seeded camp
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    # The camp still LISTS — a read-only schedule is readable, not hidden.
    Then "E2E View Camp" is listed in the Existing Camps table
    # BR-06: no new camp, and Check Status is unavailable outside Draft. Schedule 5 includes the
    # `!editable` term on both (index.tsx:1404, :1419) — unlike Schedule 4 and Schedule 8, which each
    # shipped a defect here (#293, #322). Asserting it keeps Schedule 5 from drifting the same way.
    And the Schedule 5 page-level actions are disabled
    # Deviation (B): a single View, with Edit/Delete/Copy absent rather than disabled. See the header.
    And the "E2E View Camp" row offers only a View action

    When I view the "E2E View Camp" camp
    # Descriptors keep their inputs but carry `readonly`; Isolated Camp is a Select and is disabled.
    Then the "E2E View Camp" panel is read-only with the stored values
    # The grid, by contrast, holds no inputs at all — which is what makes the amounts below rendered
    # text rather than the contents of boxes a user could still type into.
    And the Schedule 5 category grid is read-only
    # SERVED figures, never a client-side mirror: `derived` is null on a non-editable document
    # (index.tsx:1191). Same arithmetic S01 proves on the write path.
    And the read-only panel shows the stored amounts and totals
    # Save is disabled; Close is NOT — it is the only way out of a View panel, the one place the AC's
    # "everything disabled" cannot be taken literally (index.tsx:1327-1329).
    And the read-only panel offers no Save but can be closed

    When I close the camp panel
    Then the camp panel is closed
    And "E2E View Camp" is listed in the Existing Camps table
