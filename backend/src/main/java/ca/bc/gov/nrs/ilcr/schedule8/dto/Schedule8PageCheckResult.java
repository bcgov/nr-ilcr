package ca.bc.gov.nrs.ilcr.schedule8.dto;

import java.util.List;

/**
 * Check Status result for one report page (Story 14.6): the page {@code id}, whether it {@code met}
 * all its rules (its own page-level fields AND every sample), the page-level {@code issues}
 * (Contact/Phone/TFL-or-Supply-Block/at-least-one-sample), and its per-sample {@code samples} results.
 */
public record Schedule8PageCheckResult(
    Integer id,
    boolean met,
    List<Schedule8CheckFieldIssue> issues,
    List<Schedule8SampleCheckResult> samples) {
}
