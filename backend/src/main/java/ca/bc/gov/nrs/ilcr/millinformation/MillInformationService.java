package ca.bc.gov.nrs.ilcr.millinformation;

import ca.bc.gov.nrs.ilcr.millinformation.dto.MillInformationSection;
import ca.bc.gov.nrs.ilcr.util.LegacyDateText;
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
    List<MillInformationSection> sections =
        repository.findSectionRows(year).stream()
            .map(row -> toSection(row, region(regions, row)))
            .toList();
    // Count only. Contact names, phone numbers and addresses are personal data (AD-11/NFR3).
    log.info("Read {} mill information sections for year {}", sections.size(), year);
    return sections;
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
