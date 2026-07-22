// Mirrors the backend Schedule4LocationRequest / CategoryInput write DTOs (Story 4.2). The server is
// authoritative for validation, name uniqueness, the Draft gate, and the optimistic lock; derived
// `perUnit`/`kind` and read-only metadata are never sent.

// One entered category amount. `distance` is ignored server-side for fixed codes.
export interface CategoryInput {
  code: number
  volume: number | null
  cost: number | null
  distance: number | null
}

// Location save (create-or-edit). `id` null = create, present = edit (rename-safe). `revisionCount`
// is the optimistic-lock token from the read (null on create).
export default interface Schedule4LocationRequest {
  id: number | null
  revisionCount: number | null
  name: string
  categories: CategoryInput[]
}
