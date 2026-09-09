package ca.bc.gov.nrs.ilcr.originalvalue;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * The single server-side authority on original-value metadata: whether a track exposes it at all,
 * and what each exposed field's submitted value and tooltip text are (UC-CHK-005/UC-CHK-010 BR-04).
 *
 * <p>Ported from legacy {@code UserSessionMB.isSubmit(boolean):541-554} and the {@code CoreUtil}
 * comparison family ({@code CoreUtil.java:988-1026}). One component, consulted by every schedule
 * service, never inlined — the same discipline AD-9 imposes on {@link
 * ca.bc.gov.nrs.ilcr.security.ScheduleEditability}.
 *
 * <h2>The gate is a STATUS gate, not a permission gate</h2>
 *
 * <p>The natural misreading of this feature is that it is something an administrator sees. It is
 * not. Legacy's {@code isSubmit()} asks only whether <em>the track has left Draft</em>:
 *
 * <pre>{@code
 * if (!D.equals(valueOfByValue(millReportStatus.getMillReportStatusCode())) && !isSchedule11) return true;
 * if (!D.equals(valueOfByValue(millReportStatus.getMillSilviculturStatusCode())) && isSchedule11) return true;
 * return false;
 * }</pre>
 *
 * <p>So a Licensee viewing a Submitted report sees the indicators too, and a Verified report shows
 * them as well. Visibility is therefore never coupled to the {@code editable} flag: an
 * administrator is read-only at Draft (Story 16.1) yet sees no indicators there, and read-only at
 * nothing else.
 *
 * <p>The gate is evaluated <b>per track</b>, exactly as the editability matrix is: Schedule 11
 * reads {@code MILL_SILVICULTUR_STATUS_CODE} and Schedules 1&ndash;10 read {@code
 * ILCR_MILL_REPORT_STATUS_CODE}. Legacy proved the split by passing a boolean; callers here supply
 * their own track's status code, so no track parameter is needed and no caller can read the wrong
 * column by omission.
 *
 * <h2>Where the submitted value comes from</h2>
 *
 * <p>Nowhere in application code. Each schedule's snapshot is an Oracle view {@code THE.*_S_VW}
 * whose body is, uniformly across all fourteen of them:
 *
 * <pre>{@code
 * SELECT <business cols> FROM (
 *   SELECT <business cols>,
 *          ROW_NUMBER() OVER (PARTITION BY <pk> ORDER BY <aud_id> DESC) recordnumber
 *     FROM <aud table> WHERE record_state_code = 'S')
 *  WHERE recordnumber = 1
 * }</pre>
 *
 * <p>i.e. the latest audit row stamped {@code 'S'}. {@code RECORD_STATE_CODE} is written by a
 * per-table database trigger from the pair {@code (ILCR_REPORT_CATEGORY.CATEGORY_STATE_CODE, that
 * track's mill status)}, and only {@code (D, S)} yields {@code 'S'} — so the snapshot is the
 * licensee's submission and stays stable across every later ministry correction, which is what
 * UC-CHK-011 S07 requires. Nothing here needs to preserve it; the database already does.
 *
 * <h2>Why this class does not compare anything</h2>
 *
 * <p>A submitted value is exposed whenever one is on file, <em>not</em> only when it currently
 * differs. That is deliberate and load-bearing: legacy re-rendered the indicator from the field's
 * own {@code change} event ({@code schedule1.xhtml:113} names the indicator's panel group in its
 * {@code f:ajax render} list), so the comparison runs against the operator's <b>unsaved</b> value.
 * A server that omitted currently-equal values would leave the page with nothing to compare a fresh
 * edit against, and the indicator could never appear until after a save. The comparison therefore
 * lives once on the client, mirroring {@code CoreUtil}'s rules; this class owns the gate, the
 * canonical value and the text.
 */
@Component
public class OriginalValues {

  /** Draft — the mill is still entering its own data, and no indicator renders. */
  static final String DRAFT = "D";

  /**
   * The legacy bundle had no key for this: {@code "Original Submission Value: "} was hardcoded in
   * all seven {@code common/converter/ILCROriginalValue*Converter.java} classes, and {@code
   * messages.properties}, {@code validation.properties} and {@code print.properties} contain no
   * occurrence of the word at all. It lives in our bundle so the text is resolved server-side and
   * rendered verbatim (AD-8) rather than hardcoded in twelve React pages.
   */
  static final String LABEL_KEY = "originalSubmissionValueLabel";

  private final MessageSource messages;

  public OriginalValues(MessageSource messages) {
    this.messages = messages;
  }

  /**
   * Whether a track exposes original values — legacy {@code isSubmit()}: any status other than
   * Draft.
   *
   * <p><b>A null or unresolvable status yields {@code false}</b>, which is a deliberate deviation.
   * Legacy resolved an unknown code to an {@code INVALID} enum member through a swallowed {@code
   * NullPointerException} ({@code Constant.java:477-492}), and {@code !D.equals(INVALID)} is true —
   * so a report with no status row would have had indicators forced on, against a snapshot that
   * cannot exist, flagging every populated field. This fails closed instead, matching the same cell
   * Story 16.1 pinned for editability (null status &rarr; read-only).
   */
  public boolean exposesOriginalValues(String trackStatusCode) {
    return trackStatusCode != null && !DRAFT.equals(trackStatusCode);
  }

  /**
   * Start collecting one object's original-value map for the given track status.
   *
   * <p>Callers build unconditionally and let {@link Builder#build()} decide: at Draft it returns
   * {@code null} however many values were offered, so no service needs its own status branch and no
   * service can forget one.
   */
  public Builder forTrack(String trackStatusCode) {
    return new Builder(exposesOriginalValues(trackStatusCode), label());
  }

  private String label() {
    return messages.getMessage(LABEL_KEY, null, Locale.CANADA);
  }

  /**
   * Collects the exposed fields of one document or row. Not thread-safe and not reusable — one
   * instance per object mapped, created through {@link OriginalValues#forTrack(String)}.
   */
  public static final class Builder {

    private final boolean exposed;
    private final String label;
    private final Map<String, OriginalValue> values = new LinkedHashMap<>();

    private Builder(boolean exposed, String label) {
      this.exposed = exposed;
      this.label = label;
    }

    /**
     * Offer one field's submitted value under its own camelCase name (AD-12: domain names, never
     * the legacy JSF control-id spellings such as {@code isVolumeOriginalVal} or {@code
     * standingTreetoLoadedTruckVolOB}).
     *
     * <p>A null submitted value is <b>not</b> recorded. That absence is the wire representation of
     * legacy's {@code originalVal == null}, on which the client shows the indicator whenever the
     * current value is non-empty — the branch that governs roughly half of the live rows, which
     * carry no {@code 'S'} snapshot at all. A snapshot row that exists with a null column and no
     * snapshot row at all are indistinguishable in legacy too, so one representation is correct
     * rather than lossy.
     *
     * @param field the owning object's own field name, e.g. {@code volume}, {@code comments}
     * @param submitted the value from the {@code *_S_VW} snapshot; null when none is on file
     * @param format the legacy converter rule for this field's tooltip
     */
    public Builder put(String field, Object submitted, OriginalValueFormat format) {
      if (!exposed || submitted == null) {
        return this;
      }
      values.put(
          field, new OriginalValue(canonical(submitted), label + " " + format.render(submitted)));
      return this;
    }

    /**
     * The collected map, or {@code null} when the track is at Draft — which, with
     * {@code @JsonInclude(NON_NULL)} on the carrying record, leaves the Draft-time payload
     * byte-identical to what it was before this feature existed.
     *
     * <p>An empty map is returned when the track is beyond Draft but nothing was on file: that is a
     * real, distinct state (the page must still evaluate the "value added since submission" branch
     * for every field), so it is never collapsed to {@code null}.
     */
    public Map<String, OriginalValue> build() {
      return exposed ? Map.copyOf(values) : null;
    }

    /**
     * The comparison form: unformatted, ungrouped, and with a {@link BigDecimal}'s trailing zeros
     * stripped so {@code 600.0} and {@code 600} compare equal — the client-side mirror of {@code
     * CoreUtil.isBigDecimalOriginalVal}, which compared rounded values rather than text.
     */
    private static String canonical(Object submitted) {
      if (submitted instanceof BigDecimal decimal) {
        return decimal.stripTrailingZeros().toPlainString();
      }
      if (submitted instanceof Boolean flag) {
        return flag ? "Y" : "N";
      }
      return submitted.toString();
    }
  }
}
