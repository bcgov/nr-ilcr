# Re-grounded from the UC-SCH6-001 Gherkin for S06, S07 and S08 (legacy JSF/PrimeFaces) — the three
# context guards. Each one suppresses the whole Schedule 6 data-entry surface and tells the reporter
# what to change.
#
# WHAT RE-GROUNDING CHANGED
#  * Legacy rendered these into a `p:messages` panel. The rebuilt page returns a load state INSTEAD of
#    the body (index.tsx:840) and shows a Carbon notification carrying an explicit severity word, so
#    the data-entry controls are ABSENT rather than present-but-disabled — the assertions check
#    absence, because "disabled" would pass vacuously against an element that is not there.
#  * S06's MESSAGE HAS NO TRAILING SPACE. The legacy scenario quotes "… in the Home Page. " WITH one,
#    because that is the SERVER's ERR-001 literal. This guard never reaches the server — the page
#    suppresses the request entirely — so what renders is the CLIENT literal, which carries none by
#    sibling convention (index.tsx:52-55 states this, and notes the server's spaced version still
#    renders verbatim when a request genuinely returns it). Recorded as defects.md VER-4.
#  * The three guards are NOT one Scenario Outline, deliberately. S07 is a CONTEXT guard with its own
#    title, while S08 is a genuine load failure that keeps the generic one — and asserting that
#    difference is the point (see below). Folding them together would have hidden it. sch5 had to split
#    its equivalent pair for the same reason.
#
# THE ASSERTION THAT EARNS ITS PLACE: THE TWO FRAMINGS MUST NOT BE SWAPPED.
# S07 and S08 read almost identically — both suppress the page and show a message — so each scenario
# asserts the OTHER's title is absent/present. A mill closed for the reporting year is a context the
# reporter fixes on the Home page, so it carries "Mill not active for Reporting Year" and must NOT say
# "Unable to load Schedule 6"; a missing record genuinely IS a load failure and must. The detail text
# alone cannot tell them apart, so without these the two guards could be transposed and both still
# pass.
#
# ANCHORS — neither guard writes anything, and S06 needs no anchor at all:
#  * S07 = 1/2017, REUSED from the extract rather than minted. It is one of only two Draft cells the
#    whole suite left unpinned, and what makes it useless as a mutating anchor is exactly what makes it
#    right here: mill 1 is CLS, so the GET answers 409. Minting a closed cell in 2024 was rejected —
#    flipping any mill's ILCR_MILL_STATUS_XREF to manufacture a guard would silently redden the
#    closed-mill guards sch2/sch3/sch4/sch5 already pin. Its 2017 report-status row had to be added to
#    the CI seed: a row must EXIST for the year, or MillContextService answers 404 first and the 409 is
#    never reached.
#  * S08 = 23050/2024, a hole CARVED in sch6's own minted year — the patch opens 2024 for six mills and
#    skips this one. The mill IS seeded and holds 2017-2023 rows, so the mill resolves and only the
#    YEAR is missing, which is what makes it a 404 rather than an unknown-mill failure. Registered in
#    DELIBERATELY_ABSENT in preflight/ci-seed-parity.setup.ts, whose reverse check FAILS if anyone ever
#    seeds it — seeding it would delete the fixture, not fix it.
#
# Both guard anchors are also proved at the API in preflight AND again in each scenario's Given, status
# and detail both. A guard's fixture is a failure mode, and failure modes rot quietly: if 1/2017's mill
# were reopened the GET would start answering 200 and the scenario would fail on a missing banner,
# which reads as a UI defect rather than as drifted data.
#
# NOT COVERED HERE (see coverage.md): the read-only render of a non-Draft report is S17 and will join
# this file when it lands — it is a render state, not a guard, and the page is fully present.

@sch6 @UC-SCH6-001 @render-states
Feature: Report Road Management Costs (Schedule 6) — the context guards suppress data entry
  As a mill reporter
  I want to be told which part of my working context is wrong
  So that I can fix it on the Home page instead of facing an empty or broken Schedule 6

  # S06 / ERR-001 — no working mill and year. The one slice needing no anchor at all.
  @p1 @S06 @ERR-001
  Scenario: With no working mill and reporting year the page is suppressed
    When I open Schedule 6 with no working context
    Then the Schedule 6 mill and reporting year guard is shown
    And the Schedule 6 data-entry surface is suppressed

  # S07 / ERR-002 — a mill closed for the reporting year: its own title, because what the reporter has
  # to do is pick another mill on the Home page.
  @p1 @S07 @ERR-002
  Scenario: A mill that is not active for the reporting year is blocked
    Given the Schedule 6 guard anchor "closed-mill"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6 expecting a guard message
    Then the Schedule 6 page shows the closed-mill guard
    And the Schedule 6 data-entry surface is suppressed

  # S08 / ERR-003 — no Schedule 6 record for the pair IS a load failure, and keeps the generic title.
  @p1 @S08 @ERR-003
  Scenario: A mill and year with no Schedule 6 record is blocked
    Given the Schedule 6 guard anchor "not-found"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6 expecting a guard message
    Then the Schedule 6 page is blocked as a load failure
    And the Schedule 6 data-entry surface is suppressed
