import { describe, expect, test } from 'vitest'
import {
  BRIDGE_MESSAGES,
  COST_FIELDS,
  bridgeBannerLines,
  emptyBridgeForm,
  validateBridge,
} from '@/components/schedule7a/validation'

describe('bridgeBannerLines — every legacy row label (schedule7A.xhtml:584-870)', () => {
  test('a blank row reports all twelve required fields, verbatim and in screen order', () => {
    expect(bridgeBannerLines(validateBridge(emptyBridgeForm()), 3)).toEqual([
      'Id: 3 - Name/Location of Bridge: Value is required.',
      'Id: 3 - Date: Value is required.',
      'Id: 3 - New/Used: Value is required.',
      'Id: 3 - Expected Life Span: Value is required.',
      'Id: 3 - Superstructure Type: Value is required.',
      'Id: 3 - Decking Type: Value is required.',
      'Id: 3 - Abutments Type: Value is required.',
      'Id: 3 - Abutments Ht. (m): Value is required.',
      'Id: 3 - Load Rating: Value is required.',
      'Id: 3 - Length (m): Value is required.',
      'Id: 3 - Width (m): Value is required.',
      'Id: 3 - Distance (km): Value is required.',
    ])
  })

  test('row 2: every range/format line carries legacy`s `Id: 2 - ` validator/converter prefix', () => {
    const form = {
      ...emptyBridgeForm(),
      locationName: 'Bridge',
      builtDate: '2020-13',
      constructionTypeCode: 'N',
      superstructureTypeCode: 'S',
      deckTypeCode: 'D',
      abutmentTypeCode: 'A',
      loadRatingCode: 'L',
      lifeSpan: '1000',
      abutmentHeight: '10000',
      length: '10000',
      width: '10000',
      distance: '10000',
      ...Object.fromEntries(COST_FIELDS.map((field) => [field, '100000000'])),
    }
    const costLine = `Id: 2 - ${BRIDGE_MESSAGES.costRange}`
    expect(bridgeBannerLines(validateBridge(form), 2)).toEqual([
      `Id: 2 - ${BRIDGE_MESSAGES.dateFormat}`,
      `Id: 2 - ${BRIDGE_MESSAGES.lifeSpanRange}`,
      `Id: 2 - ${BRIDGE_MESSAGES.abutmentHeightRange}`,
      `Id: 2 - ${BRIDGE_MESSAGES.lengthRange}`,
      `Id: 2 - ${BRIDGE_MESSAGES.widthRange}`,
      `Id: 2 - ${BRIDGE_MESSAGES.distanceRange}`,
      ...Array.from({ length: 10 }, () => costLine),
    ])
    // The bundle text itself, unchanged (legacy messages.properties:70,133-137,156).
    expect(BRIDGE_MESSAGES.distanceRange).toBe(
      'Entered bridge distance must be between 0.0 and 999.99',
    )
  })

  test('the name and comments length caps are not legacy validators and stay unprefixed', () => {
    const form = {
      ...emptyBridgeForm(),
      builtDate: '2020-06',
      constructionTypeCode: 'N',
      superstructureTypeCode: 'S',
      deckTypeCode: 'D',
      abutmentTypeCode: 'A',
      loadRatingCode: 'L',
      lifeSpan: '5',
      abutmentHeight: '5',
      length: '5',
      width: '5',
      distance: '5',
      locationName: 'x'.repeat(31),
      comments: 'y'.repeat(3501),
    }
    expect(bridgeBannerLines(validateBridge(form), 2)).toEqual([
      BRIDGE_MESSAGES.locationMaxLength,
      BRIDGE_MESSAGES.commentsMaxLength,
    ])
  })
})
