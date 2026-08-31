import type { ReactNode } from 'react'
import { Button, Column, Grid } from '@carbon/react'
import ConfirmNavigationModal from '@/components/core/ConfirmNavigationModal'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import type { EditableCostRows, EditableRowsDoc } from '@/hooks/useEditableCostRows'
import './index.scss'

// Client-side chrome (verbatim legacy text); SUC-* come from the API message.text (AD-8).
const ERR_MILL_YEAR_NOT_SELECTED = 'Please Select Mill and Reporting Year in the Home Page.'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'

interface Props<TDoc extends EditableRowsDoc> {
  readonly editor: EditableCostRows<TDoc>
  /**
   * The parent schedule's name (e.g. "Schedule 3"), rendered as the tombstone title. Plain text, not
   * a link — up-navigation is the Back button.
   */
  readonly scheduleName: string
  readonly title: string
  readonly subtitle?: string
  /** The back button label (e.g. "Back") — also used for the error-state back button. */
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
  scheduleName,
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

  // Match the schedule pages' tombstone header: the parent schedule is the title, and the sub-page
  // name (plus any extra subtitle) forms the crumb trail beneath it.
  const subPageTrail = subtitle ? [title, subtitle] : title
  const header = <ScheduleTombstone title={scheduleName} subtitle={subPageTrail} />

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
          <Button kind="secondary" size="md" onClick={onBack}>
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
              size="md"
              // Greyed out until there is data to save (and while saving) — legacy parity.
              disabled={saving || rows.length === 0}
              onClick={handleSave}
            >
              Save
            </Button>
          )}
          <Button kind="secondary" size="md" onClick={handleBack}>
            {backLabel}
          </Button>
        </Column>
      </Grid>

      {editable && (
        <ConfirmNavigationModal
          open={confirmBackOpen}
          heading="Leave page"
          onCancel={() => setConfirmBackOpen(false)}
          onContinue={confirmBack}
        >
          {CONFIRM_NAVIGATION}
        </ConfirmNavigationModal>
      )}
    </div>
  )
}
