package ca.bc.gov.nrs.ilcr.schedule5.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MaxByteLength;
import jakarta.validation.constraints.Size;

/**
 * One row in a sub-page batch save. {@code rowId} decides the operation: null INSERTs, a known id
 * UPDATEs in place, and an id the camp does not hold is a 404 with nothing persisted — the Schedule
 * 3 reconcile idiom ({@code Schedule3Service.classifySaveRow}, {@code :441-449}), so a stale or
 * concurrently-deleted id fails loudly instead of silently re-inserting.
 *
 * <p><strong>There is deliberately no {@code @NotBlank} on {@code description} (deviation (F)).</strong>
 * Legacy's DAO writes {@code ITEM_DESCRIPTION} unconditionally ({@code Schedule5DAO.java:620}) and
 * nothing server-side ever checks it; required-ness is purely the JSF {@code required="true"} on the
 * four screen inputs. Adding a server-side blank check would do two kinds of damage: it would make
 * any legacy-stored blank row un-re-saveable, and it would make four ALREADY SHIPPED Check Status
 * conditions unreachable — those conditions exist precisely because a blank description is storable.
 * The requirement lives client-side with legacy's per-page timing (AC10).
 *
 * <p><strong>There is no {@code volume} field.</strong> A row volume is never accepted, because it
 * is never persisted — see {@link SubPageRow}.
 *
 * <p><strong>There is deliberately no declarative cost bound — BOTH page bounds live in the
 * service.</strong> One DTO serves both sub-pages (a record cannot vary a constraint per call site,
 * and two near-identical DTOs would duplicate the description rules for one differing number), and a
 * declarative {@code @Min}/{@code @Max} at Access's wider &plusmn;99,999,999 would fire BEFORE the
 * service's per-page narrowing — so a Camp-page cost beyond the wide bound would be rejected with
 * the ACCESS message instead of the Camp page's {@code costSize7ValidatorErrorMsg}, breaking the
 * AD-8 verbatim discipline on exactly the inputs a boundary test at the NARROW bound never probes.
 * {@code Schedule5Service.validateSubPageCosts} applies each page's own bound with its own message —
 * Camp &plusmn;9,999,999 ({@code costSize="7"} on {@code schedule5CampExpenses.xhtml:45} and {@code
 * :79}), Access &plusmn;99,999,999 — exactly as {@code Schedule5Service.validateCostRanges} narrows
 * the eight {@code costSize="7"} categories that {@code CategoryEntry} cannot express.
 *
 * <p>A fractional cost is REJECTED, not truncated: {@code Integer} plus the app-wide {@code
 * accept-float-as-int: false} (shipped by 7.2) turns {@code 12.5} into a clean 400. Legacy is
 * inconsistent here — {@code getNewCostReportDetail} calls {@code intValueExact()} ({@code :622}),
 * which throws inside the transaction and surfaces as a generic {@code Schedule could not be saved.},
 * while the main camp grid truncates via {@code intValue()} (deviation (M)).
 *
 * <p><strong>{@code @MaxByteLength} here is defensive-only and can never fire.</strong> Delivery
 * declares {@code ITEM_DESCRIPTION VARCHAR2(120)} with {@code CHAR_USED = 'B'} (Task 1 gate (i)),
 * and a 30-CHARACTER string carries at most 90 UTF-8 bytes, so every value {@code @Size(max = 30)}
 * admits already fits with 30 bytes to spare. The ceiling is 90 rather than the obvious 120 because
 * {@code @Size} counts Java chars: a 4-byte character is a surrogate pair costing TWO of the thirty
 * (2 bytes per unit), while a 3-byte BMP character costs one (3 bytes per unit), making 3-byte
 * characters the densest possible input at 30 &times; 3. The guard is declared anyway to keep the
 * Schedule 5 byte-guard idiom uniform across the schedule's string fields, but no input can reach
 * it, and the tests assert the boundary is ACCEPTED rather than pretending it rejects.
 *
 * @param rowId the stored {@code ILCR_COST_REPORT_DETAIL_ID}, or null to insert a new row
 * @param description the free-text description (optional, &le; 30 chars — blank is legal)
 * @param cost the whole-dollar cost (optional; page-specific range narrowed in the service)
 */
public record SubPageRowRequest(

    Integer rowId,

    @Size(max = 30, message = "{descriptionMaxLengthErrorMsg}")
    @MaxByteLength(value = 120, charMax = 30, message = "{descriptionMaxLengthErrorMsg}")
    String description,

    Integer cost) {
}
