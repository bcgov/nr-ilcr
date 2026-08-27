package ca.bc.gov.nrs.ilcr.codetable.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MaxByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * One row of a lookup code table (Story 24.3 / UC-CODE-001, BR-02): a code, its description, and
 * the effective/expiry window that gates whether downstream schedules offer it for a given year
 * (BR-07).
 *
 * <p>Declarative constraints (Story 29.13) give the {@code saveEntry} write path a uniform 400
 * {@code ProblemDetail} for shape violations via {@code @Valid} — the same error shape as the rest
 * of the API — WITHOUT replacing the authoritative service-layer checks. {@code
 * CodeTableService.validate} still owns the required/length/date-order rules and their verbatim
 * legacy message keys (FLD-001..005, BR-06); these annotations are a declarative front line, not a
 * replacement.
 *
 * <p>The {@code @Size} caps are deliberately OUTER BOUNDS — the maximum across every table ({@code
 * CodeTableRegistry}: code ≤ 20, description ≤ 120) — because a record-level annotation carries one
 * number but the real caps are PER-TABLE ({@code table.codeMaxLength()} / {@code
 * descriptionMaxLength()}). Keeping the annotation at the outer bound means the exact per-table
 * length rejection still comes from {@code validate()} with its verbatim message; the annotation
 * only catches absurd input early. Do NOT tighten these to a single table's cap.
 *
 * @param code the code value (the table's primary key); required + within the table's code cap on
 *     save
 * @param description the human-readable label; required + within the table's description cap on
 *     save
 * @param effectiveDate first day the code is offered (inclusive); required on save
 * @param expiryDate last day the code is offered (inclusive); {@code null} = never expires
 */
public record CodeTableEntry(
    // Contractual Item Codes intentionally omit a client-supplied code on insert; the service
    // allocates the legacy ILCR_REPORT_COST_ITEM identifier. Generic tables still require code at
    // the service layer.
    @Size(max = 20) String code,
    @NotBlank(message = "{descriptionRequiredErrorMsg}") @Size(max = 120, message = "{codeTableDescriptionLengthErrorMsg}") @MaxByteLength(value = 120, charMax = 120, message = "{codeTableDescriptionLengthErrorMsg}")
        String description,
    @NotNull(message = "{effectiveDateRequiredErrorMsg}") LocalDate effectiveDate,
    LocalDate expiryDate) {}
