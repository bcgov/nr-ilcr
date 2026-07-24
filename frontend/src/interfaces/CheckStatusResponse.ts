// Mirrors the backend Story 2.6 CheckStatusResponse (BR-07). Messages carry the legacy bundle key +
// verbatim resolved text; the frontend renders `text` and never hardcodes SUC/FLD/WRN strings.

import type { MessageInfo } from '@/interfaces/Schedule1Response'

export default interface CheckStatusResponse {
  requirementsMet: boolean
  errors: MessageInfo[]
  warnings: MessageInfo[]
  // SUC-003 success text when requirementsMet; null otherwise.
  message: MessageInfo | null
}
