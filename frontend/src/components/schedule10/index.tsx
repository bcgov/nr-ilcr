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
import { getRouteApi } from '@tanstack/react-router'
import type Schedule10Response from '@/interfaces/Schedule10Response'
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
import { clearFieldError } from '@/utils/forms'
import { groupFixedInput } from '@/utils/number'
import ConfirmDeleteModal from '@/components/core/ConfirmDeleteModal'
import ScheduleBanners from '@/components/core/ScheduleBanners'
import { renderScheduleLoadState } from '@/components/core/ScheduleLoadState'
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
  MASK_DIGITS,
  buildPageBody,
  buildRoadDetailBody,
  emptyPageForm,
  emptyRoadDetailForm,
  formFromPage,
  formFromRoadDetail,
  isTflLocated,
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
    setCheckResult,
    clearBanners,
    resetBanners,
    run,
  } = useScheduleBanners<Schedule10CheckSummary>(isCurrent)

  const [pagePanelMode, setPagePanelMode] = useState<PanelMode>('closed')
  const [openPageId, setOpenPageId] = useState<number | null>(null)
  const [pageForm, setPageForm] = useState<PageFormValues>(emptyPageForm)
  const [pageErrors, setPageErrors] = useState<PageErrors>({})

  const [roadPanelMode, setRoadPanelMode] = useState<PanelMode>('closed')
  const [openRoadId, setOpenRoadId] = useState<number | null>(null)
  const [roadForm, setRoadForm] = useState<RoadDetailFormValues>(emptyRoadDetailForm)
  const [roadErrors, setRoadErrors] = useState<RoadDetailErrors>({})

  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null)
  // A pending level change, held while the unsaved-changes confirmation is open.
  const [pendingNav, setPendingNav] = useState<(() => void) | null>(null)

  const closePagePanel = useCallback(() => {
    setPagePanelMode('closed')
    setOpenPageId(null)
    setPageForm(emptyPageForm())
    setPageErrors({})
  }, [])

  const closeRoadPanel = useCallback(() => {
    setRoadPanelMode('closed')
    setOpenRoadId(null)
    setRoadForm(emptyRoadDetailForm())
    setRoadErrors({})
  }, [])

  const resetTransient = useCallback(() => {
    resetBanners()
    closePagePanel()
    closeRoadPanel()
    setDeleteTarget(null)
    setPendingNav(null)
  }, [resetBanners, closePagePanel, closeRoadPanel])

  const { data, setData, errorDetail, isLoading } = useScheduleDocument<Schedule10Response>({
    path: SCHEDULE10_PATH,
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

  const query = `?millId=${String(millId)}&year=${String(year)}`

  const applyDocument = (doc: Schedule10Response) => {
    setData((prev) => (prev ? { ...doc, codeLists: doc.codeLists ?? prev.codeLists } : doc))
    clearBanners()
    setMessage(doc.message?.text ?? null)
  }

  const setPageField = (key: keyof PageFormValues, value: string) => {
    setPageForm((prev) => {
      const next: PageFormValues = { ...prev, [key]: value }
      // Switching branches clears the half that no longer applies, so a stale value never reaches
      // the wire and the disabled control never shows a leftover.
      if (key === 'tsaOrTfl') {
        if (isTflLocated(value)) {
          next.supplyBlock = ''
        } else {
          next.tflNumberCode = ''
          // Supply blocks are narrowed to the chosen TSA, so a block from the previous TSA no
          // longer belongs to the list it came from.
          if (!prev.supplyBlock.startsWith(value.trim())) {
            next.supplyBlock = ''
          }
        }
      }
      return next
    })
    setPageErrors((prev) => clearFieldError(prev, key))
  }

  const setRoadField = (key: keyof RoadDetailFormValues, value: string) => {
    setRoadForm((prev) => {
      const next: RoadDetailFormValues = { ...prev, [key]: value }
      // Legacy copies the sub-grade surface width into the stabilizing width on change,
      // unconditionally and with no dirty check.
      if (key === 'sgSurfaceWidth') {
        next.stSurfaceWidth = value
      }
      return next
    })
    setRoadErrors((prev) => clearFieldError(prev, key))
    setCheckResult(null)
  }

  const maskRoadField = (key: MaskedField) => {
    setRoadForm((prev) => {
      const masked = groupFixedInput(prev[key], MASK_DIGITS[key])
      if (masked === prev[key]) {
        return prev
      }
      const next: RoadDetailFormValues = { ...prev, [key]: masked }
      if (key === 'sgSurfaceWidth') {
        next.stSurfaceWidth = masked
      }
      return next
    })
  }

  const openNewPage = () => {
    clearBanners()
    setPagePanelMode('new')
    setOpenPageId(null)
    setPageForm(emptyPageForm())
    setPageErrors({})
  }

  const openPage = (page: ConstructionPage, editable: boolean) => {
    clearBanners()
    setPagePanelMode(editable ? 'edit' : 'view')
    setOpenPageId(page.pageId)
    setPageForm(formFromPage(page))
    setPageErrors({})
  }

  const savePage = (pages: readonly ConstructionPage[]) => {
    if (saving || pagePanelMode === 'closed' || pagePanelMode === 'view') {
      return
    }
    clearBanners()
    const errors = validatePage(pageForm)
    setPageErrors(errors)
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
    // defeat the stale-edit check.
    if (!stored || stored.revisionCount == null) {
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
            setPageForm(formFromPage(refreshed))
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
    setRoadErrors(errors)
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
    if (!stored || stored.revisionCount == null) {
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
            setRoadForm(formFromRoadDetail(refreshed))
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

  const checkStatus = () => {
    if (!data || saving) {
      return
    }
    clearBanners()
    run(
      apiService
        .getAxiosInstance()
        .post<Schedule10CheckStatusResponse>(`${CHECK_STATUS_PATH}${query}`),
      {
        fallback: 'Unable to check status.',
        onSuccess: (response) => setCheckResult(summariseCheckStatus(response)),
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

  const loadState = renderScheduleLoadState({
    header: PAGE_HEADER,
    scheduleName: 'Schedule 10',
    contextMissing,
    isLoading,
    errorDetail,
  })
  if (loadState) {
    return loadState
  }
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
                setRoadForm(emptyRoadDetailForm())
                setRoadErrors({})
              }}
              onOpenDetail={(detail) => {
                clearBanners()
                setRoadPanelMode(editable ? 'edit' : 'view')
                setOpenRoadId(detail.roadDetailId)
                setRoadForm(formFromRoadDetail(detail))
                setRoadErrors({})
              }}
              onCloseForm={closeRoadPanel}
              onSave={() => saveRoadDetail(currentPage)}
              onRequestDelete={(detail) => setDeleteTarget({ kind: 'road', detail })}
              onBack={() =>
                guardLevelChange(roadPanelMode !== 'closed', roadPanelMode === 'view', () => {
                  closeRoadPanel()
                  void navigate({ to: '/schedule-10', search: {}, replace: true })
                })
              }
              onChange={setRoadField}
              onMask={maskRoadField}
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

  return (
    <div className="app-page schedule-page">
      {PAGE_HEADER}
      <Grid fullWidth className="app-page__body">
        {banners}

        <Column sm={4} md={8} lg={16} className="schedule-10__actions schedule-10__actions--top">
          <Button
            kind="primary"
            disabled={controlsDisabled || !panelOpen || pagePanelMode === 'view'}
            onClick={() => savePage(pages)}
          >
            Save
          </Button>
          <Button kind="tertiary" disabled={controlsDisabled} onClick={checkStatus}>
            Check Status
          </Button>
        </Column>

        <Column sm={4} md={8} lg={16}>
          <TableContainer title="Page Summary" className="schedule-10__section">
            <Table aria-label="Construction pages">
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
                    // row actions is how legacy marks which page is being edited.
                    const isOpen = openPageId === page.pageId && panelOpen
                    return (
                      <TableRow key={page.pageId}>
                        <TableCell className="schedule-10__label-cell">{page.pageLabel}</TableCell>
                        <TableCell>
                          <div className="schedule-10__row-actions">
                            <Button
                              kind="ghost"
                              size="sm"
                              disabled={saving || isOpen}
                              onClick={() => openPage(page, editable)}
                            >
                              {editable ? 'Edit' : 'View'}
                            </Button>
                            <Button
                              kind="danger--ghost"
                              size="sm"
                              disabled={controlsDisabled || isOpen}
                              onClick={() => setDeleteTarget({ kind: 'page', page })}
                            >
                              Delete
                            </Button>
                            <Button
                              kind="ghost"
                              size="sm"
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

          <div className="schedule-10__actions schedule-10__actions--centred">
            <Button kind="primary" disabled={controlsDisabled} onClick={openNewPage}>
              Add
            </Button>
          </div>
        </Column>

        {panelOpen && (
          <Column sm={4} md={8} lg={16} className="schedule-10__section">
            <h3 className="schedule-10__heading">
              {pagePanelMode === 'new' ? 'New Page Data' : (openStoredPage?.pageLabel ?? 'Page')}
            </h3>
            <PageFields
              idPrefix={pagePanelMode === 'new' ? 'page-new' : `page-${String(openPageId ?? 0)}`}
              form={pageForm}
              errors={pageErrors}
              codeLists={codeLists}
              disabled={controlsDisabled}
              readOnly={pagePanelMode === 'view'}
              roadGroup={openStoredPage?.roadGroup ?? null}
              onChange={setPageField}
            />

            {/* A page must be saved before it can hold roads, so the link appears only once the
                page exists — matching the legacy link's own render condition. */}
            {openStoredPage && (
              <div className="schedule-10__enter-road">
                <Button
                  kind="ghost"
                  onClick={() =>
                    guardLevelChange(true, pagePanelMode === 'view', () => {
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
              {pagePanelMode !== 'view' && (
                <Button kind="primary" disabled={controlsDisabled} onClick={() => savePage(pages)}>
                  Save
                </Button>
              )}
              {/* Close discards silently, as legacy does — only the two level changes confirm. */}
              <Button kind="secondary" onClick={closePagePanel}>
                Close
              </Button>
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
