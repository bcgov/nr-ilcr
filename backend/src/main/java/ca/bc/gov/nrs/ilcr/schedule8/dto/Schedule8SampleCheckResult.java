package ca.bc.gov.nrs.ilcr.schedule8.dto;

import java.util.List;

/**
 * Check Status result for one sample (Story 14.6): the sample {@code id}, whether it {@code met} all
 * its Check-Status rules, and the per-field {@code issues} when it did not.
 */
public record Schedule8SampleCheckResult(
    Integer id, boolean met, List<Schedule8CheckFieldIssue> issues) {
}
