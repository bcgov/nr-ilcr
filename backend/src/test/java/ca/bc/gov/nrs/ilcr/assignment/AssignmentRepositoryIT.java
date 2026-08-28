package ca.bc.gov.nrs.ilcr.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Executes every read method on {@link IlcrUserRepository} and {@link MillUserXrefRepository}
 * against the real snapshot, because a {@code @Query} that only ever compiles proves nothing: a
 * misspelled column, a broken named-parameter binding, the Oracle {@code NUMBER}-to-{@code boolean}
 * conversion behind {@code hasActiveAssignment}, or a wrong {@code ORDER BY} would all stay green
 * under the schema tripwire alone and fail at Story 2.2's first runtime call.
 *
 * <p>This is also where the {@code DATE}-carries-time mapping is proven for real: rows are inserted
 * with an explicit time of day and read back through the repositories, pinning that {@link
 * LocalDateTime} preserves the stored instant end-to-end ({@code AssignmentEntityTest} can only pin
 * the record shape in memory).
 */
class AssignmentRepositoryIT extends AbstractOracleIT {

  /** Seeded mill-status-xref ids (V2 and V5) — the FK gate every assignment must pass. */
  private static final long MILL_514 = 514L;

  private static final long MILL_522 = 522L;

  private static final String ROLE = "LICENSEE";

  // Distinct from every other fixture's GUIDs; lexicographic order A < B < C < D is load-bearing
  // for the USER_GUID tiebreak assertion below.
  private static final String GUID_A = "RVWA0000000000000000000000000001";
  private static final String GUID_B = "RVWB0000000000000000000000000001";
  private static final String GUID_C = "RVWC0000000000000000000000000001";
  private static final String GUID_D = "RVWD0000000000000000000000000001";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private IlcrUserRepository users;
  @Autowired private MillUserXrefRepository assignments;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "DELETE FROM THE.ILCR_MILL_USER_XREF WHERE USER_GUID IN (?, ?, ?, ?)",
        GUID_A,
        GUID_B,
        GUID_C,
        GUID_D);
    jdbcTemplate.update(
        "DELETE FROM THE.ILCR_USER WHERE USER_GUID IN (?, ?, ?, ?)",
        GUID_A,
        GUID_B,
        GUID_C,
        GUID_D);
  }

  @Test
  @DisplayName("findUser maps every column, keeps the audit time of day, and is empty for unknowns")
  void findUserMapsTheRowAndIsEmptyForUnknowns() {
    LocalDateTime stamp = LocalDateTime.of(2026, 8, 25, 9, 30, 15);
    insertAccount(GUID_A, "N", stamp);

    Optional<IlcrUserEntity> found = users.findUser(GUID_A);

    assertTrue(found.isPresent());
    IlcrUserEntity account = found.orElseThrow();
    assertEquals(GUID_A, account.userGuid());
    assertEquals(ROLE, account.roleName());
    assertEquals("N", account.activeInd());
    assertEquals(0, account.revisionCount());
    assertEquals("TRIPWIRE", account.entryUserid());
    // Oracle DATE carries a time component; the LocalDateTime mapping must not lose it.
    assertEquals(stamp, account.entryTimestamp());

    assertTrue(users.findUser(GUID_B).isEmpty());
  }

  @Test
  @DisplayName(
      "findAssignment round-trips the stored time of day and is empty for unassigned pairs")
  void findAssignmentRoundTripsTheStoredInstant() {
    LocalDateTime activeAt = LocalDateTime.of(2026, 8, 25, 14, 5, 42);
    insertAccount(GUID_A, "Y", activeAt);
    insertAssignment(MILL_514, GUID_A, activeAt, null);

    Optional<MillUserXrefEntity> found = assignments.findAssignment(MILL_514, GUID_A);

    assertTrue(found.isPresent());
    MillUserXrefEntity assignment = found.orElseThrow();
    assertEquals(GUID_A, assignment.userGuid());
    assertEquals(activeAt, assignment.activeDate());
    assertNull(assignment.inactiveDate());
    assertTrue(assignment.isActive());

    assertTrue(assignments.findAssignment(MILL_522, GUID_A).isEmpty());
  }

  @Test
  @DisplayName("hasActiveAssignment is true only while some row has a null INACTIVE_DATE")
  void hasActiveAssignmentTracksTheLiveXrefState() {
    LocalDateTime when = LocalDateTime.of(2026, 8, 25, 8, 0, 0);
    insertAccount(GUID_A, "Y", when);
    insertAccount(GUID_B, "Y", when);
    insertAssignment(MILL_514, GUID_A, when, null); // active
    insertAssignment(MILL_514, GUID_B, null, when); // ended

    // Proves the CASE-from-DUAL result genuinely converts to boolean in both polarities — the
    // conversion, not just the SQL, is what the deactivate guard will lean on in 2.2.
    assertTrue(assignments.hasActiveAssignment(GUID_A));
    assertFalse(assignments.hasActiveAssignment(GUID_B));
    assertFalse(assignments.hasActiveAssignment(GUID_C));
  }

  @Test
  @DisplayName("findByMill orders active first, then most-recently-dated, then USER_GUID")
  void findByMillAppliesTheImposedDeterministicOrder() {
    LocalDateTime older = LocalDateTime.of(2026, 8, 1, 10, 0, 0);
    LocalDateTime newer = LocalDateTime.of(2026, 8, 20, 10, 0, 0);
    insertAccount(GUID_A, "Y", older);
    insertAccount(GUID_B, "Y", newer);
    insertAccount(GUID_C, "Y", older);
    insertAccount(GUID_D, "Y", newer);
    insertAssignment(MILL_514, GUID_A, older, null); // active, older
    insertAssignment(MILL_514, GUID_B, newer, null); // active, newest
    insertAssignment(MILL_514, GUID_C, null, older); // ended
    insertAssignment(MILL_514, GUID_D, newer, null); // active, ties B on date -> GUID decides

    List<String> order =
        assignments.findByMill(MILL_514).stream()
            .map(MillUserXrefEntity::userGuid)
            // Ignore the shared Story 5.7 canonical-submitter fixture (R__70 associates it to
            // every mill incl. 514); this test asserts the ORDER of its own seeded rows.
            .filter(guid -> !guid.equals(CANONICAL_SUBMITTER_GUID))
            .toList();

    assertEquals(List.of(GUID_B, GUID_D, GUID_A, GUID_C), order);
  }

  @Test
  @DisplayName("findByUser orders by mill id ascending, matching the legacy user-centric read")
  void findByUserOrdersByMillIdAscending() {
    LocalDateTime when = LocalDateTime.of(2026, 8, 25, 8, 0, 0);
    insertAccount(GUID_A, "Y", when);
    insertAssignment(MILL_522, GUID_A, when, null);
    insertAssignment(MILL_514, GUID_A, when, null);

    List<Long> order =
        assignments.findByUser(GUID_A).stream().map(MillUserXrefEntity::millId).toList();

    assertEquals(List.of(MILL_514, MILL_522), order);
  }

  private void insertAccount(String userGuid, String activeInd, LocalDateTime stamp) {
    jdbcTemplate.update(
        """
        INSERT INTO THE.ILCR_USER
          (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
          VALUES (?, ?, ?, 0, 'TRIPWIRE', ?, 'TRIPWIRE', ?)
        """,
        userGuid,
        ROLE,
        activeInd,
        Timestamp.valueOf(stamp),
        Timestamp.valueOf(stamp));
  }

  private void insertAssignment(
      long millId, String userGuid, LocalDateTime activeDate, LocalDateTime inactiveDate) {
    jdbcTemplate.update(
        """
        INSERT INTO THE.ILCR_MILL_USER_XREF
          (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
          VALUES (?, ?, ?, ?, 0, 'TRIPWIRE', SYSDATE, 'TRIPWIRE', SYSDATE)
        """,
        millId,
        userGuid,
        activeDate == null ? null : Timestamp.valueOf(activeDate),
        inactiveDate == null ? null : Timestamp.valueOf(inactiveDate));
  }
}
