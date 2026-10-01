package ca.bc.gov.nrs.ilcr.dto.base;

/**
 * The two {@code outcome} tokens carried by the {@code outcome/messages} Check Status family
 * (Schedules 2, 4, 5, 6, 8 and 10).
 *
 * <p>Hoisted here by Story 15.0 (Task 7) because the literals were written out in six places — a
 * private {@code OUTCOME_MET}/{@code OUTCOME_ISSUES} pair in five schedule services plus the public
 * pair on {@code Schedule10CheckStatusResponse} — and Story 15.1's cross-schedule sweep has to
 * compare against them to decide overall readiness. Without a shared home the sweep would have
 * become the seventh copy.
 *
 * <p><strong>This is a constant hoist, NOT a consolidation of the response families.</strong> The
 * two families survive deliberately: six schedules answer {@code outcome/messages} and six answer
 * {@code requirementsMet/errors}, Story 29.12 explicitly refused to unify them ("this is a naming
 * fix, not a consolidation") and 29.7 was descoped for the same reason. Nothing here changes a DTO,
 * a field name or a wire byte — the string values are identical to the ones every consumer already
 * receives.
 *
 * <p>Deliberately strings rather than an enum: the token is a wire value on a pinned contract
 * (AD-12), and an enum on a DTO invites a serialization change nobody asked for.
 */
public final class CheckStatusOutcome {

  /** Every checked requirement passes. Zero rows is a vacuous {@code MET} on every schedule. */
  public static final String MET = "MET";

  /** At least one requirement is outstanding. */
  public static final String ISSUES = "ISSUES";

  private CheckStatusOutcome() {}
}
