package ca.bc.gov.nrs.ilcr.schedule5.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import java.util.List;

/**
 * The Schedule 5 Check Status result (AD-5/AD-12, BR-08) — a read-only readiness evaluation that
 * mutates nothing. {@code VIEW_SCHEDULE}-gated and NOT Draft-gated (the 2.6 precedent, {@code
 * deferred-work.md:23}), so a Submitted mill can still be checked.
 *
 * <p><strong>Exactly eight conditions per camp, transcribed and not extended</strong> ({@code
 * Schedule5CheckStatus.java:13-97}): the four descriptors — {@code campName} (a TRIMMED null/empty
 * test, {@code CoreUtil.isNullOrEmptyString(name, true)} at :17) and {@code
 * roadDistanceToOperatingArea} / {@code sizeOfCamp} / {@code associatedCampVolume} (pure {@code !=
 * null}, so a stored {@code 0} PASSES — the D2 precedent, {@code deferred-work.md:135}) — plus the
 * four Other Camp/Access sub-list conditions (any row with a null/empty description, any row with a
 * null cost; {@code CheckStatusUtil.java:113-120, 132-139}, both false for an EMPTY list).
 *
 * <p><strong>The twelve category cost/volume fields are NOT tested, and that is parity, not an
 * omission</strong> — legacy's conditions and its ~65 lines of message emission are commented out
 * in three places ({@code Schedule5CheckStatus.java:21-34, 60-82}; {@code
 * Schedule5MB.java:360-424}), so re-enabling them would be a behaviour change (deviation (D)).
 * {@code isolatedCamp} is not tested either, even though Save requires it (deviation (E)).
 *
 * <p><strong>Outcomes.</strong> {@code isSchedule5Valid} ANDs over the camps and returns {@code
 * true} for an EMPTY list ({@code Schedule5CheckStatus.java:89-97}), so a mill/year with zero camps
 * is vacuously {@code MET}. On {@code MET} the schedule banner is emitted ALONE and {@code camps}
 * is empty (deviation (C)); on {@code ISSUES} {@code messages} is empty and every camp reports —
 * passing camps with their met message, failing ones with their {@code Value Required} lines.
 *
 * <p>There is no {@code severity} field. The shipped house message envelope is {@code
 * dto.base.MessageInfo(key, text)} and nothing in the backend or frontend carries a severity today;
 * a client derives it from {@code outcome} and each camp's {@code requirementsMet}, which is what
 * keeps it from drifting out of sync with them (8.3's "severity follows the outcome, never
 * hardcoded" patch).
 *
 * @param outcome {@code "MET"} (every camp passes; zero camps is vacuously met) or {@code "ISSUES"}
 * @param messages the schedule-level banner — the single {@code scheduleRequirementsMetMsg} when
 *     passing, empty otherwise
 * @param camps the per-camp results in {@code CAMP_REPORT_ID} order — EMPTY when the outcome is MET
 */
public record Schedule5CheckStatusResponse(
    String outcome, List<MessageInfo> messages, List<CampCheckResult> camps) {}
