package ca.bc.gov.nrs.ilcr.schedule5.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * One itemized Other Camp / Other Access expense row — an {@code ILCR_COST_REPORT_DETAIL} row keyed
 * by item 62 (camp) or 68 (access) and parented by {@code CAMP_REPORT_ID}.
 *
 * <p><strong>{@code volume} is stamped at read and is NEVER a stored per-row value.</strong> Legacy
 * builds these rows through {@code Schedule5DAO.getNewCostReportDetail} ({@code :617-633}), which
 * never calls {@code setVolume}, and its update path copies an always-null volume ({@code :589}).
 * What the screen shows comes from {@code CampReportType.getOtherCampExpensesList()} ({@code
 * :433-438}) / {@code getOtherAccessExpensesList()} ({@code :449-454}), which overwrite every row's
 * volume with the camp-level item-141/142 amount before returning the list. So the column is null
 * on every stored row and changing the Associated Camp Volume retroactively changes what every
 * existing row displays, with no history. Serving the stamped value reproduces the screen; storing
 * it would invent persistence legacy does not have.
 *
 * <p>{@code cost} is whole dollars ({@code COST NUMBER(8,0)}) and stays {@code Integer} rather than
 * widening to {@code Long} like {@link CategoryAmount}: a single row cannot overflow, and only the
 * summed {@code totals} needs the wider type.
 *
 * <p>{@code costPerVolume} is the ordinary scale-2 division of THIS row's cost by the stamped
 * volume — not the camp panel's per-term-rounded figure. Null when either side is null or the
 * volume is zero.
 *
 * <p><strong>null is not 0.</strong> A stored null cost or description stays null and Jackson
 * {@code non_null} omits the field; legacy rendered null as {@code ""}. A null description is a
 * legal, storable state (deviation (F)) that Check Status then flags — it is not a validation
 * failure.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubPageRow(
    Integer rowId, String description, BigDecimal volume, Integer cost, BigDecimal costPerVolume) {}
