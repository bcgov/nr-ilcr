import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'
import { CONFIRM_VERIFY_1_TO_10 } from '../index'

/**
 * The Verified button's confirmation is the one piece of user-facing text on this page the client
 * owns: it is shown BEFORE any request, so no response can carry it. Hardcoding it is only acceptable
 * while something proves it still matches the bundle — this is that proof. A reworded bundle entry
 * must fail here rather than quietly leaving the prompt saying something the ministry no longer says.
 */
describe('the Check Status confirm prompt mirrors the message bundle', () => {
  const bundle = (() => {
    // Vitest runs with cwd = frontend/, and the backend lives beside it in the monorepo.
    const path = resolve(process.cwd(), '../backend/src/main/resources/messages.properties')
    const entries: Record<string, string> = {}
    for (const line of readFileSync(path, 'utf8').split('\n')) {
      const trimmed = line.trim()
      if (trimmed === '' || trimmed.startsWith('#')) {
        continue
      }
      const eq = trimmed.indexOf('=')
      if (eq > 0) {
        entries[trimmed.slice(0, eq)] = trimmed.slice(eq + 1)
      }
    }
    return entries
  })()

  test('confirmVerifySch1-10Msg is byte-identical to the rendered constant', () => {
    expect(bundle['confirmVerifySch1-10Msg']).toBe(CONFIRM_VERIFY_1_TO_10)
  })

  test('the bundle actually carries the key, so a typo cannot pass the comparison vacuously', () => {
    expect(Object.keys(bundle)).toContain('confirmVerifySch1-10Msg')
  })
})
