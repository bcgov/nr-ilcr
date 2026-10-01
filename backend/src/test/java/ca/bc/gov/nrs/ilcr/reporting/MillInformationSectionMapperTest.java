package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.millinformation.dto.MillInformationSection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests — the "-" substitution, the phone format and the Yes/No active flag. */
@DisplayName("MillInformationSectionMapper")
class MillInformationSectionMapperTest {

  @Test
  @DisplayName("populated section maps every field, formats the phone and reads Active as Yes")
  void populatedSection() {
    Map<String, String> row = row(populated());

    assertThat(row)
        .containsEntry("mill", "MILL INFO FULL - 7300")
        .containsEntry("millAddress1", "100 MAIN STREET")
        .containsEntry("millAddress2", "SUITE 400")
        .containsEntry("millCity", "CRANBROOK")
        .containsEntry("millPostalCode", "V1C1A1")
        .containsEntry("millRegion", "Kootenay Selling Price Zone")
        .containsEntry("millActive", "Yes")
        .containsEntry("openDate", "2021-01-05")
        .containsEntry("draftStatusDate", "2021-03-10")
        .containsEntry("submittedStatusDate", "2021-05-20")
        .containsEntry("verifiedStatusDate", "2021-07-01")
        .containsEntry("ownerClientName", "FULL OWNERSHIP HOLDINGS LTD")
        .containsEntry("contactIndicator", "Y")
        .containsEntry("headOfficeName", "HEAD OFFICE CONTACT")
        .containsEntry("headOfficePhone", "(250) 555-1212")
        .containsEntry("divisionName", "DIVISION CONTACT")
        .containsEntry("divisionPhone", "-");
  }

  @Test
  @DisplayName("every absent value becomes \"-\" and a closed mill reads Active as No")
  void absentValuesBecomeDashes() {
    MillInformationSection empty =
        new MillInformationSection(
            732,
            "7320",
            "MILL INFO NO CLIENT",
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    Map<String, String> row = row(empty);

    assertThat(row)
        .containsEntry("mill", "MILL INFO NO CLIENT - 7320")
        .containsEntry("millActive", "No");
    // Address, region and contact fields fall back to "-", as legacy substituted them explicitly.
    assertThat(row)
        .extractingByKeys(
            "millAddress1",
            "millAddress2",
            "millCity",
            "millPostalCode",
            "millRegion",
            "headOfficeName",
            "headOfficePhone",
            "divisionName",
            "divisionPhone")
        .containsOnly("-");
    // Milestones, ownership name and the contact indicator fall back to EMPTY: legacy never dashed
    // these, it let its null sweep map them to "". A dash here is a visible parity break.
    assertThat(row)
        .extractingByKeys(
            "openDate",
            "draftStatusDate",
            "submittedStatusDate",
            "verifiedStatusDate",
            "ownerClientName",
            "contactIndicator")
        .containsOnly("");
  }

  @Test
  @DisplayName("a null mill name or number never reaches the heading or the bookmark as \"null\"")
  void nullMillIdentityIsSubstituted() {
    MillInformationSection nameless =
        new MillInformationSection(
            730, null, null, true, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null);

    assertThat(row(nameless)).containsEntry("mill", "- - -");
    assertThat(MillInformationSectionMapper.bookmarkTitle(nameless)).isEqualTo("- - -");
  }

  @Test
  @DisplayName("a value that is present but blank is treated as absent, not printed as whitespace")
  void whitespaceOnlyValuesAreTreatedAsAbsent() {
    // Oracle VARCHAR2 columns happily hold "   ", and the report view's milestone columns arrive as
    // prefix-only strings that strip down to whitespace. Both must fall back like a true null does,
    // or a section shows a blank gap where it should show "-" (or nothing) consistently.
    MillInformationSection blanks =
        new MillInformationSection(
            730, "7300", "MILL", true, "  ", "  ", "  ", "  ", "  ", "  ", "  ", "  ", "  ", "  ",
            "  ", "  ", "  ", "  ", "  ");

    Map<String, String> row = row(blanks);

    assertThat(row)
        .extractingByKeys(
            "millAddress1", "millCity", "millRegion", "headOfficeName", "divisionName")
        .containsOnly("-");
    assertThat(row)
        .extractingByKeys("openDate", "draftStatusDate", "ownerClientName", "contactIndicator")
        .containsOnly("");
  }

  @Test
  @DisplayName("a phone that is not exactly ten characters passes through unformatted")
  void nonTenDigitPhonePassesThrough() {
    MillInformationSection section = sectionWithHeadOfficePhone("250555121");

    assertThat(row(section)).containsEntry("headOfficePhone", "250555121");
  }

  @Test
  @DisplayName("a ten-character phone that is not ten digits is shown as stored, not fabricated")
  void tenCharacterNonNumericPhoneIsNotFormatted() {
    // BUSINESS_PHONE is VARCHAR2(10) with no format constraint. Legacy tested length alone and then
    // sliced, turning " 250555121" into "( 25) 055-5121" — a number that looks real and is not.
    assertThat(row(sectionWithHeadOfficePhone(" 250555121")))
        .containsEntry("headOfficePhone", "250555121");
    assertThat(row(sectionWithHeadOfficePhone("250-555-12")))
        .containsEntry("headOfficePhone", "250-555-12");
  }

  @Test
  @DisplayName("a padded value is trimmed rather than rendered with a leading gap")
  void paddedValueIsTrimmed() {
    // Not " 2505551212 ": BUSINESS_PHONE is VARCHAR2(10), so ten digits leave no room for padding
    // and that input cannot exist in delivery. What CAN exist is a short value stored with padding.
    assertThat(row(sectionWithHeadOfficePhone("  250-5551  ")))
        .containsEntry("headOfficePhone", "250-5551");
  }

  @Test
  @DisplayName("a blank phone becomes \"-\" rather than an empty cell")
  void blankPhoneBecomesDash() {
    MillInformationSection section = sectionWithHeadOfficePhone("   ");

    assertThat(row(section)).containsEntry("headOfficePhone", "-");
  }

  @Test
  @DisplayName("the bookmark title names the mill")
  void bookmarkTitleNamesTheMill() {
    assertThat(MillInformationSectionMapper.bookmarkTitle(populated()))
        .isEqualTo("MILL INFO FULL - 7300");
  }

  /**
   * The mapped row as a String map. {@code SectionData.rows()} is wildcard-typed for the fill API,
   * which blocks value assertions; every value this mapper produces is a String.
   */
  private static Map<String, String> row(MillInformationSection section) {
    Map<String, String> mapped = new LinkedHashMap<>();
    MillInformationSectionMapper.map(section)
        .rows()
        .getFirst()
        .forEach((key, value) -> mapped.put(key, (String) value));
    return mapped;
  }

  private static MillInformationSection populated() {
    return new MillInformationSection(
        730,
        "7300",
        "MILL INFO FULL",
        true,
        "Kootenay Selling Price Zone",
        "FULL OWNERSHIP HOLDINGS LTD",
        "100 MAIN STREET",
        "SUITE 400",
        "CRANBROOK",
        "V1C1A1",
        "Y",
        "HEAD OFFICE CONTACT",
        "2505551212",
        "DIVISION CONTACT",
        null,
        "2021-01-05",
        "2021-03-10",
        "2021-05-20",
        "2021-07-01");
  }

  private static MillInformationSection sectionWithHeadOfficePhone(String phone) {
    return new MillInformationSection(
        730,
        "7300",
        "MILL INFO FULL",
        true,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "HEAD OFFICE CONTACT",
        phone,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
