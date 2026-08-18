# UC-SCH4-001-S15 / S16 / S17 / S18 — the guard states and read-only "View" mode
#
# RE-GROUNDING NOTE:
#   - the three EF2 guards render as Carbon InlineNotifications carrying the API's own verbatim detail
#     (`millNotActiveForCurrentYearMsg` / `scheduleNotFoundErrorMsg`) or, for the missing working context,
#     the client-only EF2-001 banner — not a `p:messages` panel driven by `isScheduleNotFound()` flags.
#   - STA-001 in the rewrite: the locations still LIST, the row action renames Edit -> View, Copy/Delete are
#     rendered-but-DISABLED (legacy omitted them from the DOM entirely — same user-visible outcome, different
#     mechanism), the panel opens read-only with values as TEXT, and the sub-pages lose their add-row form.
#   - S18 is proven on BOTH non-Draft codes (Submitted "S" and Verified "V") because the app derives
#     `editable` from `trackStatus == "D"` alone: the mirror must hold on both sides, not just one.
#
# The read-only anchors are the only non-Draft locations in the seeded DB that carry amounts — supplied by
# `real-test-data-patches/sch4/view-mode-amounts.sql` (that file explains why the extract cannot). Nothing
# here writes: preflight re-verifies the patched figures so a drifted patch fails fast instead of surfacing
# as an opaque table mismatch.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — guard states and read-only view

  As a Licensee
  I want Schedule 4 blocked or read-only when it should be
  So that I cannot enter data the report is not open for

  # S15 / EF2-001 — no working mill/year: the whole data-entry surface is suppressed.
  @p1 @S15
  Scenario: With no working mill and year the page is suppressed
    When I open Schedule 4 with no working context
    Then the Schedule 4 mill and reporting year guard message is shown
    And the Schedule 4 location list is not displayed

  # S16 / EF2-002 and S17 / EF2-003 — the document GET fails, so no list renders and the API's verbatim
  # detail is what the reporter sees.
  @p1 @S16 @S17
  Scenario Outline: <name>
    Given the Schedule 4 guard anchor "<guard>"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4 expecting a guard message
    Then the Schedule 4 page is blocked with "<detail>"
    And the Schedule 4 location list is not displayed

    Examples:
      | name                                                        | guard        | detail                                                                                             |
      | S16 A mill that is not active for the year is blocked       | closed-mill  | This Mill is not active for the current Reporting Year. Please select another mill from the Home Page. |
      | S17 A mill/year with no Schedule 4 report is blocked        | not-found    | Schedule not found.                                                                                |

  # S18 / STA-001 — read-only on both non-Draft codes.
  @p0 @S18
  Scenario Outline: Schedule 4 is read-only when the report is <track>
    Given the Schedule 4 read-only anchor "<anchor>"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    # The list still renders — including the extract's own locations alongside the patched one.
    Then the Schedule 4 location "E2E View Location" is listed
    And the Schedule 4 location "<otherLocation>" is listed
    # BR-03: no new location, and the row's write actions are disabled.
    And the Schedule 4 Add New Location button is disabled
    And the Schedule 4 location "E2E View Location" offers a "View" action
    And the Schedule 4 location "E2E View Location" has no "Edit" action
    And the Schedule 4 "Copy" action for "E2E View Location" is disabled
    And the Schedule 4 "Delete" action for "E2E View Location" is disabled
    When I open the Schedule 4 location "E2E View Location" for viewing
    Then the Schedule 4 panel heading is "View Location"
    # Read-only means the values render as TEXT: zero inputs in the grid. That is what makes the value
    # assertions below meaningful rather than a reading of pre-filled boxes.
    And the Schedule 4 category grid is read-only
    And the Schedule 4 view panel shows the location name "E2E View Location"
    And the Schedule 4 category grid shows:
      | category          | distance | volume | cost  | perUnit |
      | Lakeside Dry Dump | —        | 1,200  | 3,600 | 3.00    |
      | Truck Barge/Ferry | 50       | 800    | 4,000 | 5.00    |
    And the Schedule 4 view panel shows the comments "Read-only sample comments (E2E_SEED)."
    # The panel's secondary action is "Close" in View mode (it is "Back" while editable), and there is no
    # Save at all.
    And the Schedule 4 sub-page link "Towing Total" shows 1 rows
    And the Schedule 4 sub-page row "Towing Total" totals show:
      | distance | volume | cost  | perUnit | cycle |
      | 12.5     | 500    | 1,500 | 3.00    | —     |
    # The sub-page opens with NO confirmation at all from a read-only panel (there is nothing to discard
    # and nothing to save), and lands read-only too.
    When I open the Schedule 4 "Towing Total" sub-page directly
    Then the Schedule 4 add-row form is not rendered
    And the Schedule 4 "Towing Total" rows are read-only
    And the Schedule 4 "Towing Total" row "Camp haul" shows:
      | distance | volume | cost  | perUnit |
      | 12.5     | 500    | 1,500 | 3.00    |
    And the Schedule 4 "Towing Total" totals show:
      | distance | volume | cost  |
      | 12.5     | 500    | 1,500 |
    # Back to the list, then the panel's secondary action — labelled "Close" in View mode where it reads
    # "Back" while editable (legacy had four separate `closeLocationBtnN` controls for the same job).
    When I go back from the Schedule 4 sub-page
    And I open the Schedule 4 location "E2E View Location" for viewing
    And I close the Schedule 4 view panel
    Then the Schedule 4 location panel is closed
    And the Schedule 4 location "E2E View Location" is listed

    Examples:
      | track     | anchor    | otherLocation |
      | Submitted | submitted | test 2        |
      | Verified  | verified  | loc 1         |

  # The empty-list state, which every other scenario passes through on its way to creating something.
  @p2 @S01
  Scenario: A Draft schedule with no locations shows the empty state
    Given the Schedule 4 anchor "validation" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    Then the Schedule 4 location list is empty
    And the Schedule 4 Add New Location button is enabled
    And the Schedule 4 Check Status button is enabled

  # ---------------------------------------------------------------------------------------------------
  # DIV-1 — DELIBERATELY RED. See this UC's defects.md (Divergence #1 → BA/QA → Jira).
  #
  # Legacy disabled Check Status outside Draft: `schedule4.xhtml:43` binds
  # `disabled="#{schedule4MB.disableReportEdits()}"` on it, and the source Gherkin S18 asserts it
  # explicitly ("the Check Status button (top) is disabled"). This app leaves it ENABLED — and both
  # sibling schedules disable it (`schedule2/index.tsx:319` and `schedule11/index.tsx:881` each use
  # `disabled={!editable || saving}`), so Schedule 4 is also the odd one out in its own codebase.
  # Confirmed in the browser 2026-08-17 on the Submitted anchor: Add New Location disabled=true,
  # Check Status disabled=false.
  # ---------------------------------------------------------------------------------------------------
  @p2 @S18 @discovered-divergence
  Scenario: Check Status is unavailable outside Draft [DISCOVERED DIVERGENCE — it stays enabled; defects.md Divergence #1 → BA/QA/Jira]
    Given the Schedule 4 read-only anchor "submitted"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    Then the Schedule 4 Add New Location button is disabled
    And the Schedule 4 Check Status button is disabled
