// Content Editing wire contract (Story 24.2 / UC-CNT-001).

/** One role-keyed Home message. */
export interface HomeContentEntry {
  role: string
  messageText: string | null
}

/** The response to a save (PUT /api/v1/home-content). */
export interface HomeContentSaveResponse {
  messageKey: string
  message: string
  entries: HomeContentEntry[]
}
