package ca.bc.gov.nrs.ilcr.schedule11.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Save request for the Schedule 11 page-level Save — legacy {@code Schedule11MB.save()} → {@code
 * Schedule11DAO.saveSchedule11}, which in ONE transaction deleted every row flagged for deletion
 * and updated every other row ({@code Schedule11DAO.java:135-147}, commit {@code :150}).
 *
 * <p>{@code locations} carries the rows the user EDITED, each with the {@code revisionCount} it was
 * edited against (validated with the {@link OnUpdate} group — a save is a set of corrections to
 * rows already served, never an insert; Add stays its own immediate POST). Legacy re-stamped every
 * row, but it had no optimistic lock: with ours, an untouched row another session changed would
 * refuse the whole save (Story 26.2 review D-R1, deviation (E)). {@code deletedIds} carries the
 * rows the user flagged with Delete since the last save. Either list may be empty, not both.
 *
 * @param locations the edited rows to update (possibly empty when the save only deletes)
 * @param deletedIds the location ids flagged for deletion (possibly empty)
 */
public record LocationSaveAllRequest(
    @NotNull(message = "{missingRequiredFieldMsg}") List<@NotNull(message = "{missingRequiredFieldMsg}") @Valid Item> locations,
    @NotNull(message = "{missingRequiredFieldMsg}") List<@NotNull(message = "{missingRequiredFieldMsg}") Long> deletedIds) {

  /**
   * One location in a save: its id plus the entered fields.
   *
   * @param basicSilvicultureReportId the {@code BASIC_SILVICULTURE_REPORT_ID} being corrected
   * @param location the entered fields, including the row's {@code revisionCount}
   */
  public record Item(
      @NotNull(message = "{missingRequiredFieldMsg}") Long basicSilvicultureReportId,
      @NotNull(message = "{missingRequiredFieldMsg}") @Valid SilvicultureLocationRequest location) {}
}
