# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S02.feature
# (legacy JSF/PrimeFaces). BR-03: when a Schedule 1 is opened for the FIRST time (every stored detail
# volume still null) and its Schedule 3 carries a Crown Timber volume (item 119), the server COPIES that
# volume into the full legacy 13-field volume set on the served document and raises WRN-001. Nothing is
# persisted — the warning tells the user to check and save, which is exactly what the last step proves.
#
# This slice was `deferred` while Schedule 3 was unimplemented. Schedule 3 shipped with crown data, so
# the precondition is now reachable: 28 of the 30 seeded Schedule-1/Schedule-3 pairs carry a crown
# volume. None is in the all-volumes-empty first-entry state and the app cannot produce it (a blanking
# PUT is a silent no-op — defects.md BUG-2), so the dedicated target is snapshotted, nulled
# at the DB, and restored verbatim on teardown.

@sch1 @UC-SCH1-001 @crown-prefill
Feature: Report Average Cost of Logging (Schedule 1) — Crown Timber volume pre-fill on first entry
  As a mill reporter opening Schedule 1 for the first time
  I want the Crown Timber volume from Schedule 3 carried into the volume fields
  So that I do not retype a figure the ministry already holds

  @S02 @p1 @WRN-001
  Scenario: Opening a first-entry Schedule 1 pre-fills every volume from the Schedule 3 Crown Timber volume
    Given the crown pre-fill target is an editable Draft with no volumes entered
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 1
    Then I should see the message "The Crown Timber (Sch 3) volume has been set for volume fields. Please check and save schedule."
    And every Schedule 1 volume field is pre-filled with the Schedule 3 Crown Timber volume
    And the pre-filled Schedule 1 volumes are not yet persisted
