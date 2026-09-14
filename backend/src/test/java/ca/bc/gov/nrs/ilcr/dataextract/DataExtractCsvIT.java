package ca.bc.gov.nrs.ilcr.dataextract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.dataextract.csv.DataExtractGenerator;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance tests for the generated CSV itself — the response half of the Data Extract endpoint.
 *
 * <p>The selection gate's refusals belong to {@code DataExtractValidationIT} and authorization to
 * {@code DataExtractAuthorizationIT}; what is asserted here is the FILE: its headers, its title
 * block, its section framing, its markers, the combined layout and its three negative controls, and
 * that a failure mid-build leaves neither bytes nor a spool file behind.
 *
 * <p>Assertions read the body as PARSED ROWS rather than as one long string, through {@link #rows}
 * and {@link #cells} below. A failing assertion then names the row and the cell instead of dumping
 * a several-hundred-line diff, and a row-order assertion cannot accidentally pass because a
 * substring happened to appear somewhere else in the file.
 *
 * <p>Fixtures are {@code R__60_data_extract_fixtures.sql}: mills 760 (Verified on both tracks, with
 * a Schedule 3), 761 (Verified on Schedules 1–10 only, with NO Schedule 3) and 762 (Submitted, with
 * no schedule data at all), every row in report year 2020. Year 2021 is deliberately empty of
 * fixtures — see that file's header for why the second year of a range carries nothing.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Data Extract CSV — the generated file")
class DataExtractCsvIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/reports/data-extract";
  private static final String CSV_UTF8 = "application/csv;charset=UTF-8";

  private static final String NO_DATA_FOUND = "*** NO DATA FOUND ***";
  private static final String NO_SCHEDULE_3 = "*** NO SCHEDULE 3 ***";
  private static final String NO_STATUS = "** NO STATUS **";
  private static final String END_MARKER = "**** End ****";

  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  private static final RequestPostProcessor ADMIN =
      jwt()
          .jwt(j -> j.claim("cognito:groups", List.of("ILCR_ADMIN")))
          .authorities(j -> CONVERTER.convert(j).getAuthorities());

  @MockitoBean private JwtDecoder jwtDecoder;

  /**
   * Spied rather than replaced: every test but the failure one needs the REAL Schedule 1 read
   * against the fixtures, and only that one stubs it to throw.
   */
  @MockitoSpyBean private Schedule1Service schedule1Service;

  /** The spool directory's files, so a leftover partial file is detectable. */
  private static Set<Path> spoolFiles(Path directory) throws IOException {
    try (var entries = Files.list(directory)) {
      return entries
          .filter(p -> p.getFileName().toString().startsWith("dataExtract"))
          .collect(Collectors.toSet());
    }
  }

  private static String body(int startYear, int endYear, String millIds, String schedules) {
    return """
        {"startYear":"%d","endYear":"%d","millIds":%s,"schedules":%s}
        """
        .formatted(startYear, endYear, millIds, schedules);
  }

  private MvcResult extract(String payload) throws Exception {
    return mockMvc
        .perform(
            post(ENDPOINT)
                .with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .accept(MediaType.ALL))
        .andExpect(status().isOk())
        .andExpect(content().contentType(CSV_UTF8))
        .andReturn();
  }

  /** The body as UTF-8 text. Read from the raw bytes, so a charset regression is visible. */
  private static String text(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  /**
   * The body split into physical lines.
   *
   * <p>Deliberately naive: a quoted cell CAN contain a newline, so a comment cell would split a
   * logical row across two entries here. No fixture seeds such a comment, and keeping the split
   * naive means the line count is the file's real line count — which is what a comparison against a
   * legacy sample looks at.
   */
  private static List<String> rows(MvcResult result) {
    String body = text(result);
    // The file ends with a line terminator, so the trailing empty element is not a row.
    return Arrays.asList(body.substring(0, body.length() - 1).split("\n", -1));
  }

  /**
   * One row's cells, with the quoting stripped.
   *
   * <p>Splits on the delimiter between quoted cells rather than on every comma, because a formatted
   * figure carries thousands separators inside its quotes. A null cell arrives as an empty field
   * with no quotes at all, which this renders as an empty string.
   */
  private static List<String> cells(String row) {
    return Arrays.stream(row.split("(?<=\"),(?=\")|(?<=^),|,(?=$)|(?<=\"),(?=$)|(?<=^),(?=\")"))
        .map(
            cell ->
                cell.length() >= 2 && cell.startsWith("\"") && cell.endsWith("\"")
                    ? cell.substring(1, cell.length() - 1).replace("\"\"", "\"")
                    : cell)
        .toList();
  }

  /** The index of the first row whose single cell is {@code title}, or -1. */
  private static int sectionAt(List<String> rows, String title) {
    return rows.indexOf("\"" + title + "\"");
  }

  @Nested
  @DisplayName("the response and the title block")
  class ResponseAndTitle {

    @Test
    @DisplayName("a valid selection answers a CSV attachment whose Content-Length is the body size")
    void answersAttachmentWithRealContentLength() throws Exception {
      MvcResult result = extract(body(2020, 2020, "[760]", "[\"Schedule 1\"]"));

      // The whole-file-or-no-file contract made observable: the length header can only be right if
      // the file was complete before the response was committed.
      int declared = Integer.parseInt(result.getResponse().getHeader("Content-Length"));
      assertThat(declared).isEqualTo(result.getResponse().getContentAsByteArray().length);
      assertThat(result.getResponse().getHeader("Content-Disposition"))
          .matches("attachment; filename=\"dataExtract\\d{8}\\.csv\"");
    }

    @Test
    @DisplayName("the title block is legacy's seven lines, then two blank rows")
    void titleBlockIsVerbatim() throws Exception {
      List<String> rows = rows(extract(body(2020, 2020, "[760,761]", "[\"Schedule 1\"]")));

      assertThat(rows.get(0))
          .isEqualTo(
              "\"Ministry of Forests, Lands, Natural Resource Operations & Rural Development,"
                  + " ILCR\"");
      // The timestamp is legacy's pattern, which pairs a 24-hour clock WITH an AM/PM marker.
      // Matched
      // by shape because the value is the server clock; the redundant marker is the part that must
      // not be "corrected" to a 12-hour clock.
      assertThat(cells(rows.get(1)).get(0))
          .matches("Data Extract on [A-Z][a-z]+, \\d{2} \\d{4} @ \\d{2}:\\d{2} [AP]M");
      assertThat(rows.get(2)).isEqualTo("\"Start Year: 2020\"");
      assertThat(rows.get(3)).isEqualTo("\"End Year: 2020\"");
      // Mill NUMBERs, in the order the request listed their ids — not the numbers' own order.
      assertThat(rows.get(4)).isEqualTo("\"Included Mills: 7600, 7610\"");
      assertThat(rows.get(5)).isEqualTo("\"Data Verified: Yes\"");
      // The detail-EXPANDED names, not the picker labels: legacy's title line outside the combined
      // layout listed what each label expands to, and Schedule 1 expands to itself plus its Other
      // Costs section (ExtractDataMB.java:361-363).
      assertThat(rows.get(6)).isEqualTo("\"Schedules: Schedule 1, Schedule 1 Other\"");
      // Legacy's blank line is a one-cell row holding a single space, not an empty line.
      assertThat(rows.get(7)).isEqualTo("\" \"");
      assertThat(rows.get(8)).isEqualTo("\" \"");
    }

    @Test
    @DisplayName("the file ends with two blank rows and the end marker")
    void endsWithTheEndMarker() throws Exception {
      List<String> rows = rows(extract(body(2020, 2020, "[760]", "[\"Schedule 1\"]")));

      assertThat(rows.get(rows.size() - 1)).isEqualTo("\"" + END_MARKER + "\"");
      assertThat(rows.get(rows.size() - 2)).isEqualTo("\" \"");
      assertThat(rows.get(rows.size() - 3)).isEqualTo("\" \"");
    }

    @Test
    @DisplayName("the Schedules line expands Schedule 7 into its two sub-schedules")
    void scheduleSevenExpandsInTheTitle() throws Exception {
      List<String> rows = rows(extract(body(2020, 2020, "[760]", "[\"Schedule 7\"]")));

      // One picker label, two sections — and the title line names the expansion, as legacy's
      // detail-expanded list did outside the combined layout.
      assertThat(cells(rows.get(6)).get(0)).startsWith("Schedules: ");
      assertThat(sectionAt(rows, "**** Schedule 7A - Bridge****")).isPositive();
      assertThat(sectionAt(rows, "**** Schedule 7B - Culvert ****")).isPositive();
    }
  }

  @Nested
  @DisplayName("Data Verified")
  class DataVerified {

    /** The Data Verified line's verdict alone — the cell is "Data Verified: Yes|No". */
    private String verdict(int startYear, int endYear, String mills, String schedules)
        throws Exception {
      String cell = cells(rows(extract(body(startYear, endYear, mills, schedules))).get(5)).get(0);
      assertThat(cell).startsWith("Data Verified: ");
      return cell.substring("Data Verified: ".length());
    }

    @Test
    @DisplayName("Yes when every selected mill/year is V on the one applicable track")
    void yesWhenAllVerifiedOnTheApplicableTrack() throws Exception {
      // 760 and 761 are both V on the Schedules 1-10 track; 761's DRAFT silviculture code is not
      // applicable to this selection and must not drag the verdict down.
      assertThat(verdict(2020, 2020, "[760,761]", "[\"Schedule 1\"]")).isEqualTo("Yes");
    }

    @Test
    @DisplayName("No when a mill fails the OTHER applicable track — the AND, not legacy's OR")
    void noWhenOnlyOneTrackIsVerified() throws Exception {
      // Legacy OR'd the two tracks when both kinds were selected, so this same selection printed
      // Yes on the strength of 761's main track alone while its Schedule 11 track sat at Draft.
      assertThat(verdict(2020, 2020, "[760,761]", "[\"Schedule 1\",\"Schedule 11\"]"))
          .isEqualTo("No");
      // The positive control on the same pair of tracks: 760 is V on both.
      assertThat(verdict(2020, 2020, "[760]", "[\"Schedule 1\",\"Schedule 11\"]")).isEqualTo("Yes");
    }

    @Test
    @DisplayName("No when a selected mill/year has NO status row — legacy's vacuous Yes is gone")
    void noWhenAStatusRowIsMissing() throws Exception {
      // 2021 carries no status row for these mills, so the range 2020-2021 has unverified pairs in
      // it. Legacy built its verdict only from rows that existed and so printed Yes here.
      assertThat(verdict(2020, 2021, "[760]", "[\"Schedule 1\"]")).isEqualTo("No");
    }

    @Test
    @DisplayName("No for a non-V code, and Schedule 7 alone reads the Schedules 1-10 track")
    void scheduleSevenUsesTheMainTrack() throws Exception {
      // 762 is Submitted on the main track and Verified on silviculture. A Schedule-7-only
      // selection must therefore read No — legacy read No too, but for the wrong reason: its check
      // tested for a label its expanded list never held, so it matched no track at all and would
      // have said No however verified the data was. The control below is what tells the two apart.
      assertThat(verdict(2020, 2020, "[762]", "[\"Schedule 7\"]")).isEqualTo("No");
      // Same mill, same single label, the track it IS verified on: Yes. Under legacy's blind spot
      // the first assertion would pass and this one would fail.
      assertThat(verdict(2020, 2020, "[762]", "[\"Schedule 11\"]")).isEqualTo("Yes");
    }

    @Test
    @DisplayName("the flag never blocks extraction — an unverified selection still gets its file")
    void neverBlocksExtraction() throws Exception {
      List<String> rows = rows(extract(body(2020, 2020, "[762]", "[\"Schedule 1\"]")));

      assertThat(cells(rows.get(5)).get(0)).isEqualTo("Data Verified: No");
      assertThat(rows.get(rows.size() - 1)).isEqualTo("\"" + END_MARKER + "\"");
    }
  }

  @Nested
  @DisplayName("sections, markers and order")
  class SectionsAndMarkers {

    @Test
    @DisplayName("a selected schedule with no rows anywhere still opens and carries the marker")
    void noRowsScheduleEmitsTheWholeScheduleMarker() throws Exception {
      // Mill 762 has no schedule data at all. The section is still framed — blank row, title,
      // header — and its body is the five-cell whole-schedule marker. Legacy dropped the section
      // silently in one of its two no-data paths; the rebuild always emits it.
      List<String> rows = rows(extract(body(2020, 2020, "[762]", "[\"Schedule 6\"]")));
      int title = sectionAt(rows, "**** Schedule 6 ****");

      assertThat(title).isPositive();
      assertThat(rows.get(title - 1)).isEqualTo("\" \"");
      assertThat(cells(rows.get(title + 1)).get(0)).isEqualTo("MILL_NUMBER");
      List<String> marker = cells(rows.get(title + 2));
      assertThat(marker).containsExactly("7620", "2020 - 2020", "-", "-", NO_DATA_FOUND);
    }

    @Test
    @DisplayName("one schedule's marker does not suppress another schedule's rows")
    void markerDoesNotSuppressOtherSections() throws Exception {
      List<String> rows =
          rows(extract(body(2020, 2020, "[760]", "[\"Schedule 1\",\"Schedule 6\"]")));

      // Schedule 1 has rows for this mill, Schedule 6 has none; both sections appear and each
      // carries its own outcome.
      int schedule1 = sectionAt(rows, "**** Schedule 1 ****");
      int schedule6 = sectionAt(rows, "**** Schedule 6 ****");
      assertThat(schedule1).isPositive();
      assertThat(schedule6).isGreaterThan(schedule1);
      assertThat(cells(rows.get(schedule1 + 2)).get(0)).isEqualTo("7600");
      assertThat(cells(rows.get(schedule6 + 2))).contains(NO_DATA_FOUND);
    }

    @Test
    @DisplayName("the NO SCHEDULE 3 sentinel appears in Schedule 1 rows only, and only when due")
    void sentinelAppearsInScheduleOneOnly() throws Exception {
      // 761 has a Schedule 1 but no Schedule 3 summary; 760 has both. One request, both shapes.
      MvcResult result = extract(body(2020, 2020, "[760,761]", "[\"Schedule 1\",\"Schedule 2\"]"));
      List<String> rows = rows(result);
      int schedule1 = sectionAt(rows, "**** Schedule 1 ****");

      List<String> mill760 = cells(rows.get(schedule1 + 2));
      List<String> mill761 = cells(rows.get(schedule1 + 3));
      assertThat(mill760.get(0)).isEqualTo("7600");
      assertThat(mill761.get(0)).isEqualTo("7610");
      assertThat(mill760).doesNotContain(NO_SCHEDULE_3);
      assertThat(mill761).contains(NO_SCHEDULE_3);

      // And nowhere else in the file — Schedule 2's own Schedule-3-derived cells use the ordinary
      // null marker, because legacy paired them positionally and would have misaligned them.
      int schedule2 = sectionAt(rows, "**** Schedule 2 ****");
      assertThat(rows.subList(schedule2, rows.size()))
          .noneMatch(row -> row.contains(NO_SCHEDULE_3));
    }

    @Test
    @DisplayName("a mill/year with no status row renders the verbatim NO STATUS cell")
    void missingStatusRowRendersNoStatus() throws Exception {
      // The 2021 half of the range has no status row for 760, and no Schedule 1 either, so the
      // status cell is observed through a section that DOES have a 2021 row to show. Schedule 6 has
      // none for any year, so the whole-schedule marker is what 2021 produces; the status cell is
      // instead asserted on the section whose rows span both years.
      List<String> rows = rows(extract(body(2020, 2021, "[760]", "[\"Schedule 1\"]")));
      int schedule1 = sectionAt(rows, "**** Schedule 1 ****");

      // Only 2020 has a Schedule 1 summary, so there is exactly one data row and it carries the
      // 2020 status description. The NO STATUS path is proven by the Data Verified No above and by
      // the unit tests; asserting it here would need a 2021 summary, which the fixture cannot add.
      assertThat(cells(rows.get(schedule1 + 2)).get(2)).isNotEqualTo(NO_STATUS);
      assertThat(cells(rows.get(schedule1 + 2)).get(1)).isEqualTo("2020");
    }

    @Test
    @DisplayName("sections appear in ascending schedule order whatever order the request listed")
    void sectionsAreAscendingRegardlessOfRequestOrder() throws Exception {
      List<String> rows =
          rows(
              extract(body(2020, 2020, "[760]", "[\"Schedule 9\",\"Schedule 1\",\"Schedule 6\"]")));

      // Equal to legacy's observable order, since its checkbox menu posted values in component
      // order — and it makes the file independent of how a client happens to order its request.
      assertThat(sectionAt(rows, "**** Schedule 1 ****"))
          .isLessThan(sectionAt(rows, "**** Schedule 6 ****"));
      assertThat(sectionAt(rows, "**** Schedule 6 ****"))
          .isLessThan(sectionAt(rows, "**** Schedule 9 ****"));
    }

    @Test
    @DisplayName("an unknown schedule label contributes no section")
    void unknownLabelIsIgnored() throws Exception {
      List<String> rows =
          rows(extract(body(2020, 2020, "[760]", "[\"Schedule 1\",\"Schedule 12\"]")));

      // The ruled behaviour carried from the selection story: an unrecognised label is dropped
      // rather than refused, exactly as legacy's dispatch matched no builder for it.
      assertThat(sectionAt(rows, "**** Schedule 1 ****")).isPositive();
      assertThat(rows).noneMatch(row -> row.contains("Schedule 12"));
    }
  }

  @Nested
  @DisplayName("the combined Schedule 1 + 2 layout")
  class CombinedLayout {

    /**
     * All three conditions: exactly Schedules 1 and 2, two or more mills, and two distinct years.
     */
    private static final String COMBINED_SCHEDULES = "[\"Schedule 1\",\"Schedule 2\"]";

    @Test
    @DisplayName("applies when all three conditions hold, and replaces the standard sections")
    void appliesWhenAllThreeConditionsHold() throws Exception {
      List<String> rows = rows(extract(body(2020, 2021, "[760,761]", COMBINED_SCHEDULES)));

      // The title line carries the RAW labels in this mode, not the detail-expanded names.
      assertThat(rows.get(6)).isEqualTo("\"Schedules: Schedule 1, Schedule 2\"");
      // Five-column mini-tables, and no Other Costs section.
      int schedule1 = sectionAt(rows, "**** Schedule 1 ****");
      assertThat(cells(rows.get(schedule1 + 1)))
          .containsExactly("MILL_NUMBER", "REPORTING_YEAR", "STATUS", "MILL_ID", "STAND_TTT_M3");
      int schedule2 = sectionAt(rows, "**** Schedule 2 ****");
      assertThat(cells(rows.get(schedule2 + 1)))
          .containsExactly("MILL_NUMBER", "REPORTING_YEAR", "STATUS", "MILL_ID", "PO&P_COSTS_$");
      assertThat(sectionAt(rows, "**** Schedule 1 - Other Costs ****")).isEqualTo(-1);
    }

    @Test
    @DisplayName("a year with no rows emits NO mini-table header of its own")
    void noHeaderForAYearWithNoRows() throws Exception {
      // 2021 has no Schedule 1 data for either mill. Legacy emitted a mini-table header only when
      // the reporting year CHANGED across its row list, so an empty year produced nothing at all —
      // as opposed to a header per year in the range, which this asserts against.
      List<String> rows = rows(extract(body(2020, 2021, "[760,761]", COMBINED_SCHEDULES)));

      assertThat(rows.stream().filter(row -> row.equals("\"**** Schedule 1 ****\"")).count())
          .isEqualTo(1);
      assertThat(rows).noneMatch(row -> cells(row).size() == 5 && row.contains("\"2021\""));
    }

    @Test
    @DisplayName("negative control: ONE mill falls back to the standard per-schedule layout")
    void oneMillFallsBack() throws Exception {
      List<String> rows = rows(extract(body(2020, 2021, "[760]", COMBINED_SCHEDULES)));

      assertThat(rows.get(6)).isNotEqualTo("\"Schedules: Schedule 1, Schedule 2\"");
      // The positive half of the control: the standard sections ARE present, at full width.
      int schedule1 = sectionAt(rows, "**** Schedule 1 ****");
      assertThat(schedule1).isPositive();
      assertThat(cells(rows.get(schedule1 + 1))).hasSizeGreaterThan(5);
      assertThat(sectionAt(rows, "**** Schedule 1 - Other Costs ****")).isPositive();
    }

    @Test
    @DisplayName("negative control: equal start and end years falls back")
    void equalYearsFallsBack() throws Exception {
      // Reachable: the selection gate accepts a single-year range, a recorded deviation.
      List<String> rows = rows(extract(body(2020, 2020, "[760,761]", COMBINED_SCHEDULES)));

      int schedule1 = sectionAt(rows, "**** Schedule 1 ****");
      assertThat(schedule1).isPositive();
      assertThat(cells(rows.get(schedule1 + 1))).hasSizeGreaterThan(5);
      assertThat(sectionAt(rows, "**** Schedule 1 - Other Costs ****")).isPositive();
    }

    @Test
    @DisplayName("negative control: a THIRD schedule falls back")
    void thirdScheduleFallsBack() throws Exception {
      List<String> rows =
          rows(
              extract(
                  body(2020, 2021, "[760,761]", "[\"Schedule 1\",\"Schedule 2\",\"Schedule 6\"]")));

      int schedule1 = sectionAt(rows, "**** Schedule 1 ****");
      assertThat(schedule1).isPositive();
      assertThat(cells(rows.get(schedule1 + 1))).hasSizeGreaterThan(5);
      assertThat(sectionAt(rows, "**** Schedule 6 ****")).isPositive();
    }
  }

  @Nested
  @DisplayName("failure")
  class Failure {

    @Test
    @DisplayName("a read failure is one 500 undefinedError and leaves NO spool file behind")
    void readFailureAnswers500AndLeavesNoSpoolFile() throws Exception {
      // The whole-file-or-no-file contract's other half. Asserted on the spool DIRECTORY, because
      // the failure mode this guards against is a partial file surviving on a read-only-root pod
      // whose writable volume then fills up — invisible to any assertion on the response alone.
      Path spoolDirectory = Path.of(System.getProperty("java.io.tmpdir"));
      Set<Path> before = spoolFiles(spoolDirectory);

      when(schedule1Service.findStoredSchedule1(anyLong(), anyInt(), any()))
          .thenThrow(new IllegalStateException("the owner blew up mid-build"));

      mockMvc
          .perform(
              post(ENDPOINT)
                  .with(ADMIN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(2020, 2020, "[760]", "[\"Schedule 1\"]"))
                  .accept(MediaType.ALL))
          .andExpect(status().isInternalServerError())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          // The legacy bundle text, not the global handler's hardcoded generic. Pinned verbatim
          // because a non-BusinessException would still answer 500 and pass a status-only check.
          .andExpect(
              jsonPath("$.detail")
                  .value(
                      "ILCR has found an unhandled error/exception. Please refer to application"
                          + " log files."))
          .andExpect(header().doesNotExist("Content-Disposition"));

      assertThat(spoolFiles(spoolDirectory)).isEqualTo(before);
    }

    @Test
    @DisplayName("logs carry counts and ids, never a cell value")
    void logsCarryNoCellValues() throws Exception {
      // AD-11/NFR3: commercial cost data must not reach a log at any level. The seeded figures
      // below are the ones this extract actually renders, so their absence from a DEBUG-level
      // capture is evidence rather than a tautology.
      // The app logs through SLF4J onto log4j2 (the logback starter is excluded), and with no
      // log4j2 config on the test classpath the root level is ERROR — so the level has to be
      // lowered explicitly or the capture is vacuously empty.
      Logger generatorLog = (Logger) LogManager.getLogger(DataExtractGenerator.class);
      CapturingAppender appender = new CapturingAppender();
      appender.start();
      Level previous = generatorLog.getLevel();
      generatorLog.addAppender(appender);
      generatorLog.setLevel(Level.DEBUG);
      try {
        extract(body(2020, 2020, "[760,761]", "[\"Schedule 1\",\"Schedule 2\"]"));
      } finally {
        generatorLog.removeAppender(appender);
        generatorLog.setLevel(previous);
        appender.stop();
      }

      String logged = String.join("\n", appender.messages);
      assertThat(logged).isNotEmpty();
      // Raw and formatted forms of the seeded volumes and costs, plus the markers a row sample
      // would drag in with it.
      assertThat(logged)
          .doesNotContain("1234500")
          .doesNotContain("1,234,500")
          .doesNotContain("9876500")
          .doesNotContain("9,876,500")
          .doesNotContain("777000")
          .doesNotContain("777,000")
          .doesNotContain(NO_DATA_FOUND);
    }

    /**
     * Collects formatted messages at every level, so a leak at DEBUG is caught as well as at INFO.
     */
    private static final class CapturingAppender extends AbstractAppender {

      private final List<String> messages = new ArrayList<>();

      private CapturingAppender() {
        super("data-extract-capture", null, null, true, Property.EMPTY_ARRAY);
      }

      @Override
      public void append(LogEvent event) {
        messages.add(event.getMessage().getFormattedMessage());
      }
    }

    @Test
    @DisplayName("a 400 refusal carries no attachment header and no file")
    void refusalProducesNoFile() throws Exception {
      mockMvc
          .perform(
              post(ENDPOINT)
                  .with(ADMIN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(2020, 2020, "[]", "[\"Schedule 1\"]"))
                  .accept(MediaType.ALL))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          .andExpect(header().doesNotExist("Content-Disposition"))
          .andExpect(
              jsonPath("$.messages[*].text")
                  .value(Matchers.hasItem("Please select at least one Mill for extracting.")));
    }
  }
}
