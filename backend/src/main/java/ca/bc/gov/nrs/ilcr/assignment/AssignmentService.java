package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import ca.bc.gov.nrs.ilcr.assignment.dto.SubmitterAccount;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository;
import ca.bc.gov.nrs.ilcr.millcontext.MillYearContextNotFoundException;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Licensee accounts and their mill assignments (UC-USR-001/002). Reproduces the legacy user
 * administration behaviour over the two existing legacy tables: provision the account row on a
 * first assignment, toggle the assignment in place, and keep the account flag under its
 * deactivation guard.
 *
 * <p>Entities never leave this class — every method returns a wire record.
 *
 * <p>Three behaviours here look wrong and are deliberate. An account provisioned by a first
 * assignment is created INACTIVE while its holder can report normally, because the flag has never
 * gated access. Reactivating an assignment overwrites the previous period rather than appending to
 * it, because one pair admits only one row. And a mill that has no status cross-reference row
 * cannot be assigned at all, because that table, not the mill table, is what the foreign key points
 * at.
 */
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class AssignmentService {

  /**
   * The role stamped on a provisioned account. {@code THE.ILCR_ROLE} holds exactly ADMIN, AUDITOR
   * and LICENSEE, and a submitter is the licensee.
   */
  static final String SUBMITTER_ROLE = "LICENSEE";

  /** The only mill status that permits reporting; anything else is treated as closed. */
  private static final String MILL_STATUS_ACTIVE = "ACT";

  static final String MSG_ASSIGNED = "user.activate.mill";
  static final String MSG_ENDED = "user.deactivate.mill";
  static final String MSG_ALREADY_ASSIGNED = "user.not.associated.to.mill";
  static final String MSG_ACCOUNT_ACTIVATED = "user.activated";
  static final String MSG_ACCOUNT_DEACTIVATED = "user.inactivated";

  private final IlcrUserRepository users;
  private final MillUserXrefRepository assignments;
  private final MillContextRepository mills;

  /**
   * Creates the service over the two legacy tables plus the shared mill lookup.
   *
   * @param users the licensee account rows
   * @param assignments the submitter-to-mill cross-reference rows
   * @param mills the shared mill lookup, for the mill number, name and status
   */
  public AssignmentService(
      IlcrUserRepository users, MillUserXrefRepository assignments, MillContextRepository mills) {
    this.users = users;
    this.assignments = assignments;
    this.mills = mills;
  }

  /**
   * One mill's assignments, active first.
   *
   * @param millId the mill
   * @param includeEnded whether to include assignments that have been ended
   * @return the mill's assignments
   * @throws MillYearContextNotFoundException when the mill does not exist
   */
  @Transactional(readOnly = true)
  public List<MillSubmitter> listByMill(long millId, boolean includeEnded) {
    MillSummary mill = requireMill(millId);
    return assignments.findByMill(millId).stream()
        .filter(row -> includeEnded || row.isActive())
        .map(row -> toSubmitter(row, mill))
        .toList();
  }

  /**
   * One submitter's assignments, by ascending mill id.
   *
   * <p>Each row's mill is resolved individually; a row whose mill has vanished still renders, with
   * its mill number and name absent rather than the whole list failing.
   *
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @param includeEnded whether to include assignments that have been ended
   * @return the submitter's assignments
   */
  @Transactional(readOnly = true)
  public List<MillSubmitter> listByUser(String userGuid, boolean includeEnded) {
    return assignments.findByUser(userGuid).stream()
        .filter(row -> includeEnded || row.isActive())
        .map(row -> toSubmitter(row, mills.findSelectableMillById(row.millId()).orElse(null)))
        .toList();
  }

  /**
   * Assign a submitter to a mill, or bring their ended assignment back.
   *
   * <p>Provisions the account row first when the user has never held one, because the assignment's
   * foreign key requires a parent. Re-assigning a pair that is already active changes nothing and
   * comes back as a warning, which is also how a lost race is reported — two administrators
   * clicking at once produce one assignment and one warning, never a server error.
   *
   * @param millId the mill
   * @param userGuid the directory GUID of the submitter to assign
   * @param actingUser the acting administrator's username, for the audit columns
   * @return the assignment and the message describing what happened
   * @throws MillYearContextNotFoundException when the mill does not exist
   * @throws MillNotActiveException when an ended assignment is revived on a closed mill
   * @throws StaleRevisionException when a concurrent write changed the row first
   */
  @Transactional
  public Outcome assign(long millId, String userGuid, String actingUser) {
    MillSummary mill = requireMill(millId);

    if (users.findUser(userGuid).isEmpty()) {
      users.insertAccount(userGuid, SUBMITTER_ROLE, SubmitterAccount.INACTIVE, actingUser);
    }

    Optional<MillUserXrefEntity> existing = assignments.findAssignment(millId, userGuid);
    if (existing.isPresent()) {
      MillUserXrefEntity row = existing.get();
      if (row.isActive()) {
        return new Outcome(toSubmitter(row, mill), MSG_ALREADY_ASSIGNED);
      }
      requireMillActive(mill);
      if (assignments.reactivateAssignment(millId, userGuid, row.revisionCount(), actingUser)
          == 0) {
        throw new StaleRevisionException();
      }
      return new Outcome(reload(millId, userGuid, mill), MSG_ASSIGNED);
    }

    try {
      assignments.insertActiveAssignment(millId, userGuid, actingUser);
    } catch (DataIntegrityViolationException concurrentInsert) {
      // The composite key refused a second row for this pair, so another request assigned it
      // between the read above and this insert. The pair is assigned either way, which is what the
      // caller wanted, so report it the same way a duplicate click reports.
      return new Outcome(reload(millId, userGuid, mill), MSG_ALREADY_ASSIGNED);
    }
    return new Outcome(reload(millId, userGuid, mill), MSG_ASSIGNED);
  }

  /**
   * End a submitter's assignment, leaving the row in place with its active date cleared.
   *
   * @param millId the mill
   * @param userGuid the directory GUID of the submitter
   * @param revisionCount the revision the caller read
   * @param actingUser the acting administrator's username, for the audit columns
   * @return the ended assignment
   * @throws MillYearContextNotFoundException when the mill does not exist
   * @throws AssignmentNotFoundException when the pair has no assignment row
   * @throws StaleRevisionException when a concurrent write changed the row first
   */
  @Transactional
  public MillSubmitter end(long millId, String userGuid, int revisionCount, String actingUser) {
    MillSummary mill = requireMill(millId);
    assignments.findAssignment(millId, userGuid).orElseThrow(AssignmentNotFoundException::new);

    if (assignments.endAssignment(millId, userGuid, revisionCount, actingUser) == 0) {
      throw new StaleRevisionException();
    }
    return reload(millId, userGuid, mill);
  }

  /**
   * Flag a licensee's account active or inactive.
   *
   * <p>Deactivation is refused while any assignment is still active, evaluated live rather than
   * from anything read earlier, so a concurrent assignment cannot slip past the guard. Activating a
   * directory user who has never held an account creates it active — the opposite of what a first
   * mill assignment does, which is the legacy asymmetry preserved rather than corrected.
   *
   * @param userGuid the directory GUID ({@code custom:idp_user_id})
   * @param active true to flag the account active
   * @param actingUser the acting administrator's username, for the audit columns
   * @return the account and the message describing what happened
   * @throws AccountHasActiveMillsException when deactivating a user who still has active
   *     assignments
   */
  @Transactional
  public AccountOutcome setAccountActive(String userGuid, boolean active, String actingUser) {
    if (!active && assignments.hasActiveAssignment(userGuid)) {
      throw new AccountHasActiveMillsException(userGuid);
    }

    String flag = active ? SubmitterAccount.ACTIVE : SubmitterAccount.INACTIVE;
    if (users.setAccountActive(userGuid, flag, actingUser) == 0) {
      users.insertAccount(userGuid, SUBMITTER_ROLE, flag, actingUser);
    }

    IlcrUserEntity account = users.findUser(userGuid).orElseThrow(AssignmentNotFoundException::new);
    return new AccountOutcome(
        new SubmitterAccount(
            account.userGuid(), account.activeInd(), account.roleName(), account.revisionCount()),
        active ? MSG_ACCOUNT_ACTIVATED : MSG_ACCOUNT_DEACTIVATED);
  }

  private MillSummary requireMill(long millId) {
    return mills.findSelectableMillById(millId).orElseThrow(MillYearContextNotFoundException::new);
  }

  private void requireMillActive(MillSummary mill) {
    if (!MILL_STATUS_ACTIVE.equals(mill.millStatusCode())) {
      throw new MillNotActiveException();
    }
  }

  /** Re-read the row so the caller sees the dates and revision the database actually holds. */
  private MillSubmitter reload(long millId, String userGuid, MillSummary mill) {
    return assignments
        .findAssignment(millId, userGuid)
        .map(row -> toSubmitter(row, mill))
        .orElseThrow(AssignmentNotFoundException::new);
  }

  /**
   * Map one assignment row to its wire shape. {@code displayName} is left null: resolving it needs
   * the directory, which arrives with the submitter picker, and a stored copy was deliberately not
   * kept. The dates narrow to a date for display and must never be written back from this shape.
   */
  private MillSubmitter toSubmitter(MillUserXrefEntity row, MillSummary mill) {
    return new MillSubmitter(
        row.userGuid(),
        null,
        row.millId(),
        mill == null ? null : mill.millNumber(),
        mill == null ? null : mill.millName(),
        row.isActive() ? MillSubmitter.ACTIVE : MillSubmitter.ENDED,
        toDate(row.activeDate()),
        toDate(row.inactiveDate()),
        row.revisionCount());
  }

  private static LocalDate toDate(LocalDateTime value) {
    return value == null ? null : value.toLocalDate();
  }

  /**
   * An assignment write's result before its message is resolved to text.
   *
   * @param assignment the assignment as it now stands
   * @param messageKey the legacy bundle key describing the outcome
   */
  public record Outcome(MillSubmitter assignment, String messageKey) {}

  /**
   * An account write's result before its message is resolved to text.
   *
   * @param account the account as it now stands
   * @param messageKey the legacy bundle key describing the outcome
   */
  public record AccountOutcome(SubmitterAccount account, String messageKey) {}
}
