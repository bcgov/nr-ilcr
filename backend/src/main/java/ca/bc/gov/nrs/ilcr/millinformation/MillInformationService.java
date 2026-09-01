package ca.bc.gov.nrs.ilcr.millinformation;

import ca.bc.gov.nrs.ilcr.millinformation.dto.MillInformationSection;
import ca.bc.gov.nrs.ilcr.util.LegacyDateText;
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
            .map(row -> toSection(row, regions.get(row.regionCode())))
            .toList();
    // Count only. Contact names, phone numbers and addresses are personal data (AD-11/NFR3).
    log.info("Read {} mill information sections for year {}", sections.size(), year);
    return sections;
  }

  /**
   * The zone code to description lookup, or an empty map when the table cannot be read.
   *
   * <p>Degrading here rather than failing is deliberate. {@code ISP_SELL_PRICE_ZONE_CODE} is a
   * shared ministry table reached through a PUBLIC synonym, and that synonym is dangling on the FTA
   * development database — the table is simply absent, which Oracle reports as ORA-00942. Region is
   * a display description with an established "-" fallback, so an absent lookup costs one line per
   * section; letting it propagate would deny the administrator the entire report over a decorative
   * field. Logged at WARN once per render so a genuinely broken environment is still visible.
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
      return Map.of();
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
