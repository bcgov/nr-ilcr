// Mirrors the backend Schedule4LocationRequest / CategoryInput write DTOs (Story 4.2). The server is
// authoritative for validation, name uniqueness, the Draft gate, and the optimistic lock; derived
// `perUnit`/`kind` and read-only metadata are never sent.

// One entered category amount. `distance` is ignored server-side for fixed codes.
export interface CategoryInput {
  readonly code: number
  readonly volume: number | null
  readonly cost: number | null
  readonly distance: number | null
}

// Location save (create-or-edit). `id` null = create, present = edit (rename-safe). `revisionCount`
// is the optimistic-lock token from the read (null on create).
export default interface Schedule4LocationRequest {
  readonly id: number | null
  readonly revisionCount: number | null
  readonly name: string
  // Per-location free-text comments (≤ 2000, the TRANSPORTATION_REPORT.COMMENTS width); null when blank.
  readonly comments: string | null
  readonly categories: CategoryInput[]
}

/**
 * The Check Status body — the ONE open location panel as it is on screen, mirroring the backend
 * `Schedule4CheckRequest` (#359). Overlaid on the stored locations: an open existing location replaces
 * its stored self (`id` set); an unsaved New/Copy panel (`id` null) is evaluated as an extra location.
 * `location` is null when no panel is open. Closed locations and sub-page rows are never sent — the
 * server reads them from the database.
 *
 * ⚠ A blank name is sent as `null`, never as a placeholder: "location name blank" is the check.
 */
export interface Schedule4CheckRequest {
  readonly location: {
    readonly id: number | null
    readonly name: string | null
  } | null
}
