package ca.bc.gov.nrs.ilcr.schedule7a.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Save-all request for the page-level Save (legacy {@code Schedule7aMB.save()} → {@code
 * saveSchedule7a(schedule7a)}, which persisted EVERY bridge report of the schedule in one
 * transaction). Each entry pairs a bridge id with the same {@link BridgeRequest} body the per-row
 * {@code PUT /bridges/{id}} takes, so both routes validate and persist through identical rules.
 *
 * <p>Validated with the {@link OnUpdate} group, so every entry must carry its {@code revisionCount}
 * — a save-all is a set of corrections to rows the caller has already been served, never an insert.
 *
 * @param bridges the bridges to save (at least one; an empty schedule has nothing to save, which
 *     the legacy {@code anyDataToSaveInfoMsg} branch reported rather than persisting)
 */
public record BridgeSaveAllRequest(
    @NotEmpty(message = "{missingRequiredFieldMsg}") @Valid List<Item> bridges) {

  /**
   * One bridge in a save-all: its id plus the entered fields.
   *
   * @param bridgeReportId the {@code BRIDGE_REPORT_ID} of the row being corrected
   * @param bridge the entered fields, including the row's {@code revisionCount}
   */
  public record Item(
      @NotNull(message = "{missingRequiredFieldMsg}") Long bridgeReportId,
      @NotNull(message = "{missingRequiredFieldMsg}") @Valid BridgeRequest bridge) {}
}
