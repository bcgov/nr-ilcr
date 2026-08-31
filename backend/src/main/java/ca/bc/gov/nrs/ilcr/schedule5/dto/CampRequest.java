package ca.bc.gov.nrs.ilcr.schedule5.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MaxByteLength;
import jakarta.validation.Valid;
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
 * Add/edit request for one Schedule 5 camp (AD-12) — entered fields only. It REALIZES the document
 * Story 7.1 pinned rather than re-pinning it: no derived value appears here at all. The four totals
 * ({@code campSubTotal}, {@code campTotal}, {@code accessExpenseTotal}, {@code
 * campAndAccessTotal}), every {@code costPerVolume}, both sub-page counts, and the {@code cost}
 * halves of {@code otherCampExpenses}/{@code otherAccessExpenses} are computed server-side per
 * BR-04 and are ignored if sent ({@code Camp.java:16-21}) — a client that {@code PUT}s the served
 * document straight back is therefore correct, not in error.
 *
 * <p><strong>The server never re-derives a category volume (deviation (A)).</strong> BR-03's
 * propagation of the Associated Camp Volume onto the eleven volume-bearing categories is a
 * CLIENT-side ajax listener: {@code Schedule5MB.updateCampVolumes()} (:248-261) is invoked only
 * from the two {@code <p:ajax>} handlers on the camp-volume input ({@code
 * schedule5ExistingCamp.xhtml:78}, {@code schedule5NewCamp.xhtml:46}) and NEVER from {@code
 * save()}. A volume edited after the last camp-volume change therefore persists as edited, so this
 * request's per-category volumes are stored verbatim — which is also 7.1's standing guardrail that
 * stored per-category volumes may legitimately diverge from the camp volume.
 *
 * <p><strong>Sizing.</strong> Every bound is the LEGACY validator's, and every {@code @Digits} is
 * sized to the DELIVERY column so an out-of-range value is a clean 400 rather than an ORA-12899 the
 * service could only turn into a 500 (the 8.2 lesson). {@code roadDistanceToOperatingArea} is
 * {@code NUMBER(8,2)}, so {@code fraction = 2} keeps Oracle from silently rounding; its magnitude
 * cap is 999999.9 — the value the validator really enforces ({@code
 * ILCRDistanceValidator.java:16-17}), and real delivery data sits EXACTLY on it. Legacy's {@code
 * distanceValidatorErrorMsg} said "999,999", understating its own bound by 0.9. <strong>Deviation
 * (H) is CLOSED:</strong> the Ministry confirmed the bound and ruled the MESSAGE the defect (PR
 * #370, 2026-08-27 — the value is in kilometres, which is why it carries a decimal; ruling 1 of
 * {@code docs/decisions/camps-and-access-expenses.md}), so the bundle text now reads "999,999.9".
 * The bound below never moved. {@code sizeOfCamp} is 1–999 on {@code CAMP_SIZE_CAPACITY NUMBER(3)};
 * {@code associatedCampVolume} is 0–9,999,999 on {@code ASSOCIATED_CAMP_VOLUME NUMBER(7)} with
 * {@code fraction = 0}, so a fractional volume is REJECTED rather than truncated the way legacy's
 * {@code intValue()} truncates it ({@code Schedule5DAO.java:376}). Each {@code @Digits} integer cap
 * is one wider than its magnitude bound so an over-range value trips exactly one constraint.
 *
 * <p>{@code campName} and {@code isolatedCamp} are the only required fields (BR-02/BR-05, FLD-001).
 * FLD-001 has NO legacy text — it was the JSF container default, never overridden ({@code
 * UC-SCH5-001-technical.md:279} records it as {@code [UNKNOWN]}) — so both resolve NEW modern
 * bundle keys, cited as new rather than as ports (deviation (Q), the 8.2 {@code
 * roadCommentsMaxLengthErrorMsg} precedent). {@code campName} is trimmed before the BR-02
 * uniqueness comparison AND before persisting (deviation (I)): legacy trimmed only on the insert
 * path (:289), compared untrimmed on edit (:309), and persisted untrimmed in both ({@code
 * Schedule5DAO.java:373}).
 *
 * <p>{@code comments} is capped at 3500 — the legacy textarea's own {@code maxlength} ({@code
 * schedule5ExistingCamp.xhtml:458}) — against {@code COMMENTS VARCHAR2(4000 BYTE)}. Unlike Schedule
 * 6's per-record comment there is NO over-cap defect to inherit here: the column is WIDER than the
 * screen cap, and the longest real stored comment is exactly 3500 (Task 1 gate (vii)).
 *
 * <p><strong>Two units, both enforced.</strong> {@code campName} and {@code comments} each carry a
 * {@code @Size} CHARACTER cap and a {@link MaxByteLength} BYTE cap, because the legacy bound and
 * the column bound are measured differently and neither implies the other. The character caps are
 * the legacy screen's (30 / 3500); the byte caps are the columns' own widths (30 / 4000), confirmed
 * {@code CHAR_USED = 'B'} on an {@code AL32UTF8} database. {@code campName} is the sharp case — the
 * two limits are both 30, so a SINGLE multibyte character overflows a name the character cap
 * accepts; without the byte cap that is ORA-12899 → {@code ScheduleNotSavedException} → an opaque
 * 500 on an ordinary save. {@code comments} has 500 bytes of headroom, so it only bites above
 * roughly 1.15 bytes per character. The gap was previously recorded as unguarded ({@code
 * deferred-work.md}, Schedule 6/11 comments share it); Schedule 5 now closes its own half.
 *
 * @param campName the camp name — required, non-blank after trim, &le; 30 chars AND &le; 30 UTF-8
 *     bytes, unique per (mill, year) case-insensitively (BR-02)
 * @param roadDistanceToOperatingArea the road distance (optional, 0.0–999999.9, &le; 2 decimals)
 * @param sizeOfCamp the camp capacity in persons (optional, 1–999)
 * @param associatedCampVolume the camp volume in m&sup3; (optional, 0–9,999,999, whole numbers
 *     only)
 * @param isolatedCamp whether the camp is isolated — required (BR-05); stored as {@code Y}/{@code
 *     N}
 * @param comments the per-camp comment (optional, &le; 3500 chars AND &le; 4000 UTF-8 bytes)
 * @param cateringAndFood item 56 — volume + cost
 * @param wagesAndBenefits item 58 — volume + cost; cost range is the &plusmn;99,999,999 outlier
 *     (deviation (F))
 * @param depreciationLease item 59 — volume + cost
 * @param generalCampExpenses item 60 — volume + cost
 * @param otherCampExpenses item 141 — VOLUME ONLY; its cost is the item-62 row sum (BR-04)
 * @param recoveries item 61 — COST ONLY, the volume-less category; 0-floored (deviation (G))
 * @param crewTransportation item 63 — volume + cost
 * @param equipAndSuppliesLand item 64 — volume + cost
 * @param equipAndSuppliesRail item 65 — volume + cost
 * @param equipAndSuppliesAir item 66 — volume + cost
 * @param equipAndSuppliesWater item 67 — volume + cost
 * @param otherAccessExpenses item 142 — VOLUME ONLY; its cost is the item-68 row sum (BR-04)
 * @param revisionCount the camp's optimistic-lock token, echoed from the served camp — required on
 *     UPDATE only ({@link OnUpdate}), ignored on create
 */
public record CampRequest(
    @NotBlank(message = "{campNameRequiredErrorMsg}") @Size(max = 30, message = "{campNameMaxLengthErrorMsg}") @MaxByteLength(value = 30, charMax = 30, message = "{campNameMaxLengthErrorMsg}")
        String campName,
    @DecimalMin(value = "0.0", message = "{distanceValidatorErrorMsg}") @DecimalMax(value = "999999.9", message = "{distanceValidatorErrorMsg}") @Digits(integer = 7, fraction = 2, message = "{distanceValidatorErrorMsg}") BigDecimal roadDistanceToOperatingArea,
    @Min(value = 1, message = "{numberOfPersonsValidatorErrorMsg}") @Max(value = 999, message = "{numberOfPersonsValidatorErrorMsg}") Integer sizeOfCamp,
    @DecimalMin(value = "0", message = "{volumeValidatorErrorMsg}") @DecimalMax(value = "9999999", message = "{volumeValidatorErrorMsg}") @Digits(integer = 8, fraction = 0, message = "{volumeValidatorErrorMsg}") BigDecimal associatedCampVolume,
    @NotNull(message = "{isolatedCampRequiredErrorMsg}") Boolean isolatedCamp,
    @Size(max = 3500, message = "{campCommentsMaxLengthErrorMsg}") @MaxByteLength(value = 4000, charMax = 3500, message = "{campCommentsMaxLengthErrorMsg}")
        String comments,
    @Valid CategoryEntry cateringAndFood,
    @Valid CategoryEntry wagesAndBenefits,
    @Valid CategoryEntry depreciationLease,
    @Valid CategoryEntry generalCampExpenses,
    @Valid CategoryEntry otherCampExpenses,
    @Valid CategoryEntry recoveries,
    @Valid CategoryEntry crewTransportation,
    @Valid CategoryEntry equipAndSuppliesLand,
    @Valid CategoryEntry equipAndSuppliesRail,
    @Valid CategoryEntry equipAndSuppliesAir,
    @Valid CategoryEntry equipAndSuppliesWater,
    @Valid CategoryEntry otherAccessExpenses,
    @NotNull(groups = OnUpdate.class, message = "{revisionCountRequiredErrorMsg}") Integer revisionCount) {}
