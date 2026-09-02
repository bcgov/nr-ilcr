package ca.bc.gov.nrs.ilcr.millreportstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millinformation.MillInformationRepository;
import ca.bc.gov.nrs.ilcr.millinformation.ZoneDescriptionEntity;
import ca.bc.gov.nrs.ilcr.millreportstatus.dto.MillReportStatusRow;
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
 * Unit tests for the entity → DTO projection behind the Mill Status Report table.
 *
 * <p>These exist because {@code toRow} passes SEVEN consecutive nullable milestone {@code String}s
 * positionally, four of them from one schedule track and three from the other. Any adjacent swap —
 * Draft for Submitted, or a Schedules 1–10 value into the Schedule 11 column group — compiles
 * cleanly and would reach the page looking entirely plausible. Every field below therefore carries
 * a value unique to itself, so a transposition cannot pass.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MillReportStatusService")
class MillReportStatusServiceTest {

  @Mock private MillReportStatusRepository repository;
  @Mock private MillInformationRepository millInformationRepository;

  @InjectMocks private MillReportStatusService service;

  @Test
  @DisplayName("every column lands in its own field, on its own track")
  void projectionIsFieldAccurate() {
    when(millInformationRepository.findZoneDescriptions())
        .thenReturn(List.of(new ZoneDescriptionEntity("Z1", "Kootenay")));
    when(repository.findStatusRows(2021)).thenReturn(List.of(row("ACT")));

    MillReportStatusRow projected = service.findRows(2021).getFirst();

    assertThat(projected.millId()).isEqualTo(730);
    assertThat(projected.millNumber()).isEqualTo("millNumber");
    assertThat(projected.millName()).isEqualTo("millName");
    assertThat(projected.region()).isEqualTo("Kootenay");
    assertThat(projected.active()).isTrue();
    // The Schedules 1–10 group.
    assertThat(projected.openDate()).isEqualTo("O: openDate");
    assertThat(projected.draftDate()).isEqualTo("D: draftDate");
    assertThat(projected.submitDate()).isEqualTo("S: submitDate");
    assertThat(projected.verifyDate()).isEqualTo("V: verifyDate");
    // The Schedule 11 group — separate values, so a track swap fails here.
    assertThat(projected.silviDraftDate()).isEqualTo("D: silviDraftDate");
    assertThat(projected.silviSubmitDate()).isEqualTo("S: silviSubmitDate");
    assertThat(projected.silviVerifyDate()).isEqualTo("V: silviVerifyDate");
  }

  @Test
  @DisplayName("the raw status prefix survives verbatim — this surface must NOT strip it")
  void prefixesAreNotStripped() {
    // Ratified 2026-09-01: the page's O/D/S/V legend is what decodes the letter inside the value,
    // so LegacyDateText.stripPrefix must never be applied here. Story 19.1's PDF strips because it
    // labels each milestone in words instead; this table does not.
    when(repository.findStatusRows(2021))
        .thenReturn(
            List.of(
                new MillReportStatusRowEntity(
                    730,
                    "7300",
                    "MILL",
                    "ACT",
                    null,
                    "O: 2021-01-05",
                    "D: 2021-03-10",
                    "S: 2021-05-20",
                    "V: 2021-07-01",
                    "D: 2021-04-12",
                    "S: 2021-06-15",
                    "V: 2021-08-20")));

    MillReportStatusRow projected = service.findRows(2021).getFirst();

    assertThat(projected.openDate()).isEqualTo("O: 2021-01-05");
    assertThat(projected.draftDate()).isEqualTo("D: 2021-03-10");
    assertThat(projected.submitDate()).isEqualTo("S: 2021-05-20");
    assertThat(projected.verifyDate()).isEqualTo("V: 2021-07-01");
    assertThat(projected.silviDraftDate()).isEqualTo("D: 2021-04-12");
    assertThat(projected.silviSubmitDate()).isEqualTo("S: 2021-06-15");
    assertThat(projected.silviVerifyDate()).isEqualTo("V: 2021-08-20");
  }

  @Test
  @DisplayName("a prefix-only milestone keeps its prefix; a NULL one stays null, never \"null\"")
  void prefixOnlyStaysPrefixAndNullStaysNull() {
    when(repository.findStatusRows(2021))
        .thenReturn(
            List.of(
                new MillReportStatusRowEntity(
                    731,
                    "7310",
                    "MILL",
                    "ACT",
                    null,
                    "O: 2021-01-05",
                    "D: ",
                    null,
                    null,
                    "D: ",
                    null,
                    null)));

    MillReportStatusRow projected = service.findRows(2021).getFirst();

    assertThat(projected.openDate()).isEqualTo("O: 2021-01-05");
    // Prefix-only is served AS-IS. On this surface "D: " is meaningful — the track reached Draft
    // with no recorded date — and the legend is what explains it.
    assertThat(projected.draftDate()).isEqualTo("D: ");
    assertThat(projected.silviDraftDate()).isEqualTo("D: ");
    // Null stays null so the page renders an empty line. Coercing to a String here is what would
    // put the text "null" on screen.
    assertThat(projected.submitDate()).isNull();
    assertThat(projected.verifyDate()).isNull();
    assertThat(projected.silviSubmitDate()).isNull();
    assertThat(projected.silviVerifyDate()).isNull();
  }

  @Test
  @DisplayName("Active is ACT for the REPORTING YEAR from the view; anything else is not active")
  void activeFlagDerivesFromTheYearStatusCode() {
    when(repository.findStatusRows(2021))
        .thenReturn(List.of(row("ACT"), row("CLS"), row(null), row("act")));

    assertThat(service.findRows(2021))
        .extracting(MillReportStatusRow::active)
        // Case-sensitive on purpose: the column is a code, and legacy compares it with
        // "ACT".equals(...) (MillReportStatusDAO.java:106).
        .containsExactly(true, false, false, false);
  }

  @Test
  @DisplayName("an unreadable zone table costs the Region only, not the whole table")
  void missingZoneTableDegradesToNoRegion() {
    // APPRAISAL_SELL_PRICE_ZONE_CODE is a shared ministry code table reached through a PUBLIC
    // synonym; a synonym whose target is missing makes Oracle answer ORA-00942 for the whole
    // statement. This is exactly what took down Story 19.1's report; the table must still render,
    // with Region degraded. (Until 2026-09-02 the query named the MILL column's namesake instead,
    // THE.ISP_SELL_PRICE_ZONE_CODE, which is absent on FTA — so this path ran on every request.)
    when(millInformationRepository.findZoneDescriptions())
        .thenThrow(new BadSqlGrammarException("select", "select 1", new SQLException("ORA-00942")));
    // A row with a zone code AND a row with NONE. The second one is the whole point: the empty map
    // the catch returns is looked up with a null key, and Map.of().get(null) THROWS on Java 21.
    // With
    // only the "Z1" row this test passed while the endpoint 500d on the first real mill.
    when(repository.findStatusRows(2021))
        .thenReturn(List.of(row("ACT"), rowWithNoZoneCode(), row("CLS")));

    List<MillReportStatusRow> rows = service.findRows(2021);

    assertThat(rows).hasSize(3).extracting(MillReportStatusRow::region).containsOnlyNulls();
    // The rest of every row is intact — the degrade is confined to one column.
    assertThat(rows.getFirst().millName()).isEqualTo("millName");
    assertThat(rows.getFirst().openDate()).isEqualTo("O: openDate");
  }

  @Test
  @DisplayName("a mill with NO zone code resolves to a null Region even when the lookup succeeded")
  void nullZoneCodeIsSafeAgainstAPopulatedLookup() {
    // The same null-key guard on the happy path. HashMap tolerates a null key, so this would pass
    // even without the guard — it is here so a future switch to an immutable map cannot silently
    // reintroduce the 500 on the path that DOES have descriptions.
    when(millInformationRepository.findZoneDescriptions())
        .thenReturn(List.of(new ZoneDescriptionEntity("Z1", "Kootenay")));
    when(repository.findStatusRows(2021)).thenReturn(List.of(rowWithNoZoneCode(), row("ACT")));

    assertThat(service.findRows(2021))
        .extracting(MillReportStatusRow::region)
        .containsExactly(null, "Kootenay");
  }

  @Test
  @DisplayName("a zone code with no description row yields a null Region, not a crash")
  void unknownZoneCodeYieldsNullRegion() {
    when(millInformationRepository.findZoneDescriptions())
        .thenReturn(List.of(new ZoneDescriptionEntity("Z9", "Somewhere Else")));
    when(repository.findStatusRows(2021)).thenReturn(List.of(row("ACT")));

    assertThat(service.findRows(2021).getFirst().region()).isNull();
  }

  @Test
  @DisplayName("no rows for the year yields no rows rather than an error")
  void emptyYearYieldsNoRows() {
    when(repository.findStatusRows(1999)).thenReturn(List.of());

    assertThat(service.findRows(1999)).isEmpty();
  }

  @Test
  @DisplayName("the repository's mill-id order is passed through, not re-sorted")
  void repositoryOrderIsPreserved() {
    // The query orders by ILCR_MILL_ID (legacy's Order.asc). The service must not quietly re-sort
    // by mill number just because that is the first rendered column — the page sorts client-side.
    when(repository.findStatusRows(2021))
        .thenReturn(List.of(millWith(514, "9999"), millWith(730, "7300"), millWith(731, "0001")));

    assertThat(service.findRows(2021))
        .extracting(MillReportStatusRow::millId)
        .containsExactly(514L, 730L, 731L);
  }

  /** A row whose every text column carries a value unique to that column. */
  private static MillReportStatusRowEntity row(String statusCode) {
    return new MillReportStatusRowEntity(
        730,
        "millNumber",
        "millName",
        statusCode,
        "Z1",
        "O: openDate",
        "D: draftDate",
        "S: submitDate",
        "V: verifyDate",
        "D: silviDraftDate",
        "S: silviSubmitDate",
        "V: silviVerifyDate");
  }

  /** The common delivery shape: a mill with no selling-price zone code at all. */
  private static MillReportStatusRowEntity rowWithNoZoneCode() {
    return new MillReportStatusRowEntity(
        731, "7310", "millName", "ACT", null, "O: openDate", null, null, null, null, null, null);
  }

  private static MillReportStatusRowEntity millWith(long millId, String millNumber) {
    return new MillReportStatusRowEntity(
        millId, millNumber, "MILL", "ACT", null, null, null, null, null, null, null, null);
  }
}
