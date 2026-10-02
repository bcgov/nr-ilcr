// Write bodies for the Schedule 10 page and road-detail endpoints.
//
// Derived values are never sent: road group, page/row numbers and labels, every total, the cost per
// length and the material total are all computed server-side and rejected or ignored on write.
//
// Costs are whole-dollar integers on the way in even though they come back as decimals, so every
// cost is rounded before it reaches these types.

/** Sub-grade dimensions, costs and deduction lines. Every member is optional in storage. */
export interface SubGradeRequest {
  readonly length: number | null
  readonly surfaceWidth: number | null
  readonly actualCost: number | null
  readonly ttTransfer: number | null
  readonly otherTransfer: number | null
  readonly lessBridges: number | null
  readonly lessCulverts: number | null
  readonly lessLandings: number | null
  readonly lessOverland: number | null
  readonly lessOtherEng: number | null
  readonly lessEndHaul: number | null
}

/**
 * Additional-stabilizing attributes and costs. The substructure itself is REQUIRED on every road
 * detail write because its ballast method is required.
 *
 * The method drives server-side coercion the form mirrors: `C` requires a material code; `N` zeroes
 * the four dimensions along with the actual cost and other transfer, forces the material to `NA`,
 * and deliberately leaves the TtT transfer alone; `D` forces only the material to `NA`.
 */
export interface StabilizingRequest {
  readonly ballastMethodCode: string
  readonly ballastMaterialCode: string | null
  readonly length: number | null
  readonly surfaceWidth: number | null
  readonly depth: number | null
  readonly distanceToSource: number | null
  readonly actualCost: number | null
  readonly ttTransfer: number | null
  readonly otherTransfer: number | null
}

/** The five material percentages. The total is derived and never sent. */
export interface MaterialCompositionRequest {
  readonly solidRockPct: number | null
  readonly rippableRockPct: number | null
  readonly coarsePct: number | null
  readonly finePct: number | null
  readonly organicPct: number | null
}

/**
 * A construction page. `tsaOrTfl` is one field carrying either a TSA number or the literal sentinel
 * `TFL`; the server clears whichever half the branch does not use, and may canonicalise a TFL entry
 * that omits its leading zero, so the stored location can differ from what was sent.
 *
 * `revisionCount` is omitted on create and REQUIRED on update — sending it as a fabricated 0 would
 * defeat the stale-edit check.
 */
export interface ConstructionPageRequest {
  readonly forestRegionCode: string
  readonly tsaOrTfl: string
  readonly supplyBlock: string | null
  readonly tflNumberCode: string | null
  readonly divisionName: string | null
  readonly constructionPeriod: string | null
  readonly revisionCount?: number
}

/**
 * A road detail. `becbiogeoCatalogueId` and `relSoilMoistRgmClsCode` are required because they are
 * the inputs the server derives its two retained moisture columns from.
 */
export interface RoadDetailRequest {
  readonly roadName: string
  readonly roadLifetimeCode: string
  readonly becbiogeoCatalogueId: number | null
  readonly relSoilMoistRgmClsCode: string
  readonly sideSlopePct: number | null
  readonly detailedEngineeringCostInd: string | null
  readonly subGrade: SubGradeRequest | null
  readonly stabilizing: StabilizingRequest
  readonly materialComposition: MaterialCompositionRequest | null
  readonly endHaulDistance: number | null
  readonly endHaulVolume: number | null
  readonly overlandDistance: number | null
  readonly overlandVolume: number | null
  readonly comments: string | null
  readonly revisionCount?: number
}

/**
 * The Check Status body — the page panel or road editor currently ON SCREEN, mirroring the backend
 * `Schedule10CheckRequest` (#359). The server overlays it onto the stored document; nothing is
 * persisted.
 *
 * Only an EXISTING page or road is sent: legacy built a new one outside the checked list, so an
 * unsaved new page or road is never evaluated. Both members are `null` when no such editor is open,
 * and the stored document is then evaluated alone.
 *
 * ⚠ `null` means "no usable value on screen" and MUST stay null — a blank figure is reported as
 * missing, so coercing it to `0` would turn a missing value into a pass.
 */
export interface Schedule10CheckRequest {
  readonly page: Schedule10CheckPageEntry | null
  readonly road: Schedule10CheckRoadEntry | null
}

/** The open page panel: the fields the page rules read, plus the period for the page label. */
export interface Schedule10CheckPageEntry {
  readonly pageId: number
  readonly divisionName: string | null
  readonly constructionPeriod: string | null
  readonly tsaOrTfl: string | null
  readonly supplyBlock: string | null
  readonly tflNumberCode: string | null
}

/**
 * The open road editor: every field the road rules read. The nested shapes are the write bodies'
 * (built by the same code, so ballast method `N` sends the figures Save would), minus the derived
 * totals the server recomputes. A blank ballast method is `null` here rather than Save's `''`.
 */
export interface Schedule10CheckRoadEntry {
  readonly pageId: number
  readonly roadDetailId: number
  readonly roadName: string | null
  readonly becbiogeoCatalogueId: number | null
  readonly relSoilMoistRgmClsCode: string | null
  readonly sideSlopePct: number | null
  readonly subGrade: SubGradeRequest | null
  readonly stabilizing: Omit<StabilizingRequest, 'ballastMethodCode'> & {
    readonly ballastMethodCode: string | null
  }
  readonly materialComposition: MaterialCompositionRequest | null
}
