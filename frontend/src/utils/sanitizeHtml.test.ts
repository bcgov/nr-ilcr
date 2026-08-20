import { describe, expect, test } from 'vitest'
import { sanitizeHtml } from '@/utils/sanitizeHtml'

describe('sanitizeHtml', () => {
  test('strips <script> and event-handler attributes (the XSS vectors)', () => {
    expect(sanitizeHtml('<p>hi</p><script>alert(1)</script>')).not.toContain('<script')
    expect(sanitizeHtml('<img src="x" onerror="alert(1)">')).not.toContain('onerror')
    expect(sanitizeHtml('<a href="javascript:alert(1)">x</a>')).not.toContain('javascript:')
  })

  test('keeps the formatting tags the WYSIWYG emits', () => {
    const out = sanitizeHtml(
      '<p><strong>bold</strong> <em>it</em> <u>u</u><ul><li>x</li></ul><a href="https://ex.ca">l</a></p>',
    )
    expect(out).toContain('<strong>')
    expect(out).toContain('<em>')
    expect(out).toContain('<li>')
    expect(out).toContain('href="https://ex.ca"')
  })

  test('null / empty yields an empty string', () => {
    expect(sanitizeHtml(null)).toBe('')
    expect(sanitizeHtml(undefined)).toBe('')
    expect(sanitizeHtml('')).toBe('')
  })
})
