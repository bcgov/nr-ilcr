package ca.bc.gov.nrs.ilcr.schedule5.api;

import ca.bc.gov.nrs.ilcr.schedule5.dto.CampRequest;
import ca.bc.gov.nrs.ilcr.schedule5.dto.OnUpdate;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
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
}
