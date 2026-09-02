package ca.bc.gov.nrs.ilcr.millinformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
 *
 * <p>Both entry points are covered: {@code findSections(year)} for the all-mills Mill Information
 * report (Story 19.1) and {@code findSection(millId, year)} for the per-mill drill-down (Story
 * 19.3). They share one query and one projection deliberately, so the tests pin BOTH the bind each
 * passes ({@code null} versus a mill id) and the fact that the two agree on the same row — the
 * drift that a second copy of either would introduce is invisible otherwise.
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
    when(repository.findSectionRows(2021, null)).thenReturn(List.of(row("ACT")));

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
    when(repository.findSectionRows(2021, null))
        .thenReturn(List.of(row("ACT"), row("CLS"), row(null)));

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
    when(repository.findSectionRows(2021, null)).thenReturn(List.of(prefixOnly));

    MillInformationSection section = service.findSections(2021).getFirst();

    assertThat(section.openDate()).isEqualTo("2021-01-05");
    assertThat(section.draftDate()).isNull();
    assertThat(section.submitDate()).isNull();
    assertThat(section.verifyDate()).isNull();
  }

  @Test
  @DisplayName("an unreadable zone table costs the Region only, not the whole report")
  void missingZoneTableDegradesToNoRegion() {
    // APPRAISAL_SELL_PRICE_ZONE_CODE is a shared ministry code table reached through a PUBLIC
    // synonym; if that synonym's target is missing Oracle answers ORA-00942 for the whole
    // statement. The report must still render.
    when(repository.findZoneDescriptions())
        .thenThrow(new BadSqlGrammarException("select", "select 1", new SQLException("ORA-00942")));
    when(repository.findSectionRows(2021, null)).thenReturn(List.of(row("ACT")));

    MillInformationSection section = service.findSections(2021).getFirst();

    assertThat(section.region()).isNull();
    assertThat(section.millName()).isEqualTo("name");
  }

  @Test
  @DisplayName("the degrade survives a mill with NO zone code — the 500 this used to be")
  void missingZoneTableDegradesForANullZoneCodeToo() {
    // The regression test for the recorded 19.1 defect. The catch used to return Map.of(), and
    // Map.of().get(null) THROWS NullPointerException on Java 21 (ImmutableCollections.MapN.get
    // calls Objects.requireNonNull) where HashMap.get(null) answers null. Most mills carry no zone
    // code, so the mitigation for ORA-00942 turned a one-column outage into a 500 on exactly the
    // database it existed for. The test above passed anyway because its row carries "Z1"; this one
    // is the shape that detonated it, and it fails if either half of the fix is reverted.
    when(repository.findZoneDescriptions())
        .thenThrow(new BadSqlGrammarException("select", "select 1", new SQLException("ORA-00942")));
    when(repository.findSectionRows(2021, null)).thenReturn(List.of(rowWithNoZoneCode()));

    MillInformationSection section = service.findSections(2021).getFirst();

    assertThat(section.region()).isNull();
    assertThat(section.millName()).isEqualTo("name");
  }

  @Test
  @DisplayName("a readable zone table still leaves a code-less mill with no Region")
  void aNullZoneCodeYieldsNoRegionEvenWhenTheTableReads() {
    // The same null-code row on the happy path: HashMap.get(null) is harmless, but the guard must
    // not be mistaken for degrade-only handling. Region is absent because the mill has no code.
    when(repository.findZoneDescriptions())
        .thenReturn(List.of(new ZoneDescriptionEntity("Z1", "Kootenay Selling Price Zone")));
    when(repository.findSectionRows(2021, null)).thenReturn(List.of(rowWithNoZoneCode()));

    assertThat(service.findSections(2021).getFirst().region()).isNull();
  }

  @Test
  @DisplayName("no rows for the year yields no sections rather than an error")
  void emptyYearYieldsNoSections() {
    when(repository.findSectionRows(1999, null)).thenReturn(List.of());

    assertThat(service.findSections(1999)).isEmpty();
  }

  @Test
  @DisplayName("findSections asks for EVERY mill — a null mill predicate, never a mill id")
  void findSectionsPassesANullMillPredicate() {
    // The all-mills report and the drill-down share ONE query, separated only by this bind. If the
    // report ever started sending a mill id here it would silently narrow to one section, so the
    // null is asserted rather than assumed.
    when(repository.findZoneDescriptions()).thenReturn(List.of());
    when(repository.findSectionRows(2021, null)).thenReturn(List.of(row("ACT")));

    assertThat(service.findSections(2021)).hasSize(1);

    verify(repository).findSectionRows(2021, null);
  }

  @Test
  @DisplayName("findSection binds the MILL ID it was given, so it cannot read the whole year")
  void findSectionPassesTheMillPredicate() {
    when(repository.findZoneDescriptions()).thenReturn(List.of());
    when(repository.findSectionRows(2021, 730L)).thenReturn(List.of(row("ACT")));

    assertThat(service.findSection(730, 2021)).isPresent();

    verify(repository).findSectionRows(2021, 730L);
  }

  @Test
  @DisplayName("findSection maps every column into its own field, prefixes stripped")
  void drillDownProjectionIsFieldAccurate() {
    // The same positional-transposition risk as the all-mills projection above: fifteen consecutive
    // Strings. Every value is unique to its own column, so an adjacent swap cannot pass.
    when(repository.findZoneDescriptions())
        .thenReturn(List.of(new ZoneDescriptionEntity("Z1", "Kootenay")));
    when(repository.findSectionRows(2021, 730L)).thenReturn(List.of(row("ACT")));

    MillInformationSection section = service.findSection(730, 2021).orElseThrow();

    assertThat(section.millId()).isEqualTo(730);
    assertThat(section.millNumber()).isEqualTo("number");
    assertThat(section.millName()).isEqualTo("name");
    assertThat(section.active()).isTrue();
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
  @DisplayName("the drill-down and the all-mills report project the SAME row identically")
  void bothProjectionsAgreeOnTheSameRow() {
    // The story's parity acceptance criterion, at the level where it can actually be pinned: given
    // one row, the two entry points must produce an equal section. They share `toSection` today, so
    // this passes by construction — which is the point. It fails the moment someone gives the
    // drill-down its own mapping, which is the drift the single-projection rule exists to prevent.
    when(repository.findZoneDescriptions())
        .thenReturn(List.of(new ZoneDescriptionEntity("Z1", "Kootenay")));
    when(repository.findSectionRows(2021, null)).thenReturn(List.of(row("ACT")));
    when(repository.findSectionRows(2021, 730L)).thenReturn(List.of(row("ACT")));

    assertThat(service.findSection(730, 2021).orElseThrow())
        .isEqualTo(service.findSections(2021).getFirst());
  }

  @Test
  @DisplayName("a drilled mill still at Opened/Draft yields null milestones, not a crash (S08)")
  void drillDownNullAndPrefixOnlyMilestonesBecomeNull() {
    // The recorded fix for legacy's latent NPE: MillReportStatusReport.java:96-99 called
    // .substring(2) on all four milestone strings unguarded, so drilling into a mill whose
    // milestones are NULL in the view — the Opened/Draft-only mill, fixture 732 — threw. Both
    // shapes are covered here: outright NULL, and the prefix-only "D: " that survives to the strip.
    when(repository.findZoneDescriptions()).thenReturn(List.of());
    when(repository.findSectionRows(2021, 732L))
        .thenReturn(
            List.of(
                new MillInformationRowEntity(
                    732,
                    "7320",
                    "MILL INFO NO CLIENT",
                    "CLS",
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
                    "D: ",
                    null,
                    "V: ")));

    MillInformationSection section = service.findSection(732, 2021).orElseThrow();

    assertThat(section.openDate()).isNull();
    assertThat(section.draftDate()).isNull();
    assertThat(section.submitDate()).isNull();
    assertThat(section.verifyDate()).isNull();
    assertThat(section.millName()).isEqualTo("MILL INFO NO CLIENT");
  }

  @Test
  @DisplayName("a mill with no row for the year yields an empty Optional, not an empty section")
  void findSectionForAnAbsentMillIsEmpty() {
    // The caller turns this into a 404. Answering a blank section instead would produce a PDF
    // describing a mill that has no report status, which is worse than a rejection.
    when(repository.findZoneDescriptions()).thenReturn(List.of());
    when(repository.findSectionRows(2021, 999L)).thenReturn(List.of());

    assertThat(service.findSection(999, 2021)).isEmpty();
  }

  @Test
  @DisplayName("a multi-row view answer still yields exactly ONE section — the first row")
  void findSectionTakesTheFirstRow() {
    // ILCR_MILL_REPORT_STATUS_RPT_VW is a view with no uniqueness guarantee
    // (MillContextRepository.findStatusDates documents the same fact). The drill-down's contract is
    // one mill, one section — a duplicate must not become a two-section PDF.
    when(repository.findZoneDescriptions()).thenReturn(List.of());
    when(repository.findSectionRows(2021, 730L))
        .thenReturn(List.of(row("ACT"), row("CLS"), row(null)));

    MillInformationSection section = service.findSection(730, 2021).orElseThrow();

    // The FIRST row's status code, so the choice is pinned rather than merely "one of them".
    assertThat(section.active()).isTrue();
  }

  @Test
  @DisplayName("the chosen row is the query's ORDER BY, not the arrival order of a resorted list")
  void findSectionChoosesTheRowTheQueryOrdered() {
    // Review round 1, patch P7. Taking findFirst over a multi-row view is only reproducible if the
    // QUERY totally orders the rows, and it previously ordered by mill id alone — constant across
    // exactly the rows that needed separating, so which section an administrator got was whatever
    // Oracle's plan emitted that execution. Two consecutive drill-downs of one mill could show
    // different addresses.
    //
    // A unit test cannot exercise Oracle's ordering, so it pins the half that IS in Java: the
    // service must consume the repository's order as given and never re-sort or re-pick. The two
    // rows below differ in every field the section projects, so a change of choice is visible.
    MillInformationRowEntity ordered =
        new MillInformationRowEntity(
            730,
            "7300",
            "ORDERED FIRST",
            "ACT",
            "Z1",
            "FIRST OWNER",
            "1 FIRST ST",
            null,
            "CRANBROOK",
            "V1C1A1",
            "Y",
            "FIRST HO",
            "2505551212",
            "FIRST DV",
            "2505551313",
            "O: 2021-01-05",
            "D: 2021-03-10",
            "S: 2021-05-20",
            "V: 2021-07-01");
    MillInformationRowEntity second =
        new MillInformationRowEntity(
            730,
            "7300",
            "ORDERED SECOND",
            "CLS",
            "Z1",
            "SECOND OWNER",
            "2 SECOND AVE",
            null,
            "REVELSTOKE",
            "V0E2S0",
            "N",
            "SECOND HO",
            "2505552222",
            "SECOND DV",
            "2505553333",
            "O: 2021-02-05",
            "D: 2021-04-10",
            "S: 2021-06-20",
            "V: 2021-08-01");
    when(repository.findZoneDescriptions())
        .thenReturn(List.of(new ZoneDescriptionEntity("Z1", "Kootenay")));
    when(repository.findSectionRows(2021, 730L)).thenReturn(List.of(ordered, second));

    MillInformationSection section = service.findSection(730, 2021).orElseThrow();

    // Every projected field comes from the FIRST row — not a mixture, and not the second.
    assertThat(section.millName()).isEqualTo("ORDERED FIRST");
    assertThat(section.active()).isTrue();
    assertThat(section.clientName()).isEqualTo("FIRST OWNER");
    assertThat(section.address1()).isEqualTo("1 FIRST ST");
    assertThat(section.city()).isEqualTo("CRANBROOK");
    assertThat(section.postalCode()).isEqualTo("V1C1A1");
    assertThat(section.headOfficeContactName()).isEqualTo("FIRST HO");
    assertThat(section.divisionContactName()).isEqualTo("FIRST DV");
    assertThat(section.openDate()).isEqualTo("2021-01-05");
    assertThat(section.verifyDate()).isEqualTo("2021-07-01");
  }

  /** The common delivery shape: a mill carrying no selling-price zone code at all. */
  private static MillInformationRowEntity rowWithNoZoneCode() {
    return row("ACT", null);
  }

  /** A row whose every text column carries a value unique to that column. */
  private static MillInformationRowEntity row(String statusCode) {
    return row(statusCode, "Z1");
  }

  private static MillInformationRowEntity row(String statusCode, String regionCode) {
    return new MillInformationRowEntity(
        730,
        "number",
        "name",
        statusCode,
        regionCode,
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
