package ca.bc.gov.nrs.ilcr.millmaintenance.api;

import ca.bc.gov.nrs.ilcr.millmaintenance.dto.AdminMill;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.AdminMillResponse;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.ChangeMillStatusRequest;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.ContactOption;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.ImportableMill;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.MillSearchResponse;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.SaveMillContactsRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The mill administration API (UC-MILL-001) — the wire contract for search, import, activation,
 * deactivation and the head-office/contact save.
 *
 * <p>Rooted at {@code /api/v1/admin/mills} rather than {@code /api/v1/mills}, which is the
 * caller-scoped Home selection list and a different concern with a different gate. All of it is
 * ADMIN-only behind {@code MAINTAIN_MILLS}; a submitter is refused 403 on every operation, not
 * merely hidden from the menu.
 *
 * <p>Status changes and the import are POST sub-resources, per the house endpoint convention: they
 * are actions on a mill, not replacements of one. The contact save is a PUT because it does replace
 * the whole editable panel — a null contact clears it — which mirrors how the legacy screen saved.
 *
 * <p>Errors are RFC 7807 {@code ProblemDetail}. Pinned statuses: a malformed mill number is 400
 * with the legacy converter text; an unknown or not-yet-imported mill is 404; an already-imported
 * mill, a deactivation blocked by active users, a contact outside the mill's client location, and a
 * stale revision are all 409; a rolled-back import is 500 with the legacy failure text.
 */
@RequestMapping("/api/v1/admin/mills")
public interface MillMaintenanceApi {

  /**
   * Search the tracked ILCR mills. Every criterion is optional and an omitted one is not a filter;
   * a zero-match search is a 200 carrying the legacy not-found message beside an empty list, so the
   * screen can keep its criteria and retry.
   *
   * @param millNumber exact mill number as typed; non-numeric input is refused 400
   * @param millName a case-insensitive fragment of the mill name
   * @param status {@code ACT} or {@code CLS}; omit for any; any other value is refused 400 (legacy
   *     offered a fixed dropdown, so an unknown code was unreachable there)
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the matching mills with the zero-match message when there are none
   */
  @GetMapping
  ResponseEntity<MillSearchResponse> search(
      @RequestParam(required = false) String millNumber,
      @RequestParam(required = false) String millName,
      @RequestParam(required = false) String status,
      Authentication authentication);

  /**
   * The ministry mills still available to import — those with no ILCR cross-reference (BR-04). No
   * status criterion, because an unimported mill has no ILCR status.
   *
   * @param millNumber exact mill number as typed; non-numeric input is refused 400
   * @param millName a case-insensitive fragment of the mill name
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the importable mills, ordered by mill number; empty when none match
   */
  @GetMapping("/importable")
  ResponseEntity<List<ImportableMill>> findImportable(
      @RequestParam(required = false) String millNumber,
      @RequestParam(required = false) String millName,
      Authentication authentication);

  /**
   * One tracked mill's administration detail.
   *
   * @param millId the mill id
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the mill, or 404 when unknown or not yet imported
   */
  @GetMapping("/{millId}")
  ResponseEntity<AdminMill> findById(@PathVariable long millId, Authentication authentication);

  /**
   * The contacts selectable for this mill's head-office and division slots — the contacts of the
   * mill's own client location (BR-09). Empty when the mill has no client linkage, which is the
   * common shape in real data.
   *
   * @param millId the mill id
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the selectable contacts, ordered by name
   */
  @GetMapping("/{millId}/contact-options")
  ResponseEntity<List<ContactOption>> contactOptions(
      @PathVariable long millId, Authentication authentication);

  /**
   * Import a ministry mill into ILCR (S02, BR-03): its cross-reference is created closed with the
   * head-office indicator set and both contacts empty, and it receives the current reporting year's
   * report records. The screen confirms by displaying the imported mill's details, which is the
   * only confirmation legacy gave.
   *
   * <p>Outcomes: 404 when no such ministry mill; 409 when it is already tracked, or when no
   * reporting year has been opened; 500 with the legacy failure text when the transaction rolls
   * back, in which case no cross-reference exists (S14).
   *
   * @param millId the ministry mill to import
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the imported mill
   */
  @PostMapping("/{millId}/import")
  ResponseEntity<AdminMillResponse> importMill(
      @PathVariable long millId, Authentication authentication);

  /**
   * Reopen a closed mill and guarantee its current-year report records (S04, BR-07). No
   * precondition on the current status — legacy's activate had no guard, and re-activating an
   * active mill is harmless.
   *
   * <p>Outcomes: 404 unknown; 409 when no reporting year is open, when the revision is stale, or
   * when the mill's current-year report records are in a partial state that activation cannot
   * re-create (rolled back, mill left as found).
   *
   * @param millId the mill id
   * @param request the revision last read for this mill
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the activated mill with the legacy confirmation
   */
  @PostMapping("/{millId}/activate")
  ResponseEntity<AdminMillResponse> activate(
      @PathVariable long millId,
      @Valid @RequestBody ChangeMillStatusRequest request,
      Authentication authentication);

  /**
   * Close a mill (S03). Refused while any user assignment is still active, with the mill's status
   * left unchanged (BR-01/S12).
   *
   * <p>Outcomes: 404 unknown; 409 when active assignments remain or the revision is stale.
   *
   * @param millId the mill id
   * @param request the revision last read for this mill
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the deactivated mill with the legacy confirmation
   */
  @PostMapping("/{millId}/deactivate")
  ResponseEntity<AdminMillResponse> deactivate(
      @PathVariable long millId,
      @Valid @RequestBody ChangeMillStatusRequest request,
      Authentication authentication);

  /**
   * Save the head-office indicator and both contact selections (S01). The whole panel is replaced:
   * a null contact id clears that column, so the screen sends all three fields every time.
   *
   * <p>Outcomes: 400 when the indicator is absent or not {@code Y}/{@code N}, or the revision is
   * absent; 404 unknown; 409 when a contact does not belong to the mill's client location, or the
   * revision is stale.
   *
   * @param millId the mill id
   * @param request the indicator, both contact selections, and the revision last read
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the saved mill with the legacy confirmation
   */
  @PutMapping("/{millId}/contacts")
  ResponseEntity<AdminMillResponse> saveContacts(
      @PathVariable long millId,
      @Valid @RequestBody SaveMillContactsRequest request,
      Authentication authentication);
}
