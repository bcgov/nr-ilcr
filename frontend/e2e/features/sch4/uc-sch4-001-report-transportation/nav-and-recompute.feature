# UC-SCH4-001-S12 (NAV-001) and BR-05's post-save refresh — two DELIBERATELY RED divergences
#
# Both are filed in this UC's defects.md with an `ACTION: BA/QA → Jira`, and both were reproduced in a real
# browser on 2026-08-17 before being written as tests. Playwright isolates tests, so an honest red costs no
# other coverage; run `npm run test:gate` for a "fresh failures only" pass.
#
# The scenarios assert the CORRECT (re-grounded) behaviour, so each flips to green the moment the app is
# fixed — at which point the @discovered-divergence tag comes off.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — unsaved-change warnings and the post-save recompute

  As a Licensee
  I want to be warned before losing unsaved changes, and to see the recalculated figures once I save
  So that I neither lose data by accident nor read stale numbers off the screen

  # ---------------------------------------------------------------------------------------------------
  # DIV-3 — DELIBERATELY RED. defects.md DIV-3 → BA/QA/Jira.
  #
  # NAV-001 is not implemented. Leaving a dirty location panel discards the edit with NO warning:
  #   - legacy raised `confirmNavigationMsg` ("Any unsaved data will be lost…") from the top-level
  #     `p:confirm` on Close / Edit / Add New Location while the panel was dirty
  #     (`schedule4.xhtml:74,130,160,189,213`), and slice S12 is that scenario;
  #   - Story 10.5's own epic AC requires it verbatim: "Given unsaved changes on an open location panel /
  #     When I edit another location, add a new one, or close the panel / Then the dirty-panel discard
  #     confirm fires (NAV-001 …) before entered data is dropped (S12)" (epics.md);
  #   - the app's `closePanel` is `() => setPanelMode('closed')` and `openNew`/`openEditOrView` switch the
  #     panel unconditionally. Confirmed in the browser: Back on a dirty panel opened 0 dialogs and the
  #     panel closed; Add New Location on a dirty panel opened 0 dialogs and the heading became
  #     "New Location".
  #
  # NOTE the app DOES implement the same confirm for sub-page navigation (NAV-002/003 — covered green in
  # subpages.feature), so this is a missing case rather than a missing feature.
  # ---------------------------------------------------------------------------------------------------
  @p1 @S12 @discovered-divergence
  Scenario: Closing a dirty location panel warns before discarding [DISCOVERED DIVERGENCE — no NAV-001 confirm; defects.md DIV-3 → BA/QA/Jira]
    Given the Schedule 4 anchor "nav-dirty-panel" is an editable Draft with no locations
    And the Schedule 4 location "E2E Dirty Panel" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Dirty Panel" for edit
    And I enter "9999" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I go back from the Schedule 4 panel
    # The legacy warning must appear BEFORE the entered value is dropped.
    Then the Schedule 4 unsaved-changes confirmation asks "Any unsaved data will be lost. Are you sure you would like to continue?"

  @p2 @S12 @discovered-divergence
  Scenario: Opening a new location over a dirty panel warns first [DISCOVERED DIVERGENCE — no NAV-001 confirm; defects.md DIV-3 → BA/QA/Jira]
    Given the Schedule 4 anchor "nav-dirty-switch" is an editable Draft with no locations
    And the Schedule 4 location "E2E Dirty Switch" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 500    | 1000 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Dirty Switch" for edit
    And I enter "8888" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I add a new Schedule 4 location
    Then the Schedule 4 unsaved-changes confirmation asks "Any unsaved data will be lost. Are you sure you would like to continue?"

  # ---------------------------------------------------------------------------------------------------
  # DIV-3, THIRD CASE — DELIBERATELY RED. Added 2026-08-19 after the app-wide sweep for issue #324.
  #
  # NAV-001 is missing on the SUB-PAGE's Back button too, not just on the location panel. Legacy attached
  # the confirm to that Back button UNCONDITIONALLY — `schedule4TowingTotal.xhtml:173-175`, and the same in
  # `schedule4TruckRehaul.xhtml` / `schedule4OtherTransportation.xhtml` — so it fired whether or not
  # anything had been typed. `schedule4/SubPage.tsx` has no confirm state at all.
  #
  # Confirmed in the browser 2026-08-19: typed "E2E unsaved text" into the add-row form, pressed Back, and
  # the app returned to the location list immediately with 0 dialogs and the typed input gone.
  #
  # This asserts the message rather than a named modal, because the dialog does not exist yet — pinning a
  # test id would invent an implementation detail for something unbuilt.
  # ---------------------------------------------------------------------------------------------------
  @p2 @S12 @discovered-divergence
  Scenario: Leaving a sub-page with typed row input warns before discarding [DISCOVERED DIVERGENCE — no NAV-001 confirm on the sub-page; defects.md DIV-3 / issue #324]
    Given the Schedule 4 anchor "nav-subpage-back" is an editable Draft with no locations
    And the Schedule 4 location "E2E Subpage Back" is already saved with only its name
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Subpage Back" for edit
    And I open the Schedule 4 "Towing Total" sub-page from the saved location
    And I enter the following Schedule 4 row values:
      | description       | distance | volume | cost |
      | E2E unsaved text  | 12.5     | 500    | 1500 |
    And I go back from the Schedule 4 sub-page
    # The legacy warning must appear BEFORE the typed row is dropped.
    Then the Schedule 4 sub-page unsaved-changes confirmation asks "Any unsaved data will be lost. Are you sure you would like to continue?"

  # The compensating GREEN assertion for the same behaviour: whatever the app decides about warning, the
  # discarded edit must never reach the database. This one passes today and would catch the far worse bug
  # (a silent write) if the panel ever started saving on close.
  @p1 @S12
  Scenario: A discarded panel edit is never written
    Given the Schedule 4 anchor "discard-safe" is an editable Draft with no locations
    And the Schedule 4 location "E2E Discard Safe" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 500    | 1000 |
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Discard Safe" for edit
    And I note the Schedule 4 mutation count
    And I enter "8888" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I go back from the Schedule 4 panel
    # The panel really did close (the edit was discarded, not merely hidden behind a dialog).
    Then the Schedule 4 location panel is closed
    And no further Schedule 4 write should have been sent
    And the stored Schedule 4 location "E2E Discard Safe" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 500    | 1000 | 2       |
    # Re-opening shows the STORED value, not the abandoned one.
    When I open the Schedule 4 location "E2E Discard Safe" for edit
    Then the Schedule 4 "Lakeside Dry Dump" "cost" cell shows "1,000"

  # ---------------------------------------------------------------------------------------------------
  # DIV-4 — DELIBERATELY RED. defects.md DIV-4 → BA/QA/Jira.
  #
  # The recomputed $/m³ is not shown on the panel that just saved. Both S01 and S02 assert it explicitly
  # ("the lakeSideDryDumpCostVolume field shows the recomputed / recalculated cost-per-volume"), and BR-05
  # makes $/m³ a system-computed display figure, so the reporter is meant to SEE the new rate after saving.
  #
  # The server does compute it — the API read-back in happy-path.feature proves that — but the panel keeps
  # the per-unit values it was seeded with: `handleSave` re-seeds `panelMode`/`panelEditId`/`panelRevision`
  # from the save response and never `setPanelPerUnit`. Confirmed in the browser 2026-08-17: immediately
  # after saving 1200/3600 the $/m³ cell read "—", and reopening the same location showed "3.00".
  #
  # So the figure is right in the database and stale on screen until the location is reopened.
  # ---------------------------------------------------------------------------------------------------
  @p1 @S01 @S02 @discovered-divergence
  Scenario: The recomputed $/m³ appears on the panel that saved it [DISCOVERED DIVERGENCE — stale until reopened; defects.md DIV-4 → BA/QA/Jira]
    Given the Schedule 4 anchor "per-unit-after-save" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E Per Unit" as the Schedule 4 location name
    And I enter the following Schedule 4 category amounts:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    # 3600 / 1200 = 3.00. The server returned it; the panel should be showing it.
    And the Schedule 4 category grid shows:
      | category          | distance | volume | cost  | perUnit |
      | Lakeside Dry Dump | —        | 1,200  | 3,600 | 3.00    |

  # The compensating GREEN assertion: the value IS correct once the location is reopened, so the defect is
  # scoped to the post-save refresh rather than to the computation. This keeps the honest red small and
  # tells BA/QA exactly how far it reaches.
  @p1 @S01
  Scenario: Reopening the saved location shows the recomputed $/m³
    Given the Schedule 4 anchor "per-unit-reopen" is an editable Draft with no locations
    And the Schedule 4 location "E2E Per Unit Reopen" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Per Unit Reopen" for edit
    Then the Schedule 4 category grid shows:
      | category          | distance | volume | cost  | perUnit |
      | Lakeside Dry Dump | —        | 1,200  | 3,600 | 3.00    |
