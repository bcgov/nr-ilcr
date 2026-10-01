import type { FC } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Button,
  Column,
  Grid,
  Modal,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react'
import { Add, CheckmarkOutline, Close, Copy, Edit, Save, TrashCan, View } from '@carbon/icons-react'
import { getRouteApi } from '@tanstack/react-router'
import type Schedule10Response from '@/interfaces/Schedule10Response'
import type { Schedule10CheckRequest } from '@/interfaces/Schedule10Request'
import type {
  ConstructionPage,
  RoadDetail,
  Schedule10CheckStatusResponse,
  Schedule10CodeLists,
} from '@/interfaces/Schedule10Response'
import apiService from '@/service/api-service'
import { useScheduleBanners } from '@/hooks/useScheduleBanners'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { useScheduleDocument } from '@/hooks/useScheduleDocument'
import type { BannerEntry } from '@/utils/legacyValidationBanner'
import { setBannerEntry } from '@/utils/legacyValidationBanner'
import { groupFixedInput } from '@/utils/number'
import ConfirmDeleteModal from '@/components/core/ConfirmDeleteModal'
import ScheduleBanners from '@/components/core/ScheduleBanners'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import PageFields from './PageFields'
import RoadDetailPage from './RoadDetailPage'
import type { PanelMode } from './RoadDetailPage'
import type { Schedule10CheckSummary } from './checkStatus'
import { summariseCheckStatus } from './checkStatus'
import type {
  MaskedField,
  PageErrors,
  PageFormValues,
  RoadDetailErrors,
  RoadDetailFormValues,
} from './validation'
import {
  BALLAST_RESET_FIELDS,
  MASK_DIGITS,
  SCH10_MESSAGES,
  applyBallastMethodReset,
  buildPageBody,
  buildPageCheckEntry,
  buildRoadCheckEntry,
  buildRoadDetailBody,
  emptyPageForm,
  emptyRoadDetailForm,
  formFromPage,
  formFromRoadDetail,
  isTflLocated,
  pageBannerEntries,
  pageBannerEntry,
  roadBannerEntries,
  roadBannerEntry,
  validatePage,
  validateRoadDetail,
} from './validation'
import './index.scss'

const SCHEDULE10_PATH = '/v1/schedule10'
const PAGES_PATH = `${SCHEDULE10_PATH}/pages`
const CHECK_STATUS_PATH = `${SCHEDULE10_PATH}/check-status`

// Client-only chrome; every success and failure line renders from the API, never hardcoded.
const EMPTY_LIST = 'No records found.'
const NAV_UNSAVED = 'Any unsaved data will be lost. Are you sure you would like to continue?'

/** Editing any of these invalidates the server-derived Road Group shown beside them. */
const LOCATION_FIELDS = new Set<keyof PageFormValues>(['tsaOrTfl', 'supplyBlock', 'tflNumberCode'])

/** The page panel's dropdowns: a selection IS the change, so it is judged at once. */
const PAGE_CODE_FIELDS = new Set<keyof PageFormValues>([
  'forestRegionCode',
  'tsaOrTfl',
  'supplyBlock',
])

/** The road editor's dropdowns: a selection IS the change, so it is judged at once. */
const ROAD_CODE_FIELDS = new Set<keyof RoadDetailFormValues>([
  'roadLifetimeCode',
  'becbiogeoCatalogueId',
  'relSoilMoistRgmClsCode',
  'stBallastMethodCode',
  'stBallastMaterialCode',
  'detailedEngineeringCostInd',
])

/** Recompute `fields` in an error map from a fresh validation, leaving every other field as it was. */
const mergeErrors = <K extends string>(
  prev: Partial<Record<K, string>>,
  fresh: Partial<Record<K, string>>,
  fields: readonly K[],
): Partial<Record<K, string>> => {
  const next = { ...prev }
  for (const field of fields) {
    const message = fresh[field]
    if (message === undefined) {
      delete next[field]
    } else {
      next[field] = message
    }
  }
  return next
}

const EMPTY_CODE_LISTS: Schedule10CodeLists = {
  forestRegions: [],
  tsaNumbers: [],
  supplyBlocks: [],
  roadLifetimes: [],
  ballastMethods: [],
  ballastMaterials: [],
  rsmrClasses: [],
  becClassifications: [],
}

const PAGE_HEADER = (
  <ScheduleTombstone title="Schedule 10" subtitle="Report New Road Construction Costs" />
)

const mapLoadError = (detail: string | undefined): string => detail ?? 'Unable to load Schedule 10.'

// The road level is URL-driven so the browser Back button steps out of it.
const scheduleRoute = getRouteApi('/schedule-10')

type DeleteTarget =
  | { readonly kind: 'page'; readonly page: ConstructionPage }
  | {
      readonly kind: 'road'
      readonly detail: RoadDetail
    }

const Schedule10: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  const search = scheduleRoute.useSearch()
  const navigate = scheduleRoute.useNavigate()

  const {
    saving,
    message,
    actionError,
    checkResult,
    setMessage,
    setActionError,
    setCheckResult,
    clearBanners: clearHookBanners,
    resetBanners,
    run,
  } = useScheduleBanners<Schedule10CheckSummary>(isCurrent)

  // Check Status describes one exact screen snapshot (#359). Incremented synchronously whenever that
  // snapshot changes, so an older response cannot repaint a verdict for values no longer on screen.
  const checkSnapshotVersionRef = useRef(0)

  const invalidateCheckResult = useCallback(() => {
    checkSnapshotVersionRef.current += 1
    setCheckResult(null)
  }, [setCheckResult])

  const [pagePanelMode, setPagePanelMode] = useState<PanelMode>('closed')
  const [openPageId, setOpenPageId] = useState<number | null>(null)
  const [pageForm, setPageForm] = useState<PageFormValues>(emptyPageForm)
  const [pageErrors, setPageErrors] = useState<PageErrors>({})

  const [roadPanelMode, setRoadPanelMode] = useState<PanelMode>('closed')
  const [openRoadId, setOpenRoadId] = useState<number | null>(null)
  const [roadForm, setRoadForm] = useState<RoadDetailFormValues>(emptyRoadDetailForm)
  const [roadErrors, setRoadErrors] = useState<RoadDetailErrors>({})

  // The forms as last WRITTEN, read synchronously by a field's judgement. A combo box can commit its
  // selection and lose focus in the same event (clearing its text and leaving does both), and the
  // judgement that runs on leave must see the value just written, not the render's.
  const pageFormRef = useRef<PageFormValues>(pageForm)
  const roadFormRef = useRef<RoadDetailFormValues>(roadForm)
  const writePageForm = useCallback((next: PageFormValues) => {
    pageFormRef.current = next
    setPageForm(next)
  }, [])
  const writeRoadForm = useCallback((next: RoadDetailFormValues) => {
    roadFormRef.current = next
    setRoadForm(next)
  }, [])

  // The last NON-BLANK Code the open road held. Legacy's Code change listener fired on a value
  // change of its model, and a blank Code never reached the model (the required check failed first),
  // so clearing Code and re-picking the same one changed nothing there and reset nothing.
  const lastBallastCodeRef = useRef('')
  /** Load a road into the editor (open, new, close, save echo): its Code is the baseline again. */
  const seedRoadForm = useCallback(
    (next: RoadDetailFormValues) => {
      lastBallastCodeRef.current = next.stBallastMethodCode.trim()
      writeRoadForm(next)
    },
    [writeRoadForm],
  )

  // The validation banner, one keyed line per failing field in legacy order (#359 group C change
  // log). Save and Check Status REPLACE it with the full list; a field's change adds or removes only
  // its own line — the accumulating banner the business area chose for Schedules 4, 7A and 9.
  const [bannerEntries, setBannerEntries] = useState<readonly BannerEntry[]>([])
  // Each text field's value when it took focus. Leaving judges it only if the value now DIFFERS
  // (JSF `onchange`): a focus-and-leave, or typing and then undoing it, judges nothing.
  const focusedPageValuesRef = useRef(new Map<keyof PageFormValues, string>())
  const focusedRoadValuesRef = useRef(new Map<keyof RoadDetailFormValues, string>())

  /**
   * Clears the success/failure/check-status banners, the validation banner, and both editors' red
   * fields, and supersedes any check still in flight — so the validation banner and the red fields
   * never disagree. Called before every action and on every refresh: opening an editor (page, road,
   * new), Save and Check Status (which then re-mark what they find), Copy, Delete, and applying a
   * returned document after a write.
   */
  const clearBanners = () => {
    checkSnapshotVersionRef.current += 1
    clearHookBanners()
    setBannerEntries([])
    setPageErrors({})
    setRoadErrors({})
    focusedPageValuesRef.current.clear()
    focusedRoadValuesRef.current.clear()
  }

  // Set the moment the location is edited: the Road Group on screen was derived by the server from
  // the location as STORED, so any edit makes it stale. Legacy recomputed it on every change; the
  // document does not serve the mapping to do that, so it renders blank until the save echo returns
  // the server's own value. AC6 permits blank; it does not permit a value that no longer matches.
  const [roadGroupStale, setRoadGroupStale] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null)
  // A pending level change, held while the unsaved-changes confirmation is open.
  const [pendingNav, setPendingNav] = useState<(() => void) | null>(null)

  // Closing either editor takes its values off screen, so a verdict that included them is cleared.
  const closePagePanel = useCallback(() => {
    setPagePanelMode('closed')
    setOpenPageId(null)
    writePageForm(emptyPageForm())
    setPageErrors({})
    focusedPageValuesRef.current.clear()
    setBannerEntries([])
    setRoadGroupStale(false)
    invalidateCheckResult()
  }, [invalidateCheckResult, writePageForm])

  const closeRoadPanel = useCallback(() => {
    /* eslint-disable @eslint-react/set-state-in-effect -- also the reset on a URL level change (below) */
    setRoadPanelMode('closed')
    setOpenRoadId(null)
    seedRoadForm(emptyRoadDetailForm())
    setRoadErrors({})
    focusedRoadValuesRef.current.clear()
    setBannerEntries([])
    invalidateCheckResult()
    /* eslint-enable @eslint-react/set-state-in-effect */
  }, [invalidateCheckResult, seedRoadForm])

  const resetTransient = useCallback(() => {
    resetBanners()
    closePagePanel()
    closeRoadPanel()
    setDeleteTarget(null)
    setPendingNav(null)
  }, [resetBanners, closePagePanel, closeRoadPanel])

  const { data, setData, loadState } = useScheduleDocument<Schedule10Response>({
    path: SCHEDULE10_PATH,
    scheduleName: 'Schedule 10',
    header: PAGE_HEADER,
    millId,
    year,
    contextMissing,
    seedForm: () => ({}),
    mapLoadError,
    onReset: resetTransient,
  })

  // Drop a deep link into the road level when the working context actually changes, guarded by a ref
  // so in-app drill-down never resets itself.
  const contextKey = `${String(millId)}:${String(year)}`
  const contextKeyRef = useRef(contextKey)
  useEffect(() => {
    if (contextKeyRef.current !== contextKey) {
      contextKeyRef.current = contextKey
      if (search.pageId !== undefined) {
        void navigate({ to: '/schedule-10', search: {}, replace: true })
      }
    }
  }, [contextKey, navigate, search.pageId])

  // Leaving the road level by ANY route — the browser's Back or Forward included, which bypass the
  // in-app Back — closes the road editor, so its lines and red fields never show on the page list or
  // over another page's roads. The in-app Back has already closed it; doing so again is harmless.
  const roadLevelPageRef = useRef(search.pageId)
  useEffect(() => {
    if (roadLevelPageRef.current !== search.pageId) {
      roadLevelPageRef.current = search.pageId
      closeRoadPanel()
    }
  }, [search.pageId, closeRoadPanel])

  const query = `?millId=${String(millId)}&year=${String(year)}`

  const applyDocument = (doc: Schedule10Response) => {
    setData((prev) => (prev ? { ...doc, codeLists: doc.codeLists ?? prev.codeLists } : doc))
    clearBanners()
    setMessage(doc.message?.text ?? null)
  }

  /**
   * Judge `fields` of the page panel against its current values: each failing field turns red and
   * puts its line in the banner, each passing one loses both. Every other field is left as it was.
   */
  const judgePageFields = (fields: readonly (keyof PageFormValues)[]) => {
    const errors = validatePage(pageFormRef.current)
    setPageErrors((prev) => mergeErrors(prev, errors, fields))
    setBannerEntries((prev) =>
      fields.reduce(
        (entries, field) =>
          setBannerEntry(entries, `page:${field}`, pageBannerEntry(field, errors[field])),
        prev,
      ),
    )
  }

  /** A page text field took focus: remember its value, to compare when it is left. */
  const enterPageField = (key: keyof PageFormValues) => {
    focusedPageValuesRef.current.set(key, pageFormRef.current[key])
  }

  /** True when `key` was left with a value different from the one it had on focus. */
  const leftChanged = <K extends string>(
    focused: Map<K, string>,
    key: K,
    current: string,
  ): boolean => {
    const before = focused.get(key)
    focused.delete(key)
    return before !== undefined && before !== current
  }

  const judgePageFieldAndDependents = (key: keyof PageFormValues) => {
    // Switching the TSA-or-TFL branch blanks the TFL # it no longer uses, so a TFL # line already
    // shown is re-judged with it rather than left standing over a cleared, disabled field.
    const fields: (keyof PageFormValues)[] = [key]
    if (key === 'tsaOrTfl' && pageErrors.tflNumberCode !== undefined) {
      fields.push('tflNumberCode')
    }
    judgePageFields(fields)
  }

  /**
   * A page field was left. Its value differs from the one it had on focus → judge THAT field; a
   * focus-and-leave, or a change undone before leaving, judges nothing. `changed` is a combo box's
   * own report that its value differs from the one it had on focus. A dropdown SELECTION does not
   * come through here: it is judged at once, by {@link setPageField}.
   */
  const leavePageField = (key: keyof PageFormValues, changed = false) => {
    if (!changed && !leftChanged(focusedPageValuesRef.current, key, pageFormRef.current[key])) {
      return
    }
    judgePageFieldAndDependents(key)
  }

  const setPageField = (key: keyof PageFormValues, value: string) => {
    const prev = pageFormRef.current
    const next: PageFormValues = { ...prev, [key]: value }
    // Switching branches clears the half that no longer applies, so a stale value never reaches
    // the wire and the disabled control never shows a leftover.
    if (key === 'tsaOrTfl') {
      const chosen = value.trim()
      if (isTflLocated(value)) {
        next.supplyBlock = ''
      } else {
        next.tflNumberCode = ''
        // Supply blocks are narrowed to the chosen TSA, so a block from the previous TSA no longer
        // belongs to the list it came from. Only an actual CHANGE of TSA can orphan a block:
        // re-selecting the same TSA must leave a stored cross-TSA pair (delivery holds them —
        // TSA `02` carrying block `01D`) exactly as it was. Clearing the control entirely orphans
        // any block, which `startsWith('')` would have let through since it is always true.
        const tsaChanged = chosen !== prev.tsaOrTfl.trim()
        if (chosen === '' || (tsaChanged && !prev.supplyBlock.startsWith(chosen))) {
          next.supplyBlock = ''
        }
      }
    }
    writePageForm(next)
    // The red box and its inline text are NOT cleared while typing: like legacy (and Schedules 4, 7A
    // and 9) the field is re-judged only when it is left after a change — a dropdown on selection.
    // A location edit invalidates the server-derived Road Group, and any page edit invalidates a
    // check-status result the same way a road edit does (R5).
    if (LOCATION_FIELDS.has(key)) {
      setRoadGroupStale(true)
    }
    invalidateCheckResult()
    if (PAGE_CODE_FIELDS.has(key)) {
      judgePageFieldAndDependents(key)
    }
  }

  /** The road editor's twin of {@link judgePageFields}. */
  const judgeRoadFields = (fields: readonly (keyof RoadDetailFormValues)[]) => {
    const errors = validateRoadDetail(roadFormRef.current)
    setRoadErrors((prev) => mergeErrors(prev, errors, fields))
    setBannerEntries((prev) =>
      fields.reduce(
        (entries, field) =>
          setBannerEntry(entries, `road:${field}`, roadBannerEntry(field, errors[field])),
        prev,
      ),
    )
  }

  /** The road editor's twin of {@link enterPageField}. */
  const enterRoadField = (key: keyof RoadDetailFormValues) => {
    focusedRoadValuesRef.current.set(key, roadFormRef.current[key])
  }

  const judgeRoadFieldAndDependents = (key: keyof RoadDetailFormValues) => {
    // A field this change also rewrote (the Code reset, the surface-width mirror) is re-judged with
    // it when it already shows an error, so no line outlives the value it described.
    const rewritten: readonly (keyof RoadDetailFormValues)[] =
      key === 'stBallastMethodCode'
        ? BALLAST_RESET_FIELDS
        : key === 'sgSurfaceWidth'
          ? ['stSurfaceWidth']
          : []
    judgeRoadFields([key, ...rewritten.filter((field) => roadErrors[field] !== undefined)])
  }

  /** The road editor's twin of {@link leavePageField}. */
  const leaveRoadField = (key: keyof RoadDetailFormValues, changed = false) => {
    if (!changed && !leftChanged(focusedRoadValuesRef.current, key, roadFormRef.current[key])) {
      return
    }
    judgeRoadFieldAndDependents(key)
  }

  const setRoadField = (key: keyof RoadDetailFormValues, value: string) => {
    const prev = roadFormRef.current
    let next: RoadDetailFormValues = { ...prev, [key]: value }
    // Legacy copies the sub-grade surface width into the stabilizing width on change,
    // unconditionally and with no dirty check.
    if (key === 'sgSurfaceWidth') {
      next.stSurfaceWidth = value
    }
    // A change of Code resets the figures it governs, as legacy's listener did — measured against
    // the last NON-BLANK Code, since a blank one never reached legacy's model (see the ref).
    if (key === 'stBallastMethodCode') {
      const code = value.trim()
      if (code !== '' && code !== lastBallastCodeRef.current) {
        next = applyBallastMethodReset(next, value)
      }
      if (code !== '') {
        lastBallastCodeRef.current = code
      }
    }
    writeRoadForm(next)
    invalidateCheckResult()
    if (ROAD_CODE_FIELDS.has(key)) {
      judgeRoadFieldAndDependents(key)
    }
  }

  const maskRoadField = (key: MaskedField) => {
    const prev = roadFormRef.current
    const masked = groupFixedInput(prev[key], MASK_DIGITS[key])
    if (masked === prev[key]) {
      return
    }
    const next: RoadDetailFormValues = { ...prev, [key]: masked }
    if (key === 'sgSurfaceWidth') {
      next.stSurfaceWidth = masked
    }
    writeRoadForm(next)
  }

  const openNewPage = () => {
    clearBanners()
    setPagePanelMode('new')
    setOpenPageId(null)
    writePageForm(emptyPageForm())
    setPageErrors({})
    setRoadGroupStale(false)
  }

  const openPage = (page: ConstructionPage, editable: boolean) => {
    clearBanners()
    setPagePanelMode(editable ? 'edit' : 'view')
    setOpenPageId(page.pageId)
    writePageForm(formFromPage(page))
    setPageErrors({})
    setRoadGroupStale(false)
  }

  const savePage = (pages: readonly ConstructionPage[]) => {
    if (saving || pagePanelMode === 'closed' || pagePanelMode === 'view') {
      return
    }
    clearBanners()
    const errors = validatePage(pageForm)
    // Save judges the whole panel and REPLACES the banner with the full list, in legacy order.
    setPageErrors(errors)
    setBannerEntries(pageBannerEntries(errors))
    if (Object.keys(errors).length > 0) {
      return
    }
    const axios = apiService.getAxiosInstance()
    if (pagePanelMode === 'new') {
      run(axios.post<Schedule10Response>(`${PAGES_PATH}${query}`, buildPageBody(pageForm)), {
        fallback: 'Schedule could not be saved.',
        onSuccess: (doc) => {
          applyDocument(doc)
          closePagePanel()
        },
      })
      return
    }
    const stored = pages.find((page) => page.pageId === openPageId)
    // A missing lock token is a real state, not something to coerce: a fabricated 0 would silently
    // defeat the stale-edit check. Returning quietly was worse than the coercion, though — the user
    // pressed Save and nothing at all happened. Say so, using the reload text this case means.
    //
    // The comparison stays LOOSE on purpose (R2): a revisionCount of 0 is a valid token, and
    // `0 == null` is false, so it correctly falls through to the write.
    if (stored?.revisionCount == null) {
      setMessage(null)
      setActionError(SCH10_MESSAGES.staleRecord)
      return
    }
    run(
      axios.put<Schedule10Response>(
        `${PAGES_PATH}/${String(stored.pageId)}${query}`,
        buildPageBody(pageForm, stored.revisionCount),
      ),
      {
        fallback: 'Schedule could not be saved.',
        onSuccess: (doc) => {
          applyDocument(doc)
          const refreshed = doc.pages.find((page) => page.pageId === stored.pageId)
          if (refreshed) {
            writePageForm(formFromPage(refreshed))
            // The echo carries the server's re-derived Road Group, so it is authoritative again.
            setRoadGroupStale(false)
          }
        },
      },
    )
  }

  const copyPage = (page: ConstructionPage) => {
    if (saving) {
      return
    }
    clearBanners()
    run(
      apiService
        .getAxiosInstance()
        .post<Schedule10Response>(`${PAGES_PATH}/${String(page.pageId)}/copy${query}`),
      { fallback: 'Schedule could not be saved.', onSuccess: applyDocument },
    )
  }

  const saveRoadDetail = (page: ConstructionPage) => {
    if (saving || roadPanelMode === 'closed' || roadPanelMode === 'view') {
      return
    }
    clearBanners()
    const errors = validateRoadDetail(roadForm)
    // Save judges the whole editor and REPLACES the banner with the full list, in legacy order.
    setRoadErrors(errors)
    setBannerEntries(roadBannerEntries(errors))
    if (Object.keys(errors).length > 0) {
      return
    }
    const axios = apiService.getAxiosInstance()
    const base = `${PAGES_PATH}/${String(page.pageId)}/road-details`
    if (roadPanelMode === 'new') {
      run(axios.post<Schedule10Response>(`${base}${query}`, buildRoadDetailBody(roadForm)), {
        fallback: 'Schedule could not be saved.',
        onSuccess: (doc) => {
          applyDocument(doc)
          closeRoadPanel()
        },
      })
      return
    }
    const stored = page.roadDetails.find((detail) => detail.roadDetailId === openRoadId)
    if (stored?.revisionCount == null) {
      setMessage(null)
      setActionError(SCH10_MESSAGES.staleRecord)
      return
    }
    run(
      axios.put<Schedule10Response>(
        `${base}/${String(stored.roadDetailId)}${query}`,
        buildRoadDetailBody(roadForm, stored.revisionCount),
      ),
      {
        fallback: 'Schedule could not be saved.',
        onSuccess: (doc) => {
          applyDocument(doc)
          const refreshedPage = doc.pages.find((entry) => entry.pageId === page.pageId)
          const refreshed = refreshedPage?.roadDetails.find(
            (detail) => detail.roadDetailId === stored.roadDetailId,
          )
          if (refreshed) {
            seedRoadForm(formFromRoadDetail(refreshed))
          }
        },
      },
    )
  }

  const confirmDelete = () => {
    if (deleteTarget === null || saving) {
      return
    }
    const target = deleteTarget
    setDeleteTarget(null)
    clearBanners()
    const axios = apiService.getAxiosInstance()
    if (target.kind === 'page') {
      run(axios.delete<Schedule10Response>(`${PAGES_PATH}/${String(target.page.pageId)}${query}`), {
        fallback: 'Unable to delete record.',
        onSuccess: (doc) => {
          applyDocument(doc)
          if (openPageId === target.page.pageId) {
            closePagePanel()
          }
        },
      })
      return
    }
    const pageId = search.pageId
    if (pageId === undefined) {
      return
    }
    run(
      axios.delete<Schedule10Response>(
        `${PAGES_PATH}/${String(pageId)}/road-details/${String(target.detail.roadDetailId)}${query}`,
      ),
      {
        fallback: 'Unable to delete record.',
        onSuccess: (doc) => {
          applyDocument(doc)
          if (openRoadId === target.detail.roadDetailId) {
            closeRoadPanel()
          }
        },
      },
    )
  }

  /**
   * Check Status over the whole schedule, from either level (#359). The body carries the editor open
   * at the level on screen — the page panel at the page level, the road editor at the road level —
   * but only for an EXISTING page or road: legacy built a new one outside the checked list, so an
   * unsaved new page or road is never evaluated. Returns null when Save's validator blocks the open
   * editor, after marking its fields exactly as Save does.
   */
  const buildCheckRequest = (): Schedule10CheckRequest | null => {
    const editable = data?.editable === true
    const roadPage =
      search.pageId === undefined
        ? undefined
        : data?.pages.find((entry) => entry.pageId === search.pageId)
    if (roadPage) {
      // Legacy's Check Status was a full submit, so the open editor's validation blocked it — a NEW
      // road's too, even though a new road is never sent. Gated only while the editor is editable: a
      // View editor highlights nothing, so it must not block. Blocked → the full banner, as Save.
      if (editable && (roadPanelMode === 'edit' || roadPanelMode === 'new')) {
        const errors = validateRoadDetail(roadForm)
        setRoadErrors(errors)
        setBannerEntries(roadBannerEntries(errors))
        if (Object.keys(errors).length > 0) {
          return null
        }
      }
      const roadOpen = roadPanelMode === 'edit' || roadPanelMode === 'view'
      if (!roadOpen || openRoadId === null) {
        return { page: null, road: null }
      }
      return { page: null, road: buildRoadCheckEntry(roadForm, roadPage.pageId, openRoadId) }
    }
    if (editable && (pagePanelMode === 'edit' || pagePanelMode === 'new')) {
      const errors = validatePage(pageForm)
      setPageErrors(errors)
      setBannerEntries(pageBannerEntries(errors))
      if (Object.keys(errors).length > 0) {
        return null
      }
    }
    const pageOpen = pagePanelMode === 'edit' || pagePanelMode === 'view'
    if (!pageOpen || openPageId === null) {
      return { page: null, road: null }
    }
    return { page: buildPageCheckEntry(pageForm, openPageId), road: null }
  }

  const checkStatus = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    const body = buildCheckRequest()
    if (body === null) {
      return
    }
    const submittedSnapshotVersion = checkSnapshotVersionRef.current
    run(
      apiService
        .getAxiosInstance()
        .post<Schedule10CheckStatusResponse>(`${CHECK_STATUS_PATH}${query}`, body),
      {
        fallback: 'Unable to check status.',
        onSuccess: (response) => setCheckResult(summariseCheckStatus(response)),
        // A response — success OR failure — for a superseded snapshot describes values no longer on
        // screen, so it is dropped.
        stillWanted: () => checkSnapshotVersionRef.current === submittedSnapshotVersion,
      },
    )
  }

  /**
   * Legacy confirms a level change whenever a form is open — it has no dirty check anywhere, so the
   * prompt is unconditional. A read-only panel has nothing to lose and goes straight through.
   */
  const guardLevelChange = (panelOpen: boolean, readOnly: boolean, proceed: () => void) => {
    if (!panelOpen || readOnly) {
      proceed()
      return
    }
    setPendingNav(() => proceed)
  }

  if (loadState) return loadState
  if (!data) {
    return null
  }

  const { editable, pages, codeLists = EMPTY_CODE_LISTS } = data
  const controlsDisabled = !editable || saving

  const currentPage =
    search.pageId === undefined ? undefined : pages.find((page) => page.pageId === search.pageId)

  const banners = (
    <ScheduleBanners
      keyPrefix="road"
      message={message}
      actionError={actionError}
      validationErrors={bannerEntries.map((entry) => entry.line)}
      checkResult={checkResult}
    />
  )

  const navConfirm = pendingNav !== null && (
    <Modal
      open
      modalHeading="Confirmation"
      primaryButtonText="Yes"
      secondaryButtonText="No"
      onRequestClose={() => setPendingNav(null)}
      onRequestSubmit={() => {
        const proceed = pendingNav
        setPendingNav(null)
        proceed()
      }}
    >
      <p>{NAV_UNSAVED}</p>
    </Modal>
  )

  const deleteConfirm = deleteTarget !== null && (
    <ConfirmDeleteModal onCancel={() => setDeleteTarget(null)} onConfirm={confirmDelete} />
  )

  // ---- Road level ------------------------------------------------------------------------------
  if (currentPage) {
    return (
      <div className="app-page schedule-page">
        {PAGE_HEADER}
        <Grid fullWidth className="app-page__body">
          {banners}
          <Column sm={4} md={8} lg={16}>
            <RoadDetailPage
              page={currentPage}
              codeLists={codeLists}
              editable={editable}
              saving={saving}
              panelMode={roadPanelMode}
              openDetailId={openRoadId}
              form={roadForm}
              errors={roadErrors}
              onOpenNew={() => {
                clearBanners()
                setRoadPanelMode('new')
                setOpenRoadId(null)
                seedRoadForm(emptyRoadDetailForm())
                setRoadErrors({})
              }}
              onOpenDetail={(detail) => {
                clearBanners()
                setRoadPanelMode(editable ? 'edit' : 'view')
                setOpenRoadId(detail.roadDetailId)
                seedRoadForm(formFromRoadDetail(detail))
                setRoadErrors({})
              }}
              onCloseForm={closeRoadPanel}
              onSave={() => saveRoadDetail(currentPage)}
              onCheckStatus={checkStatus}
              onRequestDelete={(detail) => setDeleteTarget({ kind: 'road', detail })}
              onBack={() =>
                guardLevelChange(roadPanelMode !== 'closed', roadPanelMode === 'view', () => {
                  closeRoadPanel()
                  // A delete confirmation left open across a level change would still be mounted
                  // over the page list, and its Yes would silently do nothing — confirmDelete needs
                  // the road level's pageId.
                  setDeleteTarget(null)
                  void navigate({ to: '/schedule-10', search: {}, replace: true })
                })
              }
              onChange={setRoadField}
              onMask={maskRoadField}
              onEnter={enterRoadField}
              onLeave={leaveRoadField}
            />
          </Column>
        </Grid>
        {navConfirm}
        {deleteConfirm}
      </div>
    )
  }

  // ---- Page level ------------------------------------------------------------------------------
  const panelOpen = pagePanelMode !== 'closed'
  const openStoredPage = pages.find((page) => page.pageId === openPageId)

  const pageModeWord = pagePanelMode === 'view' ? 'View' : 'Edit'
  const pagePanelHeading =
    pagePanelMode === 'new'
      ? 'New Page'
      : `${pageModeWord} Page — ${openStoredPage?.pageLabel ?? ''}`

  return (
    <div className="app-page schedule-page">
      {PAGE_HEADER}
      <Grid fullWidth className="app-page__body">
        {banners}

        <Column sm={4} md={8} lg={16} className="schedule-10__actions">
          <Button kind="primary" renderIcon={Add} disabled={controlsDisabled} onClick={openNewPage}>
            Add New Page
          </Button>
          <Button
            kind="tertiary"
            renderIcon={CheckmarkOutline}
            disabled={controlsDisabled}
            onClick={checkStatus}
          >
            Check Status
          </Button>
        </Column>

        <Column sm={4} md={8} lg={16}>
          <TableContainer title="Page Summary" className="schedule-10__section">
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeader>New Road Construction Pages</TableHeader>
                  <TableHeader>Action</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {pages.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={2}>{EMPTY_LIST}</TableCell>
                  </TableRow>
                ) : (
                  pages.map((page) => {
                    // The page already open in the panel below cannot act on itself; greying its
                    // row actions and highlighting the row is how the open page is marked.
                    const isOpen = openPageId === page.pageId && panelOpen
                    return (
                      <TableRow
                        key={page.pageId}
                        className={isOpen ? 'schedule-10__row--editing' : undefined}
                      >
                        <TableCell>{page.pageLabel}</TableCell>
                        <TableCell>
                          <div className="schedule-10__row-actions">
                            <Button
                              kind="ghost"
                              size="sm"
                              renderIcon={editable ? Edit : View}
                              disabled={saving || isOpen}
                              onClick={() => openPage(page, editable)}
                            >
                              {editable ? 'Edit' : 'View'}
                            </Button>
                            <Button
                              kind="danger--tertiary"
                              size="sm"
                              renderIcon={TrashCan}
                              disabled={controlsDisabled || isOpen}
                              onClick={() => setDeleteTarget({ kind: 'page', page })}
                            >
                              Delete
                            </Button>
                            <Button
                              kind="ghost"
                              size="sm"
                              renderIcon={Copy}
                              disabled={controlsDisabled || isOpen}
                              onClick={() => copyPage(page)}
                            >
                              Copy
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    )
                  })
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Column>

        {panelOpen && (
          <Column sm={4} md={8} lg={16} className="schedule-10__section">
            <div className="schedule-10__panel">
              <h3 className="schedule-10__heading">{pagePanelHeading}</h3>
              <PageFields
                idPrefix={pagePanelMode === 'new' ? 'page-new' : `page-${String(openPageId ?? 0)}`}
                form={pageForm}
                errors={pageErrors}
                codeLists={codeLists}
                disabled={controlsDisabled}
                readOnly={pagePanelMode === 'view'}
                roadGroup={roadGroupStale ? null : (openStoredPage?.roadGroup ?? null)}
                onChange={setPageField}
                onEnter={enterPageField}
                onLeave={leavePageField}
              />

              {/* A page must be saved before it can hold roads, so the link appears only once the
                  page exists — matching the legacy link's own render condition. */}
              {openStoredPage && (
                <div className="schedule-10__enter-road">
                  <Button
                    kind="ghost"
                    onClick={() =>
                      guardLevelChange(true, pagePanelMode === 'view', () => {
                        // Discard before navigating, exactly as the road level's Back does. Without
                        // this the panel still holds the edit the user was told was lost, and coming
                        // back and pressing Save writes it against a freshly re-read revisionCount,
                        // so the optimistic lock passes and the discarded data persists.
                        closePagePanel()
                        void navigate({
                          to: '/schedule-10',
                          search: { pageId: openStoredPage.pageId },
                        })
                      })
                    }
                  >
                    {`Enter Road Data (${String(openStoredPage.roadDetailCount)})`}
                  </Button>
                </div>
              )}

              <div className="schedule-10__panel-actions">
                {/* AC11 and deviation 7: every write control stays RENDERED and disabled outside
                    Draft, never removed from the DOM. `savePage` refuses a `view` panel anyway. */}
                <Button
                  kind="primary"
                  disabled={controlsDisabled || pagePanelMode === 'view'}
                  renderIcon={Save}
                  onClick={() => savePage(pages)}
                >
                  Save
                </Button>
                {/* Close discards silently, as legacy does — only the two level changes confirm. */}
                <Button kind="secondary" renderIcon={Close} onClick={closePagePanel}>
                  Close
                </Button>
              </div>
            </div>
          </Column>
        )}
      </Grid>
      {navConfirm}
      {deleteConfirm}
    </div>
  )
}

export default Schedule10
