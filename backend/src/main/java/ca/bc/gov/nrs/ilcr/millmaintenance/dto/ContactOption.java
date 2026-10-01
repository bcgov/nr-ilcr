package ca.bc.gov.nrs.ilcr.millmaintenance.dto;

/**
 * One selectable contact for a mill's head-office or division slot, limited to the mill's own
 * client location (BR-09).
 *
 * @param clientContactId the stored value
 * @param contactName the label
 */
public record ContactOption(long clientContactId, String contactName) {}
