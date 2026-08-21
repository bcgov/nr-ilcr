package ca.bc.gov.nrs.ilcr.schedule9.dto;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * One Schedule 9 contractual work record (AD-12) — a miscellaneous/unique logging cost item. Field
 * order mirrors the legacy add panel ({@code schedule9.xhtml}) so the list reads in the order a
 * licensee enters it: contractor, item, unit + units, zone, cost, side slope, source, comments.
 *
 * <p><strong>Stored vs derived.</strong> {@code costPerUnit} = {@code cost} ÷ {@code numberOfUnits}
 * is computed server-side (AD-5) and is <strong>null when {@code numberOfUnits} is
 * zero/blank</strong> (S14) — never client-editable. Everything else is stored: the descriptors on
 * {@code THE.CONTRACTUAL_WORK_REPORT} and the {@code cost} + Contractual Item (108–114) on the
 * joined {@code THE.ILCR_COST_REPORT_DETAIL} line.
 *
 * <p>The four code-list selections ({@code contractualItem}, {@code unitType}, {@code
 * biogeoclimaticZone}, {@code source}) are served as {@code {code, description}} pairs, resolved
 * from their reference tables. The three conditional descriptions ({@code itemDescription} for a
 * "Other" item, {@code unitDescription} for a "Other" unit, {@code sourceDescription} for a "O"/"S"
 * source) are stored columns served verbatim; their enable/require semantics (BR-04) are a Story
 * 9.2/9.3 concern.
 *
 * <p>{@code id} and {@code revisionCount} are primitive {@code int}: the delivery columns ({@code
 * CONTRACTUAL_WORK_REPORT_ID}, {@code REVISION_COUNT}) are {@code NOT NULL}, and boxing {@code
 * revisionCount} would let {@code NON_NULL} silently drop the optimistic-lock token the 9.2 write
 * path requires.
 *
 * @param id the record id (its own optimistic-lock key; there is no schedule-level revision)
 * @param revisionCount this record's optimistic-lock token
 * @param contractorId the contractor Company ID
 * @param contractualItem the contractual item (cost-item 108–114) code + name
 * @param itemDescription free text when the item is "Other" (else null)
 * @param unitType the unit-type code + description
 * @param unitDescription free text when the unit is "Other" (else null)
 * @param numberOfUnits units performed (NUMBER(6,1))
 * @param biogeoclimaticZone the BEC zone code + description
 * @param cost the work item cost (whole dollars)
 * @param costPerUnit derived cost ÷ units; null when units zero/blank
 * @param sideSlopePct side slope percentage (only meaningful for road-deactivation items; else
 *     null)
 * @param source the cost source code + description
 * @param sourceDescription free text when the source is "O"/"S" (else null)
 * @param comments per-record comments
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractualWorkRecord(
    int id,
    int revisionCount,
    String contractorId,
    CodeDescriptionDto contractualItem,
    String itemDescription,
    CodeDescriptionDto unitType,
    String unitDescription,
    BigDecimal numberOfUnits,
    CodeDescriptionDto biogeoclimaticZone,
    Integer cost,
    BigDecimal costPerUnit,
    Integer sideSlopePct,
    CodeDescriptionDto source,
    String sourceDescription,
    String comments) {}
