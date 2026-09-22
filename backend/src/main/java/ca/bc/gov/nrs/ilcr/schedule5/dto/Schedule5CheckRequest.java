package ca.bc.gov.nrs.ilcr.schedule5.dto;

import java.math.BigDecimal;

/**
 * The Check Status body: the camp panel currently ON SCREEN, if one is open.
 *
 * <p>Legacy's Check Status was an {@code ajax="false"} full postback, so JSF applied the on-screen
 * inputs to the managed bean BEFORE {@code checkStatus()} evaluated it ({@code
 * Schedule5MB.checkStatus} :321) — the verdict always described the screen, and nothing was
 * persisted. The shipped implementation read the database instead and compensated by DISABLING the
 * button whenever a camp panel was open, which is what bcgov/nr-ilcr#476 reported. This DTO is the
 * fix: the verdict is computed from the stored camps with {@code camp} overlaid onto them.
 *
 * <p><strong>Why one camp and not every camp, unlike {@code Schedule6CheckRequest}.</strong>
 * Schedule 6 sends all its records because all of them are on screen and independently editable.
 * Schedule 5 is the opposite shape: the camps table renders NAMES only, and all four checked
 * descriptors live inside the single open panel. A whole-list payload would make the client assert
 * values for camps it is not displaying and cannot have changed, sourced from its own possibly
 * stale copy of the document — so a reporter editing camp A could be told something about camp B
 * that the database disagrees with. The panel is the only thing the client knows that the server
 * does not, so the panel is all it sends. This also matches what legacy's postback actually
 * achieved: the bean held DB-loaded camps and JSF wrote the open panel's inputs over one of them.
 *
 * <p>The itemized Other Camp / Other Access expense rows are NOT carried. They are edited on
 * separate sub-pages and are never on this screen, so both the payload path and the stored path
 * read them from the database — the two agree there by construction.
 *
 * <p>Deliberately NOT reusing {@link CampRequest}: its {@code revisionCount} exists for writes and
 * its fields are validated, while Check Status addresses no stored row, takes no optimistic lock,
 * and must ACCEPT incomplete input — reporting incomplete input is its entire purpose.
 *
 * <p>Read-only: this type reaches no write path. {@code camp} is nullable and absent means "no
 * panel is open", which is the ordinary case and evaluates the stored camps alone. An absent BODY,
 * by contrast, is a clean 400 from {@code @RequestBody}'s own required-ness rather than a 500.
 *
 * @param camp the camp panel currently on screen, or null when none is open
 */
public record Schedule5CheckRequest(CampEntry camp) {

  /**
   * The open camp panel as typed. Unvalidated by design: Check Status REPORTS on incomplete input —
   * a missing descriptor is exactly what it exists to surface as a {@code Value Required} finding —
   * so rejecting the request with a 400 would defeat the endpoint's purpose. This is not an
   * oversight.
   *
   * <p><strong>Nulls must arrive as nulls.</strong> The three numeric descriptors are pure null
   * tests on both paths (a stored {@code 0} PASSES — the D2 precedent), so coercing a missing or
   * unparseable value to zero anywhere on the way here turns every missing descriptor into a pass.
   * The client sends null for a field that is blank or that does not parse; "the screen has no
   * usable value there" and "the value is required" are the same statement.
   *
   * @param campId the camp's stored id, or null when the panel holds a camp that has never been
   *     saved (a new or copied camp). A null id is evaluated as an ADDITIONAL camp beside the
   *     stored ones — it is on screen, so legacy evaluated it too. A non-null id that matches no
   *     stored camp (deleted in another session) is likewise evaluated rather than silently
   *     dropped.
   * @param campName the camp name as entered. Tested TRIMMED, so a whitespace-only name fails
   * @param roadDistanceToOperatingArea the road distance as entered
   * @param sizeOfCamp the size of camp as entered
   * @param associatedCampVolume the associated camp volume as entered
   */
  public record CampEntry(
      Integer campId,
      String campName,
      BigDecimal roadDistanceToOperatingArea,
      Integer sizeOfCamp,
      BigDecimal associatedCampVolume) {}
}
