package ca.bc.gov.nrs.ilcr.schedule4.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The Schedule 4 location save (create-or-edit) request (Story 4.2, AD-12 write contract).
 *
 * <p>A location is a FAMILY of {@code TRANSPORTATION_REPORT} rows sharing a
 * {@code LOCATION_DESCRIPTION}: one primary report (distance null) holding the 9 fixed categories,
 * plus one report per entered distance code (47/48/52) carrying its own distance. The write targets
 * the family by {@code id} — the primary report's {@code TRANSPORTATION_REPORT_ID}, echoed on the
 * read (rename-safe, §Decision 2). {@code id} null = CREATE; present = EDIT.
 *
 * <p>{@code revisionCount} is the optimistic-lock token on the primary report (§Decision 3); null on
 * create (matches the freshly-inserted 0). {@code name} is required and ≤ 30 chars (S09/S13):
 * blank/whitespace → 400 {@code locationEmptyOrNull} (ERR-001); a case-insensitive duplicate of
 * another location → 409 {@code locationAlreadyExists} (ERR-002), enforced server-side.
 *
 * <p>{@code categories} carries only the categories the client entered (S08 — every category is
 * optional; a name-only location sends an empty list). A category present with all-null amounts
 * clears it. Derived {@code perUnit}, {@code kind}, and read-only {@code trackStatus}/{@code editable}
 * are never accepted here — they are recomputed server-side (AD-5).
 *
 * @param id the primary report id to edit; null to create
 * @param revisionCount optimistic-lock token from the last GET (null on create)
 * @param name the location description (required, ≤ 30)
 * @param categories the entered category amounts (validated per-element + BR-04)
 */
public record Schedule4LocationRequest(
    Integer id,
    Integer revisionCount,
    @NotBlank(message = "{locationEmptyOrNull}")
    @Size(max = 30, message = "Location Name can not exceed 30 characters.")
    String name,
    @Valid List<CategoryInput> categories) {

  /** Never-null category list (an omitted/blank list is a name-only location). */
  public List<CategoryInput> categoriesOrEmpty() {
    return categories == null ? List.of() : categories;
  }
}
