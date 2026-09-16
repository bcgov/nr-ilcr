package ca.bc.gov.nrs.ilcr.dataextract.dto;

import java.util.List;

/**
 * The Data Extract selection (AD-12). The extract defines its OWN parameters — a reporting-year
 * range, a set of mills and a set of schedules — and never reads the Home mill/year context (AR20).
 *
 * <p><strong>The two years are raw {@code String}s on purpose, and carry no validation
 * annotations.</strong> {@code DataExtractService} is the single owner of the validation (AD-6),
 * and it must report EVERY failing check on one response. A typed component or a {@code @NotNull}
 * constraint makes Jackson or the bean validator reject the request before the service runs,
 * producing a 400 whose {@code detail} is a framework message and whose {@code messages} array is
 * absent — which silently removes the accumulation this screen exists to provide. Missing, blank
 * and non-numeric must all collapse to the one verbatim required-field message.
 *
 * @param startYear the raw start reporting year (may be absent, blank or non-numeric)
 * @param endYear the raw end reporting year (may be absent, blank or non-numeric)
 * @param millIds the selected mills, as {@code MillSummary.millId} values
 * @param schedules the selected schedule names verbatim from the picker, {@code "Schedule 1"} …
 *     {@code "Schedule 11"} — eleven options, because Schedule 7 is one choice covering 7A and 7B
 */
public record DataExtractRequest(
    String startYear, String endYear, List<Long> millIds, List<String> schedules) {}
