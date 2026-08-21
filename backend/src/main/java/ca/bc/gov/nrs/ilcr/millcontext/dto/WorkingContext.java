package ca.bc.gov.nrs.ilcr.millcontext.dto;

/**
 * The resolved Home working context for a selected (mill, reporting year) pair (Story 1.2, pinned
 * wire contract AD-12 — unchanged from the Story 1.1 pin). Returned by {@code GET
 * /api/v1/mill-context}; the Home page (1.3) confirms the selection with it and the banner (1.4)
 * renders it.
 *
 * <p>Either track status may be null: no {@code THE.ILCR_MILL_REPORT_STATUS} row for the pair (S07)
 * nulls both; a NULL code column nulls just that track (legacy would NPE on a NULL silviculture
 * code — tolerated here, deviation recorded in Story 1.2). Under global Jackson {@code non_null} a
 * null status is omitted from the JSON. {@code millNumber}/{@code millName} may be null (legacy
 * nullable columns — see the 1.1 contract nullability note).
 *
 * <p>Story 1.3 amendment (AD-12): {@code message} carries the SUC-001 confirmation ({@code
 * dataSavedSuccesfullyInfoMsg} → "Data saved successfully") on EVERY 200, mirroring how Schedule
 * 1's save sources its success text server-side (AD-8). Semantic caveat: the endpoint is used both
 * to CONFIRM a Home save (1.3) and to LOAD the banner (1.4); the message is present on every 200
 * but the frontend must only DISPLAY it after an explicit Save — 1.4's banner load ignores it.
 * Backward-compatible because the change is purely ADDITIVE — existing 1.2 consumers just see one
 * extra field they do not read. (Note {@code message} is populated on every 200, so the global
 * {@code non_null} setting never omits it; compatibility does not rest on omission.)
 *
 * @param millId the mill id ({@code THE.MILL.MILL_ID})
 * @param millNumber the mill number as a display string; may be null
 * @param millName the mill name; may be null
 * @param reportYear the selected reporting year
 * @param schedules1To10Status the Schedules 1–10 track status; null when none (S07)
 * @param schedule11Status the Schedule 11 (silviculture) track status; null when none
 * @param millViewable false when the mill's {@code ILCR_MILL_STATUS_XREF} status is not {@code ACT}
 *     (closed mills stay selectable — S06 — but schedule pages block viewing)
 * @param message the SUC-001 confirmation (key + server-resolved verbatim text); Story 1.3 (AC7)
 */
public record WorkingContext(
    long millId,
    String millNumber,
    String millName,
    int reportYear,
    TrackStatus schedules1To10Status,
    TrackStatus schedule11Status,
    boolean millViewable,
    MessageInfo message) {}
