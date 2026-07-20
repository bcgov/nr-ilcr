package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsSummary;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule1.dto.SilvicultureBlock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 1 aggregate document from stored detail rows and computes all derived
 * values server-side (AD-5, AD-6). The mill/year context is already validated by
 * {@code MillContextService} before this runs (AD-4) — the summary is expected to exist.
 */
@Service
@Slf4j
public class Schedule1Service {

  private static final String SCHEDULE_1_CATEGORY = "1";
  private static final String STATUS_DRAFT = "D";

  // Legacy Constant.REPORT_COST_ITEMS ids (BR-02).
  private static final List<Integer> LINE_ITEM_CODES = List.of(12, 13, 14, 15, 16, 17, 18, 143, 144);
  private static final int CODE_SILV_ACTUAL = 1;
  private static final int CODE_SILV_ACCRUED = 2;
  private static final int CODE_SILV_LESS_ADMIN = 139;
  private static final int CODE_SILV_TOTAL = 140;
  private static final int CODE_FOREST_MGMT_ADMIN = 143;
  private static final int CODE_OTHER = 19;

  // Codes writable from the PUT request (AD-5/AC2). Excludes pulled (139/143) and derived (140/144)
  // codes — the server owns those; a client-sent value for them is ignored.
  private static final Set<Integer> WRITABLE_LINE_ITEM_CODES = Set.of(12, 13, 14, 15, 16, 17, 18);

  private final Schedule1Repository repository;

  public Schedule1Service(Schedule1Repository repository) {
    this.repository = repository;
  }

  /**
   * Persist the entered Schedule 1 fields for a mill/year and return the recomputed document (S01).
   * The mill/year context is already validated by {@code MillContextService} in the controller
   * (AD-4). Enforces the server-side Draft gate (AD-9) and optimistic-lock concurrency (AR11), and
   * writes only the writable codes — never the itemized Other-Costs rows (AC2). The whole method is
   * one transaction: a persistence failure rolls back completely (S23) and surfaces as 500/ERR-004.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the entered fields + optimistic-lock token
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable} flag)
   * @param user the acting user id (audit columns)
   * @return the recomputed aggregate document (incremented {@code revisionCount})
   */
  @Transactional
  public Schedule1Response saveSchedule1(
      long millId, int year, Schedule1Request request, boolean callerMayEdit, String user) {
    int summaryId = requireEditableSummary(millId, year);
    int expectedRevision = request.revisionCount() == null ? -1 : request.revisionCount();
    try {
      int bumped = repository.bumpRevision(summaryId, expectedRevision, request.comments(), user);
      if (bumped == 0) {
        throw new StaleRevisionException();
      }
      writeWritableDetails(summaryId, request, user);
    } catch (StaleRevisionException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      // Never log cost/volume values (AD-11) — action + status + exception type only.
      log.warn("Schedule 1 save failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return getSchedule1(millId, year, callerMayEdit);
  }

  /**
   * Delete the whole Schedule 1 (summary + all detail rows) for a mill/year (BR-08, S13). Enforces
   * the same Draft gate as save. Context is already validated in the controller (AD-4).
   *
   * @param millId the mill id
   * @param year the reporting year
   */
  @Transactional
  public void deleteSchedule1(long millId, int year) {
    int summaryId = requireEditableSummary(millId, year);
    try {
      repository.deleteSchedule(summaryId);
    } catch (DataAccessException ex) {
      log.warn("Schedule 1 delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
  }

  /**
   * The Draft-gate guard shared by save and delete: the track must be Draft (else 409) and a
   * Schedule 1 summary must exist. Returns the summary id.
   */
  private int requireEditableSummary(long millId, int year) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
    return repository.findSummary(millId, year, SCHEDULE_1_CATEGORY)
        .orElseThrow(ScheduleNotFoundException::new)
        .summaryId();
  }

  /** Upsert only the writable codes; pulled/derived codes and itemized Other-Costs rows untouched. */
  private void writeWritableDetails(int summaryId, Schedule1Request request, String user) {
    if (request.lineItems() != null) {
      for (Schedule1Request.LineItemInput li : request.lineItems()) {
        if (li.costItemCode() != null && WRITABLE_LINE_ITEM_CODES.contains(li.costItemCode())) {
          repository.upsertFixedDetail(summaryId, li.costItemCode(), li.volume(), li.cost(), user);
        }
      }
    }
    if (request.silviculture() != null) {
      Schedule1Request.SilvicultureInput silv = request.silviculture();
      if (silv.actualSpent() != null) {
        repository.upsertFixedDetail(
            summaryId, CODE_SILV_ACTUAL, silv.actualSpent().volume(), silv.actualSpent().cost(), user);
      }
      if (silv.accruedLessActual() != null) {
        repository.upsertFixedDetail(
            summaryId, CODE_SILV_ACCRUED,
            silv.accruedLessActual().volume(), silv.accruedLessActual().cost(), user);
      }
    }
    // Shared item-19 volume row (null description) only — never the itemized rows (AC2). A null
    // volume is still written so clearing the field removes the stored value.
    repository.upsertFixedDetail(summaryId, CODE_OTHER, request.otherCostsVolume(), null, user);
  }

  /**
   * Assemble the Schedule 1 document for a mill/year.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds the EDIT_SCHEDULE action (from the controller)
   * @return the aggregate document
   */
  public Schedule1Response getSchedule1(long millId, int year, boolean callerMayEdit) {
    SummaryRow summary = repository.findSummary(millId, year, SCHEDULE_1_CATEGORY)
        .orElseThrow(ScheduleNotFoundException::new);
    List<DetailRow> details = repository.findDetails(summary.summaryId());
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);

    Map<Integer, DetailRow> byCode = new HashMap<>();
    List<DetailRow> otherCostRows = new ArrayList<>();
    for (DetailRow row : details) {
      if (row.costItemCode() != null && row.costItemCode() == CODE_OTHER) {
        otherCostRows.add(row);
      } else if (row.costItemCode() != null) {
        byCode.put(row.costItemCode(), row);
      }
    }

    List<LineItem> lineItems = new ArrayList<>();
    for (Integer code : LINE_ITEM_CODES) {
      DetailRow row = byCode.get(code);
      if (row != null) {
        lineItems.add(toLineItem(row));
      }
    }

    SilvicultureBlock silviculture = new SilvicultureBlock(
        toLineItem(byCode.get(CODE_SILV_ACTUAL)),
        toLineItem(byCode.get(CODE_SILV_ACCRUED)),
        toLineItem(byCode.get(CODE_SILV_LESS_ADMIN)),
        toLineItem(byCode.get(CODE_SILV_TOTAL)));

    Integer forestMgmtAdminCost = costOf(byCode.get(CODE_FOREST_MGMT_ADMIN));
    Integer lessSilvAdminCost = costOf(byCode.get(CODE_SILV_LESS_ADMIN));

    OtherCostsSummary otherCosts = toOtherCosts(otherCostRows);

    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    return new Schedule1Response(
        millId,
        year,
        trackStatus,
        editable,
        summary.crownVolume(),
        summary.revisionCount(),
        summary.comments(),
        lineItems,
        silviculture,
        forestMgmtAdminCost,
        lessSilvAdminCost,
        otherCosts,
        null); // success message is set by the controller on the PUT echo (AD-8)
  }

  private static LineItem toLineItem(DetailRow row) {
    if (row == null) {
      return null;
    }
    return new LineItem(
        row.costItemCode(),
        normalizeVolume(row.volume()),
        row.cost(),
        perUnit(row.volume(), row.cost() == null ? null : BigDecimal.valueOf(row.cost())));
  }

  private static Integer costOf(DetailRow row) {
    return row == null ? null : row.cost();
  }

  private OtherCostsSummary toOtherCosts(List<DetailRow> otherCostRows) {
    // Always present (AD-5/AD-12): a schedule with no Other Costs still carries count 0 / subtotal 0,
    // so the client can distinguish "zero" from "missing".
    // The shared volume is carried by the item-19 row with a null/empty description (legacy).
    BigDecimal sharedVolume = otherCostRows.stream()
        .filter(r -> StringUtils.isBlank(r.itemDescription()))
        .map(DetailRow::volume)
        .findFirst()
        .orElse(null);

    List<DetailRow> itemized = otherCostRows.stream()
        .filter(r -> StringUtils.isNotBlank(r.itemDescription()))
        .toList();

    // Sum as long to avoid silent int overflow across many/large itemized costs.
    long costSubtotal = itemized.stream()
        .map(DetailRow::cost)
        .filter(c -> c != null)
        .mapToLong(Integer::longValue)
        .sum();

    return new OtherCostsSummary(
        normalizeVolume(sharedVolume),
        costSubtotal,
        perUnit(sharedVolume, BigDecimal.valueOf(costSubtotal)),
        itemized.size());
  }

  /**
   * $/m³ = cost / volume, computed server-side. Null when volume is null/zero or cost is null
   * (legacy divide-by-zero returns null). Kept at scale &ge; 1 so it serializes as a decimal
   * (e.g. {@code 50.0}, not {@code 50}).
   */
  private static BigDecimal perUnit(BigDecimal volume, BigDecimal cost) {
    if (cost == null || volume == null || volume.signum() == 0) {
      return null;
    }
    BigDecimal result = cost.divide(volume, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    return result.scale() < 1 ? result.setScale(1, RoundingMode.HALF_UP) : result;
  }

  /**
   * Normalize a volume so a whole value serializes as an integer ({@code 8000}, not {@code 8000.0000}
   * or {@code 8E+3}) while a fractional value keeps its decimals.
   */
  private static BigDecimal normalizeVolume(BigDecimal volume) {
    if (volume == null) {
      return null;
    }
    BigDecimal stripped = volume.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
