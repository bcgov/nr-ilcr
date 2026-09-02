package ca.bc.gov.nrs.ilcr.millreportstatus;

import ca.bc.gov.nrs.ilcr.millinformation.MillInformationRepository;
import ca.bc.gov.nrs.ilcr.millreportstatus.dto.MillReportStatusRow;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Mill Status Report table's data (AD-14): where every mill stands on both schedule tracks
 * for one reporting year, read once and handed to the controller as DTOs. Entities never leave this
 * class.
 *
 * <p>Read-only — rendering the table changes no reporting data.
 */
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
@Transactional(readOnly = true)
public class MillReportStatusService {

  /** The view's per-year status code meaning the mill was active; legacy renders this Yes/No. */
  private static final String ACTIVE_STATUS_CODE = "ACT";

  private static final Logger log = LoggerFactory.getLogger(MillReportStatusService.class);

  private final MillReportStatusRepository repository;
  private final MillInformationRepository millInformationRepository;

  /**
   * Constructs a new MillReportStatusService.
   *
   * @param repository the per-year status-row read
   * @param millInformationRepository borrowed for its selling-price zone lookup ONLY. Sharing Story
   *     19.1's query rather than declaring a second copy keeps ONE definition of the read that has
   *     to survive a dangling PUBLIC synonym; the precedent for a service reaching another
   *     package's repository is {@code AssignmentService} → {@code MillContextRepository}
   */
  public MillReportStatusService(
      MillReportStatusRepository repository, MillInformationRepository millInformationRepository) {
    this.repository = repository;
    this.millInformationRepository = millInformationRepository;
  }

  /**
   * Every mill's reporting-cycle status for the year, ordered by mill id — legacy's order, and NOT
   * the same as mill number (fixture mill 730 carries mill number 7300). The page sorts client-side
   * from here.
   *
   * <p>An empty list means no mill has a report-status row for that year. That is a correct render
   * of an empty sortable table, not an error — unlike Story 19.1, where a missing PDF is not a
   * legitimate document. Legacy's own DB-failure path showed an empty table with no message at all
   * ({@code MillReportStatusDAO.java:96} returns null and {@code MillReportStatusMB} has no
   * try/catch); the controller's error banner is a recorded improvement on that.
   *
   * @param year the reporting year
   * @return one row per mill in mill-id order; empty when the year has no mills
   */
  public List<MillReportStatusRow> findRows(int year) {
    Map<String, String> regions = zoneDescriptions();
    List<MillReportStatusRow> rows =
        repository.findStatusRows(year).stream()
            .map(row -> toRow(row, region(regions, row)))
            .toList();
    // Count only. Mill names are commercial identifiers and the milestone strings are workflow
    // history; neither belongs in a log line (AD-11/NFR3).
    log.info("Read {} mill report status rows for year {}", rows.size(), year);
    return rows;
  }

  /**
   * A row's Region description, or {@code null} when its zone code is absent or undescribed.
   *
   * <p>The null-code guard is NOT decoration, it is the fix for a 500. Most mills carry no zone
   * code at all (fixture mills 514, 731, 732 and 733 among them), and {@code Map.of().get(null)}
   * THROWS NullPointerException on Java 21 — {@code ImmutableCollections.MapN.get} calls {@code
   * Objects.requireNonNull} — where {@code HashMap.get(null)} quietly answers null. So on the very
   * database the degrade below exists for, the first null-zone mill used to take out the whole
   * endpoint: exactly the outcome reading the code table separately is meant to prevent. The catch
   * now also returns a null-tolerant {@link Collections#emptyMap()} rather than {@code Map.of()},
   * so neither half of the pair can reintroduce it alone.
   */
  private static String region(Map<String, String> regions, MillReportStatusRowEntity row) {
    return row.regionCode() == null ? null : regions.get(row.regionCode());
  }

  /**
   * The zone code to description lookup, or an empty map when the table cannot be read.
   *
   * <p>Degrading here rather than failing is deliberate, and it is why the code is read SEPARATELY
   * instead of joined. {@code APPRAISAL_SELL_PRICE_ZONE_CODE} is a shared ministry code table
   * reached through a PUBLIC synonym, and Oracle rejects any statement naming a synonym whose
   * target is missing at parse time with ORA-00942 — join type notwithstanding. Region is a display
   * description with an established {@code "-"} fallback, so an absent lookup costs one column;
   * letting it propagate would deny the administrator the entire table over a decorative field.
   *
   * <p><b>The degrade was masking a defect, not an environment gap.</b> Until 2026-09-02 the shared
   * query named {@code THE.ISP_SELL_PRICE_ZONE_CODE} — the name of the {@code MILL} COLUMN, not of
   * the table legacy reads ({@code THE.APPRAISAL_SELL_PRICE_ZONE_CODE}, per {@code Mill.java:59-61}
   * and {@code hibernate.cfg.xml:90}). That table does not exist on the FTA database, so this catch
   * fired on every request and every mill's Region rendered {@code "-"} while legacy showed real
   * descriptions. The name is fixed in {@code MillInformationRepository#findZoneDescriptions}; a
   * WARN here now means the environment is genuinely broken.
   *
   * <p><b>Why catching here is enough, and what would break it.</b> A caught {@code
   * DataAccessException} inside a {@code @Transactional} method can still produce a 500: if the
   * failing call sits behind its own transaction proxy, it joins this transaction, marks it
   * rollback-only, and the outer commit then throws {@code UnexpectedRollbackException} after the
   * catch has already handled things. That does NOT happen here — proven end-to-end by {@code
   * MillReportStatusIT.unreadableZoneTableDegradesRatherThanFailing}, which renames the table away
   * and asserts a 200 — because neither repository declares {@code @Transactional} and neither
   * extends {@code CrudRepository}, so a declared {@code @Query} method has no transaction
   * interceptor of its own to trip. Adding {@code @Transactional} to {@code
   * MillInformationRepository}, or widening it to {@code CrudRepository}, would reintroduce the
   * 500. That IT is the tripwire; do not delete it.
   */
  private Map<String, String> zoneDescriptions() {
    try {
      Map<String, String> byCode = new HashMap<>();
      millInformationRepository
          .findZoneDescriptions()
          .forEach(zone -> byCode.put(zone.code(), zone.description()));
      return byCode;
    } catch (DataAccessException e) {
      log.warn(
          "Selling-price zone descriptions are unavailable ({}); every mill's Region will render as"
              + " \"-\". This is an environment gap, not report data.",
          e.getMostSpecificCause().getMessage());
      // Collections.emptyMap(), never Map.of(): the latter rejects a null key with an NPE, and
      // every
      // caller here looks up a nullable zone code. See the region(...) note above.
      return Collections.emptyMap();
    }
  }

  /**
   * Project one view row onto the wire DTO.
   *
   * <p>The seven milestone strings pass through VERBATIM. {@code LegacyDateText.stripPrefix} is NOT
   * called here, unlike {@code MillInformationService.toSection}: this surface renders the raw
   * prefixed value and explains it with an O/D/S/V legend, exactly as legacy's {@code h:outputText}
   * bindings do ({@code millReportStatus.xhtml:93-110}). Stripping would silently delete the letter
   * the legend decodes.
   */
  private static MillReportStatusRow toRow(MillReportStatusRowEntity row, String region) {
    return new MillReportStatusRow(
        row.millId(),
        row.millNumber(),
        row.millName(),
        region,
        ACTIVE_STATUS_CODE.equals(row.millStatusCode()),
        row.openDate(),
        row.draftDate(),
        row.submitDate(),
        row.verifyDate(),
        row.silviDraftDate(),
        row.silviSubmitDate(),
        row.silviVerifyDate());
  }
}
