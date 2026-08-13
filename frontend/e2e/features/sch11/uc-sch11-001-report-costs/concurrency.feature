# Per-row optimistic locking — closes GAP-3.
#
# NO LEGACY SLICE DESCRIBES THIS (see SPEC-1). Legacy had a single page-level Save and no per-row
# concurrency concept at all, so there is no `@S<NN>` tag to hang this on — same situation as Schedule 1's
# `clear-amounts.feature`, and tagged the same way (priority only).
#
# WHY THIS EXISTS WHEN AN IT ALREADY COVERS THE ENDPOINT.
# `Schedule11WriteIT.staleAndMissingRevision()` proves the SERVER returns 409 for a stale token. It says
# nothing about what the USER sees. That is the risk worth testing: if the app swallowed the 409, someone
# would believe their correction saved when it had not — the reject arm below is what proves it does not.
# (That IT *does* run in CI: `analysis.yml` passes `-Dskip.integration.tests=false` — added in dc6c1bb —
# even though `backend/pom.xml` still defaults the property to true, and the step is path-filtered to
# `backend/`, so no backend change merges without it. The endpoint is gated; this scenario adds the UI arm.)
#
# HOW THE CONFLICT IS STAGED without a second browser.
# `startEdit` copies that row's `revisionCount` into React state the moment Edit is clicked. So: open the
# editor, change the row through the API (bumping the stored token), then save from the browser — the PUT
# carries the token captured earlier and is now stale. One context, one API call.
#
# NO MUTATION SPY HERE, deliberately. Every other rejection in this suite is client-side, so its proof is
# that NO request was sent. This rejection is the opposite: the stale PUT *is* sent and the SERVER rejects
# it with a 409. A spy would set up a zero-write claim that must not hold — the proof is the error message
# plus the other session's value surviving the read-back below.
#
# SINGLE-OWNER SCENARIO — prove it non-flaky SERIALLY, not in parallel:
#   npm test -- --grep @concurrency --repeat-each=5 --workers=1     (33/33 on 2026-08-10)
# `--repeat-each` WITHOUT `--workers=1` self-collides: every copy seeds the same (location, biogeo) pair on
# the one anchor and the app's own uniqueness rule rejects the duplicates with
# 409 "The Biogeo/Subzone/Variant has to be unique for a location." That is the harness colliding with
# itself, not a scenario defect (and it independently corroborates the GAP-4 duplicate rule). Same
# constraint Schedule 1 records for its single-owner destructive scenarios.
#
# The other session's value is asserted as the SURVIVOR. That is the real guarantee: a lost-update bug
# would silently overwrite it with ours, and asserting only the error message would not catch that.

@sch11 @UC-SCH11-001 @concurrency
Feature: Report Basic Silviculture Costs (Schedule 11) — concurrent edits are rejected, not lost
  As a mill reporter sharing a schedule with a colleague
  I want to be told when the row I am editing has changed underneath me
  So that I do not silently overwrite someone else's correction

  @p1
  Scenario: Saving a row that another session already changed is rejected, and their value survives
    Given the Schedule 11 anchor "stale-edit" has a seeded location "E2E stale edit"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    # Seeded at actual 5000. Opening the editor captures this row's revisionCount.
    And I start editing the Schedule 11 location "E2E stale edit"
    # Someone else saves first — this succeeds and moves the token on.
    And another session changes the Schedule 11 location "E2E stale edit" to actual cost 4242
    # Our save now carries the token captured before their change.
    And I change the inline "Actual Cost" to "7777"
    And I save the inline edit
    # Verbatim from the 409 ProblemDetail detail — the app renders the server's text unchanged (AD-8).
    Then I should see the error "This schedule was changed by another user. Please reload and try again."
    # The conflict must NOT be silently swallowed: our value is not stored, theirs is.
    And the Schedule 11 location "E2E stale edit" is persisted as:
      | field       | value |
      | Actual Cost | 4242  |
