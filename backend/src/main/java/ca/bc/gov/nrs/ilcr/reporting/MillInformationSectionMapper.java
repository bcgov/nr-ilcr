package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millinformation.dto.MillInformationSection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps one mill's {@link MillInformationSection} to the template's field row.
 *
 * <p>This is where absent data becomes {@code "-"}. Legacy did the same substitution while building
 * its {@code JRMapCollectionDataSource} ({@code MillReportStatusReport.createReportDataSource}),
 * and keeping it here rather than in the read means the service can still tell "no postal code"
 * from a postal code that literally reads "-".
 */
final class MillInformationSectionMapper {

  /** What legacy printed wherever an address, region or contact field had nothing. */
  private static final String ABSENT = "-";

  /**
   * What legacy printed for an absent milestone date, ownership name or contact indicator: nothing.
   * Those three never went through the dash substitution — they fell to {@code ""} via the null
   * sweep at the end of {@code MillReportStatusReport.createReportDataSource}.
   */
  private static final String BLANK = "";

  private static final int PHONE_DIGITS = 10;

  private MillInformationSectionMapper() {}

  /**
   * Build the single-row datasource for one mill's section.
   *
   * @param section the mill's content
   * @return the section data, one row keyed by the template's field names
   */
  static SectionData map(MillInformationSection section) {
    Map<String, Object> row = new HashMap<>();
    row.put("mill", millTitle(section));
    row.put("millAddress1", orDash(section.address1()));
    row.put("millAddress2", orDash(section.address2()));
    row.put("millCity", orDash(section.city()));
    row.put("millPostalCode", orDash(section.postalCode()));
    row.put("millRegion", orDash(section.region()));
    // Legacy renders the ACT/CLS status code as a plain Yes/No on the report.
    row.put("millActive", section.active() ? "Yes" : "No");
    row.put("openDate", orBlank(section.openDate()));
    row.put("draftStatusDate", orBlank(section.draftDate()));
    row.put("submittedStatusDate", orBlank(section.submitDate()));
    row.put("verifiedStatusDate", orBlank(section.verifyDate()));
    row.put("ownerClientName", orBlank(section.clientName()));
    row.put("contactIndicator", orBlank(section.headOfficeContactIndicator()));
    row.put("headOfficeName", orDash(section.headOfficeContactName()));
    row.put("headOfficePhone", phone(section.headOfficePhone()));
    row.put("divisionName", orDash(section.divisionContactName()));
    row.put("divisionPhone", phone(section.divisionPhone()));
    // No section-level parameters: every value this report shows is per-mill and rides the row.
    // Passing an empty map rather than inventing one keeps the fill from silently ignoring
    // parameters a later change might add here without wiring them through ReportService.
    return new SectionData(List.of(row), Map.of());
  }

  /** The PDF outline title for a mill's section — the same text as the section heading. */
  static String bookmarkTitle(MillInformationSection section) {
    return millTitle(section);
  }

  /**
   * {@code name - number}, the legacy heading. Both halves are nullable in {@code THE.MILL}, and
   * concatenating them raw would print the literal "null" into a heading and a PDF bookmark, so
   * each is substituted before joining.
   */
  private static String millTitle(MillInformationSection section) {
    return orDash(section.millName()) + " - " + orDash(section.millNumber());
  }

  private static String orDash(String value) {
    return value == null || value.isBlank() ? ABSENT : value;
  }

  /** Absent → empty, the legacy null sweep. */
  private static String orBlank(String value) {
    return value == null || value.isBlank() ? BLANK : value;
  }

  /**
   * Format a stored phone number as {@code (250) 555-1212}, matching the legacy {@code
   * ILCRPhoneNumberConverter}. Anything that is not ten DIGITS is passed through trimmed but
   * otherwise unaltered.
   *
   * <p>Two deliberate departures from legacy, both raised in review on PR #401. Legacy tested
   * LENGTH and then sliced blindly, so a ten-character value that is not ten digits became a
   * fabricated number — {@code " 250555121"} rendered as {@code "( 25) 055-5121"}, which reads as
   * real on a ministry report. And the pass-through is trimmed, which legacy's was not, so a short
   * value stored with padding does not render with a leading gap. {@code BUSINESS_PHONE} is a
   * {@code VARCHAR2(10)} with no format constraint, so both states are reachable.
   */
  private static String phone(String value) {
    if (value == null || value.isBlank()) {
      return ABSENT;
    }
    String trimmed = value.trim();
    if (trimmed.length() != PHONE_DIGITS || !trimmed.chars().allMatch(Character::isDigit)) {
      return trimmed;
    }
    return String.format(
        "(%s) %s-%s", trimmed.substring(0, 3), trimmed.substring(3, 6), trimmed.substring(6, 10));
  }
}
