import type { ReactNode } from 'react'
import { Button, Column, Grid, Modal } from '@carbon/react'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import PageTitle, { type BreadCrumb } from '@/components/core/PageTitle'
import type { EditableCostRows, EditableRowsDoc } from '@/hooks/useEditableCostRows'
import './index.scss'

// Client-side chrome (verbatim legacy text); SUC-* come from the API message.text (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'

interface Props<TDoc extends EditableRowsDoc> {
  readonly editor: EditableCostRows<TDoc>
  /** Ancestor trail shown above the title (e.g. ILCR → Schedule 3). */
  readonly breadCrumbs?: BreadCrumb[]
  readonly title: string
  readonly subtitle?: string
  /** "Back to Schedule N" — also the error-state back button. */
  readonly backLabel: string
  readonly loadingLabel: string
  readonly errorTitle: string
  /** The page-specific panels (add + list), rendered once data is present. */
  readonly children: (data: TDoc) => ReactNode
}

/**
 * Shared chrome for the editable cost sub-pages (Schedule 1 Other Costs, Schedule 3 Included
 * Unacceptable / Other Costs): page title, the mill-year / loading / load-error guard states, the
 * success/error notifications, the Save + Back action row, and the unsaved-changes Back modal. Pages
 * supply only their own panels via {@code children}; all shared behaviour lives in
 * {@link useEditableCostRows}. Keeps the pages free of duplicated boilerplate.
 */
export default function EditableSubPageLayout<TDoc extends EditableRowsDoc>({
  editor,
  breadCrumbs,
  title,
  subtitle,
  backLabel,
  loadingLabel,
  errorTitle,
  children,
}: Props<TDoc>) {
  const {
    contextMissing,
    data,
    isLoading,
    errorDetail,
    message,
    actionError,
    saving,
    rows,
    confirmBackOpen,
    setConfirmBackOpen,
    handleSave,
    handleBack,
    confirmBack,
    onBack,
  } = editor

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle breadCrumbs={breadCrumbs} title={title} subtitle={subtitle} />
    </Grid>
  )

  if (contextMissing) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: 'Mill and Reporting Year required',
          subtitle: ERR_MILL_YEAR_NOT_SELECTED,
        }}
      />
    )
  }

  if (isLoading) {
    return (
      <PageState header={header}>
        <Column sm={4} md={8} lg={16}>
          <LoadingScreen label={loadingLabel} />
        </Column>
      </PageState>
    )
  }

  if (errorDetail) {
    return (
      <PageState
        header={header}
        notification={{ kind: 'error', title: errorTitle, subtitle: errorDetail }}
      >
        <Column sm={4} md={8} lg={16}>
          <Button kind="secondary" onClick={onBack}>
            {backLabel}
          </Button>
        </Column>
      </PageState>
    )
  }

  if (!data) {
    return null
  }

  const editable = data.editable

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {message && <NotificationColumn kind="success" title="Success" subtitle={message} />}
        {actionError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={actionError} />
        )}

        {children(data)}

        <Column sm={4} md={8} lg={16} className="editable-subpage__actions">
          {editable && (
            <Button
              kind="primary"
              // Greyed out until there is data to save (and while saving) — legacy parity.
              disabled={saving || rows.length === 0}
              onClick={handleSave}
            >
              Save
            </Button>
          )}
          <Button kind="secondary" onClick={handleBack}>
            {backLabel}
          </Button>
        </Column>
      </Grid>

      {editable && (
        <Modal
          open={confirmBackOpen}
          modalHeading="Leave page"
          primaryButtonText="Continue"
          secondaryButtonText="Cancel"
          onRequestClose={() => setConfirmBackOpen(false)}
          onRequestSubmit={confirmBack}
        >
          <p>{CONFIRM_NAVIGATION}</p>
        </Modal>
      )}
    </div>
  )
}
