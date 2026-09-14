package ca.bc.gov.nrs.ilcr.dataextract.csv;

import ca.bc.gov.nrs.ilcr.dataextract.DataVerifiedRule;
import ca.bc.gov.nrs.ilcr.dataextract.ValidatedSelection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.CombinedSchedule1And2Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.RowContext;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule10RoadSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule10Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule11Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule1OtherSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule1Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule2Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule3AcceptSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule3Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule3UnacceptSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule4Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule4SubPageSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule5Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule5SubPageSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule6Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule7aSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule7bSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule8RateSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule8SampleSection;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule8Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.Schedule9Section;
import ca.bc.gov.nrs.ilcr.dataextract.csv.section.SectionBuilder;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillYearTrackCodes;
import ca.bc.gov.nrs.ilcr.reporting.FileSpooler;
import ca.bc.gov.nrs.ilcr.reporting.SpooledFile;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Service;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Service;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Service;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Service;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import ca.bc.gov.nrs.ilcr.schedule5.CampNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Service;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Service;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecord;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Bridge;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aResponse;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bService;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Culvert;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bResponse;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Service;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Options;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Service;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecord;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
import ca.bc.gov.nrs.ilcr.security.EditableStatuses;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Builds the Data Extract CSV for a validated selection — the rebuilt {@code
 * ILCRExtractDataService.getExtractStream()} plus its {@code SchedulesExtractData} accumulator.
 *
 * <p>Every figure comes from the owning schedule service's read method, one (mill, year) at a time
 * (AD-14): the twenty-five legacy {@code *Extract} builders survive only as the column shapes in
 * {@code section/}, never as SQL. The reads are sequential and each is its own short transaction;
 * there is deliberately no transaction around the whole build, which for a wide selection would pin
 * one of the pool's five connections for the duration.
 *
 * <p>The file is spooled to disk in full before anything is returned, so a failure anywhere in the
 * build is an ordinary error with no bytes sent and no file offered (see {@link FileSpooler}). The
 * spool prefix is the download's name; the exact temp name is never shown to the user.
 *
 * <p>Sections appear in ascending schedule order whatever the request order — equal to legacy's
 * observable order, since its checkbox menu posted its values in component order. Within a schedule
 * the sub-sections follow legacy's dispatch order, including Schedule 5's Camp-before- Access.
 *
 * <p>Logging names mills, years, labels and row COUNTS only. Never a cell, never a row (AD-11).
 */
@Component
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class DataExtractGenerator {

  private static final Logger log = LoggerFactory.getLogger(DataExtractGenerator.class);

  /** Legacy's spool prefix, less the leading slash {@code createTempFile} discarded anyway. */
  static final String SPOOL_PREFIX = "dataExtract";

  /** Legacy {@code MMMM, dd yyyy @ HH:mm aaa}: a 24-hour clock AND an AM/PM marker, verbatim. */
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("MMMM, dd yyyy @ HH:mm a", Locale.ENGLISH);

  /** Legacy's server sat in Pacific time and so do the readers; the pod clock is UTC. */
  static final ZoneId ZONE = ZoneId.of("America/Vancouver");

  private static final String TITLE_MINISTRY =
      "Ministry of Forests, Lands, Natural Resource Operations & Rural Development, ILCR";

  private final MillContextService millContextService;
  private final Schedule1Service schedule1Service;
  private final Schedule2Service schedule2Service;
  private final Schedule3Service schedule3Service;
  private final Schedule4Service schedule4Service;
  private final Schedule5Service schedule5Service;
  private final Schedule6Service schedule6Service;
  private final Schedule7aService schedule7aService;
  private final Schedule7bService schedule7bService;
  private final Schedule8Service schedule8Service;
  private final Schedule9Service schedule9Service;
  private final Schedule10Service schedule10Service;
  private final Schedule11Service schedule11Service;
  private final FileSpooler spooler;
  private final Clock clock;

  private final Schedule1Section schedule1 = new Schedule1Section();
  private final Schedule1OtherSection schedule1Other = new Schedule1OtherSection();
  private final Schedule2Section schedule2 = new Schedule2Section();
  private final Schedule3Section schedule3 = new Schedule3Section();
  private final Schedule3AcceptSection schedule3Accept = new Schedule3AcceptSection();
  private final Schedule3UnacceptSection schedule3Unaccept = new Schedule3UnacceptSection();
  private final Schedule4Section schedule4 = new Schedule4Section();
  private final Schedule4SubPageSection schedule4Towing =
      new Schedule4SubPageSection(Schedule4SubPageSection.Kind.TOWING);
  private final Schedule4SubPageSection schedule4Rehaul =
      new Schedule4SubPageSection(Schedule4SubPageSection.Kind.REHAUL);
  private final Schedule4SubPageSection schedule4Other =
      new Schedule4SubPageSection(Schedule4SubPageSection.Kind.OTHER);
  private final Schedule5Section schedule5 = new Schedule5Section();
  private final Schedule5SubPageSection schedule5Camp =
      new Schedule5SubPageSection(Schedule5SubPageSection.Kind.CAMP);
  private final Schedule5SubPageSection schedule5Access =
      new Schedule5SubPageSection(Schedule5SubPageSection.Kind.ACCESS);
  private final Schedule6Section schedule6 = new Schedule6Section();
  private final Schedule7aSection schedule7a = new Schedule7aSection();
  private final Schedule7bSection schedule7b = new Schedule7bSection();
  private final Schedule8Section schedule8 = new Schedule8Section();
  private final Schedule8SampleSection schedule8Samples = new Schedule8SampleSection();
  private final Schedule8RateSection schedule8Additions =
      new Schedule8RateSection(Schedule8RateSection.Kind.ADDITIONS);
  private final Schedule8RateSection schedule8Deductions =
      new Schedule8RateSection(Schedule8RateSection.Kind.DEDUCTIONS);
  private final Schedule9Section schedule9 = new Schedule9Section();
  private final Schedule10Section schedule10 = new Schedule10Section();
  private final Schedule10RoadSection schedule10Road = new Schedule10RoadSection();
  private final Schedule11Section schedule11 = new Schedule11Section();
  private final CombinedSchedule1And2Section combined = new CombinedSchedule1And2Section();

  /**
   * Creates the generator on the Pacific-time system clock.
   *
   * @param millContextService mill list, bulk status codes and status descriptions
   * @param schedule1Service owner of Schedule 1 figures
   * @param schedule2Service owner of Schedule 2 figures
   * @param schedule3Service owner of Schedule 3 figures
   * @param schedule4Service owner of Schedule 4 figures
   * @param schedule5Service owner of Schedule 5 figures
   * @param schedule6Service owner of Schedule 6 figures
   * @param schedule7aService owner of Schedule 7A figures
   * @param schedule7bService owner of Schedule 7B figures
   * @param schedule8Service owner of Schedule 8 figures
   * @param schedule9Service owner of Schedule 9 figures
   * @param schedule10Service owner of Schedule 10 figures
   * @param schedule11Service owner of Schedule 11 figures
   * @param spooler writes the finished file to disk
   */
  @Autowired
  public DataExtractGenerator(
      MillContextService millContextService,
      Schedule1Service schedule1Service,
      Schedule2Service schedule2Service,
      Schedule3Service schedule3Service,
      Schedule4Service schedule4Service,
      Schedule5Service schedule5Service,
      Schedule6Service schedule6Service,
      Schedule7aService schedule7aService,
      Schedule7bService schedule7bService,
      Schedule8Service schedule8Service,
      Schedule9Service schedule9Service,
      Schedule10Service schedule10Service,
      Schedule11Service schedule11Service,
      FileSpooler spooler) {
    this(
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
        Clock.system(ZONE));
  }

  DataExtractGenerator(
      MillContextService millContextService,
      Schedule1Service schedule1Service,
      Schedule2Service schedule2Service,
      Schedule3Service schedule3Service,
      Schedule4Service schedule4Service,
      Schedule5Service schedule5Service,
      Schedule6Service schedule6Service,
      Schedule7aService schedule7aService,
      Schedule7bService schedule7bService,
      Schedule8Service schedule8Service,
      Schedule9Service schedule9Service,
      Schedule10Service schedule10Service,
      Schedule11Service schedule11Service,
      FileSpooler spooler,
      Clock clock) {
    this.millContextService = millContextService;
    this.schedule1Service = schedule1Service;
    this.schedule2Service = schedule2Service;
    this.schedule3Service = schedule3Service;
    this.schedule4Service = schedule4Service;
    this.schedule5Service = schedule5Service;
    this.schedule6Service = schedule6Service;
    this.schedule7aService = schedule7aService;
    this.schedule7bService = schedule7bService;
    this.schedule8Service = schedule8Service;
    this.schedule9Service = schedule9Service;
    this.schedule10Service = schedule10Service;
    this.schedule11Service = schedule11Service;
    this.spooler = spooler;
    this.clock = clock;
  }

  /**
   * Build the extract for {@code selection} and hand back the finished file.
   *
   * @param selection the gate's output
   * @return the complete CSV on disk, whose {@code close()} deletes it
   */
  public SpooledFile generate(ValidatedSelection selection) {
    Extract extract = new Extract(selection);
    log.debug(
        "Building data extract: {} mill(s), years {}-{}, schedules {}",
        selection.millIds().size(),
        selection.startYear(),
        selection.endYear(),
        extract.schedules.numbers());
    return spooler.spool(SPOOL_PREFIX, ".csv", extract::writeTo);
  }

  /** One build's resolved inputs and its walk over the selection. */
  private final class Extract {

    private final ValidatedSelection selection;
    private final ScheduleSelection schedules;
    private final List<MillSummary> mills;
    private final Map<String, MillYearTrackCodes> statuses = new HashMap<>();
    private final Map<String, String> descriptions = new HashMap<>();
    private final boolean combinedLayout;
    private final boolean verified;
    private final String millNumbers;
    private final String yearRange;

    Extract(ValidatedSelection selection) {
      this.selection = selection;
      this.schedules = ScheduleSelection.of(selection.schedules());
      this.mills = resolveMills(selection.millIds());
      for (MillYearTrackCodes row :
          millContextService.findTrackStatusCodes(
              selection.millIds(), selection.startYear(), selection.endYear())) {
        statuses.put(key(row.millId(), row.year()), row);
      }
      this.combinedLayout =
          schedules.isExactlySchedules1And2()
              && selection.millIds().size() > 1
              && selection.startYear() != selection.endYear();
      this.verified =
          DataVerifiedRule.allVerified(
              selection.millIds(),
              selection.startYear(),
              selection.endYear(),
              schedules,
              statuses.values());
      this.millNumbers = String.join(", ", mills.stream().map(this::millNumber).toList());
      this.yearRange = selection.startYear() + " - " + selection.endYear();
    }

    void writeTo(OutputStream out) throws IOException {
      try (CsvWriter csv = new CsvWriter(out)) {
        writeTitle(csv);
        if (combinedLayout) {
          csv.writeRows(combinedBody());
        } else {
          for (int schedule : schedules.numbers()) {
            writeSchedule(csv, schedule);
          }
        }
        csv.writeBlankRow();
        csv.writeBlankRow();
        csv.writeRow("**** End ****");
      }
    }

    private void writeTitle(CsvWriter csv) throws IOException {
      csv.writeRow(TITLE_MINISTRY);
      csv.writeRow("Data Extract on " + ZonedDateTime.now(clock).format(TIMESTAMP));
      csv.writeRow("Start Year: " + selection.startYear());
      csv.writeRow("End Year: " + selection.endYear());
      csv.writeRow("Included Mills: " + millNumbers);
      csv.writeRow("Data Verified: " + (verified ? "Yes" : "No"));
      csv.writeRow("Schedules: " + String.join(", ", scheduleNames()));
      csv.writeBlankRow();
      csv.writeBlankRow();
    }

    /** Legacy's title line: raw labels in combined mode, the detail-expanded names otherwise. */
    private List<String> scheduleNames() {
      List<String> names = new ArrayList<>();
      for (int n : schedules.numbers()) {
        if (combinedLayout) {
          names.add("Schedule " + n);
          continue;
        }
        switch (n) {
          case 1 -> names.addAll(List.of("Schedule 1", "Schedule 1 Other"));
          case 3 -> names.addAll(List.of("Schedule 3", "Schedule 3 Accept", "Schedule 3 Unaccept"));
          case 4 ->
              names.addAll(
                  List.of(
                      "Schedule 4", "Schedule 4 Towing", "Schedule 4 Rehaul", "Schedule 4 Other"));
          case 5 -> names.addAll(List.of("Schedule 5", "Schedule 5 Access", "Schedule 5 Camp"));
          case 7 -> names.addAll(List.of("Schedule 7 A", "Schedule 7 B"));
          case 8 ->
              names.addAll(
                  List.of(
                      "Schedule 8",
                      "Schedule 8 TTT",
                      "Schedule 8 TTT Additions",
                      "Schedule 8 TTT Deductions"));
          case 10 -> names.addAll(List.of("Schedule 10", "Schedule 10 Road"));
          default -> names.add("Schedule " + n);
        }
      }
      return names;
    }

    private void writeSchedule(CsvWriter csv, int schedule) throws IOException {
      long started = System.nanoTime();
      dispatch(csv, schedule);
      // INFO, per schedule, so the cost of a wide selection is visible in production before anyone
      // has to decide what a reasonable cap is. Counts and labels only (AD-11).
      log.info(
          "Data extract schedule {} written for {} mill(s) x {} year(s) in {} ms",
          schedule,
          mills.size(),
          selection.endYear() - selection.startYear() + 1,
          (System.nanoTime() - started) / 1_000_000);
    }

    private void dispatch(CsvWriter csv, int schedule) throws IOException {
      switch (schedule) {
        case 1 -> writeSchedule1(csv);
        case 2 -> writeSchedule2(csv);
        case 3 -> writeSchedule3(csv);
        case 4 -> writeSchedule4(csv);
        case 5 -> writeSchedule5(csv);
        case 6 -> writeSchedule6(csv);
        case 7 -> writeSchedule7(csv);
        case 8 -> writeSchedule8(csv);
        case 9 -> writeSchedule9(csv);
        case 10 -> writeSchedule10(csv);
        case 11 -> writeSchedule11(csv);
        default -> log.debug("Schedule {} has no extract section; ignored", schedule);
      }
    }

    // ---- Schedule 1 ---------------------------------------------------------------------------

    private void writeSchedule1(CsvWriter csv) throws IOException {
      List<String[]> main = new ArrayList<>();
      List<String[]> other = new ArrayList<>();
      // Legacy branched the WHOLE section on "every Schedule 1 record is empty"
      // (Schedule1Extract.java:38, isEmptyAllSchedule at :472-486): all empty ⇒ ONE whole-schedule
      // marker, not one per-record marker per pair. Only once some pair has figures do the empty
      // pairs get their own markers (:221-228). Tracked here because the decision spans the pairs.
      boolean[] anyReported = {false};
      forEachPair(
          (mill, year) -> {
            // findStoredSchedule1, NOT findSchedule1: the screen read copies a Schedule 3 Crown
            // Timber volume into all thirteen volume fields on first entry (BR-03), which would
            // print thirteen figures for a Schedule 1 nobody has filled in and suppress the marker
            // legacy wrote. A report shows what was reported.
            Optional<Schedule1Response> s1 =
                schedule1Service.findStoredSchedule1(mill.millId(), year, EditableStatuses.NONE);
            if (s1.isEmpty()) {
              return;
            }
            RowContext ctx = context(mill, year);
            Schedule3Response s3 =
                schedule3Service
                    .findSchedule3(mill.millId(), year, EditableStatuses.NONE)
                    .orElse(null);
            if (!Schedule1Section.isEmpty(s1.get())) {
              anyReported[0] = true;
            }
            main.add(schedule1.row(ctx, s1.get(), s3));
            other.addAll(schedule1Other.rows(ctx, otherCosts(mill.millId(), year)));
          });
      writeSection(csv, schedule1, anyReported[0] ? main : List.of());
      // Schedule 1 Other keeps the ordinary rule: legacy gated it on an EMPTY LIST
      // (Schedule1OtherExtract.java:36), not on all-records-empty, which is what writeSection does.
      writeSection(csv, schedule1Other, other);
    }

    private OtherCostsDocument otherCosts(long millId, int year) {
      try {
        return schedule1Service.getOtherCostsDocument(millId, year, EditableStatuses.NONE);
      } catch (ScheduleNotFoundException e) {
        return null;
      }
    }

    // ---- Schedule 2 ---------------------------------------------------------------------------

    private void writeSchedule2(CsvWriter csv) throws IOException {
      List<String[]> rows = new ArrayList<>();
      forEachPair(
          (mill, year) -> {
            Schedule2Response s2 =
                schedule2Service.getSchedule2(mill.millId(), year, EditableStatuses.NONE);
            boolean hasSchedule3 =
                schedule3Service
                    .findSchedule3(mill.millId(), year, EditableStatuses.NONE)
                    .isPresent();
            schedule2.row(context(mill, year), s2, hasSchedule3).ifPresent(rows::add);
          });
      writeSection(csv, schedule2, rows);
    }

    // ---- Schedule 3 ---------------------------------------------------------------------------

    private void writeSchedule3(CsvWriter csv) throws IOException {
      List<String[]> main = new ArrayList<>();
      List<String[]> accept = new ArrayList<>();
      List<String[]> unaccept = new ArrayList<>();
      forEachPair(
          (mill, year) -> {
            Optional<Schedule3Response> s3 =
                schedule3Service.findSchedule3(mill.millId(), year, EditableStatuses.NONE);
            if (s3.isEmpty()) {
              return;
            }
            RowContext ctx = context(mill, year);
            main.add(schedule3.row(ctx, s3.get()));
            // The child reads re-resolve the summary that findSchedule3 just returned. The build is
            // not a transaction and can run for minutes, so a licensee deleting the schedule in
            // between must cost that pair its sub-tables — the per-record marker — and not the
            // whole extract.
            accept.addAll(
                schedule3Accept.rows(
                    ctx,
                    orNull(
                        () ->
                            schedule3Service.getOtherAcceptableDocument(
                                mill.millId(), year, EditableStatuses.NONE))));
            unaccept.addAll(
                schedule3Unaccept.rows(
                    ctx,
                    orNull(
                        () ->
                            schedule3Service.getUnacceptableDocument(
                                mill.millId(), year, EditableStatuses.NONE))));
          });
      writeSection(csv, schedule3, main);
      writeSection(csv, schedule3Accept, accept);
      writeSection(csv, schedule3Unaccept, unaccept);
    }

    // ---- Schedule 4 ---------------------------------------------------------------------------

    private void writeSchedule4(CsvWriter csv) throws IOException {
      List<String[]> main = new ArrayList<>();
      List<String[]> towing = new ArrayList<>();
      List<String[]> rehaul = new ArrayList<>();
      List<String[]> other = new ArrayList<>();
      forEachPair(
          (mill, year) -> {
            Schedule4Response s4;
            try {
              s4 = schedule4Service.getSchedule4(mill.millId(), year, EditableStatuses.NONE);
            } catch (ScheduleNotFoundException e) {
              return;
            }
            if (s4.locations() == null) {
              return;
            }
            RowContext ctx = context(mill, year);
            for (Location location : s4.locations()) {
              main.add(schedule4.row(ctx, location));
              towing.addAll(schedule4Towing.rows(ctx, location));
              rehaul.addAll(schedule4Rehaul.rows(ctx, location));
              other.addAll(schedule4Other.rows(ctx, location));
            }
          });
      writeSection(csv, schedule4, main);
      writeSection(csv, schedule4Towing, towing);
      writeSection(csv, schedule4Rehaul, rehaul);
      writeSection(csv, schedule4Other, other);
    }

    // ---- Schedule 5 ---------------------------------------------------------------------------

    private void writeSchedule5(CsvWriter csv) throws IOException {
      List<String[]> main = new ArrayList<>();
      List<String[]> camp = new ArrayList<>();
      List<String[]> access = new ArrayList<>();
      forEachPair(
          (mill, year) -> {
            Schedule5Response s5 =
                schedule5Service.getSchedule5(mill.millId(), year, EditableStatuses.NONE);
            if (s5.camps() == null) {
              return;
            }
            RowContext ctx = context(mill, year);
            for (Camp c : s5.camps()) {
              main.add(schedule5.row(ctx, c));
              // getSubPage re-resolves the camp that getSchedule5 just listed; a camp deleted in
              // between costs its two sub-tables the per-camp marker, not the extract.
              camp.addAll(
                  schedule5Camp.rows(
                      ctx,
                      c,
                      orNull(
                          () ->
                              schedule5Service.getSubPage(
                                  mill.millId(),
                                  year,
                                  c.campId(),
                                  Schedule5Service.SubPage.CAMP,
                                  EditableStatuses.NONE))));
              access.addAll(
                  schedule5Access.rows(
                      ctx,
                      c,
                      orNull(
                          () ->
                              schedule5Service.getSubPage(
                                  mill.millId(),
                                  year,
                                  c.campId(),
                                  Schedule5Service.SubPage.ACCESS,
                                  EditableStatuses.NONE))));
            }
          });
      writeSection(csv, schedule5, main);
      // Legacy dispatch order: Camp before Access, although the title line lists Access first.
      writeSection(csv, schedule5Camp, camp);
      writeSection(csv, schedule5Access, access);
    }

    // ---- Schedule 6 ---------------------------------------------------------------------------

    private void writeSchedule6(CsvWriter csv) throws IOException {
      List<String[]> rows = new ArrayList<>();
      forEachPair(
          (mill, year) -> {
            Schedule6Response s6 =
                schedule6Service.getSchedule6(mill.millId(), year, EditableStatuses.NONE);
            if (s6.roadRecords() == null) {
              return;
            }
            RowContext ctx = context(mill, year);
            for (RoadRecord record : s6.roadRecords()) {
              rows.add(schedule6.row(ctx, record, s6.codeLists()));
            }
          });
      writeSection(csv, schedule6, rows);
    }

    // ---- Schedule 7 (7A then 7B) ---------------------------------------------------------------

    private void writeSchedule7(CsvWriter csv) throws IOException {
      List<String[]> bridges = new ArrayList<>();
      List<String[]> culverts = new ArrayList<>();
      forEachPair(
          (mill, year) -> {
            RowContext ctx = context(mill, year);
            Schedule7aResponse s7a =
                schedule7aService.getSchedule7a(mill.millId(), year, EditableStatuses.NONE);
            if (s7a.bridges() != null) {
              for (Bridge bridge : s7a.bridges()) {
                bridges.add(schedule7a.row(ctx, bridge, s7a.codeLists()));
              }
            }
            Schedule7bResponse s7b =
                schedule7bService.getSchedule7b(mill.millId(), year, EditableStatuses.NONE);
            if (s7b.culverts() != null) {
              for (Culvert culvert : s7b.culverts()) {
                culverts.add(schedule7b.row(ctx, culvert, s7b.codeLists()));
              }
            }
          });
      writeSection(csv, schedule7a, bridges);
      writeSection(csv, schedule7b, culverts);
    }

    // ---- Schedule 8 ---------------------------------------------------------------------------

    private void writeSchedule8(CsvWriter csv) throws IOException {
      List<String[]> main = new ArrayList<>();
      List<String[]> samples = new ArrayList<>();
      List<String[]> additions = new ArrayList<>();
      List<String[]> deductions = new ArrayList<>();
      Map<Integer, String> costItemNames = costItemNames(schedule8Service.getOptions());
      forEachPair(
          (mill, year) -> {
            Schedule8Response s8 =
                schedule8Service.getSchedule8(mill.millId(), year, EditableStatuses.NONE);
            if (s8.pages() == null) {
              return;
            }
            RowContext ctx = context(mill, year);
            int pageNumber = 0;
            for (Page page : s8.pages()) {
              pageNumber++;
              main.add(schedule8.row(ctx, page, pageNumber));
              samples.addAll(schedule8Samples.rows(ctx, page, pageNumber));
              additions.addAll(schedule8Additions.rows(ctx, page, pageNumber, costItemNames));
              deductions.addAll(schedule8Deductions.rows(ctx, page, pageNumber, costItemNames));
            }
          });
      writeSection(csv, schedule8, main);
      writeSection(csv, schedule8Samples, samples);
      writeSection(csv, schedule8Additions, additions);
      writeSection(csv, schedule8Deductions, deductions);
    }

    private Map<Integer, String> costItemNames(Schedule8Options options) {
      Map<Integer, String> names = new HashMap<>();
      if (options != null) {
        addCostItemNames(names, options.additionCostItems());
        addCostItemNames(names, options.deductionCostItems());
      }
      return names;
    }

    private void addCostItemNames(
        Map<Integer, String> names, List<Schedule8Options.CodeOption> options) {
      if (options == null) {
        return;
      }
      for (Schedule8Options.CodeOption option : options) {
        try {
          names.put(Integer.valueOf(option.code()), option.description());
        } catch (NumberFormatException e) {
          log.debug("Schedule 8 cost item code {} is not numeric; skipped", option.code());
        }
      }
    }

    // ---- Schedule 9 ---------------------------------------------------------------------------

    private void writeSchedule9(CsvWriter csv) throws IOException {
      List<String[]> rows = new ArrayList<>();
      forEachPair(
          (mill, year) -> {
            Schedule9Response s9 =
                schedule9Service.getSchedule9(mill.millId(), year, EditableStatuses.NONE);
            if (s9.records() == null) {
              return;
            }
            RowContext ctx = context(mill, year);
            for (ContractualWorkRecord record : s9.records()) {
              rows.add(schedule9.row(ctx, record));
            }
          });
      writeSection(csv, schedule9, rows);
    }

    // ---- Schedule 10 --------------------------------------------------------------------------

    private void writeSchedule10(CsvWriter csv) throws IOException {
      List<String[]> main = new ArrayList<>();
      List<String[]> details = new ArrayList<>();
      List<String[]> noDetails = new ArrayList<>();
      forEachPair(
          (mill, year) -> {
            Schedule10Response s10 =
                schedule10Service.getSchedule10(mill.millId(), year, EditableStatuses.NONE);
            if (s10.pages() == null) {
              return;
            }
            RowContext ctx = context(mill, year);
            for (ConstructionPage page : s10.pages()) {
              main.add(schedule10.row(ctx, page));
              if (schedule10Road.hasNoDetails(page)) {
                noDetails.add(schedule10Road.noDetailsRow(ctx, page));
              } else {
                details.addAll(schedule10Road.detailRows(ctx, page));
              }
            }
          });
      writeSection(csv, schedule10, main);
      // Legacy appended the no-road-data pages AFTER every detail row of the section.
      List<String[]> road = new ArrayList<>(details);
      road.addAll(noDetails);
      writeSection(csv, schedule10Road, road);
    }

    // ---- Schedule 11 --------------------------------------------------------------------------

    private void writeSchedule11(CsvWriter csv) throws IOException {
      List<Schedule11Section.Entry> entries = new ArrayList<>();
      forEachPair(
          (mill, year) ->
              entries.add(
                  new Schedule11Section.Entry(
                      silvicultureContext(mill, year),
                      schedule11Service.getSchedule11(
                          mill.millId(), year, EditableStatuses.NONE))));
      writeSection(csv, schedule11, schedule11.rows(entries));
    }

    // ---- Combined Schedules 1 + 2 --------------------------------------------------------------

    private List<String[]> combinedBody() {
      List<CombinedSchedule1And2Section.Schedule1Entry> s1 = new ArrayList<>();
      List<CombinedSchedule1And2Section.Schedule2Entry> s2 = new ArrayList<>();
      // Legacy inverted its sort keys here: reporting year first, then mill id.
      for (int year = selection.startYear(); year <= selection.endYear(); year++) {
        for (MillSummary mill : millsByIdAscending()) {
          RowContext ctx = context(mill, year);
          // The stored read, for the same reason writeSchedule1 uses it: the screen's BR-03 crown
          // pre-fill would put a Schedule 3 volume in STAND_TTT_M3 for an unfilled Schedule 1.
          Optional<Schedule1Response> r1 =
              schedule1Service.findStoredSchedule1(mill.millId(), year, EditableStatuses.NONE);
          if (r1.isPresent()) {
            s1.add(new CombinedSchedule1And2Section.Schedule1Entry(year, ctx, r1.get()));
          }
          // Existence, not emptiness: legacy iterated every stored Schedule 2 record and a saved
          // but blank one printed a row of dashes. The document alone cannot tell the two apart.
          if (schedule2Service.hasSchedule2(mill.millId(), year)) {
            Schedule2Response r2 =
                schedule2Service.getSchedule2(mill.millId(), year, EditableStatuses.NONE);
            s2.add(new CombinedSchedule1And2Section.Schedule2Entry(year, ctx, r2));
          }
        }
      }
      return combined.rows(s1, s2);
    }

    // ---- shared -------------------------------------------------------------------------------

    private void writeSection(CsvWriter csv, SectionBuilder section, List<String[]> rows)
        throws IOException {
      csv.writeBlankRow();
      csv.writeRow(section.title());
      csv.writeRow(section.header());
      if (rows.isEmpty()) {
        csv.writeRow(section.noDataRow(millNumbers, yearRange));
      } else {
        csv.writeRows(rows);
      }
      log.debug("{} rows written for section {}", rows.size(), section.title());
    }

    /** Legacy's pair walk: mill id ascending, then year ascending. */
    private void forEachPair(PairVisitor visitor) {
      for (MillSummary mill : millsByIdAscending()) {
        for (int year = selection.startYear(); year <= selection.endYear(); year++) {
          // Ids and years only, never a figure (AD-11).
          log.debug("Data extract reading mill {} year {}", mill.millId(), year);
          visitor.visit(mill, year);
        }
      }
    }

    /**
     * A child read that may legitimately find its parent gone: the build spans many short
     * transactions, so a summary or camp listed a moment ago can have been deleted since. Absence
     * becomes {@code null}, which every sub-table builder renders as its per-record marker; any
     * other failure still propagates and fails the extract as it should.
     */
    private static <T> T orNull(java.util.function.Supplier<T> read) {
      try {
        return read.get();
      } catch (ScheduleNotFoundException | CampNotFoundException e) {
        return null;
      }
    }

    private List<MillSummary> millsByIdAscending() {
      return mills.stream().sorted((a, b) -> Long.compare(a.millId(), b.millId())).toList();
    }

    private RowContext context(MillSummary mill, int year) {
      return new RowContext(
          millNumber(mill),
          String.valueOf(year),
          statusDescription(mill.millId(), year, MillYearTrackCodes::schedules1To10Code),
          String.valueOf(mill.millId()));
    }

    private RowContext silvicultureContext(MillSummary mill, int year) {
      return new RowContext(
          millNumber(mill),
          String.valueOf(year),
          statusDescription(mill.millId(), year, MillYearTrackCodes::schedule11Code),
          String.valueOf(mill.millId()));
    }

    private String statusDescription(
        long millId, int year, Function<MillYearTrackCodes, String> code) {
      MillYearTrackCodes row = statuses.get(key(millId, year));
      String value = row == null ? null : code.apply(row);
      if (value == null) {
        return RowContext.NO_STATUS;
      }
      return descriptions.computeIfAbsent(
          value, c -> millContextService.findStatusDescription(c).orElse(RowContext.NO_STATUS));
    }

    /** The mill NUMBER as stored, with the page's own fallback for a mill that has none. */
    private String millNumber(MillSummary mill) {
      String number = mill.millNumber();
      return number == null || number.isBlank() ? "Mill " + mill.millId() : number.strip();
    }
  }

  /**
   * The selected mills in the SELECTION's order, from the administrator's full list. A selected id
   * the list does not hold (no status xref, never enrolled) still gets a row shell so its reads run
   * and its number cell falls back to the id, rather than failing the whole extract as legacy's map
   * lookup would have.
   */
  private List<MillSummary> resolveMills(List<Long> millIds) {
    Map<Long, MillSummary> byId = new LinkedHashMap<>();
    for (MillSummary mill : millContextService.listMills(true, null)) {
      byId.put(mill.millId(), mill);
    }
    List<MillSummary> resolved = new ArrayList<>();
    for (Long id : millIds) {
      MillSummary mill = byId.get(id);
      resolved.add(mill != null ? mill : new MillSummary(id, null, null, null));
    }
    return resolved;
  }

  private static String key(long millId, int year) {
    return millId + "/" + year;
  }

  @FunctionalInterface
  private interface PairVisitor {
    void visit(MillSummary mill, int year);
  }
}
