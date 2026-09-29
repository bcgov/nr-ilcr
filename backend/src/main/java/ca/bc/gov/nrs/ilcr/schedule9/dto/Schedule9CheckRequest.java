package ca.bc.gov.nrs.ilcr.schedule9.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * The Check Status body: every contractual-work row currently ON SCREEN (bcgov/nr-ilcr#359, group
 * B).
 *
 * <p>Legacy's Check Status evaluated the bean's in-memory document without reloading it ({@code
 * Schedule9MB.java:106-108} → {@code Schedule9CheckStatus.java:35}), and every row input wrote into
 * that document on change ({@code schedule9.xhtml:431-435}) — including rows on other paginator
 * pages. So the verdict described the screen, unsaved edits included, and nothing was persisted.
 * The shipped implementation re-read the database instead, so an unsaved edit was invisible to the
 * verdict in both directions. This DTO restores the legacy behaviour; it is Schedule 6's shape
 * (every row, by payload ordinal).
 *
 * <p>The Add panel is NOT part of the check: legacy's Add saved at once ({@code
 * Schedule9MB.java:69-72}), so a drafted record that was never added is not a row.
 *
 * <p>Deliberately NOT reusing {@link ContractualWorkRecordRequest}: its fields are validated and
 * its {@code revisionCount} exists for writes. Check Status addresses no stored row, takes no
 * optimistic lock, and must ACCEPT incomplete — even out-of-range — input: reporting it is the
 * check's entire purpose, and the range lines are the check's own. Rows are identified by payload
 * ordinal (1-based), exactly what the row number in {@code "Contractual Work Report Id : N"} has
 * always meant. Field names follow {@link ContractualWorkRecordRequest}.
 *
 * <p>Read-only: this type reaches no write path. The body is required (an absent one is a clean
 * 400); {@code records} must be present (empty is a legitimate "nothing on screen"), and so must
 * each element — both {@code @NotNull}s turn a malformed body into a clean 400 rather than an NPE.
 * The {@link RecordEntry} FIELDS stay unvalidated. Both carry the DEFAULT Bean Validation message
 * on purpose: a missing list or a null element is a malformed request, not a field left blank, so
 * it must not read as "Value Required".
 *
 * @param records the contractual-work rows currently on screen, in display order
 */
public record Schedule9CheckRequest(@NotNull List<@NotNull RecordEntry> records) {

  /**
   * One on-screen row: exactly the eight values the check reads. Unvalidated by design: a missing
   * or out-of-range value is exactly what Check Status exists to surface.
   *
   * <p><strong>Nulls must arrive as nulls.</strong> Side Slope, Number of Units and Cost are null
   * tests (a typed {@code 0} PASSES), so coercing a blank field to zero anywhere on the way here
   * would turn a missing value into a pass. The item is read from here too, so an item switched to
   * 111/112 on screen but not saved brings the Side Slope check into force.
   *
   * @param contractorId the Company ID as entered
   * @param contractualItemCode the contractual item code as selected
   * @param sideSlopePct the side slope % as entered (checked only for items 111/112)
   * @param numberOfUnits the number of units as entered
   * @param unitCode the unit type code as selected
   * @param biogeoclimaticZone the biogeoclimatic zone code as selected
   * @param cost the cost as entered
   * @param sourceCode the source code as selected
   */
  public record RecordEntry(
      String contractorId,
      Integer contractualItemCode,
      Integer sideSlopePct,
      BigDecimal numberOfUnits,
      String unitCode,
      String biogeoclimaticZone,
      Integer cost,
      String sourceCode) {}
}
