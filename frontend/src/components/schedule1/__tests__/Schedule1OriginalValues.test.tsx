import type { ReactNode } from 'react'
import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen, waitFor } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'

// PageTitle / TanStack Link throw outside a RouterProvider, exactly as in Schedule1.test.tsx.
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: ReactNode }) => children,
}))

import Schedule1 from '@/components/schedule1'

const URL = 'http://localhost:3000/api/v1/schedule1'

/**
 * Schedule 1's original-value indicators (Story 16.2, UC-CHK-005/UC-CHK-010 BR-04).
 *
 * The figures are mill 12050 / 2016 / category 1's real divergence on the delivery database —
 * volume 60002 against a submitted 60000, cost 7000 against a submitted 600 — so the fixture and a
 * manual check on the real data tell the same story.
 */
const submittedDoc = (overrides: Record<string, unknown> = {}) => ({
  millId: 514,
  year: 2021,
  trackStatus: 'S',
  // An administrator correcting at Submitted (Story 16.1's matrix).
  editable: true,
  crownVolume: 60002,
  schedule3CrownVolume: null,
  revisionCount: 3,
  comments: 'the ministry corrected this',
  originalValues: {
    comments: {
      value: 'what the mill actually reported',
      tooltip: 'Original Submission Value: what the mill actually reported',
    },
  },
  lineItems: [
    {
      costItemCode: 12,
      volume: 60002,
      cost: 7000,
      perUnit: 0.12,
      originalValues: {
        volume: { value: '60000', tooltip: 'Original Submission Value: 60,000' },
        cost: { value: '600', tooltip: 'Original Submission Value: 600' },
      },
    },
  ],
  silviculture: { actualSpent: null, accruedLessActual: null, lessAdmin: null, total: null },
  forestMgmtAdminCost: null,
  lessSilvAdminCost: null,
  otherCosts: { volume: null, costSubtotal: 0, perUnit: null, count: 0 },
  subtotalCompanyLoggingCost: 7000,
  subtotalCompanyLoggingPerUnit: null,
  totalSilvicultureCost: null,
  totalSilviculturePerUnit: null,
  totalCompanyLoggingCost: 7000,
  totalCompanyLoggingPerUnit: null,
  forestMgmtAdminPerUnit: null,
  lessSilvAdminPerUnit: null,
  warnings: [],
  ...overrides,
})

const serve = (doc: unknown) => {
  server.use(http.get(URL, () => HttpResponse.json(doc)))
}

const volumeField = () => screen.getByLabelText('Standing Tree to Loaded Truck volume')

describe('Schedule 1 — original-value indicators', () => {
  it('shows an indicator on a corrected value, carrying the submitted value verbatim', async () => {
    serve(submittedDoc())
    render(<Schedule1 />)

    const indicator = await waitFor(() => screen.getByTestId('original-value-volume'))

    // The accessible name names the field AND the submitted value, so a screen-reader user does not
    // have to infer which cell changed from its position in the table. Never colour alone (NFR1).
    expect(indicator).toHaveAttribute(
      'aria-label',
      'Standing Tree to Loaded Truck volume: Original Submission Value: 60,000',
    )
    // A real focusable control, so the tooltip is reachable without a pointer.
    expect(indicator.tagName).toBe('BUTTON')
  })

  it('shows one for the cost too, and for the comments field', async () => {
    serve(submittedDoc())
    render(<Schedule1 />)

    await waitFor(() => expect(screen.getByTestId('original-value-cost')).toBeInTheDocument())
    // Legacy gave the comments textarea an indicator as well, icon and tooltip only
    // (schedule1.xhtml:774-785).
    expect(screen.getByTestId('original-value-comments')).toHaveAttribute(
      'aria-label',
      'Comments: Original Submission Value: what the mill actually reported',
    )
  })

  it('renders NO indicator at Draft, whatever the values are', async () => {
    // The isSubmit() gate: at Draft the server sends no originalValues at all and the page must be
    // exactly as it was before this feature existed.
    serve(
      submittedDoc({
        trackStatus: 'D',
        originalValues: undefined,
        lineItems: [{ costItemCode: 12, volume: 60002, cost: 7000, perUnit: 0.12 }],
      }),
    )
    render(<Schedule1 />)

    await waitFor(() => expect(volumeField()).toBeInTheDocument())
    expect(screen.queryByTestId('original-value-volume')).not.toBeInTheDocument()
    expect(screen.queryByTestId('original-value-comments')).not.toBeInTheDocument()
  })

  it('renders no indicator for a value that matches what was submitted — CHK-005 S09', async () => {
    serve(
      submittedDoc({
        lineItems: [
          {
            costItemCode: 12,
            volume: 60000,
            cost: 7000,
            perUnit: 0.12,
            originalValues: {
              volume: { value: '60000', tooltip: 'Original Submission Value: 60,000' },
            },
          },
        ],
      }),
    )
    render(<Schedule1 />)

    await waitFor(() => expect(volumeField()).toBeInTheDocument())
    expect(screen.queryByTestId('original-value-volume')).not.toBeInTheDocument()
  })

  it('appears as you type and clears when the value is reverted — CHK-010 S05', async () => {
    // The live behaviour, and the reason the API serves the submitted value rather than a
    // server-computed "differs" flag: legacy re-rendered the icon from the field's own change event
    // (schedule1.xhtml:113), so the comparison runs against the UNSAVED value and no save is needed
    // to see the indicator appear or clear.
    serve(
      submittedDoc({
        lineItems: [
          {
            costItemCode: 12,
            volume: 60000,
            cost: 7000,
            perUnit: 0.12,
            originalValues: {
              volume: { value: '60000', tooltip: 'Original Submission Value: 60,000' },
            },
          },
        ],
      }),
    )
    render(<Schedule1 />)

    const field = await waitFor(() => volumeField())
    expect(screen.queryByTestId('original-value-volume')).not.toBeInTheDocument()

    // Correct it: the indicator appears without a save or a reload.
    await userEvent.clear(field)
    await userEvent.paste('60002')
    await waitFor(() => expect(screen.getByTestId('original-value-volume')).toBeInTheDocument())

    // Revert it: the indicator clears again.
    await userEvent.clear(field)
    await userEvent.paste('60000')
    await waitFor(() =>
      expect(screen.queryByTestId('original-value-volume')).not.toBeInTheDocument(),
    )
  })

  it('flags a value added since submission, saying so in its own words', async () => {
    // No key for `cost` means nothing was submitted for it — roughly half the live rows are in this
    // state. Legacy showed its tooltip trailing off after the colon; this says what it means.
    serve(
      submittedDoc({
        lineItems: [
          {
            costItemCode: 12,
            volume: 60000,
            cost: 7000,
            perUnit: 0.12,
            originalValues: {
              volume: { value: '60000', tooltip: 'Original Submission Value: 60,000' },
            },
          },
        ],
      }),
    )
    render(<Schedule1 />)

    const indicator = await waitFor(() => screen.getByTestId('original-value-cost'))

    expect(indicator).toHaveAttribute(
      'aria-label',
      'Standing Tree to Loaded Truck cost: No value was originally submitted by the Licensee',
    )
  })
})
