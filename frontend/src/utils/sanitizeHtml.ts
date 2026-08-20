import DOMPurify from 'dompurify'

/**
 * Sanitize admin-authored rich-text before rendering it unescaped on Home (Story 24.2 / UC-CNT-001).
 * The messages are stored raw (legacy parity — legacy did no sanitization), so this render-time pass is
 * the defence-in-depth net: DOMPurify's defaults strip <script>, event-handler attributes, and other
 * active content while keeping the formatting tags the WYSIWYG produces. The ONLY place the app feeds
 * HTML to dangerouslySetInnerHTML, so all such HTML flows through here first.
 */
export function sanitizeHtml(html: string | null | undefined): string {
  return html ? DOMPurify.sanitize(html) : ''
}
