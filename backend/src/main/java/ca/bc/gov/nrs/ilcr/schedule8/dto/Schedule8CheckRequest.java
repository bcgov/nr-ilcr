package ca.bc.gov.nrs.ilcr.schedule8.dto;

/**
 * The all-pages Check Status body: the page panel currently ON SCREEN, if one is open
 * (bcgov/nr-ilcr#359, group C).
 *
 * <p>Legacy's Check Status walked its in-memory model with no reload, and the checked page inputs
 * wrote into that model on change ({@code Schedule8MB.java:139-159}), so an unsaved edit on an open
 * page moved the verdict. The shipped implementation re-read the database instead, so it could not.
 * This DTO restores the legacy behaviour: the verdict is computed from the stored pages with {@code
 * page} overlaid onto the one with the same id, and nothing is persisted.
 *
 * <p><strong>Why one panel and not the whole document.</strong> The open panel is the only thing
 * the client knows that the server does not. A whole-document payload would make the client assert
 * values for pages it is not displaying and cannot have changed, from its own possibly stale copy —
 * the #476 reasoning. Samples are NOT carried: legacy loaded them once and they are edited on
 * another view, so both paths read them from the database.
 *
 * <p><strong>A new page is never evaluated.</strong> Legacy's Add built the new page outside the
 * checked list ({@code Schedule8MB.java:193-198}), so its final check never saw it. Only an
 * EXISTING page is overlaid, matched by id; a null id, or one that matches nothing stored (deleted
 * in another session), is ignored rather than evaluated.
 *
 * <p>Deliberately NOT reusing {@link Schedule8PageRequest}: its members are validated and its
 * {@code revisionCount} exists for writes. Check Status addresses no stored row for writing, takes
 * no optimistic lock, and must ACCEPT incomplete input — reporting it is its entire purpose.
 *
 * <p>Read-only: this type reaches no write path. {@code page} is nullable and absent means "no page
 * panel is open", which evaluates the stored pages alone. An absent BODY, by contrast, is a clean
 * 400 from {@code @RequestBody}'s own required-ness rather than a 500.
 *
 * @param page the page panel currently on screen, or null when none is open
 */
public record Schedule8CheckRequest(PageEntry page) {

  /**
   * The open page panel as on screen: the checked fields, plus Cutting Permit for the page label.
   * Unvalidated by design: a blank field is exactly what Check Status exists to surface. Blank is
   * sent as null; the server also treats whitespace as blank, as Save does.
   *
   * @param id the page's stored id; null (a new page) or an id matching no stored page is ignored
   * @param division the on-screen Division
   * @param contact the on-screen Contact
   * @param phone the on-screen Phone
   * @param tsaNumber the TSA-or-TFL selector: a TSA code, or the {@code "TFL"} marker. Mapped
   *     through Save's own rule, so a TFL page with a blank TFL # reports {@code TFL #}; carried
   *     into the page label
   * @param tflNumber the on-screen TFL # (TFL pages)
   * @param supplyBlock the on-screen Supply Block (TSA pages)
   * @param cuttingPermit the on-screen Cutting Permit; not checked, carried into the page label
   */
  public record PageEntry(
      Integer id,
      String division,
      String contact,
      String phone,
      String tsaNumber,
      String tflNumber,
      String supplyBlock,
      String cuttingPermit) {}
}
