package ca.bc.gov.nrs.ilcr.millinformation;

import ca.bc.gov.nrs.ilcr.millinformation.dto.MillInformationSection;
import ca.bc.gov.nrs.ilcr.util.LegacyDateText;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Mill Information report's data (AD-14): the per-mill content for one reporting year,
 * read once and handed to the reporting layer as DTOs. Entities never leave this class.
 *
 * <p>Read-only — generating the report changes no reporting data (BR-08).
 */
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
@Transactional(readOnly = true)
public class MillInformationService {

  /** The status-xref code meaning the mill is active; legacy prints this as Yes/No. */
  private static final String ACTIVE_STATUS_CODE = "ACT";

  private static final Logger log = LoggerFactory.getLogger(MillInformationService.class);

  private final MillInformationRepository repository;

  /**
   * Constructs a new MillInformationService.
   *
   * @param repository the mill-information read
   */
  public MillInformationService(MillInformationRepository repository) {
    this.repository = repository;
  }

  /**
   * Every mill's section content for the reporting year, ordered by mill id — legacy's order, and
   * NOT the same as mill number (fixture mill 730 carries mill number 7300).
   *
   * <p>An empty list means no mill has a report-status row for that year — a real outcome for a
   * year that was never opened, and the caller's signal that there is no report to produce.
   *
   * @param year the reporting year
   * @return one section per mill in mill-id order; empty when the year has no mills
   */
  public List<MillInformationSection> findSections(int year) {
    Map<String, String> regions = zoneDescriptions();
    // null mill predicate = every mill. Passing null here rather than calling a second query is
    // what keeps the all-mills report and the per-mill drill-down incapable of drifting — see
    // MillInformationRepository#findSectionRows.
    List<MillInformationSection> sections =
        repository.findSectionRows(year, null).stream()
            .map(row -> toSection(row, region(regions, row)))
            .toList();
    // Count only. Contact names, phone numbers and addresses are personal data (AD-11/NFR3).
    log.info("Read {} mill information sections for year {}", sections.size(), year);
    return sections;
  }

  /**
   * ONE mill's section content for the reporting year — the per-mill drill-down (Story 19.3,
   * UC-MRPT-002 S02 / UC-MRPT-004 S02) launched from the Mill Status Report table.
   *
   * <p>Deliberately the same read, the same Region lookup and the same {@code toSection} projection
   * as {@link #findSections(int)}, differing only in the mill predicate. That is the parity
   * contract: the section this returns must be byte-identical to the one the all-mills PDF renders
   * for the same mill, and it can only stay so while there is one projection to change.
   *
   * <p>An empty {@link java.util.Optional} means this mill has no report-status row for that year —
   * a real outcome (a mill added after the year was initialised, or a year the mill never reported
   * in), and the caller's signal to answer 404 rather than produce an empty PDF.
   *
   * <p><b>Takes the FIRST row, and that is not laziness.</b> {@code ILCR_MILL_REPORT_STATUS_RPT_VW}
   * is a view, not a table, so nothing constrains it to one row per (year, mill) — {@code
   * MillContextRepository.findStatusDates} documents the same fact and takes first-row semantics
   * for it, as legacy did with {@code get(0)}. The all-mills report renders whatever the view
   * yields; here the contract is one mill, one section, so a duplicate must produce one section
   * rather than an arbitrary-length PDF.
   *
   * <p>Which row that is, is <b>reproducible</b>, and it is the QUERY that makes it so. {@code
   * findSectionRows} orders by mill id and then by every view column the section projects, so the
   * first row is the same one on every execution. Do not shorten that {@code ORDER BY} to mill id
   * alone: {@code findFirst} would then return whatever Oracle's plan emitted, and two consecutive
   * drill-downs of one mill could show the administrator different addresses or contacts. See
   * {@link MillInformationRepository#findSectionRows}.
   *
   * @param millId the mill to report on — the mill id from the status table's clicked row, NOT the
   *     mill number
   * @param year the reporting year
   * @return the mill's section, or empty when it has no report-status row for the year
   */
  public Optional<MillInformationSection> findSection(long millId, int year) {
    Map<String, String> regions = zoneDescriptions();
    List<MillInformationRowEntity> rows = repository.findSectionRows(year, millId);
    // Mill id and year only, and a count. Everything else on this row is personal data
    // (AD-11/NFR3): client name, contact names, phone numbers, addresses.
    log.info("Read {} mill information row(s) for mill {} year {}", rows.size(), millId, year);
    return rows.stream().findFirst().map(row -> toSection(row, region(regions, row)));
  }

  /**
   * A row's Region description, or {@code null} when its zone code is absent or undescribed.
   *
   * <p>The null-code guard is NOT decoration, it is the fix for a 500. A mill may carry no zone
   * code at all, and {@code Map.of().get(null)} THROWS NullPointerException on Java 21 — {@code
   * ImmutableCollections.MapN.get} calls {@code Objects.requireNonNull} — where {@code
   * HashMap.get(null)} quietly answers null. So whenever the degrade below fired, the first
   * null-zone mill took out the whole report: exactly the outcome reading the code table separately
   * is meant to prevent. Both halves of the pair were fixed together — this guard, and the {@link
   * Collections#emptyMap()} the catch now returns — so neither can reintroduce it alone. Story 19.2
   * fixed its own copy of this in-story; this is 19.1's, tracked in {@code deferred-work.md}.
   */
  private static String region(Map<String, String> regions, MillInformationRowEntity row) {
    return row.regionCode() == null ? null : regions.get(row.regionCode());
  }

  /**
   * The zone code to description lookup, or an empty map when the table cannot be read.
   *
   * <p>Degrading here rather than failing is deliberate. {@code APPRAISAL_SELL_PRICE_ZONE_CODE} is
   * a shared ministry code table reached through a PUBLIC synonym; when a synonym's target is
   * missing Oracle rejects the statement at parse time with ORA-00942. Region is a display
   * description with an established "-" fallback, so an absent lookup costs one line per section;
   * letting it propagate would deny the administrator the entire report over a decorative field.
   * Logged at WARN once per render so a genuinely broken environment is still visible.
   *
   * <p>The WARN is no longer the normal case. Until 2026-09-02 this query named {@code
   * THE.ISP_SELL_PRICE_ZONE_CODE} — the {@code MILL} column's name, not the table legacy reads — so
   * the catch fired on every request and every Region rendered "-". See {@code
   * MillInformationRepository#findZoneDescriptions}.
   */
  private Map<String, String> zoneDescriptions() {
    try {
      Map<String, String> byCode = new HashMap<>();
      repository
          .findZoneDescriptions()
          .forEach(zone -> byCode.put(zone.code(), zone.description()));
      return byCode;
    } catch (DataAccessException e) {
      log.warn(
          "Selling-price zone descriptions are unavailable ({}); every mill's Region will render as"
              + " \"-\". This is an environment gap, not report data.",
          e.getMostSpecificCause().getMessage());
      // Collections.emptyMap(), never Map.of(): the latter rejects a null key with an NPE, and the
      // caller looks up a nullable zone code. See the region(...) note above.
      return Collections.emptyMap();
    }
  }

  private static MillInformationSection toSection(MillInformationRowEntity row, String region) {
    return new MillInformationSection(
        row.millId(),
        row.millNumber(),
        row.millName(),
        ACTIVE_STATUS_CODE.equals(row.millStatusCode()),
        region,
        row.clientLocationName(),
        row.address1(),
        row.address2(),
        row.city(),
        row.postalCode(),
        row.headOfficeContactIndicator(),
        row.headOfficeContactName(),
        row.headOfficePhone(),
        row.divisionContactName(),
        row.divisionPhone(),
        LegacyDateText.stripPrefix(row.openDate()),
        LegacyDateText.stripPrefix(row.draftDate()),
        LegacyDateText.stripPrefix(row.submitDate()),
        LegacyDateText.stripPrefix(row.verifyDate()));
  }
}
