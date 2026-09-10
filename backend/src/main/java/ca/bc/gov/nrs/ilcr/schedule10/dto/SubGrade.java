package ca.bc.gov.nrs.ilcr.schedule10.dto;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Map;

/**
 * The sub-grade substructure: dimensions, three cost lines, and the six "Less" deduction lines.
 *
 * <p>Every {@code total*} and {@code costPerLength} value is DERIVED server-side and is read-only —
 * present in responses, ignored or rejected on write (AD-5, AD-12). The arithmetic lives in {@code
 * Schedule10Amounts} and nowhere else.
 *
 * <p><strong>Null is not zero.</strong> When a road detail has no cost lines at all — the normal
 * shape in the real delivery database — every field here is absent from the JSON rather than
 * serialized as {@code 0}.
 *
 * @param length the sub-grade length in km
 * @param surfaceWidth the sub-grade surface width in m
 * @param actualCost the actual cost
 * @param ttTransfer the tree-to-truck transfer, which may be negative (BR-09)
 * @param otherTransfer the other transfer, which may be negative (BR-09)
 * @param lessBridges deduction for bridges
 * @param lessCulverts deduction for culverts
 * @param lessLandings deduction for landings
 * @param lessOverland deduction for overland
 * @param lessOtherEng deduction for other engineering; stored under cost subcategory 3, not 1
 * @param lessEndHaul deduction for end haul
 * @param totalCosts DERIVED: actual + both transfers
 * @param totalDeductions DERIVED: the six deduction lines
 * @param total DERIVED: totalCosts minus totalDeductions
 * @param costPerLength DERIVED: total divided by length; absent when length is zero or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubGrade(
    BigDecimal length,
    BigDecimal surfaceWidth,
    BigDecimal actualCost,
    BigDecimal ttTransfer,
    BigDecimal otherTransfer,
    BigDecimal lessBridges,
    BigDecimal lessCulverts,
    BigDecimal lessLandings,
    BigDecimal lessOverland,
    BigDecimal lessOtherEng,
    BigDecimal lessEndHaul,
    BigDecimal totalCosts,
    BigDecimal totalDeductions,
    BigDecimal total,
    BigDecimal costPerLength,
    Map<String, OriginalValue> originalValues) {

  /**
   * Without original values — the shape every caller that is not serving a stored, beyond-Draft
   * document uses (a print mapper, a check-status projection, a test fixture). The canonical
   * constructor is the one the read path uses (Story 16.2).
   */
  public SubGrade(
      BigDecimal length,
      BigDecimal surfaceWidth,
      BigDecimal actualCost,
      BigDecimal ttTransfer,
      BigDecimal otherTransfer,
      BigDecimal lessBridges,
      BigDecimal lessCulverts,
      BigDecimal lessLandings,
      BigDecimal lessOverland,
      BigDecimal lessOtherEng,
      BigDecimal lessEndHaul,
      BigDecimal totalCosts,
      BigDecimal totalDeductions,
      BigDecimal total,
      BigDecimal costPerLength) {
    this(
        length,
        surfaceWidth,
        actualCost,
        ttTransfer,
        otherTransfer,
        lessBridges,
        lessCulverts,
        lessLandings,
        lessOverland,
        lessOtherEng,
        lessEndHaul,
        totalCosts,
        totalDeductions,
        total,
        costPerLength,
        null);
  }
}
