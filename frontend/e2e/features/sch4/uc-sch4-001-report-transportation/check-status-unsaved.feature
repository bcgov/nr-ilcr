# UC-SCH4-001-S33 / S34 — Check Status and an UNSAVED location panel (bcgov/nr-ilcr#359, group B)
#
# WHAT LEGACY DID (observed on the legacy app, 2026-09-25): pressing Check Status ran the page's
# own field validation over the OPEN panel's on-screen values — the same rules Save runs — before any
# check. On the three distance categories (Truck Barge/Ferry, Crew Barge/Ferry, Rail Haul) a Distance
# makes Volume and Cost required, and on a New or Copy panel the Location Name is required too. A
# failing field blocked the check and was listed in the top banner as `{label}: Value is required.`
# (legacy's JSF override, `common/validation.properties:11`), `{label}` being the input's own `label`
# in `schedule4ExistingLocation.xhtml` / `schedule4NewLocation.xhtml`. No verdict was given and nothing
# was stored.
#
# WHY THIS FILE WAS RETIRED AND IS BACK. The first version (2026-08-27, DIV-8) reproduced Check Status
# judging the SAVED locations — a stored Volume-only category it flagged. #465 (DIV-9, 2026-09-18)
# removed that finding for legacy parity, which left no saved state to flag, so the file was deleted.
# The on-screen behaviour above is what #359 group B restores; the rebuild had skipped it and answered
# "requirements met" over an invalid panel. These scenarios are therefore GREEN with the fix and are not
# tagged `@discovered-divergence`.
#
# WHY DISTANCE ONLY. Legacy's bug: on Truck Barge/Ferry, Crew Barge/Ferry and Rail Haul, a Volume or
# Cost entered first revealed the other two required fields in steps — first the Distance, then, once
# the Distance was in, the remaining amount — while a Distance (km) entered first showed both at once
# (`schedule4ExistingLocation.xhtml:524,550,572`; Crew Barge/Ferry `:607,632,654`; Rail Haul
# `:881,905,925`). The decision: a deliberate fix of that legacy behaviour, by BA decision
# (2026-09-29) — once any one of Distance/Volume/Cost has a value, all three are required and every
# missing one is reported at once, so Check Status (or Save) shows every required field of the row in
# one go (see `validation.feature` S22/S23). The set of valid rows is unchanged (only an all-empty or
# all-filled row passes, under either rule); only which missing fields are reported, and when,
# changes. These scenarios enter the Distance, whose answer is the same before and after the fix.
#
# THE INLINE MARKER STAYS. The rebuild's own `Value Required` under each missing cell is an enhancement
# legacy did not have; both are asserted, because the banner is where legacy's wording lives.
#
# ANCHORS AND WRITES. The existing-location scenario owns `check-unsaved` (9050/2015) — SEEDED by
# `real-test-data-patches/sch4/unsaved-check-anchors.sql` and the CI seed, because no free Draft
# mill-year was left in the extract. Its Given saves the location through the API; the scenario itself
# writes nothing (the write spy proves it) and the stored location still carries no amounts at the end.
# The new-location scenario saves nothing at all, so it runs on the shared validate-only `validation`
# anchor like every other client-blocked rejection: a second scenario on `check-unsaved` would race the
# first one's Given under `fullyParallel`, since that Given requires the anchor to be empty.

@UC-SCH4-001 @sch4 @check-status-unsaved
Feature: Schedule 4 — Check Status validates the open location panel first

  As a Licensee
  I want Check Status to check the entries I can see in the open panel before it gives a verdict
  So that I am never told the schedule is ready while the panel in front of me is incomplete

  @p1 @S33 @S34
  Scenario: A Distance typed into an existing location, unsaved, blocks Check Status until Volume and Cost are given
    Given the Schedule 4 anchor "check-unsaved" is an editable Draft with no locations
    And the Schedule 4 location "E2E Unsaved Check" is already saved with only its name
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Unsaved Check" for edit
    And I enter "50" in the Schedule 4 "Crew Barge/Ferry" "distance" cell
    And I check Schedule 4 status
    # The existing-location panel's labels (`schedule4ExistingLocation.xhtml:629,650`).
    Then the Schedule 4 error banner lists:
      | Crew Barge Ferry Volume (m3): Value is required. |
      | Crew Barge Ferry (Cost $): Value is required.    |
    And the Schedule 4 "Crew Barge/Ferry" "volume" cell is invalid with "Value Required"
    And the Schedule 4 "Crew Barge/Ferry" "cost" cell is invalid with "Value Required"
    And I should not see the message "All requirements for this schedule have been met"
    And I should not see the message "All requirements for E2E Unsaved Check have been met."
    # Nothing persisted: no write left the browser, and the stored location is as its Given left it.
    And the Schedule 4 write request should not have been sent
    And the stored Schedule 4 location "E2E Unsaved Check" carries no amounts

  @p1 @S33 @S34
  Scenario: A new location with no name and a Rail Haul Distance only blocks Check Status on all three fields
    Given the Schedule 4 anchor "validation" is an editable Draft with no locations
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "12" in the Schedule 4 "Rail Haul" "distance" cell
    And I check Schedule 4 status
    # The New Location panel's labels (`schedule4NewLocation.xhtml:14,206,209`) — its Volume label
    # carries `<sup>` markup that legacy's banner printed literally; it is rendered as m³ here.
    Then the Schedule 4 error banner lists:
      | Location Name: Value is required.                    |
      | Rail Haul (Volume m³): Value is required. |
      | Rail Haul (Cost $): Value is required.               |
    And the Schedule 4 location name field is invalid with "Location Name can not be empty. Please enter a description."
    And the Schedule 4 "Rail Haul" "volume" cell is invalid with "Value Required"
    And the Schedule 4 "Rail Haul" "cost" cell is invalid with "Value Required"
    And I should not see the message "All requirements for this schedule have been met"
    And the Schedule 4 write request should not have been sent
    And no Schedule 4 locations are stored
