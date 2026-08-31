# DIVERGENCE — this scenario is DELIBERATELY RED. It reproduces defects.md DIV-6, tracked upstream as
# bcgov/nr-ilcr#359, and stays failing until Check Status accounts for what is on screen. Do not weaken
# it, skip it, or "fix" it by asserting the current behaviour: the failing state IS the tracking signal.
# Filter it out of a fresh-failures run with `npm run test:gate`.
#
# WHAT IT REPRODUCES
# Check Status reports on the LAST SAVED schedule and silently ignores anything typed since. Change the
# Override switch (or any amount) and press Check Status without saving, and the answer describes the
# stored data, not the screen — with nothing telling the reporter that.
#
# Legacy could not behave this way. Its Check Status button was `ajax="false"`
# (`webapp/schedule3.xhtml:38,421`), i.e. a full form postback: JSF pushed every submitted field —
# including `overrideTotPopVal`, bound to `#{schedule3MB.schedule3.overrideTotalPop}` (`:323-324`) —
# into the bean during UPDATE_MODEL_VALUES, and only then ran `checkStatus()`, which validated that
# in-memory schedule and persisted nothing. So legacy checked what you were looking at.
#
# The rewrite cannot: `POST /api/v1/schedule3/check-status` takes only `millId` and `year` and carries NO
# request body (`Schedule3Api.java:85-87`), the client posts no payload
# (`useScheduleMutations.checkStatus` -> `api().post(url(suffix))`), and the service reads the persisted
# summary and details (`Schedule3Service.java:889-895`, `override = OVERRIDE_YES.equals(summary.location())`).
#
# WHY THIS ANCHOR AND WHY IT IS SAFE TO SHARE. `check-override` is seeded with Override "Y" plus two
# stored BR-03 violations, so it PASSES Check Status as it stands — which is exactly the starting point
# needed. The scenario only changes a dropdown and presses Check Status; Check Status mutates nothing by
# contract (AD-5) and nothing here is saved, so this stays a read-only scenario on a read-only anchor.
# The unmoved optimistic-lock token is asserted at the end to prove that.

@sch3 @UC-SCH3-001 @check-status-unsaved
Feature: Report Forest Management Administration Costs (Schedule 3) — Check Status and unsaved edits
  As a mill reporter
  I want Check Status to judge what is on my screen
  So that I am not told the schedule is fine when what I am looking at is not

  @discovered-divergence @p1 @S12
  Scenario: Check Status reflects an Override change that has not been saved yet [DISCOVERED DIVERGENCE — Check Status judges the SAVED schedule, ignoring the screen; defects.md DIV-6 / issue #359]
    Given the Schedule 3 anchor "check-override"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    # As stored: Override "Y", so the two BR-03 violations are suppressed and the schedule passes.
    And I run Check Status on Schedule 3
    Then I should see the message "All requirements for this schedule have been met"
    # Now switch the override OFF on screen and check again WITHOUT saving. The stored violations are
    # no longer excused by what the reporter can see, so legacy would report both of them.
    When I set the Override Harvest and Total PO&P selection to "N"
    And I run Check Status on Schedule 3
    Then the "Wages/Salaries, incl Benefits" line is flagged as Harvest below PO&P
    And the other-acceptable subtotal is flagged as Harvest below PO&P
    And I should not see the message "All requirements for this schedule have been met"
    # Nothing was saved: Check Status must stay read-only whichever data it judges.
    And the Schedule 3 optimistic-lock token has not moved

  # ---------------------------------------------------------------------------------------------------
  # S25, the FALSE-GREEN arm on an AMOUNT rather than the Override switch. Added 2026-08-27 to finish the
  # slice: the scenario above pins one control (a dropdown), and a reader could reasonably wonder whether
  # the defect is specific to it. It is not — the endpoint carries no body at all, so every field on the
  # page is invisible to it.
  #
  # WHY CLEARING, AND NOT TYPING A VIOLATION. The obvious probe is to type a Harvest below its PO&P on a
  # schedule that passes. It cannot be done on read-only data: the only at-rest-PASSING anchor is
  # `check-override`, and it passes BECAUSE Override is "Y" — which legitimately suppresses the
  # Harvest>=PO&P comparison, so a typed violation would be correctly ignored even by legacy. Clearing a
  # mandatory amount is the probe that works on the same anchor, because the Override switch does NOT
  # suppress the required-field checks (`Schedule3CheckStatus.isScheduleValid`, the two families are
  # independent). Verified at rest 2026-08-27: `POST /check-status` on 22050/2021 answers
  # `requirementsMet: true` with zero errors, and Office Expense holds Harvest 25,000.
  @discovered-divergence @p1 @S25
  Scenario: Check Status reports a mandatory amount cleared on screen but not saved [DISCOVERED DIVERGENCE — Check Status judges the SAVED schedule, ignoring the screen; defects.md DIV-6 / issue #359]
    Given the Schedule 3 anchor "check-override"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    # As stored this schedule is complete, so Check Status passes.
    And I run Check Status on Schedule 3
    Then I should see the message "All requirements for this schedule have been met"
    # Empty a mandatory amount on screen and check again WITHOUT saving. Legacy's full postback would have
    # submitted the empty field and reported it required.
    When I clear the "Office Expense" Harvest amount
    And I run Check Status on Schedule 3
    Then the "Office Expense (Harvest Total $)" field is flagged as required
    And I should not see the message "All requirements for this schedule have been met"
    And the Schedule 3 optimistic-lock token has not moved

  # ---------------------------------------------------------------------------------------------------
  # S26, the FALSE-RED arm — the direction a reporter meets most often, and the one this suite had no
  # scenario for at all. Check Status tells you to fix a field; you fix it; you re-check without saving and
  # are told about it again.
  #
  # ANCHOR. `check-harvest-pop` (22050/2020) stores Wages/Salaries Harvest 40,000 against PO&P 50,000 with
  # Override "N", and — verified at rest 2026-08-27 — `POST /check-status` returns EXACTLY ONE error, that
  # line's. That single-error state is what makes the mirror assertable in both directions: correcting the
  # one thing on screen must clear the one error AND flip the whole verdict to met. Read-only: typing
  # without saving writes nothing, which the unmoved lock token proves.
  @discovered-divergence @p1 @S26
  Scenario: Check Status stops reporting a flagged amount once it is corrected on screen [DISCOVERED DIVERGENCE — Check Status judges the SAVED schedule, ignoring the screen; defects.md DIV-6 / issue #359]
    Given the Schedule 3 anchor "check-harvest-pop"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I run Check Status on Schedule 3
    Then the "Wages/Salaries, incl Benefits" line is flagged as Harvest below PO&P
    # Correct it on screen — 60,000 clears the stored PO&P of 50,000 — and re-check WITHOUT saving.
    When I enter "60000" into the "Wages/Salaries, incl Benefits" Harvest field
    And I run Check Status on Schedule 3
    Then the "Wages/Salaries, incl Benefits" line is not flagged as Harvest below PO&P
    And I should see the message "All requirements for this schedule have been met"
    And the Schedule 3 optimistic-lock token has not moved
