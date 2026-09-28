# UC-SCH4-001-S12 (NAV-001) and BR-05's post-save refresh — two divergences, BOTH NOW FIXED
#
# Both are filed in this UC's defects.md and TICKETED (DIV-3 → issue #324, DIV-4 → the pre-existing issue
# #291), and both were reproduced in a real browser on 2026-08-17 before being written as tests. Playwright
# isolates tests, so an honest red costs no other coverage; run `npm run test:gate` for a "fresh failures
# only" pass.
#
# STATE 2026-08-27: **DIV-4 is FIXED** — its two scenarios (S01/S02) are green and their tag is retired.
# STATE 2026-09-22: **DIV-3 is FIXED** by the PR closing #324 — the NAV-001 confirm now guards the panel's
# Back / Add New Location / Edit / Copy and each sub-page's Back (when there is unsaved input). Its three
# S12 scenarios keep their original assertions and lose the @discovered-divergence tag; the Cancel arm and
# the Edit-another-location path were folded into them (no new anchors). Nothing in this file is
# deliberately red any more.
#
# The scenarios assert the CORRECT (re-grounded) behaviour, so each flips to green the moment the app is
# fixed — at which point the @discovered-divergence tag comes off. That is exactly what happened here.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — unsaved-change warnings and the post-save recompute

  As a Licensee
  I want to be warned before losing unsaved changes, and to see the recalculated figures once I save
  So that I neither lose data by accident nor read stale numbers off the screen

  # ---------------------------------------------------------------------------------------------------
  # DIV-3 — FIXED (issue #324). See this UC's defects.md (DIV-3). The original analysis follows.
  #
  # NAV-001 was not implemented. Leaving a dirty location panel discarded the edit with NO warning:
  #   - legacy raised `confirmNavigationMsg` ("Any unsaved data will be lost…") from the top-level
  #     `p:confirm` on Close / Edit / Add New Location while the panel was dirty
  #     (`schedule4.xhtml:74,130,160,189,213`), and slice S12 is that scenario;
  #   - Story 10.5's own epic AC requires it verbatim: "Given unsaved changes on an open location panel /
  #     When I edit another location, add a new one, or close the panel / Then the dirty-panel discard
  #     confirm fires (NAV-001 …) before entered data is dropped (S12)" (epics.md);
  #   - the app's `closePanel` was `() => setPanelMode('closed')` and `openNew`/`openEditOrView` switched
  #     the panel unconditionally. Confirmed in the browser: Back on a dirty panel opened 0 dialogs and the
  #     panel closed; Add New Location on a dirty panel opened 0 dialogs and the heading became
  #     "New Location".
  #
  # The fix routes every panel-leaving control (Back/Close, Add New Location, Edit, Copy) through one
  # dirty check and the SAME "Unsaved changes" modal the NAV-002 prompt already used — so these scenarios
  # address it through `navExistingModal`, unchanged. Deliberate deviation from legacy, shared with
  # Schedule 5: the prompt fires only when the panel holds something not yet saved (an untouched panel
  # closes silently; a Copy counts as unsaved from the moment it opens).
  # ---------------------------------------------------------------------------------------------------
  @p1 @S12
  Scenario: Closing a dirty location panel warns before discarding
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
    # The Cancel arm — a confirm that cannot be declined is not a confirm: the panel and the typed value
    # both survive. (The Continue arm is the "never written" scenario below.)
    When I cancel the Schedule 4 unsaved-changes prompt
    Then the Schedule 4 panel heading is "Edit Location"
    And the Schedule 4 "Lakeside Dry Dump" "cost" cell shows "9,999"

  # Both panel-SWITCH paths the story names ("When I edit another location, add a new one"), on one anchor:
  # Edit on another location is declined and the dirty panel survives; Add New Location is confirmed and
  # the panel switches. The Edit half was added 2026-09-22 with the fix.
  @p2 @S12
  Scenario: Switching the panel over a dirty edit warns first — Edit on another location, and Add New Location
    Given the Schedule 4 anchor "nav-dirty-switch" is an editable Draft with no locations
    And the Schedule 4 location "E2E Dirty Switch" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 500    | 1000 |
    And the Schedule 4 location "E2E Dirty Other" is already saved with only its name
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Dirty Switch" for edit
    And I enter "8888" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    # Edit on ANOTHER location: held behind the prompt; Cancel leaves the first location open, edit intact.
    And I open the Schedule 4 location "E2E Dirty Other" for edit
    Then the Schedule 4 unsaved-changes confirmation asks "Any unsaved data will be lost. Are you sure you would like to continue?"
    And the Schedule 4 location name shows "E2E Dirty Switch"
    When I cancel the Schedule 4 unsaved-changes prompt
    Then the Schedule 4 location name shows "E2E Dirty Switch"
    And the Schedule 4 "Lakeside Dry Dump" "cost" cell shows "8,888"
    # Add New Location: the same prompt; Continue is what switches the panel.
    When I add a new Schedule 4 location
    Then the Schedule 4 unsaved-changes confirmation asks "Any unsaved data will be lost. Are you sure you would like to continue?"
    When I confirm the Schedule 4 unsaved-changes prompt
    Then the Schedule 4 panel heading is "New Location"
    And the Schedule 4 location name is empty

  # ---------------------------------------------------------------------------------------------------
  # DIV-3, THIRD CASE — FIXED with the rest of #324. Added 2026-08-19 after the app-wide sweep for the issue.
  #
  # NAV-001 was missing on the SUB-PAGE's Back button too, not just on the location panel. Legacy attached
  # the confirm to that Back button UNCONDITIONALLY — `schedule4TowingTotal.xhtml:173-175`, and the same in
  # `schedule4TruckRehaul.xhtml` / `schedule4OtherTransportation.xhtml` — so it fired whether or not
  # anything had been typed. `schedule4/SubPage.tsx` had no confirm state at all.
  #
  # Confirmed in the browser 2026-08-19: typed "E2E unsaved text" into the add-row form, pressed Back, and
  # the app returned to the location list immediately with 0 dialogs and the typed input gone.
  #
  # The fix gates the sub-page's Back on unsaved input (a typed-but-not-Added row, or an in-place row edit
  # not yet Saved) — the same dirty-gated choice Schedule 8's rates page made, rather than legacy's
  # unconditional prompt. This still asserts the message rather than a named modal: the message is the
  # part the spec fixes.
  # ---------------------------------------------------------------------------------------------------
  @p2 @S12
  Scenario: Leaving a sub-page with typed row input warns before discarding
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

  # The compensating assertion for the same behaviour: whatever the app decides about warning, the
  # discarded edit must never reach the database. It would catch the far worse bug (a silent write) if the
  # panel ever started saving on close — including on the prompt's Continue, which now sits on this path.
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
    # NAV-001 holds the dirty panel first (since #324); Continue is the discard.
    And I confirm the Schedule 4 unsaved-changes prompt
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
  # DIV-4 — FIXED and GREEN since issue #291's fix landed (`6e86d7a`, "automatic recalculation of derived
  # figures during data entry"). It went green on its own exactly as designed, so only the
  # @discovered-divergence tag was dropped — no assertion was edited. Verified 2026-08-27; both scenarios
  # are now ordinary regression guards. See this UC's defects.md (DIV-4, closed).
  #
  # The original analysis follows.
  #
  # See the pre-existing issue #291 ("Automatic Recalculation for Schedule 1, 2, 3 and 4").
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
  @p1 @S01 @S02
  Scenario: The recomputed $/m³ appears on the panel that saved it
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
