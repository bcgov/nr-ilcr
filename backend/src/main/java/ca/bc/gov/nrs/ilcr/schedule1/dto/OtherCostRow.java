package ca.bc.gov.nrs.ilcr.schedule1.dto;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Map;

/**
 * One itemized Subtotal Other Costs row (AD-12). Keyed by the legacy detail id ({@code
 * ILCR_COST_REPORT_DETAIL_ID}) for edit/delete. {@code perUnit} ($/m³) is derived server-side from
 * the row cost and the shared Other-Costs volume (read-only).
 *
 * <p>{@code originalValues} carries the licensee's submitted {@code description} and {@code cost}
 * once the track has left Draft (Story 16.2, BR-04) — the two fields legacy's row template rendered
 * indicators for ({@code schedule1OtherCosts.xhtml}). Rows repeat cost item 19 within one summary,
 * so a row's snapshot is matched on the detail id rather than the cost item.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtherCostRow(
    Integer id,
    String description,
    Integer cost,
    BigDecimal perUnit,
    Map<String, OriginalValue> originalValues) {

  /**
   * Without original values — the shape every caller that is not serving a stored, beyond-Draft
   * document uses (a print mapper, a check-status projection, a test fixture). The canonical
   * constructor is the one the read path uses (Story 16.2).
   */
  public OtherCostRow(Integer id, String description, Integer cost, BigDecimal perUnit) {
    this(id, description, cost, perUnit, null);
  }
}
