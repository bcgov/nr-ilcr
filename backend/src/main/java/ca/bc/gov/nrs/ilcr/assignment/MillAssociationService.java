package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.millmaintenance.AdminMillEntity;
import ca.bc.gov.nrs.ilcr.millmaintenance.MillMaintenanceException;
import ca.bc.gov.nrs.ilcr.millmaintenance.MillMaintenanceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mill-user associations as the MILL record works them (UC-MILL-001 S05-S09, S13). This is the
 * second of the two legacy screens over the same cross-reference, with deliberately different
 * semantics from the users-screen {@link AssignmentService#assign}:
 *
 * <ul>
 *   <li>An add creates the association INACTIVE — legacy hard-coded it — and confirms with the
 *       legacy "has been activated" sentence anyway, a pinned quirk kept for parity. Making the
 *       association effective is the separate per-row Activate action.
 *   <li>An add against ANY existing pair — active or ended, either legacy role — warns and writes
 *       nothing. It never revives an ended pair; that is the users-screen semantic, and here it
 *       would let the closed-mill activation block be skipped by re-adding instead of activating.
 *   <li>Mills resolve as TRACKED (a status cross-reference exists), not as schedule-selectable: the
 *       legacy panel worked on any selected mill, and this surface must reach every mill its own
 *       admin record can display.
 * </ul>
 *
 * <p>Writes go through the same single-writer repository as the users screen; this class adds no
 * SQL of its own beyond calling it. Entities never leave this class.
 */
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class MillAssociationService {

  private final MillUserXrefRepository assignments;
  private final MillMaintenanceRepository mills;
  private final AssignmentService accounts;

  /**
   * Creates the service over the shared cross-reference repository and the admin mill read.
   *
   * @param assignments the submitter-to-mill cross-reference rows (the table's only writer)
   * @param mills the tracked-mill read the admin surface resolves mills with
   * @param accounts the users-screen service, for the shared provisioning and closed-mill rules
   */
  public MillAssociationService(
      MillUserXrefRepository assignments,
      MillMaintenanceRepository mills,
      AssignmentService accounts) {
    this.assignments = assignments;
    this.mills = mills;
    this.accounts = accounts;
  }

  /**
   * One mill's associations as the mill record lists them — both states by default, because the
   * legacy panel showed inactive rows beside active ones (each with its own toggle).
   *
   * @param millId the mill
   * @param includeEnded whether ended/inactive associations are included
   * @return the mill's associations
   * @throws MillMaintenanceException 404 when the mill is unknown or not yet imported
   */
  @Transactional(readOnly = true)
  public List<MillSubmitter> listByMill(long millId, boolean includeEnded) {
    AdminMillEntity mill = requireTrackedMill(millId);
    return assignments.findByMill(millId).stream()
        .filter(row -> includeEnded || row.isActive())
        .map(row -> toSubmitter(row, mill))
        .toList();
  }

  /**
   * Add a user to the mill (S05): provision the account row when this is their first association,
   * then create the cross-reference INACTIVE. Any existing pair — in any state — warns and writes
   * nothing (S07, BR-08).
   *
   * @param millId the mill
   * @param userGuid the directory GUID of the user to associate
   * @param actingUser the acting administrator's username, for the audit columns
   * @return the association and the message describing what happened
   * @throws MillMaintenanceException 404 when the mill is unknown or not yet imported
   */
  @Transactional
  public AssignmentService.Outcome add(long millId, String userGuid, String actingUser) {
    AdminMillEntity mill = requireTrackedMill(millId);

    // Any existing pair warns, whatever its state or origin: legacy's duplicate check carried no
    // active/role predicate, and an ended pair is deliberately NOT revived here — the panel's
    // explicit Activate action is the revive path, and it is the one the closed-mill block guards.
    Optional<MillUserXrefEntity> existing = assignments.findAssignment(millId, userGuid);
    if (existing.isPresent()) {
      return new AssignmentService.Outcome(
          toSubmitter(existing.get(), mill), AssignmentService.MSG_ALREADY_ASSIGNED);
    }

    accounts.provisionAccountIfAbsent(userGuid, actingUser);

    try {
      assignments.insertInactiveAssignment(millId, userGuid, actingUser);
    } catch (DataIntegrityViolationException concurrentInsert) {
      // The composite key refused a second row, so another request associated the pair between the
      // read above and this insert — which is exactly the duplicate this surface warns about. Only
      // a duplicate leaves a row to find; any other integrity failure keeps its own error.
      return assignments
          .findAssignment(millId, userGuid)
          .map(
              row ->
                  new AssignmentService.Outcome(
                      toSubmitter(row, mill), AssignmentService.MSG_ALREADY_ASSIGNED))
          .orElseThrow(() -> concurrentInsert);
    }
    // The legacy quirk, kept: the confirmation for this INACTIVE insert is the same "has been
    // activated" key the true activate raises, because that is exactly what legacy served.
    return new AssignmentService.Outcome(
        reload(millId, userGuid, mill), AssignmentService.MSG_ASSIGNED);
  }

  /**
   * Activate an association (S09), refused while the mill is not active (S13, BR-02) — the same
   * rule and message the users screen enforces on a revive, because it is the same legacy rule.
   *
   * @param millId the mill
   * @param userGuid the directory GUID of the associated user
   * @param revisionCount the revision the caller read
   * @param actingUser the acting administrator's username, for the audit columns
   * @return the activated association
   * @throws MillMaintenanceException 404 when the mill is unknown or not yet imported
   * @throws AssignmentNotFoundException 404 when the pair has no association row
   * @throws MillNotActiveException 409 when the mill is not active — the association is unchanged
   * @throws ca.bc.gov.nrs.ilcr.exception.StaleRevisionException 409 when the association is already
   *     active, or a concurrent write changed the row first
   */
  @Transactional
  public AssignmentService.Outcome activate(
      long millId, String userGuid, int revisionCount, String actingUser) {
    final AdminMillEntity mill = requireTrackedMill(millId);
    MillUserXrefEntity row =
        assignments.findAssignment(millId, userGuid).orElseThrow(AssignmentNotFoundException::new);
    if (row.isActive()) {
      // The caller acted on a view that no longer matches the row; re-stamping would silently move
      // the activation date and invalidate every other administrator's revision for nothing.
      throw new StaleRevisionException();
    }

    // The status is re-read under the mill row's lock, not taken from the display read above: it is
    // the deactivation side's serialization point, and without it a mill closed between that read
    // and this write would end up closed with an active association (BR-01/BR-02). See
    // MillMaintenanceRepository#lockStatusCode.
    accounts.requireMillActive(mills.lockStatusCode(millId).orElse(null));

    if (assignments.reactivateAssignment(millId, userGuid, revisionCount, actingUser) == 0) {
      throw new StaleRevisionException();
    }
    return new AssignmentService.Outcome(
        reload(millId, userGuid, mill), AssignmentService.MSG_ASSIGNED);
  }

  /**
   * Deactivate an association (S08). No status guard of any kind — legacy checked nothing here, and
   * the per-user toggle is governed by no business rule.
   *
   * <p>It does still resolve the mill, which is one step stricter than the users screen's {@link
   * AssignmentService#end}: that one deliberately skips the mill lookup so an assignment can be
   * ended even when its mill is no longer resolvable, or the account-deactivation guard could never
   * be satisfied. The asymmetry is deliberate here and narrower than it looks. The
   * cross-reference's enabled foreign key points at {@code ILCR_MILL_STATUS_XREF}, so an
   * association cannot exist without a status row at all; the only state this refuses and {@code
   * end} allows is a mill whose status code is absent from {@code ILCR_MILL_STATUS_CODE}, which the
   * tracked-mill read inner-joins. Legacy's own Mills screen hid such mills too (the same inner
   * join), so refusing to operate its panel is the faithful behaviour — and the users screen
   * remains the escape hatch. Recorded as deviation (F) on Story 22.2.
   *
   * @param millId the mill
   * @param userGuid the directory GUID of the associated user
   * @param revisionCount the revision the caller read
   * @param actingUser the acting administrator's username, for the audit columns
   * @return the deactivated association
   * @throws MillMaintenanceException 404 when the mill is unknown or not yet imported
   * @throws AssignmentNotFoundException 404 when the pair has no association row
   * @throws ca.bc.gov.nrs.ilcr.exception.StaleRevisionException 409 when the association is already
   *     inactive, or a concurrent write changed the row first
   */
  @Transactional
  public AssignmentService.Outcome deactivate(
      long millId, String userGuid, int revisionCount, String actingUser) {
    AdminMillEntity mill = requireTrackedMill(millId);
    MillUserXrefEntity row =
        assignments.findAssignment(millId, userGuid).orElseThrow(AssignmentNotFoundException::new);
    if (!row.isActive()) {
      throw new StaleRevisionException();
    }

    if (assignments.endAssignment(millId, userGuid, revisionCount, actingUser) == 0) {
      throw new StaleRevisionException();
    }
    return new AssignmentService.Outcome(
        reload(millId, userGuid, mill), AssignmentService.MSG_ENDED);
  }

  private AdminMillEntity requireTrackedMill(long millId) {
    return mills.findById(millId).orElseThrow(MillMaintenanceException::millNotFound);
  }

  /** Re-read the row so the caller sees the dates and revision the database actually holds. */
  private MillSubmitter reload(long millId, String userGuid, AdminMillEntity mill) {
    return assignments
        .findAssignment(millId, userGuid)
        .map(row -> toSubmitter(row, mill))
        .orElseThrow(AssignmentNotFoundException::new);
  }

  /**
   * Map one association row to the shared wire shape. {@code displayName} stays null until the
   * directory join ships; the dates narrow to a date for display and must never be written back.
   *
   * <p>The status vocabulary has two values, so a row this surface has just created reports {@code
   * ENDED} carrying an {@code inactiveDate} that is really its creation date — it was never active
   * to be ended. That is legacy parity (its panel displayed the same two states) and a third value
   * would change a contract the users screen also consumes, so it stands as deviation (E) rather
   * than being fixed here. The mill page (Story 22.3) must therefore DERIVE the distinction it
   * needs: {@code activeDate == null && revisionCount == 0} means never activated, anything else
   * with a null {@code activeDate} was genuinely deactivated. Note also that {@code findByMill}
   * orders on {@code COALESCE(ACTIVE_DATE, INACTIVE_DATE)}, so never-activated rows sort by their
   * creation date against genuinely-ended rows' end dates.
   */
  private static MillSubmitter toSubmitter(MillUserXrefEntity row, AdminMillEntity mill) {
    return new MillSubmitter(
        row.userGuid(),
        null,
        row.millId(),
        mill.millNumber(),
        mill.millName(),
        row.isActive() ? MillSubmitter.ACTIVE : MillSubmitter.ENDED,
        toDate(row.activeDate()),
        toDate(row.inactiveDate()),
        row.revisionCount());
  }

  private static LocalDate toDate(LocalDateTime value) {
    return value == null ? null : value.toLocalDate();
  }
}
