import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@/test-utils'

// TipTap is mocked so the wrapper's own logic runs without ProseMirror's jsdom friction: useEditor
// captures the options (so we can fire onUpdate), and EditorContent renders a marker. This exercises
// RichTextEditor's JSX, the invalid branch, and the onUpdate -> onChange(getHTML()) wiring.
type EditorOptions = {
  content: string
  onUpdate: (arg: { editor: { getHTML: () => string } }) => void
}
let captured: EditorOptions | null = null

vi.mock('@tiptap/react', () => ({
  useEditor: (options: EditorOptions) => {
    captured = options
    return { getHTML: () => options.content }
  },
  EditorContent: () => <div data-testid="editor-content" />,
}))
vi.mock('@tiptap/starter-kit', () => ({ default: {} }))

import RichTextEditor from '@/components/homeContent/RichTextEditor'

describe('RichTextEditor', () => {
  test('renders the label and editor, and forwards edits as HTML', () => {
    captured = null
    const onChange = vi.fn()
    render(
      <RichTextEditor id="rte" label="Licensee Message" value="<p>seed</p>" onChange={onChange} />,
    )

    expect(screen.getByText('Licensee Message')).toBeInTheDocument()
    expect(screen.getByTestId('editor-content')).toBeInTheDocument()
    expect(captured?.content).toBe('<p>seed</p>')

    // Simulate a TipTap update: onUpdate should relay the current HTML to onChange.
    captured?.onUpdate({ editor: { getHTML: () => '<p>edited</p>' } })
    expect(onChange).toHaveBeenCalledWith('<p>edited</p>')
  })

  test('shows the requirement text when invalid', () => {
    render(
      <RichTextEditor
        id="rte"
        label="Licensee Message"
        value=""
        invalid
        invalidText="Value is required."
        onChange={vi.fn()}
      />,
    )
    expect(screen.getByText('Value is required.')).toBeInTheDocument()
  })
})
