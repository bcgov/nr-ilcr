package ca.bc.gov.nrs.ilcr.schedule8.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The Schedule 8 report-page save (create-or-edit) request (Story 14.2, AD-12 write contract). Targets
 * one {@code TREE_TO_TRUCK_REPORT} row (category {@code '8'}) by {@code id} — null = CREATE, present =
 * EDIT (rename-safe). {@code revisionCount} is the optimistic-lock token from the last GET (null on
 * create, matching the freshly-inserted 0).
 *
 * <p>Required at Save (S19, FLD-006 → 400 {@code Value Required}): {@code license}, {@code supportCentre},
 * {@code region}, {@code becZone}, and a TSA-or-TFL context (at least one of {@code tsaNumber}/
 * {@code tflNumber}, via {@link TsaOrTflRequired}). Optional at Save: {@code division}, {@code contact},
 * {@code phone}, {@code cuttingPermit}, {@code comments} (Contact/Phone become required only at Check
 * Status — 14.6).
 *
 * <p>TFL vs Supply Block are mutually exclusive (S10, BR-03): a non-blank {@code tflNumber} selects TFL
 * (the service clears {@code supplyBlock}); otherwise {@code supplyBlock} applies (the service clears
 * {@code tflNumber}) — normalized server-side. The TFL-resolves-to-Road-Group check (S22) is deferred
 * with the {@code RoadGroupUtil} port (14.1 §Decision 2); recorded in the 14.2 Completion Notes.
 *
 * <p>Copy (S02) is a client-driven prefill that arrives here as an ordinary create (§Decision, mirrors
 * Schedule 4) — no dedicated server endpoint. Derived/read-only fields ({@code sampleCount}, labels,
 * {@code trackStatus}, {@code editable}) are never accepted here (AD-5).
 */
@TsaOrTflRequired
public record Schedule8PageRequest(
    Integer id,
    Integer revisionCount,
    @NotBlank(message = "{missingRequiredFieldMsg}")
    @Size(max = 8, message = "Harvest license can not exceed 8 characters.")
    String license,
    @NotBlank(message = "{missingRequiredFieldMsg}")
    @Size(max = 10, message = "Support centre can not exceed 10 characters.") String supportCentre,
    @NotBlank(message = "{missingRequiredFieldMsg}")
    @Size(max = 10, message = "Region can not exceed 10 characters.") String region,
    @NotBlank(message = "{missingRequiredFieldMsg}")
    @Size(max = 10, message = "BEC zone can not exceed 10 characters.") String becZone,
    @Size(max = 10, message = "TSA number can not exceed 10 characters.") String tsaNumber,
    @Size(max = 10, message = "TFL number can not exceed 10 characters.") String tflNumber,
    @Size(max = 10, message = "Supply block can not exceed 10 characters.") String supplyBlock,
    @Size(max = 30, message = "Division can not exceed 30 characters.") String division,
    @Size(max = 50, message = "Contact can not exceed 50 characters.") String contact,
    @Size(max = 20, message = "Phone can not exceed 20 characters.") String phone,
    @Size(max = 10, message = "Cutting permit can not exceed 10 characters.") String cuttingPermit,
    @Size(max = 3500, message = "Comments can not exceed 3500 characters.") String comments) {
}
