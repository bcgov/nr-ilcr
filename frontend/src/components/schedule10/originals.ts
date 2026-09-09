import type { OriginalValues } from '@/interfaces/OriginalValue'
import type { RoadDetail } from '@/interfaces/Schedule10Response'

/**
 * Re-key one sub-object's originals onto the form's names. Keys the rename table does not mention
 * pass through unchanged — the sub-grade deductions (`lessBridges`, `lessCulverts`, …) are named the
 * same on both sides, so listing them would be noise that could drift.
 */
const prefixed = (
  values: OriginalValues | null | undefined,
  _prefix: string,
  renames: Readonly<Record<string, string>>,
): OriginalValues => {
  if (values == null) {
    return {}
  }
  const out: Record<string, OriginalValues[string]> = {}
  for (const [key, value] of Object.entries(values)) {
    out[renames[key] ?? key] = value
  }
  return out
}

/**
 * Flatten a road detail's four original-value maps onto the FORM's own field names (Story 16.2).
 *
 * The served document nests a road detail's values in four objects — the detail itself, its
 * `materialComposition`, its `subGrade` and its `stabilizing` — while the editor holds one flat form
 * that prefixes the last two (`sg*`, `st*`). Mapping the two shapes onto each other once, here, is
 * what lets `RoadDetailFields` ask for an indicator by the same key it already uses for the value
 * and its error, instead of every one of its ~30 fields knowing which sub-object it came from.
 *
 * Returns null when the document carries no originals at all — the Draft case, which suppresses
 * every indicator on the page.
 */
export const roadDetailOriginals = (detail: RoadDetail): OriginalValues | null => {
  const own = detail.originalValues
  const material = detail.materialComposition?.originalValues
  const subGrade = detail.subGrade?.originalValues
  const stabilizing = detail.stabilizing?.originalValues

  // At Draft the backend omits every one of the four, so any of them being present means the track
  // has left Draft. Checked across all four rather than on `own` alone: a road detail whose own
  // fields were all unentered still has costs to flag.
  if (own == null && material == null && subGrade == null && stabilizing == null) {
    return null
  }

  return {
    ...(own ?? {}),
    // `becbiogeoCatalogueId` is the form's name for the BEC selection; the document calls it
    // `becClassification`, which is the field the indicator decorates.
    ...(own?.becClassification ? { becbiogeoCatalogueId: own.becClassification } : {}),
    ...(material ?? {}),
    ...prefixed(subGrade, 'sg', {
      length: 'sgLength',
      surfaceWidth: 'sgSurfaceWidth',
      actualCost: 'sgActualCost',
      ttTransfer: 'sgTtTransfer',
      otherTransfer: 'sgOtherTransfer',
    }),
    ...prefixed(stabilizing, 'st', {
      ballastMethodCode: 'stBallastMethodCode',
      ballastMaterialCode: 'stBallastMaterialCode',
      length: 'stLength',
      surfaceWidth: 'stSurfaceWidth',
      depth: 'stDepth',
      distanceToSource: 'stDistanceToSource',
      actualCost: 'stActualCost',
      ttTransfer: 'stTtTransfer',
      otherTransfer: 'stOtherTransfer',
    }),
  }
}
