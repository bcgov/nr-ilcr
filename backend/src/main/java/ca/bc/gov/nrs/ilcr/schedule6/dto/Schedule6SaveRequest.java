package ca.bc.gov.nrs.ilcr.schedule6.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The whole-document save body (S04 + S19 in one call).
 *
 * <p>Legacy's single Save posted the entire {@code Schedule6DO} — records AND the general comment —
 * through one {@code saveSchedule6} transaction ({@code Schedule6DAO.saveSchedule} :236-346). This
 * request restores that shape, retiring deviation (C): with rows always editable there is no
 * per-row Save to scope a single-record PUT to, and two separate requests would not be atomic
 * across the records and the comment the records replicate.
 *
 * <p>{@code records} must carry EVERY served row. An omitted row is a 400, not a silent skip — a
 * placeholder row (excluded from {@code roadRecords[]} on the read side) does NOT count as served,
 * so a lone-comment document with no real rows is still savable with an empty list.
 *
 * <p>{@code generalComments} is capped at 3500 — the legacy UI cap over the 4000-wide {@code
 * ROAD_MAINTENANCE_REPORT.COMMENTS}, a different and wider column than the per-record comment's 400
 * ({@link RoadRecordEntry#comments()}). Null or blank clears it.
 *
 * <p>The element-type {@code @NotNull} on {@code List<@NotNull RoadRecordEntry>} closes a {@code
 * null} ENTRY inside an otherwise-present list ({@code "records":[null]}) — without it, a null
 * element reaches {@code requireEveryServedRow} ({@link
 * ca.bc.gov.nrs.ilcr.schedule6.Schedule6Service#requireEveryServedRow}) and NPEs past the {@code
 * catch (DataAccessException)} there into a 500, instead of the clean 400 every other malformed
 * payload gets. The sibling {@link Schedule6CheckRequest.CheckEntry} closed the identical hole one
 * commit later ({@code List<@NotNull CheckEntry>}); this mirrors it.
 *
 * @param generalComments the schedule-level comment (null/blank clears)
 * @param records every served road record, each with its own revision token
 */
public record Schedule6SaveRequest(
    @Size(max = 3500, message = "{commentsMaxLengthErrorMsg}") String generalComments,
    @NotNull(message = "{missingRequiredFieldMsg}") @Valid List<@NotNull(message = "{missingRequiredFieldMsg}") RoadRecordEntry> records) {}
