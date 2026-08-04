package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentOutcome;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillLabel;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submitter↔mill assignment maintenance (Story 2.2). Serves a mill's assignments, assigns a submitter
 * (idempotent: an already-active pair is a no-op warning; an ended pair is reactivated by inserting a
 * fresh active row — D4), and soft-ends an active assignment (AD-11 — never deletes). Writes are
 * optimistic-locked on {@code REVISION_COUNT} and stamped with the acting admin's id (AD-9); the
 * ILCR_ADMIN gate is enforced at the controller.
 *
 * <p>Identity note: {@code userGuid} is the submitter's FAM {@code custom:idp_user_id} (Story 1.0),
 * carried in the request; {@code actingUser} is the admin's {@code custom:idp_username} for audit. The
 * {@code USER_DISPLAY_NAME}/{@code IDP_USERNAME} name snapshot (Q5) is populated by the picker flow
 * (Story 2.3) — null here.
 */
@Service
@RequiredArgsConstructor
public class MillAssignmentService {

  private static final String MSG_ACTIVATED = "user.activate.mill";
  private static final String MSG_DEACTIVATED = "user.deactivate.mill";
  private static final String MSG_DUPLICATE = "user.not.associated.to.mill";

  private final MillUserProfileXrefRepository repository;

  /** A mill's assignments (active + ended), newest first, labelled with the mill number/name. */
  @Transactional(readOnly = true)
  public List<MillSubmitter> listByMill(long millId) {
    MillLabel label = repository.findMillLabel(millId).orElse(null);
    return repository.findByMill(millId).stream().map(e -> toSubmitter(e, label)).toList();
  }

  /**
   * Assign a submitter to a mill. If already active → no-op {@code user.not.associated.to.mill}
   * warning (also the outcome of a concurrent-insert race caught at the unique index). Otherwise a new
   * active row is inserted (reactivating any ended history) → {@code user.activate.mill}.
   */
  @Transactional
  public AssignmentOutcome assign(long millId, String userGuid, String actingUser) {
    MillLabel label = repository.findMillLabel(millId).orElse(null);

    Optional<MillUserProfileXrefEntity> active = repository.findActive(millId, userGuid);
    if (active.isPresent()) {
      return new AssignmentOutcome(toSubmitter(active.get(), label), MSG_DUPLICATE);
    }
    try {
      repository.insertActive(repository.nextId(), userGuid, millId, null, null, actingUser);
    } catch (DataIntegrityViolationException race) {
      // Concurrent assign tripped the D4 active-unique index — translate to the friendly warning
      // (never a raw 500), returning the row the other admin created.
      return repository.findActive(millId, userGuid)
          .map(e -> new AssignmentOutcome(toSubmitter(e, label), MSG_DUPLICATE))
          .orElseThrow(() -> race);
    }
    MillUserProfileXrefEntity created = repository.findActive(millId, userGuid)
        .orElseThrow(() -> new IllegalStateException("assignment not found immediately after insert"));
    return new AssignmentOutcome(toSubmitter(created, label), MSG_ACTIVATED);
  }

  /**
   * Soft-end the active assignment for a (mill, user) pair (set END_DATE). 404 when none is active;
   * 409 when the caller's {@code expectedRevision} is stale (AD-9).
   */
  @Transactional
  public AssignmentOutcome end(long millId, String userGuid, int expectedRevision, String actingUser) {
    if (repository.findActive(millId, userGuid).isEmpty()) {
      throw new AssignmentNotFoundException();
    }
    int updated = repository.endActive(millId, userGuid, expectedRevision, actingUser);
    if (updated == 0) {
      throw new AssignmentStaleException(); // active existed but the revision moved under us
    }
    MillLabel label = repository.findMillLabel(millId).orElse(null);
    MillUserProfileXrefEntity ended = repository.findByMill(millId).stream()
        .filter(e -> userGuid.equals(e.userGuid()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("ended assignment not found after update"));
    return new AssignmentOutcome(toSubmitter(ended, label), MSG_DEACTIVATED);
  }

  private static MillSubmitter toSubmitter(MillUserProfileXrefEntity e, MillLabel label) {
    return new MillSubmitter(
        e.userGuid(), e.userDisplayName(), e.idpUsername(),
        e.millId(), label == null ? null : label.millNumber(), label == null ? null : label.millName(),
        e.endDate() == null ? "ACTIVE" : "ENDED",
        e.startDate(), e.endDate(), e.revisionCount());
  }
}
