package ca.bc.gov.nrs.ilcr.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the two assignment row shapes and the state convention they encode — record semantics
 * only. The dates are mapped as {@link LocalDateTime} rather than a plain date because the
 * underlying Oracle {@code DATE} columns carry a time component in real data; whether that time
 * actually survives the database round-trip is proven by {@code AssignmentRepositoryIT}, not here.
 */
class AssignmentEntityTest {

  private static final String GUID = "B29C746A6BAF45B9844EE2E2984CA472";

  @Test
  @DisplayName("the account row carries every column through its accessor")
  void accountRowCarriesEveryColumn() {
    LocalDateTime stamp = LocalDateTime.of(2026, 8, 25, 9, 30, 15);

    IlcrUserEntity account =
        new IlcrUserEntity(GUID, "LICENSEE", "N", 3, "GRPASCUC", stamp, "SSCHOLEF", stamp);

    assertEquals(GUID, account.userGuid());
    assertEquals("LICENSEE", account.roleName());
    // 'N' on a working account is the legacy provisioning asymmetry, preserved deliberately.
    assertEquals("N", account.activeInd());
    assertEquals(3, account.revisionCount());
    assertEquals("GRPASCUC", account.entryUserid());
    assertEquals(stamp, account.entryTimestamp());
    assertEquals("SSCHOLEF", account.updateUserid());
    assertEquals(stamp, account.updateTimestamp());
  }

  @Test
  @DisplayName("an assignment with no inactive date is active, and keeps its time of day")
  void assignmentWithNoInactiveDateIsActive() {
    LocalDateTime activeAt = LocalDateTime.of(2026, 8, 25, 14, 5, 42);

    MillUserXrefEntity assignment =
        new MillUserXrefEntity(
            514L, GUID, activeAt, null, 0, "GRPASCUC", activeAt, "GRPASCUC", activeAt);

    assertEquals(514L, assignment.millId());
    assertEquals(GUID, assignment.userGuid());
    assertEquals(activeAt, assignment.activeDate());
    assertNull(assignment.inactiveDate());
    assertTrue(assignment.isActive());
    // The time of day is real in delivery data; the record must be able to carry it (the DB
    // round-trip itself is pinned in AssignmentRepositoryIT).
    assertEquals(14, assignment.activeDate().getHour());
    assertEquals(42, assignment.activeDate().getSecond());
  }

  @Test
  @DisplayName("an ended assignment carries an inactive date and no active date")
  void endedAssignmentClearsTheActiveDate() {
    LocalDateTime endedAt = LocalDateTime.of(2026, 8, 25, 16, 0);

    MillUserXrefEntity assignment =
        new MillUserXrefEntity(
            514L, GUID, null, endedAt, 1, "GRPASCUC", endedAt, "SSCHOLEF", endedAt);

    // Every ended row in delivery data carries a null active date. This pins only that the record
    // can represent that state — the 2.2 end-write's own tests must prove the clearing happens.
    assertNull(assignment.activeDate());
    assertEquals(endedAt, assignment.inactiveDate());
    assertFalse(assignment.isActive());
  }
}
