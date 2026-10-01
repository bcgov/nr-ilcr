package ca.bc.gov.nrs.ilcr.schedule6.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * The Check Status body: the values currently ON SCREEN.
 *
 * <p>Legacy's Check Status was an {@code ajax="false"} full postback, so JSF applied the on-screen
 * inputs to the managed bean BEFORE {@code checkStatus()} evaluated it ({@code Schedule6MB}
 * :139-140) — the verdict always described the screen, and nothing was persisted. The shipped
 * implementation read the database instead, which was near-equivalent while an Edit button meant at
 * most one row could be unsaved; once every row is always editable (Task 7) the two disagree on
 * every keystroke. This DTO is the fix: the verdict is computed from exactly what it carries.
 *
 * <p>Deliberately NOT reusing {@link Schedule6SaveRequest}: its {@code recordId} and {@code
 * revisionCount} are both required, and this endpoint needs neither. Check Status addresses no
 * stored row and takes no optimistic lock, and an unsaved row on screen legitimately has no
 * revision token. Rows are identified by payload ordinal (1-based), which is exactly what {@code
 * rowCounter} has always meant.
 *
 * <p>Read-only: this type reaches no write path. The body itself is required — an absent body is a
 * clean 400 from Bean Validation, not a 500. {@code records} must be present (empty is a legitimate
 * "nothing on screen"); {@code @NotNull} here turns an omitted list into the same clean 400 rather
 * than an NPE against {@code request.records()} deep in {@code Schedule6Service#checkStatus}. The
 * element-type {@code @NotNull} on {@code List<@NotNull CheckEntry>} closes the same NPE for a
 * {@code null} ENTRY inside an otherwise-present list ({@code "records":[null]}) — the payload
 * equivalent of the just-closed omitted-list case, both are a clean 400, never a 500. Individual
 * {@link CheckEntry} FIELDS stay unvalidated (see below) — only the list's and each element's
 * presence is enforced.
 *
 * @param generalComments the comment currently on screen
 * @param records the rows currently on screen, in display order
 */
public record Schedule6CheckRequest(
    String generalComments,
    @NotNull(message = "{missingRequiredFieldMsg}") List<@NotNull(message = "{missingRequiredFieldMsg}") CheckEntry> records) {

  /**
   * One on-screen row. Unvalidated by design: Check Status REPORTS on incomplete input — a missing
   * field is exactly what it exists to surface as an "Action required" finding — so rejecting the
   * request with a 400 would defeat the endpoint's whole purpose. This is not an oversight.
   *
   * @param areaType the area type as entered ({@code "TFL"} or a TSA number)
   * @param tflNumber the TFL number as entered
   * @param supplyBlock the supply block as entered
   * @param volume the volume as entered (unused by the verdict — legacy never checks it either)
   * @param cost the cost as entered
   * @param comments the per-record comment as entered (unused by the verdict)
   */
  public record CheckEntry(
      String areaType,
      String tflNumber,
      String supplyBlock,
      BigDecimal volume,
      Integer cost,
      String comments) {}
}
