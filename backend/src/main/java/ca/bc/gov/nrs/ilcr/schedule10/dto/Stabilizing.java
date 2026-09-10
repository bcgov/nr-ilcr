package ca.bc.gov.nrs.ilcr.schedule10.dto;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Map;

/**
 * The additional-stabilizing substructure. Unlike the sub-grade it has no deduction lines, so its
 * total is a plain sum of the three cost terms.
 *
 * @param ballastMethodCode the ballast method code ({@code N}, {@code C} or {@code D})
 * @param ballastMaterialCode the ballast material code
 * @param length the stabilizing length in km
 * @param surfaceWidth the stabilizing surface width in m; legacy auto-copies this from the
 *     sub-grade width on entry (S14), a write-path behaviour that does not affect this read
 * @param depth the stabilizing depth in m
 * @param distanceToSource the distance to the material source
 * @param actualCost the actual cost
 * @param ttTransfer the tree-to-truck transfer, which may be negative
 * @param otherTransfer the other transfer, which may be negative
 * @param total DERIVED: actual + both transfers
 * @param costPerLength DERIVED: total divided by length; absent when length is zero or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Stabilizing(
    String ballastMethodCode,
    String ballastMaterialCode,
    BigDecimal length,
    BigDecimal surfaceWidth,
    BigDecimal depth,
    BigDecimal distanceToSource,
    BigDecimal actualCost,
    BigDecimal ttTransfer,
    BigDecimal otherTransfer,
    BigDecimal total,
    BigDecimal costPerLength,
    Map<String, OriginalValue> originalValues) {

  /**
   * Without original values — the shape every caller that is not serving a stored, beyond-Draft
   * document uses (a print mapper, a check-status projection, a test fixture). The canonical
   * constructor is the one the read path uses (Story 16.2).
   */
  public Stabilizing(
      String ballastMethodCode,
      String ballastMaterialCode,
      BigDecimal length,
      BigDecimal surfaceWidth,
      BigDecimal depth,
      BigDecimal distanceToSource,
      BigDecimal actualCost,
      BigDecimal ttTransfer,
      BigDecimal otherTransfer,
      BigDecimal total,
      BigDecimal costPerLength) {
    this(
        ballastMethodCode,
        ballastMaterialCode,
        length,
        surfaceWidth,
        depth,
        distanceToSource,
        actualCost,
        ttTransfer,
        otherTransfer,
        total,
        costPerLength,
        null);
  }
}
