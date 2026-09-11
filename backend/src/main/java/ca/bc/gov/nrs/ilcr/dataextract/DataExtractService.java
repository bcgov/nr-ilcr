package ca.bc.gov.nrs.ilcr.dataextract;

import ca.bc.gov.nrs.ilcr.dataextract.dto.DataExtractRequest;
import ca.bc.gov.nrs.ilcr.exception.MultiMessageException;
import ca.bc.gov.nrs.ilcr.reporting.ReportYearGuard;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
   */
  private void validate(DataExtractRequest request) {
    List<String> keys = new ArrayList<>();
    List<Object[]> arguments = new ArrayList<>();

    Integer startYear = parseYear(request.startYear());
    Integer endYear = parseYear(request.endYear());

    if (startYear == null) {
      keys.add(MSG_FIELD_REQUIRED);
      arguments.add(new Object[] {LABEL_START_YEAR});
    }
    if (endYear == null) {
      keys.add(MSG_FIELD_REQUIRED);
      arguments.add(new Object[] {LABEL_END_YEAR});
    }
    if (nothingSelected(request.millIds())) {
      keys.add(MSG_MILLS_NOT_SELECTED);
      arguments.add(null);
    }
    if (nothingSelected(request.schedules())) {
      keys.add(MSG_SCHEDULES_NOT_SELECTED);
      arguments.add(null);
    }
    if (startYear != null && endYear != null && startYear > endYear) {
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
    reportYearGuard.requireOpenYear(request.startYear());
    reportYearGuard.requireOpenYear(request.endYear());
  }

  /**
   * Absent, blank and non-numeric all collapse to "no year was chosen" — the legacy control was a
   * dropdown of opened periods, so anything that is not a year means nothing was picked.
   */
  private static Integer parseYear(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Whether a picker carries no usable selection. Null and empty are the obvious cases; a list
   * holding only nulls or blank strings is counted as empty too, so a client sending {@code [""]}
   * cannot slip an empty selection past the gate and reach the generator with nothing to extract.
   */
  private static boolean nothingSelected(Collection<?> selection) {
    return selection == null || selection.stream().noneMatch(DataExtractService::isUsable);
  }

  private static boolean isUsable(Object value) {
    if (value == null) {
      return false;
    }
    return !(value instanceof CharSequence text) || !text.toString().isBlank();
  }
}
