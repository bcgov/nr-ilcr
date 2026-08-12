package ca.bc.gov.nrs.ilcr.schedule5.api;

import ca.bc.gov.nrs.ilcr.schedule5.dto.CampRequest;
import ca.bc.gov.nrs.ilcr.schedule5.dto.OnUpdate;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageDocument;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageSaveRequest;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 5 (Camp and Access Expenses) API contract (controller + api-interface split, the
 * established idiom). The interface owns the request mapping and parameter contract; {@code
 * Schedule5Controller} implements it and adds authorization.
 *
 * <p>Story 7.1 is the READ side only. The write/copy/delete endpoints and {@code POST
 * /check-status} belong to 7.2, and the two expense sub-pages to 7.4; they realize the document
 * pinned by {@link Schedule5Response} rather than re-pinning it.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — like Schedules 6 and 11, unlike
 * Schedule 1's typed params — because AC4 pins the verbatim legacy ERR-003 message for missing,
 * blank, AND non-numeric values, which a typed required {@code @RequestParam} cannot produce (it
 * yields the generic missing-parameter / type-mismatch 400s). Parsing and the guard chain live in
 * {@code MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule5")
public interface Schedule5Api {

  /**
   * Get the Schedule 5 camp-and-access-expenses document for a mill and reporting year.
   *
   * <p>Guards (verbatim legacy text resolved from the message bundle): missing/blank/non-numeric
   * params → 400 UC-SCH5-001 ERR-003; mill not {@code ACT} for the year → 409 UC-SCH5-001 ERR-004;
   * no {@code ILCR_MILL_REPORT_STATUS} row → 404 UC-SCH5-001 ERR-005; no {@code VIEW_SCHEDULE} →
   * 403. A mill/year with zero camps is a valid 200 with {@code camps: []}, never a 404.
   *
   * <p><strong>The ERR-00n numbers are per use case, so always cite the UC with them.</strong>
   * These three outcomes come from the shared {@code MillContextService}, whose own javadoc numbers
   * the same three exceptions ERR-001/002/003 — its numbering follows a different UC. An
   * unqualified "ERR-003" is therefore ambiguous between "select a mill" and "schedule not found"
   * depending on which file the reader has open; the UC prefix is what disambiguates a support
   * ticket. Schedule 1 numbers them differently again ({@code uc-slice-epic-parity-audit:102}).
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the camps document
   */
  @GetMapping
  ResponseEntity<Schedule5Response> getSchedule5(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Add one camp (S01). The camp persists immediately and the recomputed document — every total and
   * $/m&sup3; re-derived — is echoed with a {@code dataSavedSuccesfullyInfoMsg} message.
   *
   * <p>A renamed COPY (BR-10/S03) is an ordinary call to this endpoint: legacy's {@code copyCamp()}
   * makes no database call at all, so there is no separate copy endpoint (deviation (B)). An
   * UNRENAMED copy is the duplicate-name 409 below.
   *
   * <p>Guards: field validation → 400 with the verbatim FLD-002 texts; a duplicate camp name in the
   * same mill/year (case-insensitive, BR-02) → 409 {@code Camp name already exists.}; a non-Draft
   * 1–10 track → 409; no {@code EDIT_SCHEDULE} → 403; bad mill/year context → 400/409/404
   * (UC-SCH5-001 ERR-003/004/005).
   *
   * @param millId the raw mill id param (validated by millcontext; verbatim ERR-003 on absence)
   * @param year the raw reporting year param (validated by millcontext)
   * @param request the entered camp fields (validated, default group — {@code revisionCount} is not
   *     required on a create)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document and the success message
   */
  @PostMapping("/camps")
  ResponseEntity<Schedule5Response> addCamp(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody CampRequest request,
      Authentication authentication);

  /**
   * Edit one camp in place (S02). Same validation and gates as add; the body must additionally
   * carry the camp's own {@code revisionCount} (the {@link OnUpdate} group — omitting it is a clean
   * 400, never a coerced 409). A stale token → 409; an unknown or foreign camp id → 404.
   *
   * <p>Detail rows are upserted per item id and a cleared value writes {@code NULL} into the
   * surviving row (deviation (N)) — nothing is deleted and reinserted.
   *
   * @param campId the camp id ({@code CAMP_REPORT_ID}) to edit
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered fields plus the required {@code revisionCount} (default + OnUpdate)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document and the success message
   */
  @PutMapping("/camps/{campId}")
  ResponseEntity<Schedule5Response> updateCamp(
      @PathVariable int campId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody CampRequest request,
      Authentication authentication);

  /**
   * Delete one camp and its whole expense family (S07) — the camp plus every related {@code
   * ILCR_COST_REPORT_DETAIL} row, including the item-62/68 sub-page rows. Echoes the recomputed
   * document with a {@code dataDeletedSuccesfullyInfoMsg} message.
   *
   * <p>CFM-001 ({@code This will delete the current record. Do you want to continue?}) is the
   * CLIENT's confirmation, not a server round-trip — reaching this endpoint IS the confirmation.
   *
   * <p>Carries NO body and NO revision token (deviation (L), shared with Schedules 4/7A/11), so a
   * delete is never rejected as stale. An unknown or foreign camp id → 404 (deviation (M): the
   * delete is mill/year/category-scoped, unlike legacy's PK-only delete); a non-Draft track → 409.
   *
   * @param campId the camp id to delete
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller (EDIT_SCHEDULE + echoed editability)
   * @return 200 with the recomputed document and the delete message
   */
  @DeleteMapping("/camps/{campId}")
  ResponseEntity<Schedule5Response> deleteCamp(
      @PathVariable int campId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Check Status for Schedule 5 (S06, S20) — read-only readiness validation. Mutates nothing, takes
   * NO request body, and is NOT Draft-gated: {@code VIEW_SCHEDULE} only, the 2.6 precedent, so a
   * Submitted mill can still be checked.
   *
   * <p>Returns either {@code MET} with the single schedule banner and NO per-camp results
   * (deviation (C) — legacy's all-met branch emits {@code scheduleRequirementsMetMsg} alone,
   * contrary to both the epics AC and the UC, which describe a pair), or {@code ISSUES} with each
   * camp's composed {@code Value Required} lines byte-for-byte. Zero camps is vacuously {@code
   * MET}.
   *
   * @param millId the raw mill id param (validated by millcontext)
   * @param year the raw reporting year param
   * @param authentication the caller (VIEW_SCHEDULE)
   * @return 200 with the check-status result
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule5CheckStatusResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  // ===============================================================================================
  // Sub-pages (Story 7.4) — the itemized Other Camp (62) / Other Access (68) expense rows.
  //
  // Nested one level under the camp because the rows are FK-parented by CAMP_REPORT_ID — Schedule
  // 4's URL shape (/locations/{id}/rows[/{rowId}]). Six methods rather than a shared handler taking
  // the page as a path segment: an explicit route per page keeps the OpenAPI surface readable and
  // keeps Schedule5ApiSurfaceTest able to assert each one separately.
  //
  // There is deliberately NO PATCH and no per-row POST. Legacy's Save persists the whole list
  // (Schedule5DAO.saveOtherCampExpenses, :438-486) and its Add is "append then save the whole list"
  // (Schedule5CampExpensesMB.java:147-156), so one batch PUT reproduces both atomically; a per-row
  // API could not express one Save that cleared a description and edited two costs.
  // ===============================================================================================

  /**
   * The Other Camp Expenses sub-page for one camp (S04).
   *
   * <p>Serves the rows in {@code ILCR_COST_REPORT_DETAIL_ID} order plus the camp context the page
   * renders around them and the server-computed footer {@code totals}. Every row's {@code volume} is
   * the camp's item-141 amount STAMPED AT READ — no per-row volume is stored anywhere.
   *
   * <p>Guards: bad mill/year → 400/409/404 (UC-SCH5-001 ERR-003/004/005); an unknown or foreign
   * camp id → 404; no {@code VIEW_SCHEDULE} → 403. A camp with no rows is a valid 200 with
   * {@code rows: []}.
   *
   * @param campId the parent camp id
   * @param millId the raw mill id param (validated by millcontext)
   * @param year the raw reporting year param
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the sub-page document
   */
  @GetMapping("/camps/{campId}/other-camp-expenses")
  ResponseEntity<SubPageDocument> getOtherCampExpenses(
      @PathVariable int campId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * The Other Access Expenses sub-page for one camp (S04) — the item-142 twin of {@link
   * #getOtherCampExpenses}.
   *
   * <p>⚠ Its footer {@code totals.volume} is the SINGLE camp volume, not the sum of the row volumes
   * the camp side reports (deviation (C)). The two pages look identical and their footers are not.
   *
   * @param campId the parent camp id
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller
   * @return 200 with the sub-page document
   */
  @GetMapping("/camps/{campId}/other-access-expenses")
  ResponseEntity<SubPageDocument> getOtherAccessExpenses(
      @PathVariable int campId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Reconcile the whole Other Camp Expenses list (S04) — the SOLE writer of item 62 (AD-5).
   *
   * <p>{@code rowId: null} inserts, a known {@code rowId} updates in place, and a stored row absent
   * from the body is deleted. An unknown or foreign {@code rowId} is a 404 with NOTHING persisted:
   * the whole body is classified before any statement runs, so a stale id cannot half-apply.
   *
   * <p>Costs on THIS page are bounded &plusmn;9,999,999 ({@code costSize7ValidatorErrorMsg}) — every
   * cost input on the Camp sub-page carries {@code costSize="7"}. A blank or null description is
   * ACCEPTED and persisted (deviation (F)); Check Status is what flags it.
   *
   * <p>Guards: a non-Draft 1–10 track → 409; no {@code EDIT_SCHEDULE} → 403; an out-of-range cost or
   * an over-long description → 400 with the verbatim legacy text.
   *
   * @param campId the parent camp id
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the complete row set the camp should hold afterwards (an empty list clears it)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the refreshed document and {@code dataSavedSuccesfullyInfoMsg}
   */
  @PutMapping("/camps/{campId}/other-camp-expenses")
  ResponseEntity<SubPageDocument> saveOtherCampExpenses(
      @PathVariable int campId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody SubPageSaveRequest request,
      Authentication authentication);

  /**
   * Reconcile the whole Other Access Expenses list (S04) — the SOLE writer of item 68 (AD-5).
   *
   * <p>Identical to {@link #saveOtherCampExpenses} except for the cost bound: this page's inputs
   * carry no {@code costSize}, so legacy validates them at &plusmn;99,999,999 ({@code
   * costValidatorErrorMsg}).
   *
   * @param campId the parent camp id
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the complete row set the camp should hold afterwards
   * @param authentication the caller
   * @return 200 with the refreshed document and {@code dataSavedSuccesfullyInfoMsg}
   */
  @PutMapping("/camps/{campId}/other-access-expenses")
  ResponseEntity<SubPageDocument> saveOtherAccessExpenses(
      @PathVariable int campId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody SubPageSaveRequest request,
      Authentication authentication);

  /**
   * Delete one Other Camp Expenses row immediately (S07).
   *
   * <p>Legacy's Delete persists on click rather than at Save ({@code
   * Schedule5CampExpensesMB.deleteCampExpense()}, {@code :158-167}), so this is a real endpoint and
   * not a client-side list edit. CFM-001 is the CLIENT's confirmation — reaching this endpoint IS
   * the confirmation.
   *
   * <p>Camp- AND item-scoped, so a foreign {@code rowId} is a 404, never a cross-camp or
   * cross-page delete (deviation (O): legacy matched on detail id alone against the camp's entire
   * detail collection). Carries no revision token (AR11 house deviation (N)).
   *
   * @param campId the parent camp id
   * @param rowId the row to delete
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller
   * @return 200 with the refreshed document and {@code dataDeletedSuccesfullyInfoMsg}
   */
  @DeleteMapping("/camps/{campId}/other-camp-expenses/{rowId}")
  ResponseEntity<SubPageDocument> deleteOtherCampExpense(
      @PathVariable int campId,
      @PathVariable int rowId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Delete one Other Access Expenses row immediately (S07) — the item-68 twin of {@link
   * #deleteOtherCampExpense}.
   *
   * @param campId the parent camp id
   * @param rowId the row to delete
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller
   * @return 200 with the refreshed document and {@code dataDeletedSuccesfullyInfoMsg}
   */
  @DeleteMapping("/camps/{campId}/other-access-expenses/{rowId}")
  ResponseEntity<SubPageDocument> deleteOtherAccessExpense(
      @PathVariable int campId,
      @PathVariable int rowId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
