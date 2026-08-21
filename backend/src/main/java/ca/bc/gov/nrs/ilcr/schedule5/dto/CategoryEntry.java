package ca.bc.gov.nrs.ilcr.schedule5.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * One entered category amount on a Schedule 5 write (AD-12) — the volume/cost pair a licensee types
 * into one row of the camp grid.
 *
 * <p><strong>This is NOT {@link CategoryAmount}.</strong> The served type carries a third
 * component, {@code costPerVolume}, which is derived per BR-04 and never client-supplied. Sending
 * it is not an error — it is ignored, along with the four totals and both counts, exactly as
 * legacy's JSF postback discards the {@code disabled="true"} computed inputs ({@code
 * Camp.java:16-21}).
 *
 * <p><strong>Both halves are optional, and {@code null} means CLEARED, not invalid.</strong> All
 * six legacy validators short-circuit unless the submitted value {@code instanceof BigDecimal}
 * ({@code ILCRCostValidator.java:26} and siblings), so an empty input was silently accepted and
 * reached the DAO as null. A null therefore writes {@code NULL} into the stored row — never {@code
 * 0} — and the row itself survives (deviation (N)).
 *
 * <p><strong>Which half applies depends on the category.</strong> {@code otherCampExpenses} (item
 * 141) and {@code otherAccessExpenses} (item 142) carry {@code volume} only; {@code recoveries}
 * (item 61) carries {@code cost} only — it is the volume-less twelfth category. Sending the
 * excluded half is ignored, not rejected, and the § ITEM WRITE MAP hard-codes the {@code null}
 * legacy passes for it ({@code Schedule5DAO.java:391-392, 398}).
 *
 * <p><strong>Bounds.</strong> {@code volume} is 0–9,999,999 for every category ({@code
 * ILCRVolumeValidator} default {@code volSize="7"}, {@code Constants.java:110,114}), with {@code
 * fraction = 2} matching the delivery {@code ILCR_COST_REPORT_DETAIL.VOLUME NUMBER(10,2)} so Oracle
 * never silently rounds what was entered (the 25.2 lesson), and {@code integer = 8} — one wider
 * than the magnitude bound — leaving magnitude to {@code @DecimalMax} so an over-range value trips
 * exactly ONE constraint.
 *
 * <p>{@code cost} is declared here at the WIDEST legal Schedule 5 bound, &plusmn;99,999,999: that
 * is both the delivery {@code COST NUMBER(8,0)} capacity and the real enforced range of {@code
 * wagesAndBenefits}, whose input is missing the {@code costSize} attribute its ten siblings carry
 * (deviation (F)). The two NARROWER per-field ranges cannot be expressed here — one record type
 * cannot vary a constraint by which property holds it — so {@code Schedule5Service} enforces them
 * with their own verbatim message keys: &plusmn;9,999,999 / {@code costSize7ValidatorErrorMsg} for
 * the eight ordinary categories, and 0–9,999,999 / {@code costValidatorSchedule9ErrorMsg} for
 * {@code recoveries} (deviation (G)). Both surface as the same 400 {@code ProblemDetail} shape this
 * constraint would.
 *
 * <p>Whole dollars: {@code cost} is an {@code Integer}, so a fractional cost must be REJECTED
 * rather than truncated (AC6). Jackson's {@code ACCEPT_FLOAT_AS_INT} would silently truncate it
 * ({@code deferred-work.md:180}) and legacy's {@code intValue()} truncates toward zero ({@code
 * Schedule5DAO.java:639}); the feature is disabled in {@code application.yml} so the rejection is
 * real. Legacy is itself inconsistent here — its sub-page helper uses {@code intValueExact()} and
 * throws ({@code Schedule5DAO.java:622}).
 *
 * @param volume the entered volume in m&sup3; (optional; 0–9,999,999, at most two decimals; null
 *     clears the stored value)
 * @param cost the entered whole-dollar cost (optional; null clears; per-category range enforced in
 *     the service)
 */
public record CategoryEntry(
    @DecimalMin(value = "0", message = "{volumeValidatorErrorMsg}") @DecimalMax(value = "9999999", message = "{volumeValidatorErrorMsg}") @Digits(integer = 8, fraction = 2, message = "{volumeValidatorErrorMsg}") BigDecimal volume,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}") @Max(value = 99999999, message = "{costValidatorErrorMsg}") Integer cost) {}
