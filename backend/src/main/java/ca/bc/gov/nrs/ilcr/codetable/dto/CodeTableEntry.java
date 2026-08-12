package ca.bc.gov.nrs.ilcr.codetable.dto;

import java.time.LocalDate;

/**
 * One row of a lookup code table (Story 24.3 / UC-CODE-001, BR-02): a code, its description, and the
 * effective/expiry window that gates whether downstream schedules offer it for a given year (BR-07).
 *
 * @param code the code value (the table's primary key); {@code null}/blank for a Contractual add
 * @param description the human-readable label
 * @param effectiveDate first day the code is offered (inclusive); {@code null} = no lower bound
 * @param expiryDate last day the code is offered (inclusive); {@code null} = never expires
 */
public record CodeTableEntry(
    String code, String description, LocalDate effectiveDate, LocalDate expiryDate) {
}
