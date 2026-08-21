package ca.bc.gov.nrs.ilcr.schedule6.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Add/edit request for one Schedule 6 road-maintenance record (AD-12). Entered fields only — the
 * derived {@code rmg} and {@code costPerVolume} (BR-04/BR-07) are never client-supplied; they exist
 * only on the served {@link RoadRecord}. Range/required messages resolve the LEGACY bundle keys
 * (AD-8) via the wired {@code MessageSource} ({@code ValidationConfiguration}).
 *
 * <p>{@code areaType} is a TSA code or the literal {@code "TFL"} (BR-02) and is the only required
 * field at save (FLD-001). The counterpart field is cleared server-side per BR-02 (TFL clears
 * TSA/Supply Block; TSA clears TFL) — the service, not this DTO, owns that. {@code tflNumber} is
 * validated in the service (normalize-alias-then-lookup, BR-03) because validity means "resolves to
 * an RMG", not a shape rule; the {@code @Size} cap matches the {@code TFL_NUMBER_CODE VARCHAR2(2)}
 * column and legacy's own {@code maxlength="2"} inputs, so an over-long code fails with the same
 * verbatim FLD-002 text the lookup miss produces. No accepted TFL value is excluded by that cap:
 * legacy's 3-char {@code "52B"} lookup entry was never storable or enterable and is commented out
 * of {@code RoadGroupLookup} for the same reason (code review 2026-08-05).
 *
 * <p><b>Every {@code @Size} here is column fidelity, not decoration</b> (code review 2026-08-04):
 * an over-long value that reaches Oracle raises ORA-12899, which the service's {@code catch
 * (DataAccessException)} can only turn into a 500 — so each cap must match the column the value
 * lands in, verified against the delivery DB. {@code areaType} is capped at 3 for the literal
 * {@code "TFL"}; on the TSA branch the service additionally enforces &le;2 for {@code TSA_NUMBER
 * VARCHAR2(2)}. {@code supplyBlock} matches {@code TSB_NUMBER_CODE VARCHAR2(3)}. {@code comments}
 * is capped at <b>400</b> — the per-record comment is written to {@code
 * ILCR_COST_REPORT_DETAIL.COMMENTS VARCHAR2(400 BYTE)}, NOT to the 4000-wide {@code
 * ROAD_MAINTENANCE_REPORT.COMMENTS} the schedule-level general comment uses; the two are different
 * columns and only the general comment may carry the legacy UI's 3500. Legacy's own add/edit
 * textareas are {@code maxlength="3500"} ({@code schedule6.xhtml:180,410}) over a 400-wide column,
 * so legacy would fail there too — the frontend (8.3) must cap at 400, not 3500. Byte semantics
 * ({@code CHAR_USED='B'}): multibyte text can still overflow below 400 characters, which this
 * constraint does not catch.
 *
 * <p>{@code volume} range is 0–9,999,999 (FLD-003); the {@code @Digits} fraction cap matches the
 * delivery {@code ILCR_COST_REPORT_DETAIL.VOLUME NUMBER(10,2)} (Task 1 gate (i)) so Oracle never
 * silently rounds what was entered (the 25.2 lesson); integer=8 — one wider than the magnitude
 * bound — leaves magnitude to {@code @DecimalMax} so an over-range value trips ONE constraint.
 * {@code cost} is whole dollars &plusmn;99,999,999 (FLD-004). Both optional at save — a missing
 * cost is Check Status's finding (S09), not a save rejection.
 *
 * <p>{@code revisionCount} is the RECORD's own optimistic-lock token (AR11 per-record keying, 8.1
 * deviation (b)) — required only on UPDATE (the {@link OnUpdate} group), ignored on create.
 *
 * @param areaType a TSA code (&le;2) or the literal {@code "TFL"} (required — FLD-001)
 * @param tflNumber the TFL number (required-and-validated iff {@code areaType == "TFL"}, BR-03;
 *     otherwise cleared server-side)
 * @param supplyBlock the TSB code, &le;3 (applies iff {@code areaType != "TFL"}; otherwise cleared
 *     server-side; absent → flagged by Check Status, not by save)
 * @param volume the volume in m&sup3; (optional, 0–9,999,999, at most two decimals)
 * @param cost the whole-dollar cost (optional at save, &plusmn;99,999,999; required by Check
 *     Status)
 * @param comments the per-record comment (optional, &le; 400 — the detail column's width)
 * @param revisionCount the optimistic-lock token echoed from the served record (required on UPDATE)
 */
public record RoadRecordRequest(
    @NotBlank(message = "{tsaOrTflRequiredErrorMsg}") @Size(max = 3, message = "{invalidCodeValueErrorMsg}") String areaType,
    @Size(max = 2, message = "{tflNumberValidatorErrorMsg}") String tflNumber,
    @Size(max = 3, message = "{invalidCodeValueErrorMsg}") String supplyBlock,
    @DecimalMin(value = "0", message = "{volumeValidatorErrorMsg}") @DecimalMax(value = "9999999", message = "{volumeValidatorErrorMsg}") @Digits(integer = 8, fraction = 2, message = "{volumeValidatorErrorMsg}") BigDecimal volume,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}") @Max(value = 99999999, message = "{costValidatorErrorMsg}") Integer cost,
    @Size(max = 400, message = "{roadCommentsMaxLengthErrorMsg}") String comments,
    @NotNull(groups = OnUpdate.class, message = "{revisionCountRequiredErrorMsg}") Integer revisionCount) {}
