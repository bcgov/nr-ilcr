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
 * One road record inside a {@link Schedule6SaveRequest}.
 *
 * <p>Identical field constraints to {@link RoadRecordRequest} — see that record's javadoc for why
 * every {@code @Size} is column fidelity rather than decoration: an over-long value that reaches
 * Oracle raises ORA-12899, which the service's {@code catch (DataAccessException)} can only turn
 * into a 500. Two differences from {@code RoadRecordRequest}: {@code recordId} is required (this
 * addresses an EXISTING row; adds still go through {@code POST /records}), and {@code
 * revisionCount} is unconditionally required rather than group-scoped — every entry in a whole-
 * document save is an update, so there is no create case to exempt.
 *
 * @param recordId the road record id being saved (required)
 * @param revisionCount the record's own optimistic-lock token, echoed from the served document
 * @param areaType a TSA code (&le;2) or the literal {@code "TFL"} (required — FLD-001)
 * @param tflNumber the TFL number (required-and-validated iff {@code areaType == "TFL"}, BR-03)
 * @param supplyBlock the TSB code, &le;3 (applies iff {@code areaType != "TFL"})
 * @param volume the volume in m&sup3; (optional, 0–9,999,999, at most two decimals)
 * @param cost the whole-dollar cost (optional at save, &plusmn;99,999,999)
 * @param comments the per-record comment (optional, &le; 400 — the detail column's width)
 */
public record RoadRecordEntry(
    @NotNull(message = "{invalidCodeValueErrorMsg}") Integer recordId,
    @NotNull(message = "{revisionCountRequiredErrorMsg}") Integer revisionCount,
    @NotBlank(message = "{tsaOrTflRequiredErrorMsg}") @Size(max = 3, message = "{invalidCodeValueErrorMsg}") String areaType,
    @Size(max = 2, message = "{tflNumberValidatorErrorMsg}") String tflNumber,
    @Size(max = 3, message = "{invalidCodeValueErrorMsg}") String supplyBlock,
    @DecimalMin(value = "0", message = "{volumeValidatorErrorMsg}") @DecimalMax(value = "9999999", message = "{volumeValidatorErrorMsg}") @Digits(integer = 8, fraction = 2, message = "{volumeValidatorErrorMsg}") BigDecimal volume,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}") @Max(value = 99999999, message = "{costValidatorErrorMsg}") Integer cost,
    @Size(max = 400, message = "{roadCommentsMaxLengthErrorMsg}") String comments) {}
