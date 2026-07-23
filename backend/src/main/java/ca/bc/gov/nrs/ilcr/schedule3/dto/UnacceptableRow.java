package ca.bc.gov.nrs.ilcr.schedule3.dto;

/**
 * One itemized Included Unacceptable Cost (item 38) row (AD-12). Keyed by its detail id for edit/delete.
 * A single detail row (NULL comments) per user row — unlike Other Acceptable, there is no PO&amp;P/crown.
 *
 * @param id the item-38 detail id
 * @param description the cost description
 * @param total the Total $ (row cost)
 */
public record UnacceptableRow(Integer id, String description, Integer total) {
}
