package ca.bc.gov.nrs.ilcr.schedule9;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.CostRow;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.RecordRow;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecord;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schedule 9 document assembly + server-side derivation (AD-5/AD-6). Reads the contractual work
 * records and their cost lines, joins each record to its cost + Contractual Item, and derives the
 * {@code costPerUnit} ($/Unit). The read never re-reads cross-schedule figures — Schedule 9 is
 * self-contained.
 *
 * <p>{@code editable} = the caller holds {@code EDIT_SCHEDULE} AND the 1–10 track is Draft, computed
 * here and server-authoritative (AD-9, S30). A non-Draft mill still lists every record.
 */
@Service
public class Schedule9Service {

  private static final String STATUS_DRAFT = "D";

  private final Schedule9Repository repository;

  public Schedule9Service(Schedule9Repository repository) {
    this.repository = repository;
  }

  /**
   * The Schedule 9 document for a mill/year. A valid, active mill/year with no records returns an
   * empty {@code records} list (never a 404 — the guards live in {@code MillContextService}).
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the pinned document
   */
  @Transactional(readOnly = true)
  public Schedule9Response getSchedule9(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    // One cost line per record; lowest ILCR_COST_REPORT_DETAIL_ID wins if delivery ever holds more
    // (no unique constraint on the FK) — the repository's ORDER BY makes that deterministic, mirroring
    // how Schedule 5 resolves its keyed detail rows (its recorded deviation (c)).
    Map<Integer, CostRow> costByRecord = repository.findCostLines(millId, year).stream()
        .collect(Collectors.toMap(CostRow::reportId, Function.identity(), (first, dup) -> first));

    List<ContractualWorkRecord> records = repository.findRecords(millId, year).stream()
        .map(row -> toRecord(row, costByRecord.get(row.id())))
        .toList();

    return new Schedule9Response(millId, year, trackStatus, editable, records, null);
  }

  private static ContractualWorkRecord toRecord(RecordRow row, CostRow cost) {
    Integer costValue = cost == null ? null : cost.cost();
    CodeDescriptionDto contractualItem = cost == null || cost.itemCode() == null
        ? null
        : new CodeDescriptionDto(String.valueOf(cost.itemCode()), cost.itemName());
    String itemDescription = cost == null ? null : cost.itemDescription();

    return new ContractualWorkRecord(
        row.id(),
        row.revisionCount(),
        row.contractorId(),
        contractualItem,
        itemDescription,
        code(row.unitCode(), row.unitCodeDescription()),
        row.unitDescription(),
        row.numberOfUnits(),
        code(row.becCode(), row.becDescription()),
        costValue,
        costPerUnit(costValue, row.numberOfUnits()),
        row.sideSlopePct(),
        code(row.sourceCode(), row.sourceCodeDescription()),
        row.sourceDescription(),
        row.comments());
  }

  /** A code/description pair, or null when the code itself is absent. */
  private static CodeDescriptionDto code(String code, String description) {
    return code == null ? null : new CodeDescriptionDto(code, description);
  }

  /** $/Unit = cost ÷ units (S14: null when units are zero/blank), scale 2 HALF_UP. */
  private static BigDecimal costPerUnit(Integer cost, BigDecimal units) {
    if (cost == null || units == null || units.signum() == 0) {
      return null;
    }
    return BigDecimal.valueOf(cost).divide(units, 2, RoundingMode.HALF_UP);
  }
}
