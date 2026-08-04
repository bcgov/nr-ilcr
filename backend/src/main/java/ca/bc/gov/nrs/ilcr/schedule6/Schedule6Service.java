package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.CostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.RoadRecordRow;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecord;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 6 (Road Management Costs) read document from the stored
 * {@code ROAD_MAINTENANCE_REPORT} records and their item-69 cost details, computing every derived
 * value server-side (AD-5, AD-6): the Resource Management Grouping (RMG, BR-04), the $/m&sup3;
 * cost-per-volume (BR-04/BR-07), and the running totals (BR-07). The mill/year context is validated
 * by {@code MillContextService} in the controller before this runs (AD-4).
 *
 * <p>A valid, active mill/year with NO road records is NOT a 404 — it is the legitimate no-records
 * state and yields a 200 {@code roadRecords: []} with zero totals (mirrors the legacy
 * {@code Schedule6DAO.getSchedule}, which returned an empty document, never null, for an empty
 * result; the 404 is reserved for the missing mill/year context, Story 8.1 Task 1). A record whose
 * classification (TSA/TSB/TFL) is entirely blank is a general-comment placeholder (S18): it is
 * excluded from {@code roadRecords} but its {@code COMMENTS} supplies the schedule-level
 * {@code generalComments}. Read-only for Story 8.1 (GET); the write path arrives with Story 8.2.
 */
@Service
@Slf4j
public class Schedule6Service {

  private static final String STATUS_DRAFT = "D";
  private static final String AREA_TYPE_TFL = "TFL";

  private final Schedule6Repository repository;

  public Schedule6Service(Schedule6Repository repository) {
    this.repository = repository;
  }

  /**
   * Assemble the Schedule 6 read document for a mill/year.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (from the controller)
   * @return the read document (never null; {@code roadRecords: []} when the mill/year has none)
   */
  @Transactional(readOnly = true)
  public Schedule6Response getSchedule6(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    List<RoadRecordRow> rows = repository.findRoadRecords(millId, year);
    Map<Integer, CostDetailRow> costByRecord = new HashMap<>();
    for (CostDetailRow detail : repository.findCostDetails(millId, year)) {
      // One item-69 detail per road record is the invariant; if a duplicate ever exists,
      // first-by-id wins (rows ordered by detail id) so a derived total can't depend on row order.
      if (costByRecord.putIfAbsent(detail.roadMaintenanceReportId(), detail) != null) {
        log.warn("Schedule 6 mill {}/{}: duplicate item-69 cost detail for road record {}; "
            + "keeping first-by-id", millId, year, detail.roadMaintenanceReportId());
      }
    }

    List<RoadRecord> roadRecords = new ArrayList<>();
    long totalCost = 0L;
    BigDecimal totalVolume = BigDecimal.ZERO;
    // The general comment is stored replicated on every road-record row (legacy data-model quirk);
    // legacy reads the LAST row's COMMENTS, so track it across all rows (placeholders included).
    String generalComments = null;

    for (RoadRecordRow row : rows) {
      // Comments are served raw, exactly as saved — legacy Schedule6DAO.getReport appends COMMENTS
      // untrimmed (read-side normalization rejected at code review 2026-08-04, legacy-faithful).
      generalComments = row.generalComment();
      // Classification codes ARE normalized (trimToNull), once, so the TSA-vs-TFL split below and
      // RoadGroupLookup.rmgFor decide from identical values; rmgFor's TFL-first "!= null" routing
      // is legacy-verbatim (RoadMaintenanceReportType.getRmg).
      String tsaNumber = StringUtils.trimToNull(row.tsaNumber());
      String tsbNumberCode = StringUtils.trimToNull(row.tsbNumberCode());
      String tflNumberCode = StringUtils.trimToNull(row.tflNumberCode());
      if (tsaNumber == null && tsbNumberCode == null && tflNumberCode == null) {
        // General-comment placeholder (S18, legacy Schedule6MB onlyGeneralCommentExists): no
        // classification at all — contributes the comment, not a road record. A cost detail on a
        // placeholder is a data anomaly whose money would silently vanish from totals; say so.
        if (costByRecord.containsKey(row.recordId())) {
          log.warn("Schedule 6 mill {}/{}: placeholder row {} carries an item-69 cost detail; "
              + "excluded from records and totals", millId, year, row.recordId());
        }
        continue;
      }
      CostDetailRow detail = costByRecord.get(row.recordId());
      BigDecimal volume = detail == null ? null : detail.volume();
      Integer cost = detail == null ? null : detail.cost();
      String comments = detail == null ? null : detail.comments();

      boolean tfl = tsaNumber == null && tflNumberCode != null;
      roadRecords.add(new RoadRecord(
          row.recordId(),
          row.revisionCount(),
          tfl ? AREA_TYPE_TFL : tsaNumber,
          tfl ? tflNumberCode : null,
          tfl ? null : tsbNumberCode,
          RoadGroupLookup.rmgFor(tsaNumber, tsbNumberCode, tflNumberCode),
          normalizeVolume(volume),
          cost,
          perUnit(cost == null ? null : (long) cost, volume),
          comments));

      if (cost != null) {
        totalCost += cost;
      }
      if (volume != null) {
        totalVolume = totalVolume.add(volume);
      }
    }

    // generalComments now holds the LAST row's COMMENTS (legacy reads the general comment off the
    // last road-record row; the data model replicates it on every row, so any row would do).
    return new Schedule6Response(
        millId, year, trackStatus, editable,
        generalComments,
        roadRecords,
        normalizeVolume(totalVolume),
        totalCost,
        perUnit(totalCost, totalVolume),
        null);
  }

  /**
   * $/m&sup3; = cost / volume, computed server-side to match legacy {@code CostVolumeCommentsType
   * .getCostVolume} ({@code CoreUtil.bigDecimalDivision}: divide at scale 10 HALF_UP, then round to
   * scale 2 HALF_UP). Null when cost is null or volume is null/zero.
   */
  private static BigDecimal perUnit(Long cost, BigDecimal volume) {
    if (cost == null || volume == null || volume.signum() == 0) {
      return null;
    }
    return BigDecimal.valueOf(cost)
        .divide(volume, 10, RoundingMode.HALF_UP)
        .setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Normalize a volume so a whole value serializes as an integer ({@code 1000}, not
   * {@code 1000.0000} or {@code 1.0E+3}) while a fractional value keeps its decimals. Null-safe.
   */
  private static BigDecimal normalizeVolume(BigDecimal volume) {
    if (volume == null) {
      return null;
    }
    BigDecimal stripped = volume.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
