# Per-row optimistic locking — closes GAP-3.
#
# NO LEGACY SLICE DESCRIBES THIS (see SPEC-1). Legacy's page-level Save had no optimistic lock at all — the
# last writer silently won — so there is no `@S<NN>` tag to hang this on; same situation as Schedule 1's
# `clear-amounts.feature`, and tagged the same way (priority only).
#
# WHY THIS EXISTS WHEN AN IT ALREADY COVERS THE ENDPOINT.
# `Schedule11CorrectionIT.staleRevision_409_writesNothing()` proves the SERVER returns 409 for a stale
# token. It says nothing about what the USER sees. That is the risk worth testing: if the app swallowed the
# 409, someone would believe their correction saved when it had not — the reject arm below is what proves it
# does not.
#
# HOW THE CONFLICT IS STAGED without a second browser.
# The page-level Save sends each edited row with the revisionCount it was served (and edited) at (Story
# 26.2). So: open the page, change the row through the API (bumping the stored token), then edit and save
# from the browser — the PUT carries the token from the page load and is now stale. One context, one API
# call.
#
# NO MUTATION SPY HERE, deliberately. Every other rejection in this suite is client-side, so its proof is
# that NO request was sent. This rejection is the opposite: the stale PUT *is* sent and the SERVER rejects
# it with a 409. A spy would set up a zero-write claim that must not hold — the proof is the error message
# plus the other session's value surviving the read-back below.
#
# SINGLE-OWNER SCENARIO — prove it non-flaky SERIALLY, not in parallel:
#   npm test -- --grep @concurrency --repeat-each=5 --workers=1
# `--repeat-each` WITHOUT `--workers=1` self-collides: every copy seeds the same (location, biogeo) pair on
# the one anchor and the app's own uniqueness rule rejects the duplicates with
# 409 "The Biogeo/Subzone/Variant has to be unique for a location." That is the harness colliding with
# itself, not a scenario defect (and it independently corroborates the GAP-4 duplicate rule). Same
# constraint Schedule 1 records for its single-owner destructive scenarios.
#
# The other session's value is asserted as the SURVIVOR. That is the real guarantee: a lost-update bug
# would silently overwrite it with ours, and asserting only the error message would not catch that. And the
# page keeps OUR pending edit after the refusal (26.2: a refused Save never discards the user's work).

@sch11 @UC-SCH11-001 @concurrency
Feature: Report Basic Silviculture Costs (Schedule 11) — concurrent edits are rejected, not lost
  As a mill reporter sharing a schedule with a colleague
  I want to be told when the row I am editing has changed underneath me
  So that I do not silently overwrite someone else's correction

  @p1
  Scenario: Saving a row that another session already changed is rejected, and their value survives
    Given the Schedule 11 anchor "stale-edit" has a seeded location "E2E stale edit"
    And I have selected that mill and reporting year on the Home page
    # Seeded at actual 5000. The page is served the row at its current revisionCount.
    When I open Schedule 11
    # Someone else saves first — this succeeds and moves the token on.
    And another session changes the Schedule 11 location "E2E stale edit" to actual cost 4242
    # Our Save now carries the token the page was served before their change.
    And I change the Schedule 11 location "E2E stale edit" field "Actual Cost" to "7777"
    And I save Schedule 11
    # Verbatim from the 409 ProblemDetail detail — the app renders the server's text unchanged (AD-8).
    Then I should see the error "This schedule was changed by another user. Please reload and try again."
    # Our edit is still on the page, for the user to decide on after reloading.
    And the Schedule 11 row "E2E stale edit" shows "7777" in "Actual Cost ($)"
    # The conflict must NOT be silently swallowed: our value is not stored, theirs is.
    And the Schedule 11 location "E2E stale edit" is persisted as:
      | field       | value |
      | Actual Cost | 4242  |
