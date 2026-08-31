# UC-SCH3-001 — leaving a cost sub-page with unsaved edits warns first, and discarding writes nothing.
# Closes coverage gap GAP-3.
#
# WHAT WAS ALREADY COVERED, AND WHAT WAS NOT. The confirm on the way IN to a sub-page is asserted on
# EVERY entry (`pages/sch3/schedule3Page.ts` `openSubPage` checks the verbatim legacy
# `confirmNavigationMsg` text), so that direction could not regress unnoticed. The way OUT was not
# asserted anywhere: `useEditableCostRows.handleBack` (`:293-298`) opens a "Leave page" modal only when
# `dirty` is set, and nothing proved that either the warning appears or that Continue discards rather
# than saves. Legacy guarded the same direction — `webapp/schedule3SubtotalOtherCosts.xhtml` puts
# `<p:confirm message="#{msg.confirmNavigationMsg}">` on its Back button.
#
# THE TWO HALVES. Cancel keeps you on the page with the edit intact (so the guard is not a one-way
# door), and Continue leaves WITHOUT writing — asserted at the API, because a "discard" that quietly
# persisted would look identical on screen.
#
# READ-ONLY BY CONSTRUCTION, WHICH IS WHY IT SHARES AN ANCHOR. An in-place row edit is held in React
# state; only Save persists it (Add and Remove persist immediately, and this scenario does neither). So
# nothing here writes, and the scenario shares the read-only `check-oa-pop` anchor and its seeded
# other-acceptable group rather than needing a mutating anchor of its own. The final API read-back is
# also the proof of that claim, not merely a nice-to-have.

@sch3 @UC-SCH3-001 @subpage-back
Feature: Report Forest Management Administration Costs (Schedule 3) — leaving a sub-page with unsaved edits
  As a mill reporter
  I want to be warned before I walk away from an itemized cost I have changed but not saved
  So that I do not lose an edit by pressing Back out of habit

  @p2 @S04
  Scenario: Back with an unsaved row edit warns, and discarding leaves the stored row untouched
    Given the Schedule 3 anchor "check-oa-pop"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I open the Schedule 3 Other Costs sub-page
    Then the sub-page lists the seeded other-acceptable row
    # Change the row in place and do NOT save — this is what makes the page dirty.
    When I change the seeded row total to "9999"
    And I press Back on the sub-page
    Then the sub-page warns me about leaving with unsaved edits
    # Half 1: cancelling is not a one-way door — we stay, and the edit is still on screen.
    When I cancel leaving the sub-page
    Then the sub-page row total reads "9999"
    # Half 2: discarding really discards. We leave, and nothing was written.
    When I press Back on the sub-page
    And I confirm leaving the sub-page
    Then Schedule 3 is displayed
    And the stored other-acceptable row total is unchanged
