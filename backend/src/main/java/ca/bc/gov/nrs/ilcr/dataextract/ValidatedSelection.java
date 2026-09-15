package ca.bc.gov.nrs.ilcr.dataextract;

import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import java.util.List;

/**
 * A selection that passed the whole accumulating gate, in the shape the generator consumes.
 *
 * <p>The mills arrive RESOLVED rather than as bare ids. The gate already has to read the
 * administrator's mill directory to refuse an id no picker could have offered, so it hands the
 * resolved rows on instead of making the generator read the same directory a second time within the
 * one request.
 *
 * @param startYear the opened start reporting year
 * @param endYear the opened end reporting year, {@code >= startYear}
 * @param mills the selected mills in request order, each resolved against the administrator's own
 *     list — distinct by id, because the ids they were resolved from were
 * @param schedules the distinct selected picker labels, in request order — membership in the eleven
 *     names is the generator's concern: an unknown label matches no schedule and is ignored, as
 *     legacy's builder lookup would have done
 */
public record ValidatedSelection(
    int startYear, int endYear, List<MillSummary> mills, List<String> schedules) {

  /**
   * The selected mill ids, in the same order — for the reads and the log lines that want the key
   * rather than the row.
   *
   * <p>Derived, never stored alongside {@link #mills()}: two lists that must agree are two lists
   * that can disagree.
   *
   * @return the selected mill ids in request order
   */
  public List<Long> millIds() {
    return mills.stream().map(MillSummary::millId).toList();
  }
}
