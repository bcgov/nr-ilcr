package ca.bc.gov.nrs.ilcr.dataextract.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dataextract.ValidatedSelection;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillYearTrackCodes;
import ca.bc.gov.nrs.ilcr.reporting.FileSpooler;
import ca.bc.gov.nrs.ilcr.reporting.SpooledFile;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Service;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Service;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Service;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Service;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Service;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Service;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bService;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Service;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Service;
import ca.bc.gov.nrs.ilcr.security.EditableStatuses;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the extract's TITLE BLOCK bytes (Story 21.2), with every owner service mocked and
 * the spooler replaced by an in-memory sink.
 *
 * <p>This exists for the one line the integration tests cannot pin: the timestamp. Legacy's format
 * was {@code MMMM, dd yyyy @ HH:mm aaa} — a 24-hour clock AND an AM/PM marker, which reads oddly
 * ({@code 15:30 PM}) and is exactly why it must be reproduced verbatim rather than "fixed" to
 * {@code hh}. The ITs run on the live UTC clock and can only regex the shape, so {@code 03:30 PM}
 * and {@code 15:30 PM} look identical to them; a fixed clock in the afternoon is the only way to
 * tell {@code HH} from {@code hh}. The same fixed instant also proves the Pacific zone: 23:30 UTC
 * must print as 15:30, not 23:30.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataExtractGenerator — the title block")
class DataExtractGeneratorTest {

  /** 2020-03-05 15:30 Pacific — 23:30 UTC, so a UTC render would say 23:30 and the 6th. */
  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2020-03-05T23:30:00Z"), DataExtractGenerator.ZONE);

  private static final long MILL_ID = 514L;
  private static final int YEAR = 2020;

  @Mock private MillContextService millContextService;
  @Mock private Schedule1Service schedule1Service;
  @Mock private Schedule2Service schedule2Service;
  @Mock private Schedule3Service schedule3Service;
  @Mock private Schedule4Service schedule4Service;
  @Mock private Schedule5Service schedule5Service;
  @Mock private Schedule6Service schedule6Service;
  @Mock private Schedule7aService schedule7aService;
  @Mock private Schedule7bService schedule7bService;
  @Mock private Schedule8Service schedule8Service;
  @Mock private Schedule9Service schedule9Service;
  @Mock private Schedule10Service schedule10Service;
  @Mock private Schedule11Service schedule11Service;
  @Mock private FileSpooler spooler;

  /** What the writer produced — the bytes that would have gone to the spool file. */
  private final ByteArrayOutputStream sink = new ByteArrayOutputStream();

  private DataExtractGenerator generator;

  @BeforeEach
  void setUp() {
    generator =
        new DataExtractGenerator(
            millContextService,
            schedule1Service,
            schedule2Service,
            schedule3Service,
            schedule4Service,
            schedule5Service,
            schedule6Service,
            schedule7aService,
            schedule7bService,
            schedule8Service,
            schedule9Service,
            schedule10Service,
            schedule11Service,
            spooler,
            FIXED);
    // The spooler's contract is "run the writer to completion, hand back the finished file". Here
    // the writer runs against the in-memory sink so the bytes can be read, and the returned file
    // is a Mockito dummy: SpooledFile's constructor is package-private to reporting (only the
    // spooler may mint one), and nothing in the generator reads the returned file anyway.
    when(spooler.spool(eq(DataExtractGenerator.SPOOL_PREFIX), eq(".csv"), any()))
        .thenAnswer(
            invocation -> {
              FileSpooler.SpoolWriter writer = invocation.getArgument(2);
              writer.writeTo(sink);
              return mock(SpooledFile.class);
            });
  }

  /** One enrolled mill, one Verified/Verified year, one Schedule 6 read with no road records. */
  private void arrangeOneMillOneYearSchedule6() {
    when(millContextService.listMills(true, null))
        .thenReturn(List.of(new MillSummary(MILL_ID, "1234", "Test Mill", "ACT")));
    when(millContextService.findTrackStatusCodes(List.of(MILL_ID), YEAR, YEAR))
        .thenReturn(List.of(new MillYearTrackCodes(MILL_ID, YEAR, "V", "V")));
    // The row context is built even for a pair with no records, so the description IS looked up.
    when(millContextService.findStatusDescription("V")).thenReturn(Optional.of("Verified"));
    when(schedule6Service.getSchedule6(MILL_ID, YEAR, EditableStatuses.NONE))
        .thenReturn(
            new Schedule6Response(
                MILL_ID, YEAR, "V", false, null, Map.of(), List.of(), null, null, null, null,
                null));
  }

  private ValidatedSelection schedule6Selection() {
    return new ValidatedSelection(YEAR, YEAR, List.of(MILL_ID), List.of("Schedule 6"));
  }

  private String body() {
    return sink.toString(StandardCharsets.UTF_8);
  }

  /** The file's lines after the BOM, split on the bare {@code \n} the writer emits. */
  private List<String> lines() {
    String text = body();
    assertThat(text).startsWith(CsvWriter.BOM);
    return List.of(text.substring(1).split("\n", -1));
  }

  @Nested
  @DisplayName("the timestamp line")
  class Timestamp {

    @Test
    @DisplayName("is Pacific time on a 24-hour clock WITH an AM/PM marker, verbatim from legacy")
    void secondLine_isLegacyTimestamp_24HourClockAndMeridiem() {
      arrangeOneMillOneYearSchedule6();

      generator.generate(schedule6Selection());

      // "15:30 PM": HH not hh (hh would print 03:30 PM, which the IT regex cannot distinguish), and
      // 15 not 23 (the instant is 23:30 UTC — the zone is doing work). Quoted, because every cell
      // is, and the whole line is the cell.
      assertThat(lines().get(1)).isEqualTo("\"Data Extract on March, 05 2020 @ 15:30 PM\"");
    }

    @Test
    @DisplayName("sits directly under the ministry title, as the second row of the file")
    void firstLine_isTheMinistryTitle() {
      arrangeOneMillOneYearSchedule6();

      generator.generate(schedule6Selection());

      // Pins the position the timestamp assertion above relies on: line 1 is the timestamp only
      // because line 0 is the title, so a reordering of the title block would fail HERE, legibly.
      assertThat(lines().get(0))
          .isEqualTo(
              "\"Ministry of Forests, Lands, Natural Resource Operations & Rural Development,"
                  + " ILCR\"");
    }
  }

  @Nested
  @DisplayName("the file's framing")
  class Framing {

    @Test
    @DisplayName("opens with the three-byte UTF-8 BOM")
    void startsWithUtf8Bom() {
      arrangeOneMillOneYearSchedule6();

      generator.generate(schedule6Selection());

      // Excel on Windows is the stated reader; without these three bytes it opens the file as the
      // local ANSI page and every m³ is mojibake. Asserted on the raw bytes, not the decoded text,
      // so a writer that emitted the character in another encoding could not pass.
      byte[] bytes = sink.toByteArray();
      assertThat(bytes.length).isGreaterThan(3);
      assertThat(new byte[] {bytes[0], bytes[1], bytes[2]})
          .containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    @DisplayName("ends with legacy's end marker as the last row")
    void endsWithEndMarker() {
      arrangeOneMillOneYearSchedule6();

      generator.generate(schedule6Selection());

      // The trailing "\n" is the row terminator, so the file's last bytes are the marker row plus
      // exactly one newline — a truncated spool would lose this first.
      assertThat(body()).endsWith("\"**** End ****\"\n");
      List<String> lines = lines();
      assertThat(lines.get(lines.size() - 1)).isEmpty(); // the split's tail after the final \n
      assertThat(lines.get(lines.size() - 2)).isEqualTo("\"**** End ****\"");
    }

    @Test
    @DisplayName("a V/V pair with an empty Schedule 6 still reports Data Verified: Yes")
    void verifiedPair_reportsYes_andNoDataMarkerForTheEmptySection() {
      arrangeOneMillOneYearSchedule6();

      generator.generate(schedule6Selection());

      // Cheap to pin while the state is arranged: the verdict is about STATUS, not about whether
      // the schedule holds rows (BR-05), and the empty section gets its no-data row rather than
      // nothing at all.
      assertThat(lines()).contains("\"Data Verified: Yes\"", "\"Included Mills: 1234\"");
      assertThat(body()).contains(ExtractFormat.NO_DATA_FOUND);
    }
  }
}
