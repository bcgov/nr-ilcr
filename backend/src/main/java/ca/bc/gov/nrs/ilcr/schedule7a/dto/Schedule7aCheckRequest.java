package ca.bc.gov.nrs.ilcr.schedule7a.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * The Check Status body: every bridge row currently ON SCREEN (bcgov/nr-ilcr#359, group B).
 *
 * <p>Legacy's Check Status evaluated the bean's in-memory document without reloading it ({@code
 * Schedule7aMB.java:194-196}), and every row input wrote into that document on change ({@code
 * schedule7A.xhtml:582-587}) — including rows on other paginator pages. So the verdict described
 * the screen, unsaved edits included, and nothing was persisted. The shipped implementation re-read
 * the database instead, so an unsaved edit was invisible to the verdict in both directions. This
 * DTO restores the legacy behaviour; it is Schedule 6's shape (every row, by payload ordinal).
 *
 * <p>The Add panel is NOT part of the check: legacy's Add saved at once ({@code
 * Schedule7aMB.java:323-332}), so a drafted bridge that was never added is not a row.
 *
 * <p>Deliberately NOT reusing {@link BridgeRequest}: its fields are validated (twelve of them
 * required) and its {@code revisionCount} exists for writes. Check Status addresses no stored row,
 * takes no optimistic lock, and must ACCEPT incomplete input — reporting incomplete input is its
 * entire purpose. Rows are identified by payload ordinal (1-based), exactly what {@code rowCounter}
 * has always meant. The five type codes and the comments are absent because the check reads none of
 * them.
 *
 * <p>Read-only: this type reaches no write path. The body is required (an absent one is a clean
 * 400); {@code bridges} must be present (empty is a legitimate "nothing on screen"), and so must
 * each element — both {@code @NotNull}s turn a malformed body into a clean 400 rather than an NPE.
 * The {@link BridgeEntry} FIELDS stay unvalidated. Both carry the DEFAULT Bean Validation message
 * on purpose: a missing list or a null element is a malformed request, not a field left blank, so
 * it must not read as "Value Required".
 *
 * @param bridges the bridge rows currently on screen, in display order
 */
public record Schedule7aCheckRequest(@NotNull List<@NotNull BridgeEntry> bridges) {

  /**
   * One on-screen bridge row: the seven attributes and ten costs the check reads, named as the
   * served {@link Bridge} names them. Unvalidated by design: a missing value is exactly what Check
   * Status exists to surface as "Value Required".
   *
   * <p><strong>Nulls must arrive as nulls.</strong> Every check is a null test (a typed {@code 0}
   * PASSES), so coercing a blank field to zero anywhere on the way here would turn a missing value
   * into a pass. A blank {@code builtDate} is read as absent; a blank name is flagged, as it is on
   * the stored path.
   *
   * @param locationName the name / location as entered
   * @param builtDate the built date ({@code yyyy-MM}) as entered; not parsed — only its presence is
   *     checked
   * @param lifeSpan the expected life span as entered
   * @param abutmentHeight the abutments height as entered
   * @param length the length (m) as entered
   * @param width the deck width (m) as entered
   * @param distance the distance (km) as entered
   * @param sitePlanCost the site plan / general arrangement cost (item 70)
   * @param superstructureMaterialCost the superstructure material cost (item 79)
   * @param superstructureDeliverCost the superstructure deliver cost (item 80)
   * @param superstructureInstallCost the superstructure install cost (item 81)
   * @param abutmentMaterialCost the abutments material cost (item 74)
   * @param abutmentDeliverCost the abutments deliver cost (item 75)
   * @param abutmentInstallCost the abutments install cost (item 76)
   * @param approachCost the approach works cost (item 71)
   * @param afterInstallCost the certification-after-install cost (item 72)
   * @param otherCost the other costs (item 73)
   */
  public record BridgeEntry(
      String locationName,
      String builtDate,
      Integer lifeSpan,
      BigDecimal abutmentHeight,
      BigDecimal length,
      BigDecimal width,
      Integer distance,
      Integer sitePlanCost,
      Integer superstructureMaterialCost,
      Integer superstructureDeliverCost,
      Integer superstructureInstallCost,
      Integer abutmentMaterialCost,
      Integer abutmentDeliverCost,
      Integer abutmentInstallCost,
      Integer approachCost,
      Integer afterInstallCost,
      Integer otherCost) {}
}
