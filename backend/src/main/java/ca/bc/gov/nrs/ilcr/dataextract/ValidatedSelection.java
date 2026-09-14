package ca.bc.gov.nrs.ilcr.dataextract;

import java.util.List;

/**
 * A selection that passed the whole accumulating gate, in the shape the generator consumes.
 *
 * @param startYear the opened start reporting year
 * @param endYear the opened end reporting year, {@code >= startYear}
 * @param millIds the distinct selected mill ids, in request order
 * @param schedules the distinct selected picker labels, in request order — membership in the eleven
 *     names is the generator's concern: an unknown label matches no schedule and is ignored, as
 *     legacy's builder lookup would have done
 */
public record ValidatedSelection(
    int startYear, int endYear, List<Long> millIds, List<String> schedules) {}
