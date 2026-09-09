package ca.bc.gov.nrs.ilcr.millcontext.dto;

/**
 * The two independent track status codes of one mill/year's {@code ILCR_MILL_REPORT_STATUS} row
 * (Story 15.1) — millcontext is the single owner of the track-status read (AD-9). The cheap
 * both-tracks shape: one row, one query, codes only. Consumers that need the resolved description
 * and display date use the Home working context instead ({@code WorkingContext}), which pays for
 * the lookups.
 *
 * @param schedules1To10Code the Schedules 1–10 code ({@code ILCR_MILL_REPORT_STATUS_CODE});
 *     nullable
 * @param schedule11Code the Schedule 11 code ({@code MILL_SILVICULTUR_STATUS_CODE}); nullable —
 *     legacy would NPE on a null here, Story 1.2 tolerates it
 */
public record TrackStatusCodes(String schedules1To10Code, String schedule11Code) {}
