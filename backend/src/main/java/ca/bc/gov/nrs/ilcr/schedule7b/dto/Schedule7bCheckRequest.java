package ca.bc.gov.nrs.ilcr.schedule7b.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * The Check Status body: every culvert row currently ON SCREEN (bcgov/nr-ilcr#359, group B).
 *
 * <p>Legacy's Check Status evaluated the bean's in-memory document without reloading it ({@code
 * Schedule7bMB.java:123-125}), and every row input wrote into that document on change ({@code
 * schedule7B.xhtml:376-380}) — including rows on other paginator pages. So the verdict described
 * the screen, unsaved edits included, and nothing was persisted. The shipped implementation re-read
 * the database instead, so an unsaved edit was invisible to the verdict in both directions. This
 * DTO restores the legacy behaviour; it is Schedule 6's shape (every row, by payload ordinal).
 *
 * <p>The Add panel is NOT part of the check: legacy's Add saved at once ({@code
 * Schedule7bMB.java:185-191}), so a drafted culvert that was never added is not a row.
 *
 * <p>Deliberately NOT reusing {@link CulvertRequest}: its fields are validated and its {@code
 * revisionCount} exists for writes. Check Status addresses no stored row, takes no optimistic lock,
 * and must ACCEPT incomplete input — reporting incomplete input is its entire purpose. Rows are
 * identified by payload ordinal (1-based), exactly what {@code rowCounter} has always meant.
 *
 * <p>Read-only: this type reaches no write path. The body is required (an absent one is a clean
 * 400); {@code culverts} must be present (empty is a legitimate "nothing on screen"), and so must
 * each element — both {@code @NotNull}s turn a malformed body into a clean 400 rather than an NPE.
 * The {@link CulvertEntry} FIELDS stay unvalidated. Both carry the DEFAULT Bean Validation message
 * on purpose: a missing list or a null element is a malformed request, not a field left blank, so
 * it must not read as "Value Required".
 *
 * @param culverts the culvert rows currently on screen, in display order
 */
public record Schedule7bCheckRequest(@NotNull List<@NotNull CulvertEntry> culverts) {

  /**
   * One on-screen culvert row, exactly the values the check reads. Unvalidated by design: a missing
   * value is exactly what Check Status exists to surface as "Value Required". Rise is absent
   * because the check never reads it, for any type.
   *
   * <p><strong>Nulls must arrive as nulls.</strong> Every check is a pure null test (a typed {@code
   * 0} PASSES), so coercing a blank field to zero anywhere on the way here would turn a missing
   * value into a pass. The type is read from here too, so a type switched on screen but not saved
   * moves the type-conditional rules.
   *
   * @param culvertTypeCode the culvert type code as selected
   * @param spanSize the span as entered
   * @param length the length as entered
   * @param culvertPieceCount the number of pieces as entered
   * @param materialCost the material cost as entered
   * @param installCost the installation cost as entered
   * @param comments the comments as entered (tested untrimmed, as legacy did)
   */
  public record CulvertEntry(
      String culvertTypeCode,
      Integer spanSize,
      BigDecimal length,
      Integer culvertPieceCount,
      Integer materialCost,
      Integer installCost,
      String comments) {}
}
