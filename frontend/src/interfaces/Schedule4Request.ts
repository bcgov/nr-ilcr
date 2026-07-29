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
  readonly categories: CategoryInput[]
}
