package ca.bc.gov.nrs.ilcr.dataextract;

import ca.bc.gov.nrs.ilcr.dataextract.csv.ScheduleSelection;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillYearTrackCodes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The title block's {@code Data Verified: Yes|No} verdict (BR-05).
 *
 * <p>{@code Yes} only when EVERY (mill, year) pair in the selection has a status row whose EVERY
 * applicable track code is {@code V}. The applicable tracks follow from the schedules chosen: any
 * of Schedules 1–10 (Schedule 7 included) brings in the Schedules 1–10 track; Schedule 11 brings in
 * the silviculture track; both kinds selected require both. A missing row, a null code or any
 * non-{@code V} code on an applicable track is {@code No}.
 *
 * <p>This is the rule the legacy code was written FOR, not the one it shipped. Legacy OR'd the two
 * tracks when both were selected, printed {@code Yes} when no status rows existed at all (an empty
 * list contains no {@code false}), and never counted Schedule 7 toward the 1–10 track because it
 * tested a label its own detail list never held. All three defeat the rule's stated meaning, so the
 * rebuild implements the meaning. The verdict never blocks the extract — it is one line of text.
 */
public final class DataVerifiedRule {

  private static final String VERIFIED = "V";

  private DataVerifiedRule() {}

  /**
   * Whether every selected (mill, year) is Verified on every applicable track.
   *
   * @param millIds the selected mills
   * @param fromYear the first selected year, inclusive
   * @param toYear the last selected year, inclusive
   * @param schedules the selected schedules, deciding which tracks apply
   * @param statusRows the status rows found for the selection; a pair with no row is unverified
   * @return true only when nothing in the selection is short of Verified
   */
  public static boolean allVerified(
      Collection<Long> millIds,
      int fromYear,
      int toYear,
      ScheduleSelection schedules,
      Collection<MillYearTrackCodes> statusRows) {
    boolean needs1To10 = schedules.includesSchedules1To10();
    boolean needs11 = schedules.includesSchedule11();
    if (!needs1To10 && !needs11) {
      // Nothing selected names a track: there is nothing that could be verified.
      return false;
    }
    Map<String, MillYearTrackCodes> byPair = new HashMap<>();
    for (MillYearTrackCodes row : statusRows) {
      byPair.put(key(row.millId(), row.year()), row);
    }
    for (long millId : millIds) {
      for (int year = fromYear; year <= toYear; year++) {
        MillYearTrackCodes row = byPair.get(key(millId, year));
        if (row == null) {
          return false;
        }
        if (needs1To10 && !VERIFIED.equals(row.schedules1To10Code())) {
          return false;
        }
        if (needs11 && !VERIFIED.equals(row.schedule11Code())) {
          return false;
        }
      }
    }
    return true;
  }

  private static String key(long millId, int year) {
    return millId + "/" + year;
  }
}
