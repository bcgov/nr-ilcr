package ca.bc.gov.nrs.ilcr.schedule9;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.CostRow;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.RecordRow;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CheckRequest.RecordEntry;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * {@link Schedule9Service}'s two Check Status paths over ONE evaluator (bcgov/nr-ilcr#359): {@code
 * checkStatus} judges the posted screen and never reads the database; {@code checkStatusStored}
 * judges the saved records. The real {@code messages.properties} bundle is used, so every asserted
 * line is the byte the endpoint serves.
 */
@DisplayName("Schedule9Service — Check Status on the screen vs the saved record (#359)")
class Schedule9CheckStatusServiceTest {

  private static final long MILL = 703L;
  private static final int YEAR = 2021;

  private final Schedule9Repository repository = mock(Schedule9Repository.class);
  private final Schedule9Service service =
      new Schedule9Service(
          repository,
          realBundle(),
          OriginalValuesFixture.real(),
          mock(CostDetailSnapshotRepository.class));

  private static ResourceBundleMessageSource realBundle() {
    ResourceBundleMessageSource bundle = new ResourceBundleMessageSource();
    bundle.setBasename("messages");
    bundle.setDefaultEncoding("UTF-8");
    return bundle;
  }

  /** A complete on-screen row: item 108 (side slope not checked), every other value present. */
  private static RecordEntry complete() {
    return new RecordEntry("ACME", 108, null, new BigDecimal("10.0"), "KM", "CWH", 5000, "C");
  }

  private static RecordEntry with(Integer item, Integer sideSlope, Integer cost) {
    return new RecordEntry("ACME", item, sideSlope, new BigDecimal("10.0"), "KM", "CWH", cost, "C");
  }

  private Schedule9CheckStatusResponse screen(RecordEntry... entries) {
    return service.checkStatus(MILL, YEAR, new Schedule9CheckRequest(List.of(entries)));
  }

  private static List<String> texts(Schedule9CheckStatusResponse response) {
    return response.errors().stream().map(MessageInfo::text).toList();
  }

  private static RecordRow storedRow(
      int id,
      String contractor,
      BigDecimal units,
      Integer slope,
      String unit,
      String bec,
      String src) {
    return new RecordRow(
        id, 0, contractor, units, slope, null, unit, null, null, src, null, null, bec, null);
  }

  /** Stored data that DISAGREES with every screen below: one complete record. */
  private void storedIsComplete() {
    lenient()
        .when(repository.findRecords(MILL, YEAR))
        .thenReturn(List.of(storedRow(1, "ACME", new BigDecimal("10.0"), null, "KM", "CWH", "C")));
    lenient()
        .when(repository.findCostLines(MILL, YEAR))
        .thenReturn(List.of(new CostRow(1, 108, "Item", null, 5000)));
  }

  @Nested
  @DisplayName("the screen path")
  class Screen {

    @Test
    @DisplayName("unsaved clear: cost emptied on screen, stored cost present -> flagged")
    void unsavedClear_isFlagged() {
      storedIsComplete();

      Schedule9CheckStatusResponse response = screen(with(108, null, null));

      assertThat(response.requirementsMet()).isFalse();
      assertThat(texts(response))
          .containsExactly("Contractual Work Report Id : 1 Cost$: Value Required");
      assertThat(response.requirementsMetMessage()).isNull();
      verify(repository, never()).findRecords(anyLong(), anyInt());
      verify(repository, never()).findCostLines(anyLong(), anyInt());
    }

    @Test
    @DisplayName("unsaved fix: stored-missing values typed on screen -> met with the banner")
    void unsavedFix_passes() {
      lenient()
          .when(repository.findRecords(MILL, YEAR))
          .thenReturn(List.of(storedRow(1, null, null, null, null, null, null)));
      lenient().when(repository.findCostLines(MILL, YEAR)).thenReturn(List.of());

      Schedule9CheckStatusResponse response = screen(complete(), complete());

      assertThat(response.requirementsMet()).isTrue();
      assertThat(response.errors()).isEmpty();
      assertThat(response.requirementsMetMessage().text())
          .isEqualTo("All requirements for this schedule have been met");
    }

    @Test
    @DisplayName("other page: row 13 of 13 is evaluated and numbered by its payload ordinal")
    void otherPageRow_numberedByOrdinal() {
      List<RecordEntry> rows = new ArrayList<>();
      for (int i = 0; i < 12; i++) {
        rows.add(complete());
      }
      rows.add(new RecordEntry(" ", 108, null, new BigDecimal("1.0"), "KM", "CWH", 1, "C"));

      assertThat(texts(service.checkStatus(MILL, YEAR, new Schedule9CheckRequest(rows))))
          .containsExactly("Contractual Work Report Id : 13 Company ID: Value Required");
    }

    @Test
    @DisplayName("item switch to 111 / 112 with side slope blank -> the Side Slope line appears")
    void itemSwitch_bringsSideSlopeIntoForce() {
      assertThat(texts(screen(with(111, null, 5000))))
          .containsExactly("Contractual Work Report Id : 1 Side Slope %: Value Required");
      assertThat(texts(screen(with(112, null, 5000))))
          .containsExactly("Contractual Work Report Id : 1 Side Slope %: Value Required");
      // ... and switching AWAY from 111/112 drops it again, slope still blank.
      assertThat(screen(with(108, null, 5000)).requirementsMet()).isTrue();
    }

    @Test
    @DisplayName("the side-slope range line is the check's own, over the on-screen value")
    void sideSlopeRange_fromScreen() {
      assertThat(texts(screen(with(111, 100, 5000))))
          .containsExactly(
              "Contractual Work Report Id : 1 Side Slope %: Entered value must be between 0 and"
                  + " 99.");
    }

    @Test
    @DisplayName("a typed 0 passes every null test; null is never coerced to 0")
    void zeroPasses_nullIsReported() {
      RecordEntry zeros = new RecordEntry("A", 111, 0, BigDecimal.ZERO, "KM", "CWH", 0, "C");
      assertThat(screen(zeros).requirementsMet()).isTrue();

      RecordEntry blanks = new RecordEntry(null, null, null, null, "", " ", null, null);
      assertThat(texts(screen(blanks)))
          .containsExactly(
              "Contractual Work Report Id : 1 Company ID: Value Required",
              "Contractual Work Report Id : 1 Contractual Item: Value Required",
              "Contractual Work Report Id : 1 Number of Units: Value Required",
              "Contractual Work Report Id : 1 Unit Type: Value Required",
              "Contractual Work Report Id : 1 Biogeoclimatic Zone: Value Required",
              "Contractual Work Report Id : 1 Cost$: Value Required",
              "Contractual Work Report Id : 1 Source: Value Required");
    }

    @Test
    @DisplayName("an empty screen is vacuously met, as zero stored records are")
    void emptyScreen_isMet() {
      assertThat(screen().requirementsMet()).isTrue();
    }
  }

  @Nested
  @DisplayName("the stored path, and parity with the screen path")
  class Stored {

    @Test
    @DisplayName("stored: a record with no cost line flags item and cost, in legacy order")
    void stored_noCostLine_flagsItemAndCost() {
      when(repository.findRecords(MILL, YEAR))
          .thenReturn(
              List.of(storedRow(1, "ACME", new BigDecimal("10.0"), null, "KM", "CWH", "C")));
      when(repository.findCostLines(MILL, YEAR)).thenReturn(List.of());

      assertThat(texts(service.checkStatusStored(MILL, YEAR)))
          .containsExactly(
              "Contractual Work Report Id : 1 Contractual Item: Value Required",
              "Contractual Work Report Id : 1 Cost$: Value Required");
    }

    @Test
    @DisplayName("parity: a body mirroring the stored records yields the stored verdict exactly")
    void mirroringBody_equalsStoredVerdict() {
      when(repository.findRecords(MILL, YEAR))
          .thenReturn(
              List.of(
                  storedRow(1, "ACME", new BigDecimal("10.0"), null, "KM", "CWH", "C"),
                  storedRow(2, " ", new BigDecimal("100000.0"), 100, "KM", null, "C"),
                  storedRow(3, "B", null, null, null, "CWH", null)));
      when(repository.findCostLines(MILL, YEAR))
          .thenReturn(
              List.of(
                  new CostRow(1, 108, "Item", null, 5000),
                  new CostRow(2, 112, "Item", null, 10000000),
                  new CostRow(2, 108, "Dup", null, 1)));

      Schedule9CheckStatusResponse stored = service.checkStatusStored(MILL, YEAR);
      Schedule9CheckStatusResponse payload =
          screen(
              new RecordEntry("ACME", 108, null, new BigDecimal("10.0"), "KM", "CWH", 5000, "C"),
              new RecordEntry(" ", 112, 100, new BigDecimal("100000.0"), "KM", null, 10000000, "C"),
              new RecordEntry("B", null, null, null, null, "CWH", null, null));

      assertThat(stored.requirementsMet()).isFalse();
      assertThat(stored.errors()).hasSizeGreaterThan(5);
      assertThat(payload).isEqualTo(stored);
    }

    @Test
    @DisplayName("parity: an all-met stored schedule and its mirror both carry the banner")
    void allMet_parity() {
      storedIsComplete();

      Schedule9CheckStatusResponse stored = service.checkStatusStored(MILL, YEAR);

      assertThat(stored.requirementsMet()).isTrue();
      assertThat(screen(complete())).isEqualTo(stored);
    }
  }
}
