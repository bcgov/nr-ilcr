package ca.bc.gov.nrs.ilcr.schedule6.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * The Schedule 6 Check Status result (AD-5/AD-12) — a read-only MET/ISSUES evaluation that mutates
 * nothing (VIEW-gated, not Draft-gated; the 2.6 precedent). Per record (placeholders excluded —
 * deviation (d)): the area type must be present, TFL records need a TFL number, TSA records need a
 * Supply Block, and cost must be non-null ({@code 0} is present — D2 precedent; volume is never
 * checked, commented out in legacy). The schedule-level pass ignores the area-type flag — the
 * {@code Schedule6CheckStatus.isScheduleValid} quirk, ported verbatim (unreachable in practice:
 * FLD-001 blocks area-type-less writes).
 *
 * <p>{@code messages} is the schedule-level banner: one {@code scheduleRequirementsMetMsg} ("All
 * requirements for this schedule have been met") when {@code outcome == "MET"}, empty otherwise —
 * and a MET schedule carries NO per-record results at all (the legacy pass branch never enters the
 * loop). The service emits bundle keys; the controller resolves the verbatim composed text (AD-8).
 *
 * @param outcome {@code "MET"} (every record passes; zero records is vacuously met) or
 *     {@code "ISSUES"}
 * @param messages the schedule-level message(s) — the MET banner when passing, empty otherwise
 * @param records the per-record results in {@code rowCounter} order — empty when the outcome is MET
 */
public record Schedule6CheckStatusResponse(
    String outcome,
    List<MessageInfo> messages,
    List<RoadRecordCheckResult> records) {
}
