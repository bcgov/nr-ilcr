package ca.bc.gov.nrs.ilcr.schedule4.dto;

/**
 * The Check Status body: the location panel currently ON SCREEN, if one is open (bcgov/nr-ilcr#359,
 * group B). Schedule 5's shape ({@code Schedule5CheckRequest}), not Schedule 6's.
 *
 * <p>Legacy bound an open EXISTING location's panel to the very object the final check walked
 * ({@code Schedule4MB.java:304}), so an unsaved rename on that panel moved the verdict. The shipped
 * implementation re-read the database instead, so it could not. This DTO restores the legacy
 * behaviour: the verdict is computed from the stored locations with {@code location} overlaid onto
 * them, and nothing is persisted.
 *
 * <p><strong>Why one panel and not every location.</strong> Schedule 4's table renders location
 * NAMES only; the open panel is the only thing the client knows that the server does not. A
 * whole-list payload would make the client assert values for locations it is not displaying and
 * cannot have changed, from its own possibly stale copy — the #476 reasoning. The sub-page rows
 * (Towing, Truck Rehaul, Other Transportation) and the category grid are likewise NOT carried: the
 * one live rule (#465) reads only the location name, and the sub-page rows are edited elsewhere, so
 * both paths read them from the database.
 *
 * <p><strong>Recorded deviation (DL-28, the #476 precedent).</strong> A new or copied panel was
 * detached in legacy ({@code Schedule4MB.java:270,286}) and legacy's final check skipped it. Here
 * it is evaluated as an ADDITIONAL location, because it is on screen. The difference is
 * unobservable in practice: the client's panel validation already requires a name before the check
 * is sent.
 *
 * <p>Deliberately NOT reusing {@link Schedule4LocationRequest}: its name is {@code @NotBlank}, its
 * categories are validated and its {@code revisionCount} exists for writes. Check Status addresses
 * no stored row, takes no optimistic lock, and must ACCEPT incomplete input — reporting it is its
 * entire purpose.
 *
 * <p>Read-only: this type reaches no write path. {@code location} is nullable and absent means "no
 * panel is open", which evaluates the stored locations alone. An absent BODY, by contrast, is a
 * clean 400 from {@code @RequestBody}'s own required-ness rather than a 500.
 *
 * @param location the location panel currently on screen, or null when none is open
 */
public record Schedule4CheckRequest(LocationEntry location) {

  /**
   * The open location panel as on screen. Unvalidated by design: a blank name is exactly what Check
   * Status exists to surface.
   *
   * @param id the location's stored id, or null when the panel holds a location that has never been
   *     saved (a new or copied location). A null id is evaluated as an ADDITIONAL location after
   *     the stored ones; a non-null id that matches no stored location (deleted in another session)
   *     is likewise evaluated rather than silently dropped.
   * @param name the on-screen location name. The page sends it trimmed (blank as null), the form
   *     Save stores it in; the server judges whatever it receives with {@code isBlank()}, as on the
   *     stored path, so a null or whitespace-only name fails. The verdict's messages carry this
   *     name
   */
  public record LocationEntry(Integer id, String name) {}
}
