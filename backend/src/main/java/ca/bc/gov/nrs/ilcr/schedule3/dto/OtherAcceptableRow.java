package ca.bc.gov.nrs.ilcr.schedule3.dto;

/**
 * One itemized Other Acceptable Cost (item 124) group, as seen by the user (AD-12). Stored as a paired
 * TOT + PO&amp;P detail row sharing a group key; {@code id} is the TOT row's detail id (the group's
 * identity for edit/delete). {@code total} is the TOT row's COST, {@code pop} the PO&amp;P row's COST,
 * {@code crown} is derived read-only ({@code total − pop}, null unless both present).
 *
 * @param id the TOT detail id (group identity)
 * @param description the cost description (stored on both rows)
 * @param total the Harvest Total $ (TOT row cost)
 * @param pop the PO&amp;P $ (PO&amp;P row cost)
 * @param crown the derived Crown $ (total − pop)
 */
public record OtherAcceptableRow(
    Integer id, String description, Integer total, Integer pop, Integer crown) {
}
