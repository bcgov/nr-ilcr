package ca.bc.gov.nrs.ilcr.dataextract;

import ca.bc.gov.nrs.ilcr.dataextract.dto.DataExtractRequest;
import ca.bc.gov.nrs.ilcr.exception.MultiMessageException;
import ca.bc.gov.nrs.ilcr.reporting.ReportYearGuard;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single owner of Data Extract selection validation (AD-6). The controller only delegates.
 *
 * <p>Every failing check is collected and reported TOGETHER on one 400. That is not a client
 * convenience — it is the behaviour the legacy bean was written for. {@code
 * ExtractDataMB.extract():300-310} runs its three checks as unguarded sequential {@code if}s with
 * no early return, so it accumulates by construction; two of the three were simply unreachable
 * through the JSF lifecycle, so a user only ever saw one message from it. The rebuild has no phase
 * ordering to hide them behind, and reports all of them.
 *
 * <p>Because that is the point of the screen, the implementation must never grow an early return, a
 * first-match ladder, or a guard that throws its own single-message rejection mid-gate. The
 * openness check below runs strictly AFTER the gate for exactly that reason.
 */
@Service
@Transactional(readOnly = true)
public class DataExtractService {

  /**
   * The JSF framework required-field template, resolved with a field label. Both year messages
   * share this one key and differ only by their argument, which is why each key is raised with its
   * own argument array rather than a shared one.
   */
  private static final String MSG_FIELD_REQUIRED = "javax.faces.component.UIInput.REQUIRED";

  private static final String MSG_MILLS_NOT_SELECTED = "extractMillsNotSelectedMsg";
  private static final String MSG_SCHEDULES_NOT_SELECTED = "extractSchedulesNotSelectedMsg";
  private static final String MSG_YEAR_RANGE = "extractReportingYearsNotMetMsg";

  /**
   * Field labels for the required-year messages, in screen order. Verbatim from the legacy {@code
   * label} attributes ({@code extractData.xhtml:39}, {@code :55}) — NOT the visible {@code
   * p:outputLabel} text, which carries a trailing space the message never showed.
   */
  private static final String LABEL_START_YEAR = "Start Year";

  private static final String LABEL_END_YEAR = "End Year";

  /**
   * An all-digit value, optionally signed — a year that WAS supplied, whatever its magnitude. The
   * same ruling {@link ReportYearGuard} makes for the single-year report endpoints: {@code
   * 99999999999} overflows an int and so fails {@code Integer.valueOf} exactly like {@code
   * "not-a-year"}, but the caller demonstrably typed a number, and answering "Value is required"
   * for it is the confusion that guard's two-message split exists to prevent. Such a value skips
   * the required message and the range check here and is refused by the guard after the gate.
   */
  private static final Pattern SUPPLIED_NUMBER = Pattern.compile("[+-]?\\d+");

  private final ReportYearGuard reportYearGuard;

  /**
   * Creates the extract service.
   *
   * @param reportYearGuard the shared opened-period check, reused rather than re-querying the year
   *     list
   */
  public DataExtractService(ReportYearGuard reportYearGuard) {
    this.reportYearGuard = reportYearGuard;
  }

  /**
   * Validate a selection and produce its extract.
   *
   * @param request the raw selection
   * @throws MultiMessageException 400 carrying every failing check together
   * @throws DataExtractUnavailableException 501 — the selection is valid but the CSV writer is not
   *     built yet
   */
  public void generate(DataExtractRequest request) {
    validate(request);
    throw new DataExtractUnavailableException();
  }

  /**
   * The accumulating gate. One collection point, walked in screen order: the two year pickers, the
   * two multi-selects, then the cross-field range comparison.
   *
   * <p>The range check comes last and is SKIPPED rather than guessed when either year is unusable.
   * It needs two parseable years, so it is mutually exclusive with the two required-year messages
   * and can never join them — treating a blank year as zero would invent a range failure legacy
   * never reported (its own {@code parseInt} was unguarded and threw instead).
   *
   * @return the selection the generator will consume: both years as ints, both lists distinct and
   *     stripped of unusable entries — the "validated parameter object" the CSV story builds on
   */
  ValidatedSelection validate(DataExtractRequest request) {
    List<String> keys = new ArrayList<>();
    List<Object[]> arguments = new ArrayList<>();

    YearValue startYear = parseYear(request.startYear());
    YearValue endYear = parseYear(request.endYear());

    if (startYear.missing()) {
      keys.add(MSG_FIELD_REQUIRED);
      arguments.add(new Object[] {LABEL_START_YEAR});
    }
    if (endYear.missing()) {
      keys.add(MSG_FIELD_REQUIRED);
      arguments.add(new Object[] {LABEL_END_YEAR});
    }
    List<Long> millIds = distinctUsable(request.millIds());
    if (millIds.isEmpty()) {
      keys.add(MSG_MILLS_NOT_SELECTED);
      arguments.add(null);
    }
    List<String> schedules = distinctUsable(request.schedules());
    if (schedules.isEmpty()) {
      keys.add(MSG_SCHEDULES_NOT_SELECTED);
      arguments.add(null);
    }
    if (startYear.year() != null && endYear.year() != null && startYear.year() > endYear.year()) {
      keys.add(MSG_YEAR_RANGE);
      arguments.add(null);
    }

    if (!keys.isEmpty()) {
      throw new MultiMessageException(
          HttpStatus.BAD_REQUEST, keys, arguments.toArray(new Object[0][]));
    }

    // Only past the gate, so a year that is merely unopened can never displace one of the messages
    // above. Unreachable through the UI — both pickers list opened periods only — but a plain JSON
    // body has no such guarantee, and without this an unopened year would reach the generator and
    // surface as an unhandled error, reading as a system fault rather than a bad selection.
    //
    // Deliberately NOT accumulating (review D-R1, 2026-09-11): the guard reports the FIRST unopened
    // year only, with the shipped "Report Year" text that names neither picker. Legacy had no
    // server-side openness check at all — its dropdown of opened periods was the whole guard — so
    // the minimal addition is the legacy-closest one. Recorded as deviation (I).
    int start = reportYearGuard.requireOpenYear(request.startYear());
    int end = reportYearGuard.requireOpenYear(request.endYear());
    return new ValidatedSelection(start, end, millIds, schedules);
  }

  /**
   * Absent, blank and non-numeric all collapse to "no year was chosen" — the legacy control was a
   * dropdown of opened periods, so anything that is not a year means nothing was picked. An
   * all-digit value too large for an int is the one exception: it WAS supplied, so it is neither
   * missing nor comparable, and is left for the openness guard to refuse.
   */
  private static YearValue parseYear(String value) {
    if (value == null || value.isBlank()) {
      return YearValue.MISSING;
    }
    String supplied = value.trim();
    try {
      return new YearValue(Integer.valueOf(supplied), false);
    } catch (NumberFormatException e) {
      return SUPPLIED_NUMBER.matcher(supplied).matches() ? YearValue.SUPPLIED : YearValue.MISSING;
    }
  }

  /**
   * The usable entries of a picker's selection, in first-seen order and without repeats. Null and
   * blank entries are dropped, so a client sending {@code [""]} cannot slip an empty selection past
   * the gate and reach the generator with nothing to extract; repeats are collapsed so a doubled id
   * cannot double the rows the generator produces (review D-R2 — legacy's checkbox menu could send
   * neither, so no message exists for either and none is invented).
   */
  private static <T> List<T> distinctUsable(Collection<T> selection) {
    if (selection == null) {
      return List.of();
    }
    return selection.stream().filter(DataExtractService::isUsable).distinct().toList();
  }

  private static boolean isUsable(Object value) {
    if (value == null) {
      return false;
    }
    return !(value instanceof CharSequence text) || !text.toString().isBlank();
  }

  /**
   * A parsed year picker value: {@code year} when comparable, else whether the field was blank
   * ({@code missing}) or held a number too large to compare (supplied but not missing).
   */
  private record YearValue(Integer year, boolean missing) {
    static final YearValue MISSING = new YearValue(null, true);
    static final YearValue SUPPLIED = new YearValue(null, false);
  }

  /**
   * A selection that passed the whole gate, in the shape the generator consumes. Pinned here so the
   * CSV story extends this record rather than re-reading the raw request.
   *
   * @param startYear the opened start reporting year
   * @param endYear the opened end reporting year, {@code >= startYear}
   * @param millIds the distinct selected mill ids, in request order
   * @param schedules the distinct selected picker labels, in request order — membership in the
   *     eleven names is the generator's concern: an unknown label matches no schedule and is
   *     ignored, as legacy's builder lookup would have done (review D-R2)
   */
  record ValidatedSelection(
      int startYear, int endYear, List<Long> millIds, List<String> schedules) {}
}
