package ca.bc.gov.nrs.ilcr.schedule8.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * The Schedule 8 Check Status result (Story 14.6, BR-07) — read-only (AD-5), mutates nothing.
 * {@code outcome} is {@code "MET"} only when every in-scope page (and its samples) passes, else
 * {@code "ISSUES"}. {@code messages} carries the schedule-level banner (the SUC-003 all-met message on
 * MET, empty on ISSUES). {@code pages} is the per-page → per-sample → per-field breakdown. Serves both
 * the all-pages sweep and the single-page scope (the single-page result carries one page entry).
 */
public record Schedule8CheckStatusResponse(
    String outcome, List<MessageInfo> messages, List<Schedule8PageCheckResult> pages) {
}
