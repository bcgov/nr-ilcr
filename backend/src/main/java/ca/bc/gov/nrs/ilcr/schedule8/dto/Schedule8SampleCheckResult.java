package ca.bc.gov.nrs.ilcr.schedule8.dto;

import java.util.List;

/**
 * Check Status result for one sample (Story 14.6): the sample {@code id}, whether it {@code met}
 * all its Check-Status rules, and the per-field {@code issues} when it did not.
 *
 * <p>{@code sampleNumber} and {@code sampleLabel} (#461) say WHICH sample: the 1-based position
 * within its page and the legacy sample title ({@code "Sample # 1 - CMET"}) the sample list shows
 * for it — legacy prefixed each sample finding with its row number.
 *
 * @param id the sample's database id, for UI correlation
 * @param sampleNumber the 1-based positional ordinal within the page
 * @param sampleLabel the legacy sample title, the prefix of every notice for this sample
 * @param met whether the sample has no outstanding requirements
 * @param issues the outstanding requirements, in legacy emission order
 */
public record Schedule8SampleCheckResult(
    Integer id,
    int sampleNumber,
    String sampleLabel,
    boolean met,
    List<Schedule8CheckFieldIssue> issues) {}
