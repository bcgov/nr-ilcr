package ca.bc.gov.nrs.ilcr.reporting;

/**
 * The two content print flags passed through to every schedule template (the legacy {@code
 * p_do_print_body} / {@code p_do_print_comment} gating): {@code printBody} shows the expense/record
 * body, {@code printComment} shows the comments block. Kept as its own value type so the fill API
 * does not depend on the request DTO.
 *
 * @param printBody render the template body ({@code p_do_print_body})
 * @param printComment render the comments block ({@code p_do_print_comment})
 */
public record PrintOptions(boolean printBody, boolean printComment) {

  /** Both flags on — the single-schedule Story 20.1 proof shows everything. */
  public static PrintOptions showEverything() {
    return new PrintOptions(true, true);
  }
}
