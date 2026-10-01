package ca.bc.gov.nrs.ilcr.schedule3.dto;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * One itemized Other Acceptable Cost (item 124) group, as seen by the user (AD-12). Stored as a
 * paired TOT + PO&amp;P detail row sharing a group key; {@code id} is the TOT row's detail id (the
 * group's identity for edit/delete). {@code total} is the TOT row's COST, {@code pop} the PO&amp;P
 * row's COST, {@code crown} is derived read-only ({@code total − pop}, null unless both present).
 *
 * @param id the TOT detail id (group identity)
 * @param description the cost description (stored on both rows)
 * @param total the Harvest Total $ (TOT row cost)
 * @param pop the PO&amp;P $ (PO&amp;P row cost)
 * @param crown the derived Crown $ (total − pop)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtherAcceptableRow(
    Integer id,
    String description,
    Integer total,
    Integer pop,
    Integer crown,
    Map<String, OriginalValue> originalValues) {

  /**
   * Without original values — the shape every caller that is not serving a stored, beyond-Draft
   * document uses (a print mapper, a check-status projection, a test fixture). The canonical
   * constructor is the one the read path uses (Story 16.2).
   */
  public OtherAcceptableRow(
      Integer id, String description, Integer total, Integer pop, Integer crown) {
    this(id, description, total, pop, crown, null);
  }
}
