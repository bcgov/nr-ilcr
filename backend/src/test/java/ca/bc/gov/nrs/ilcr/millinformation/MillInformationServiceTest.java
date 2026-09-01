package ca.bc.gov.nrs.ilcr.millinformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millinformation.dto.MillInformationSection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;

/**
 * Unit tests for the entity → DTO projection.
 *
 * <p>These exist because {@code toSection} passes fifteen consecutive {@code String} arguments
 * positionally, so any adjacent swap — address 1 for address 2, city for postal code, head-office
 * phone for division phone — compiles cleanly and would reach the PDF looking plausible. Every
 * field below is given a value unique to itself so a transposition cannot pass.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MillInformationService")
class MillInformationServiceTest {

  @Mock private MillInformationRepository repository;

  @InjectMocks private MillInformationService service;

  @Test
  @DisplayName("every column lands in its own field, and the date prefixes are stripped")
  void projectionIsFieldAccurate() {
    when(repository.findZoneDescriptions())
        .thenReturn(List.of(new ZoneDescriptionEntity("Z1", "Kootenay")));
    when(repository.findSectionRows(2021)).thenReturn(List.of(row("ACT")));

    MillInformationSection section = service.findSections(2021).getFirst();

    assertThat(section.millId()).isEqualTo(730);
    assertThat(section.millNumber()).isEqualTo("number");
    assertThat(section.millName()).isEqualTo("name");
    assertThat(section.region()).isEqualTo("Kootenay");
    assertThat(section.clientName()).isEqualTo("clientName");
    assertThat(section.address1()).isEqualTo("address1");
    assertThat(section.address2()).isEqualTo("address2");
    assertThat(section.city()).isEqualTo("city");
    assertThat(section.postalCode()).isEqualTo("postalCode");
    assertThat(section.headOfficeContactIndicator()).isEqualTo("Y");
    assertThat(section.headOfficeContactName()).isEqualTo("hoName");
    assertThat(section.headOfficePhone()).isEqualTo("hoPhone");
    assertThat(section.divisionContactName()).isEqualTo("dvName");
    assertThat(section.divisionPhone()).isEqualTo("dvPhone");
    assertThat(section.openDate()).isEqualTo("openDate");
    assertThat(section.draftDate()).isEqualTo("draftDate");
    assertThat(section.submitDate()).isEqualTo("submitDate");
    assertThat(section.verifyDate()).isEqualTo("verifyDate");
  }

  @Test
  @DisplayName("ACT for the reporting year means active; anything else does not")
  void activeFlagDerivesFromTheYearStatusCode() {
    when(repository.findSectionRows(2021)).thenReturn(List.of(row("ACT"), row("CLS"), row(null)));

    List<MillInformationSection> sections = service.findSections(2021);

    assertThat(sections)
        .extracting(MillInformationSection::active)
        .containsExactly(true, false, false);
  }

  @Test
  @DisplayName("a milestone holding only its 3-character prefix becomes null, not the raw prefix")
  void prefixOnlyMilestonesBecomeNull() {
    MillInformationRowEntity prefixOnly =
        new MillInformationRowEntity(
            730,
            "number",
            "name",
            "ACT",
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
            "O: 2021-01-05",
            "D: ",
            "S: ",
            null);
    when(repository.findSectionRows(2021)).thenReturn(List.of(prefixOnly));

    MillInformationSection section = service.findSections(2021).getFirst();

    assertThat(section.openDate()).isEqualTo("2021-01-05");
    assertThat(section.draftDate()).isNull();
    assertThat(section.submitDate()).isNull();
    assertThat(section.verifyDate()).isNull();
  }

  @Test
  @DisplayName("an unreadable zone table costs the Region only, not the whole report")
  void missingZoneTableDegradesToNoRegion() {
    // ISP_SELL_PRICE_ZONE_CODE is reached through a PUBLIC synonym that is dangling on the FTA dev
    // database, so Oracle answers ORA-00942. The report must still render.
    when(repository.findZoneDescriptions())
        .thenThrow(new BadSqlGrammarException("select", "select 1", new SQLException("ORA-00942")));
    when(repository.findSectionRows(2021)).thenReturn(List.of(row("ACT")));

    MillInformationSection section = service.findSections(2021).getFirst();

    assertThat(section.region()).isNull();
    assertThat(section.millName()).isEqualTo("name");
  }

  @Test
  @DisplayName("no rows for the year yields no sections rather than an error")
  void emptyYearYieldsNoSections() {
    when(repository.findSectionRows(1999)).thenReturn(List.of());

    assertThat(service.findSections(1999)).isEmpty();
  }

  /** A row whose every text column carries a value unique to that column. */
  private static MillInformationRowEntity row(String statusCode) {
    return new MillInformationRowEntity(
        730,
        "number",
        "name",
        statusCode,
        "Z1",
        "clientName",
        "address1",
        "address2",
        "city",
        "postalCode",
        "Y",
        "hoName",
        "hoPhone",
        "dvName",
        "dvPhone",
        "XX:openDate",
        "XX:draftDate",
        "XX:submitDate",
        "XX:verifyDate");
  }
}
