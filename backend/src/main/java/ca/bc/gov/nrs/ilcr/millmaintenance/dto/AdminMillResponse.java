package ca.bc.gov.nrs.ilcr.millmaintenance.dto;

/**
 * A mill write outcome: the mill as it now stands, plus the legacy confirmation that names what
 * happened.
 *
 * <p>The message is part of the contract, not decoration — each operation has its own verbatim
 * legacy sentence, and the service names the outcome while the controller resolves the text.
 *
 * @param mill the mill after the write
 * @param messageKey the message bundle key
 * @param message the resolved text
 */
public record AdminMillResponse(AdminMill mill, String messageKey, String message) {}
