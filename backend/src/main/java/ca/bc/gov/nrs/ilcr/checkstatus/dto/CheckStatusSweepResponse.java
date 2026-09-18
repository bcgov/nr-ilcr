package ca.bc.gov.nrs.ilcr.checkstatus.dto;

/**
 * The Check Status sweep (Story 15.1, {@code GET /api/v1/check-status}): every schedule's
 * validation re-run on demand against persisted data, partitioned by track, with both track
 * statuses (UC-CHK-001 BR-02/BR-03/BR-04/BR-05). Strictly read-only — no data or status changes
 * (FR5, AD-5).
 *
 * <p><strong>Not a port — legacy has no sweep.</strong> {@code CheckStatusMB} holds eleven
 * independent bean properties and the PAGE drives evaluation tab by tab; nothing in legacy combines
 * verdicts except the eleven-term {@code &&} in {@code submitReport():258-262}, which collects
 * nothing. Aggregating into one response is a recorded structural deviation; each verdict's MEANING
 * is each schedule's own (AD-5), and rides here verbatim as its {@code verdict}.
 *
 * <p>Two deliberate departures are recorded on the endpoint: it is a {@code GET} where the twelve
 * per-schedule siblings are {@code POST /check-status} (correct for a read-only sweep, but a
 * departure from the "actions are POST sub-resources" convention), and {@code /api/v1/check-status}
 * is a new top-level resource — the root beneath which Stories 15.3/17/18 add {@code /submit},
 * {@code /verify}, {@code /set-to-draft} and {@code /set-to-submit}.
 *
 * @param millId the validated mill id
 * @param year the validated reporting year
 *     <p>Story 15.3 added ONE field, additively (AD-12): {@code schedules1To10.canSubmit}, the
 *     server-decided "is Submit offered to this caller" flag ({@code TrackCheckResult#canSubmit}).
 *     {@code schedule11} carries none until Epic 26.
 * @param schedules1To10 the Schedules 1–10 track: eleven verdicts (7A and 7B separately), the
 *     {@code ILCR_MILL_REPORT_STATUS_CODE} and {@code canSubmit}
 * @param schedule11 the Schedule 11 track: one verdict and the {@code MILL_SILVICULTUR_STATUS_CODE}
 */
public record CheckStatusSweepResponse(
    long millId, int year, TrackCheckResult schedules1To10, TrackCheckResult schedule11) {}
