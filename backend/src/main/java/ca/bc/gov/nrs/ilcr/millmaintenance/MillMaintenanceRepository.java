package ca.bc.gov.nrs.ilcr.millmaintenance;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * SQL for the mill administration surface (UC-MILL-001), Spring Data JDBC with explicit queries
 * (AD-3). No decisions live here.
 *
 * <p>This is the only writer of {@code THE.ILCR_MILL_STATUS_XREF}. {@code THE.MILL} is read and
 * never written: legacy's Mills screen never wrote it either, and importing a mill means inserting
 * the cross-reference for a ministry mill that already exists, not creating a mill.
 *
 * <p>Every column is listed explicitly rather than selected with a star, so a snapshot that drifts
 * from delivery fails here instead of surfacing as a null field far downstream. The audit quartet
 * and {@code REVISION_COUNT} are stamped on every write: all five are NOT NULL in delivery with no
 * DEFAULT and no trigger that fills them (the one trigger on this table copies the row into its
 * audit shadow), so an omission is ORA-01400 in production.
 *
 * <p>{@code SYSDATE} rather than {@code SYSTIMESTAMP} because both timestamp columns are {@code
 * DATE} in delivery.
 */
@org.springframework.stereotype.Repository
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public interface MillMaintenanceRepository extends Repository<AdminMillEntity, Long> {

  /**
   * The ILCR mill search (S11). Mirrors legacy's non-import query: {@code MILL} inner-joined to its
   * cross-reference and the cross-reference inner-joined to the status code table
   * (MillDAO.java:41), so a mill with no cross-reference — or one whose code has no code-table row
   * — is invisible here and reachable only through the import list.
   *
   * <p>Each criterion is optional via the standard Oracle optional-bind idiom, which reproduces
   * legacy's conditionally-appended predicates. Mill number matches exactly and name matches a
   * contained substring, both legacy-faithful; the name match is case-insensitive on BOTH sides,
   * which legacy was not — it upper-cased only the bind parameter (MillDAO.java:48 against :73), so
   * a mixed-case stored name could not be found by any spelling. Ordering is legacy's single
   * ascending mill-number key.
   *
   * @param millNumber exact mill number, or null for any
   * @param millName substring of the mill name with LIKE metacharacters already escaped, or null
   * @param statusCode exact status code, or null for any
   * @return the matching mills, ordered by mill number
   */
  @Query(
      """
      SELECT m.MILL_ID, m.MILL_NUMBER, m.MILL_NAME, x.ILCR_MILL_STATUS_CODE,
             c.DESCRIPTION AS STATUS_DESCRIPTION, x.HEAD_OFFICE_CONTACT_IND,
             x.HEAD_OFFICE_CONTACT_ID, x.DIVISION_CONTACT_ID, x.REVISION_COUNT
        FROM THE.MILL m
        JOIN THE.ILCR_MILL_STATUS_XREF x ON x.ILCR_MILL_STATUS_XREF_ID = m.MILL_ID
        JOIN THE.ILCR_MILL_STATUS_CODE c ON c.ILCR_MILL_STATUS_CODE = x.ILCR_MILL_STATUS_CODE
       WHERE (:millNumber IS NULL OR m.MILL_NUMBER = :millNumber)
         AND (:millName IS NULL
              OR UPPER(m.MILL_NAME) LIKE '%' || UPPER(:millName) || '%' ESCAPE '\\')
         AND (:statusCode IS NULL OR x.ILCR_MILL_STATUS_CODE = :statusCode)
       ORDER BY m.MILL_NUMBER
      """)
  List<AdminMillEntity> search(
      @Param("millNumber") Long millNumber,
      @Param("millName") String millName,
      @Param("statusCode") String statusCode);

  /**
   * The ministry mills available to import (BR-04). An anti-join: {@code MILL} rows with no
   * cross-reference at all, which is legacy's {@code x.ilcr_mill_status_xref_id is null} over a
   * left outer join (MillDAO.java:40, :58-65).
   *
   * <p>No status criterion, because these mills have no status — legacy's import dialog offered
   * only number and name (mills.xhtml:239-248). No effective/expiry-date filter either: both
   * columns exist on {@code MILL} and legacy ignored them, so an expired ministry mill is
   * importable.
   *
   * @param millNumber exact mill number, or null for any
   * @param millName escaped substring of the mill name, or null
   * @return the importable mills, ordered by mill number
   */
  @Query(
      """
      SELECT m.MILL_ID, m.MILL_NUMBER, m.MILL_NAME
        FROM THE.MILL m
        LEFT JOIN THE.ILCR_MILL_STATUS_XREF x ON x.ILCR_MILL_STATUS_XREF_ID = m.MILL_ID
       WHERE x.ILCR_MILL_STATUS_XREF_ID IS NULL
         AND (:millNumber IS NULL OR m.MILL_NUMBER = :millNumber)
         AND (:millName IS NULL
              OR UPPER(m.MILL_NAME) LIKE '%' || UPPER(:millName) || '%' ESCAPE '\\')
       ORDER BY m.MILL_NUMBER
      """)
  List<ImportableMillEntity> findImportable(
      @Param("millNumber") Long millNumber, @Param("millName") String millName);

  /**
   * One tracked mill's administration detail. Same joins as {@link #search}, so a mill that the
   * search cannot see cannot be addressed directly either.
   *
   * @param millId the mill id
   * @return the mill, or empty when untracked or unknown
   */
  @Query(
      """
      SELECT m.MILL_ID, m.MILL_NUMBER, m.MILL_NAME, x.ILCR_MILL_STATUS_CODE,
             c.DESCRIPTION AS STATUS_DESCRIPTION, x.HEAD_OFFICE_CONTACT_IND,
             x.HEAD_OFFICE_CONTACT_ID, x.DIVISION_CONTACT_ID, x.REVISION_COUNT
        FROM THE.MILL m
        JOIN THE.ILCR_MILL_STATUS_XREF x ON x.ILCR_MILL_STATUS_XREF_ID = m.MILL_ID
        JOIN THE.ILCR_MILL_STATUS_CODE c ON c.ILCR_MILL_STATUS_CODE = x.ILCR_MILL_STATUS_CODE
       WHERE m.MILL_ID = :millId
      """)
  Optional<AdminMillEntity> findById(@Param("millId") long millId);

  /**
   * Whether a ministry mill row exists at all, tracked or not. Separates "no such mill" from
   * "already imported" so the import path can answer 404 and 409 distinctly.
   *
   * @param millId the mill id
   * @return true when {@code THE.MILL} holds the row
   */
  @Query(
      "SELECT CASE WHEN EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = :millId) "
          + "THEN 1 ELSE 0 END FROM DUAL")
  boolean millExists(@Param("millId") long millId);

  /**
   * The contacts selectable for a mill's head-office and division slots (BR-09) — the contacts of
   * the mill's own client location, reached through the {@code (CLIENT_NUMBER, CLIENT_LOCN_CODE)}
   * pair on {@code MILL}.
   *
   * <p>Deliberately NOT filtered by {@code BUS_CONTACT_CODE}. Legacy has a purpose-built query that
   * filters on the ILCR business-contact code (MillDAO.java:256-277) and it has no callers
   * anywhere; the screen instead walked the whole client-location association in the view. The
   * unfiltered list is what an administrator actually saw, so it is what this serves. Ordering by
   * name is added — legacy's association came back unordered, which is not a behaviour worth
   * reproducing.
   *
   * @param millId the mill id
   * @return the selectable contacts, ordered by name
   */
  @Query(
      """
      SELECT cc.CLIENT_CONTACT_ID, cc.CONTACT_NAME
        FROM THE.MILL m
        JOIN THE.CLIENT_CONTACT cc ON cc.CLIENT_NUMBER = m.CLIENT_NUMBER
                                  AND cc.CLIENT_LOCN_CODE = m.CLIENT_LOCN_CODE
       WHERE m.MILL_ID = :millId
       ORDER BY cc.CONTACT_NAME
      """)
  List<ContactOptionEntity> findContactOptions(@Param("millId") long millId);

  /**
   * Whether a contact belongs to a mill's client location — the BR-09 membership test.
   *
   * <p>Legacy never ran it: its save resolved the posted id against the whole of {@code
   * CLIENT_CONTACT} with no location predicate (MillDAO.java:226-234), so any client's contact
   * could be stored against any mill. The rule was stated and unenforced.
   *
   * @param millId the mill id
   * @param clientContactId the posted contact id
   * @return true when the contact is on the mill's client location
   */
  @Query(
      """
      SELECT CASE WHEN EXISTS (
               SELECT 1
                 FROM THE.MILL m
                 JOIN THE.CLIENT_CONTACT cc ON cc.CLIENT_NUMBER = m.CLIENT_NUMBER
                                           AND cc.CLIENT_LOCN_CODE = m.CLIENT_LOCN_CODE
                WHERE m.MILL_ID = :millId AND cc.CLIENT_CONTACT_ID = :clientContactId)
             THEN 1 ELSE 0 END FROM DUAL
      """)
  boolean contactBelongsToMill(
      @Param("millId") long millId, @Param("clientContactId") long clientContactId);

  /**
   * Whether the mill still has an active user assignment — the BR-01 deactivation guard, evaluated
   * live against the table when the deactivation runs. The guard and the status write are separate
   * read-committed statements, so an assignment activated between them can still slip through; that
   * residual window is a known, repo-wide trait of the mill⇄assignment guards (the assignment side
   * has the mirror image) and is orders of magnitude narrower than what it replaces.
   *
   * <p>Active means {@code INACTIVE_DATE IS NULL}, the convention {@code
   * MillUserXrefEntity.isActive()} owns and the only one the delivery data supports (the two dates
   * are strictly mutually exclusive across all real rows).
   *
   * <p>Legacy asked a different question and could get it wrong twice over: it scanned the licensee
   * and auditor lists already loaded into the screen, so a change made after the mill was selected
   * was invisible, and those lists silently dropped any assignment whose user no longer held a
   * matching directory role — letting a mill with a live active assignment be closed.
   *
   * @param millId the mill id
   * @return true when at least one assignment is active
   */
  @Query(
      "SELECT CASE WHEN EXISTS (SELECT 1 FROM THE.ILCR_MILL_USER_XREF "
          + "WHERE ILCR_MILL_ID = :millId AND INACTIVE_DATE IS NULL) THEN 1 ELSE 0 END FROM DUAL")
  boolean hasActiveAssignment(@Param("millId") long millId);

  /**
   * Create a ministry mill's ILCR cross-reference (BR-03). Every value is legacy's, including the
   * comment literal and the initial state: a mill arrives CLOSED with its head-office indicator
   * set, and both contact slots empty (MillDAO.java:181-197).
   *
   * @param millId the mill id, which becomes the cross-reference id unchanged
   * @param statusCode the initial status
   * @param headOfficeContactInd the initial indicator
   * @param comments the provenance note
   * @param user the acting administrator
   * @return rows inserted
   */
  @Modifying
  @Query(
      """
      INSERT INTO THE.ILCR_MILL_STATUS_XREF
        (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND,
         HEAD_OFFICE_CONTACT_ID, DIVISION_CONTACT_ID, COMMENTS, REVISION_COUNT,
         ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (:millId, :statusCode, :headOfficeContactInd, NULL, NULL, :comments, 0,
              :user, SYSDATE, :user, SYSDATE)
      """)
  int insertStatusXref(
      @Param("millId") long millId,
      @Param("statusCode") String statusCode,
      @Param("headOfficeContactInd") String headOfficeContactInd,
      @Param("comments") String comments,
      @Param("user") String user);

  /**
   * Move a mill's status, guarded by the revision it was read at. Returns 0 when another
   * administrator has written the row since, which the service turns into a conflict rather than an
   * overwrite — legacy had no such guard and let the last write win.
   *
   * @param millId the mill id
   * @param statusCode the new status
   * @param revisionCount the revision the caller read
   * @param user the acting administrator
   * @return rows updated: 1 on success, 0 when stale
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_MILL_STATUS_XREF
         SET ILCR_MILL_STATUS_CODE = :statusCode,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_STATUS_XREF_ID = :millId
         AND REVISION_COUNT = :revisionCount
      """)
  int updateStatusCode(
      @Param("millId") long millId,
      @Param("statusCode") String statusCode,
      @Param("revisionCount") int revisionCount,
      @Param("user") String user);

  /**
   * Save the head-office indicator and both contact selections (S01). A null contact id clears its
   * column, which is what an empty selection did in legacy.
   *
   * <p>Touches only these three columns plus the audit pair: the status code and the import comment
   * are not the save panel's to change, and legacy's save left both alone.
   *
   * @param millId the mill id
   * @param headOfficeContactInd the indicator
   * @param headOfficeContactId the head-office contact, or null to clear
   * @param divisionContactId the division contact, or null to clear
   * @param revisionCount the revision the caller read
   * @param user the acting administrator
   * @return rows updated: 1 on success, 0 when stale
   */
  @Modifying
  @Query(
      """
      UPDATE THE.ILCR_MILL_STATUS_XREF
         SET HEAD_OFFICE_CONTACT_IND = :headOfficeContactInd,
             HEAD_OFFICE_CONTACT_ID = :headOfficeContactId,
             DIVISION_CONTACT_ID = :divisionContactId,
             REVISION_COUNT = REVISION_COUNT + 1,
             UPDATE_USERID = :user,
             UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_STATUS_XREF_ID = :millId
         AND REVISION_COUNT = :revisionCount
      """)
  int updateContacts(
      @Param("millId") long millId,
      @Param("headOfficeContactInd") String headOfficeContactInd,
      @Param("headOfficeContactId") Long headOfficeContactId,
      @Param("divisionContactId") Long divisionContactId,
      @Param("revisionCount") int revisionCount,
      @Param("user") String user);
}
