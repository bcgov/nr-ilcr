# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-MILL-001/gherkin/UC-MILL-001-S03.feature,
# -S04.feature and -S12.feature (legacy JSF/PrimeFaces). The mill-level status actions: deactivate an
# Active mill with no active users (S03), activate a Closed one (S04), and the deactivate refused while
# a user is still active (S12).
#
# WHAT RE-GROUNDING CHANGED
#  * Route, menu, identity and the "Find and select Mill" dialog: as in happy-path.feature.
#  * The Deactivate / Activate pair is two Carbon Buttons in the Mill Details panel; exactly one is
#    rendered, chosen by the status code (STA-001) — presence, never enable/disable. Neither is
#    confirmed, as in legacy (mills.xhtml:100-101).
#  * `form:messagesId` -> a Carbon notification. Both success texts and the S12 error are legacy's
#    verbatim (`mill.expired`, `mill.activated`, `error.mill.deactivate.hasactiveusers`).
#  * "the mill's status is set to Closed/Active" is read back through the API, not off the panel alone:
#    after a status action the page installs the RESPONSE's mill, so the panel would change whether or
#    not the row did (the same reason S01 reads back).
#  * S03's "schedule viewing is thereafter blocked for every role" (BR-06) is asserted at the
#    enforcement point: Schedule 1 answers 409 with ERR-002 for a submitter AND an administrator, and
#    the working context reports the mill not viewable. It answered 200 before the deactivate.
#  * S04's "per-category report records exist, created if missing" (BR-07) is asserted as "exist": the
#    anchor carries its complete current-year set, so activate writes the status alone and the cleanup
#    can undo it. The CREATE branch writes twelve rows no endpoint removes — defects.md GAP-6.
#  * S12's two legacy scenarios are ONE here, as S01's were: the second's Given is exactly the first's
#    end state. The legacy step names a "form:auditorList" too; the Associated Auditors panel is retired
#    (DL-23), so there is one table, "Associated Licensee User".
#  * ANCHORS: three SEEDED mills, one per scenario (real-test-data-patches/mill/mill-status-anchors.sql,
#    folded into R__80). No extract mill can take a status change — see fixtures/mill/mills-test-data.ts.

@mill @UC-MILL-001 @status
Feature: Maintain Mills — deactivate and activate a mill
  As a Ministry Administrator
  I want to close a mill that no longer reports, and reopen a closed one
  So that only open mills can be reported on

  @p0 @S03
  Scenario: Deactivate an Active mill with no active user assignments
    Given the S03 mill is at rest with its status and active users
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    Then the Mill Details panel shows the mill's number, name, status and last-edited details
    And "Deactivate" is offered and "Activate" is not
    When I deactivate the mill
    Then I should see the message "Mill 9181 - E2E-DEACTIVATE-TEST has been deactivated."
    And the mill's status is persisted as "CLS"
    And the Mill Details panel shows the status "Close"
    And "Activate" is offered and "Deactivate" is not
    And schedule viewing for the mill is blocked for every role

  @p0 @S04
  Scenario: Activate a Closed mill
    Given the S04 mill is at rest with its status and active users
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    Then the Mill Details panel shows the mill's number, name, status and last-edited details
    And "Activate" is offered and "Deactivate" is not
    When I activate the mill
    Then I should see the message "Mill 9182 - E2E-ACTIVATE-TEST has been activated."
    And the mill's status is persisted as "ACT"
    And the Mill Details panel shows the status "Active"
    And "Deactivate" is offered and "Activate" is not
    And the mill has its report records for the current reporting year and its schedules open

  @p0 @S12
  Scenario: Deactivation is refused while a user is active, and succeeds once every user is deactivated
    Given the S12 mill is at rest with its status and active users
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    Then every associated user is shown as active
    When I deactivate the mill
    Then I should see the error "The selected mill has active users, you must deactivate them first."
    And the mill's status is persisted as "ACT"
    And "Deactivate" is offered and "Activate" is not
    When I deactivate every active associated user
    Then no associated user is active any more
    When I deactivate the mill
    Then I should see the message "Mill 9183 - E2E-BLOCKED-TEST has been deactivated."
    And the mill's status is persisted as "CLS"
    And "Activate" is offered and "Deactivate" is not
