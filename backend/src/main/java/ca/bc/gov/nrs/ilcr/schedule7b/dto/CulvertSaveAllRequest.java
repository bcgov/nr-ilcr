package ca.bc.gov.nrs.ilcr.schedule7b.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The page-level Save body: EVERY culvert of the schedule in one request, mirroring legacy {@code
 * Schedule7bMB.save()} → {@code Schedule7bDAO.saveSchedule()}, which persisted the whole schedule
 * from a single button ({@code Schedule7bMB.java:208-230}). Each entry carries its id and its
 * {@code revisionCount}, and is persisted through the same per-row path as the single PUT, so the
 * validation, optimistic-lock and cost-upsert rules are identical.
 *
 * <p>An empty list is rejected rather than treated as a no-op save: a Save that silently did
 * nothing would read to the reporter as a successful save.
 *
 * @param culverts every culvert to save (non-empty; each entry validated)
 */
public record CulvertSaveAllRequest(
    @NotEmpty(message = "{missingRequiredFieldMsg}") @Valid List<Item> culverts) {

  /**
   * One entry of the batch: which culvert, and the values to store on it.
   *
   * @param culvertReportId the culvert id ({@code CULVERT_REPORT_ID}) to correct
   * @param culvert the entered fields + required {@code revisionCount}
   */
  public record Item(
      @NotNull(message = "{missingRequiredFieldMsg}") Long culvertReportId,
      @NotNull(message = "{missingRequiredFieldMsg}") @Valid CulvertRequest culvert) {}
}
