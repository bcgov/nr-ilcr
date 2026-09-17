# Re-grounded from UC-SCH5-001-S12, -S13 and -S14 (legacy JSF/PrimeFaces). The three name rules:
# required (FLD-001), duplicate within a mill/year (ERR-001 / BR-02), and the copy that must be renamed.
#
# WHAT RE-GROUNDING CHANGED
#  * FLD-001'S `[UNKNOWN]` IS RESOLVED. The source Gherkin could not state the required-field text —
#    legacy overrode no `requiredMessage`, so it was whatever the JSF runtime's default happened to be
#    and was unrecoverable from source. The rewrite states both explicitly:
#    "Camp Name is required." / "Isolated Camp is required." (validation.ts CAMP_MESSAGES, transcribed
#    from the backend bundle so client advice and server rejection are byte-identical).
#  * S14 IS RE-GROUNDED ONTO A DIFFERENT ERROR — see SPEC-2. It predicts that saving an unrenamed copy
#    gives the DUPLICATE-name error, on the assumption that the copy panel keeps the source's name. It
#    does not: legacy's copy constructor nulls `campName` (CampReportType.java:120-121) and the rewrite
#    reproduces that, so the name is BLANK and the save is rejected as REQUIRED, not duplicate. The
#    duplicate error is unreachable by that route. Asserting the Gherkin's version would have pinned a
#    behaviour neither system has ever had.
#  * Errors render as Carbon inline field text (`.cds--form-requirement`) for the required case and as
#    a banner for the duplicate case, not in a `schedule5Form:messages` panel.
#  * BLUR IS THE COMMIT POINT — "a field's error appears only once the licensee has left it"
#    (index.tsx:734), so the steps fill AND blur.
#  * Every rejection arm reads the anchor back. An inline error only shows the page complained; it does
#    not show that nothing reached the database, which is what "the entry is not persisted" claims.
#  * ANCHORS: 17052/2023 (`REQUIRED_FIELD_ANCHOR`), 24051/2022 (`DUPLICATE_NAME_ANCHOR`),
#    25050/2022 (`COPY_DUPLICATE_ANCHOR`). S12 has its own rather than sharing S15's validate-only key,
#    because its second arm SAVES.

@sch5 @UC-SCH5-001 @name-validation
Feature: Report Camp and Access Expenses (Schedule 5) — camp name rules
  As a mill reporter
  I want the system to reject a camp that is unnamed or whose name is already taken
  So that every stored camp is identifiable and unique within its mill and year

  @p1 @S12 @FLD-001
  Scenario: Save is blocked when the required descriptors are left blank, then succeeds once filled
    Given the Schedule 5 anchor "required-field" is an editable Draft with no camps
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp
    And I try to save the camp
    Then the "Camp Name" field shows the error "Camp Name is required."
    And the "Isolated Camp" field shows the error "Isolated Camp is required."
    And no camp named "Cedar Creek Camp" is stored
    # The recovery arm: fill what the validator asked for and the same Save goes through.
    When I name the new camp "Cedar Creek Camp" and set Isolated Camp to "No"
    And I save the camp
    Then I should see the message "Data saved successfully"
    And "Cedar Creek Camp" is listed in the Existing Camps table

  @p1 @S13 @ERR-001 @BR-02
  Scenario: Save is blocked when the camp name duplicates another, case-insensitively
    Given the Schedule 5 anchor "duplicate-name" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp
    # Deliberately a DIFFERENT CASE — BR-02 is case-insensitive.
    And I name the new camp "NORTH CAMP" and set Isolated Camp to "No"
    And I try to save the camp
    Then I should see the camp-name duplicate error
    # Pinning the WHOLE list, not "no camp named NORTH CAMP": name lookup is case-insensitive (as BR-02
    # is), so asking for "NORTH CAMP" always finds the seeded "North Camp" and would fail against a
    # correct rejection. This distinguishes "the duplicate was not created" from "the original remains".
    And the anchor holds exactly "North Camp"
    When I rename the new camp to "North Camp Annex"
    And I save the camp
    Then I should see the message "Data saved successfully"
    And "North Camp Annex" is listed in the Existing Camps table

  @p1 @S14 @FLD-001
  Scenario: Saving a copied camp without naming it is rejected as REQUIRED, not duplicate
    Given the Schedule 5 anchor "copy-duplicate" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I copy the "North Camp" camp
    And I try to save the camp
    # SPEC-2: the copy panel opens with a BLANK name, so there is nothing to duplicate.
    Then the "Camp Name" field shows the error "Camp Name is required."
    And no camp named "North Camp Annex" is stored
    When I rename the new camp to "North Camp Annex"
    And I save the camp
    Then I should see the message "Data saved successfully"
    And both "North Camp" and "North Camp Annex" are stored
