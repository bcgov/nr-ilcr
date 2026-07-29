// Mirrors the backend Story 2.6 CheckStatusResponse (BR-07). Messages carry the legacy bundle key +
// verbatim resolved text; the frontend renders `text` and never hardcodes SUC/FLD/WRN strings.

import type { MessageInfo } from '@/interfaces/Schedule1Response'

export default interface CheckStatusResponse {
  readonly requirementsMet: boolean
  readonly errors: readonly MessageInfo[]
  readonly warnings: readonly MessageInfo[]
  // SUC-003 success text when requirementsMet; null otherwise.
  readonly message: MessageInfo | null
}
