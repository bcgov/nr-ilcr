# Re-grounded from UC-SCH3-001-S06.feature / -S07.feature — BR-09, the one Schedule 3 behaviour that
# crosses schedules: changing the Crown Timber volume and saving propagates the new volume into
# Schedule 1's volume fields, and the reporter is told which way it went.
#
# WHAT RE-GROUNDING CHANGED
#  * The legacy scenarios asserted only the two messages. These also read Schedule 1 back through its
#    own API and prove the volume actually landed on all thirteen items the push covers (the seven fixed
#    lines 12-18, Forest Mgmt Admin 143, Subtotal Company Logging 144 and the four silviculture rows) —
#    or, in the WRN-002 arm, that Schedule 1 still does not exist.
#  * WRN-002's wording is ungrammatical in the source bundle ("couldn't been applied"); it is asserted
#    verbatim rather than tidied.
#  * The two outcomes are pinned by DATA, not by ordering: `crown-applied` carries an open Schedule 1
#    and `crown-not-opened` deliberately carries none. Preflight re-asserts both, because a drift there
#    would silently swap the two scenarios' expected messages.

@sch3 @UC-SCH3-001 @crown-push
Feature: Report Forest Management Administration Costs (Schedule 3) — the Crown Timber volume push (BR-09)
  As a mill reporter
  I want a changed Crown Timber volume to reach Schedule 1 when I save Schedule 3
  So that the two schedules agree on the volume without my re-keying it

  @p0 @S06
  Scenario: Changing the Crown Timber volume applies it to an already-opened Schedule 1
    Given the Schedule 3 anchor "crown-applied"
    And Schedule 1 has been opened for the same mill and year
    And a Crown Timber volume has already been saved
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I change the Crown Timber volume
    And I save Schedule 3
    Then I should see the message "Data saved successfully"
    And I should see the warning "The new Crown Timber volume has been applied to Schedule 1 volume fields. Please check."
    And the stored Crown Timber volume is the new one
    And the new Crown Timber volume is applied to Schedule 1

  @p1 @S07
  Scenario: Changing the Crown Timber volume when Schedule 1 has never been opened reports it was not applied
    Given the Schedule 3 anchor "crown-not-opened"
    And Schedule 1 has never been opened for the same mill and year
    And a Crown Timber volume has already been saved
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I change the Crown Timber volume
    And I save Schedule 3
    Then I should see the message "Data saved successfully"
    And I should see the warning "The new Crown Timber volume couldn't been applied to Schedule 1 volume fields as it has not been opened."
    And the stored Crown Timber volume is the new one
    And Schedule 1 still has not been opened
