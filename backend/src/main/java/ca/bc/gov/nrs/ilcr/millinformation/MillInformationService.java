package ca.bc.gov.nrs.ilcr.millinformation;

import ca.bc.gov.nrs.ilcr.millinformation.dto.MillInformationSection;
import ca.bc.gov.nrs.ilcr.util.LegacyDateText;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
   * Every mill's section content for the reporting year, ordered by mill number.
   *
   * <p>An empty list means no mill has a report-status row for that year — a real outcome for a
   * year that was never opened, and the caller's signal that there is no report to produce.
   *
   * @param year the reporting year
   * @return one section per mill; empty when the year has no mills
   */
  public List<MillInformationSection> findSections(int year) {
    List<MillInformationSection> sections =
        repository.findSectionRows(year).stream().map(MillInformationService::toSection).toList();
    // Count only. Contact names, phone numbers and addresses are personal data (AD-11/NFR3).
    log.info("Read {} mill information sections for year {}", sections.size(), year);
    return sections;
  }

  private static MillInformationSection toSection(MillInformationRowEntity row) {
    return new MillInformationSection(
        row.millId(),
        row.millNumber(),
        row.millName(),
        ACTIVE_STATUS_CODE.equals(row.millStatusCode()),
        row.regionDescription(),
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
