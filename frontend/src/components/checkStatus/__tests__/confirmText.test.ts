import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, test } from 'vitest'
import {
  CONFIRM_SET_TO_DRAFT_1_TO_10,
  CONFIRM_SET_TO_SUBMIT_1_TO_10,
  CONFIRM_SUBMIT_11,
  CONFIRM_VERIFY_1_TO_10,
  CONFIRM_VERIFY_11,
} from '../index'
import { SCH11_NOT_DRAFT_TEXT, SCH11_SUBMITTED_TEXT, SCH11_VERIFIED_TEXT } from './fixtures'

/**
 * The transition confirmations are the only user-facing text on this page the client owns: each is
 * shown BEFORE any request, so no response can carry it. Hardcoding them is only acceptable while
 * something proves they still match the bundle — this is that proof. A reworded bundle entry must
 * fail here rather than quietly leaving a prompt saying something the ministry no longer says.
 *
 * Each key is checked twice. The byte-identity test is the one that matters, but on its own it passes
 * vacuously when the key is misspelled on BOTH sides: `bundle[typo]` is `undefined`, and a constant
 * that was never added is `undefined` too. The key-exists test is what makes the comparison mean
 * something.
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

  // The two reversal prompts (Story 18.2). Their key names carry legacy's real `Sche` typo
  // (2.0.4 messages.properties:104-105) — recovered verbatim, not corrected, so a search of either
  // codebase for the legacy key finds both ends of it.
  test('confirmDraftSche1-10Msg is byte-identical to the rendered constant', () => {
    expect(bundle['confirmDraftSche1-10Msg']).toBe(CONFIRM_SET_TO_DRAFT_1_TO_10)
  })

  test('the bundle carries confirmDraftSche1-10Msg, typo and all', () => {
    expect(Object.keys(bundle)).toContain('confirmDraftSche1-10Msg')
  })

  test('confirmSubmitBackSche1-10Msg is byte-identical to the rendered constant', () => {
    expect(bundle['confirmSubmitBackSche1-10Msg']).toBe(CONFIRM_SET_TO_SUBMIT_1_TO_10)
  })

  test('the bundle carries confirmSubmitBackSche1-10Msg, typo and all', () => {
    expect(Object.keys(bundle)).toContain('confirmSubmitBackSche1-10Msg')
  })

  // Schedule 11's submit prompt (Story 26.1), legacy key and text verbatim (2.0.4 :111).
  test('confirmSubmitSch11Msg is byte-identical to the rendered constant', () => {
    expect(bundle['confirmSubmitSch11Msg']).toBe(CONFIRM_SUBMIT_11)
  })

  test('the bundle carries confirmSubmitSch11Msg', () => {
    expect(Object.keys(bundle)).toContain('confirmSubmitSch11Msg')
  })

  // Schedule 11's verify prompt (Story 26.3), legacy key and text verbatim (2.0.4 :112).
  test('confirmVerifySch11Msg is byte-identical to the rendered constant', () => {
    expect(bundle['confirmVerifySch11Msg']).toBe(CONFIRM_VERIFY_11)
  })

  test('the bundle carries confirmVerifySch11Msg', () => {
    expect(Object.keys(bundle)).toContain('confirmVerifySch11Msg')
  })

  // Not client-owned — the server sends these — but the page suite's MSW fixtures stand in for the
  // server, and a fixture that says something the server never would makes every banner arm a test
  // of the mock. Pinned here so the stand-in cannot drift from the real envelope.
  test('the Schedule 11 success and not-Draft fixtures are the bundle’s own text', () => {
    expect(Object.keys(bundle)).toContain('sch11SubmittedMsg')
    expect(Object.keys(bundle)).toContain('sch11SubmitNotDraftErrorMsg')
    expect(bundle['sch11SubmittedMsg']).toBe(SCH11_SUBMITTED_TEXT)
    expect(bundle['sch11SubmitNotDraftErrorMsg']).toBe(SCH11_NOT_DRAFT_TEXT)
  })

  test('the Schedule 11 verify success fixture is the bundle’s own text', () => {
    expect(Object.keys(bundle)).toContain('sch11VerifiedMsg')
    expect(bundle['sch11VerifiedMsg']).toBe(SCH11_VERIFIED_TEXT)
  })
})
