import { vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { render, screen } from '@/test-utils'
import userEvent from '@testing-library/user-event'
import { server } from '@/test-setup'
import HomeContent from '@/components/homeContent'

// The TipTap editor is mocked to a plain textarea so the page logic (load / save-all / FLD-001) is
// testable without ProseMirror's jsdom friction. The editor itself is a thin presentational wrapper.
vi.mock('@/components/homeContent/RichTextEditor', () => ({
  default: ({
    id,
    label,
    value,
    invalid,
    invalidText,
    onChange,
  }: {
    id: string
    label: string
    value: string
    invalid?: boolean
    invalidText?: string
    onChange: (html: string) => void
  }) => (
    <div>
      <textarea
        id={id}
        aria-label={label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
      {invalid && <span role="alert">{invalidText}</span>}
    </div>
  ),
}))

const ENDPOINT = 'http://localhost:3000/api/v1/home-content'
const SEED = [
  { role: 'LICENSEE', messageText: '<p>Lic</p>' },
  { role: 'AUDITOR', messageText: '<p>Aud</p>' },
  { role: 'ADMIN', messageText: '<p>Adm</p>' },
]

describe('Content Editing (Story 24.2)', () => {
  test('loads the three role messages and saves all together', async () => {
    const put = vi.fn()
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json(SEED)),
      http.put(ENDPOINT, async ({ request }) => {
        put(await request.json())
        return HttpResponse.json({
          messageKey: 'dataSavedSuccesfullyInfoMsg',
          message: 'Data saved successfully',
          entries: SEED,
        })
      }),
    )
    render(<HomeContent />)

    expect(await screen.findByDisplayValue('<p>Lic</p>')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(put).toHaveBeenCalledWith({
      licensee: '<p>Lic</p>',
      auditor: '<p>Aud</p>',
      administrator: '<p>Adm</p>',
    })
  })

  test('a blank editor blocks the save with FLD-001 and makes no PUT', async () => {
    const put = vi.fn()
    server.use(
      http.get(ENDPOINT, () =>
        HttpResponse.json([
          { role: 'LICENSEE', messageText: '' },
          { role: 'AUDITOR', messageText: '<p>Aud</p>' },
          { role: 'ADMIN', messageText: '<p>Adm</p>' },
        ]),
      ),
      http.put(ENDPOINT, async ({ request }) => {
        put(await request.json())
        return HttpResponse.json({ messageKey: '', message: '', entries: [] })
      }),
    )
    render(<HomeContent />)

    await screen.findByLabelText('Administrator Welcome Message')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(
      await screen.findByText('Licensee Welcome Message: Value is required.'),
    ).toBeInTheDocument()
    expect(put).not.toHaveBeenCalled()
  })

  test('an edited message is sent on save', async () => {
    const put = vi.fn()
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json(SEED)),
      http.put(ENDPOINT, async ({ request }) => {
        put(await request.json())
        return HttpResponse.json({
          messageKey: '',
          message: 'Data saved successfully',
          entries: SEED,
        })
      }),
    )
    render(<HomeContent />)

    const licensee = await screen.findByLabelText('Licensee Welcome Message')
    await userEvent.clear(licensee)
    await userEvent.type(licensee, '<p>New licensee text</p>')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()
    expect(put).toHaveBeenCalledWith(
      expect.objectContaining({ licensee: '<p>New licensee text</p>' }),
    )
  })

  test('a load failure surfaces the error and disables save', async () => {
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json({ detail: 'Boom on load.' }, { status: 500 })),
    )
    render(<HomeContent />)

    expect(await screen.findByText('Boom on load.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
  })

  test('a 400 with per-field messages shows each verbatim (deduped)', async () => {
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json(SEED)),
      http.put(ENDPOINT, () =>
        HttpResponse.json(
          {
            messages: [
              { text: 'Licensee message is too long.' },
              { text: 'Auditor message is too long.' },
              { text: 'Licensee message is too long.' },
            ],
          },
          { status: 400 },
        ),
      ),
    )
    render(<HomeContent />)

    await screen.findByLabelText('Administrator Welcome Message')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Licensee message is too long.')).toBeInTheDocument()
    expect(screen.getByText('Auditor message is too long.')).toBeInTheDocument()
    // Deduped: the repeated licensee message renders once.
    expect(screen.getAllByText('Licensee message is too long.')).toHaveLength(1)
  })

  test('a 400 with only a detail falls back to it', async () => {
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json(SEED)),
      http.put(ENDPOINT, () =>
        HttpResponse.json({ detail: 'Home content not found.' }, { status: 400 }),
      ),
    )
    render(<HomeContent />)

    await screen.findByLabelText('Administrator Welcome Message')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Home content not found.')).toBeInTheDocument()
  })

  test('a save failure with no problem body shows the generic fallback', async () => {
    server.use(
      http.get(ENDPOINT, () => HttpResponse.json(SEED)),
      http.put(ENDPOINT, () => HttpResponse.error()),
    )
    render(<HomeContent />)

    await screen.findByLabelText('Administrator Welcome Message')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Unable to save the Home content.')).toBeInTheDocument()
  })
})
