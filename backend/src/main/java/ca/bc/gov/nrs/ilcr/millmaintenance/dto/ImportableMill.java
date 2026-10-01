package ca.bc.gov.nrs.ilcr.millmaintenance.dto;

/**
 * A ministry mill not yet tracked in ILCR, offered for import (BR-04).
 *
 * @param millId the mill id
 * @param millNumber the mill number as a display string
 * @param millName the mill name
 */
public record ImportableMill(long millId, String millNumber, String millName) {}
