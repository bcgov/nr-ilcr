import type { FC } from 'react'
import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'

interface RichTextEditorProps {
  id: string
  label: string
  /** Initial HTML — the editor is mounted after the content loads, so this seeds it once. */
  value: string
  invalid?: boolean
  invalidText?: string
  onChange: (html: string) => void
}

/**
 * A minimal TipTap WYSIWYG editor for a single Home message (Story 24.2 / UC-CNT-001) — the modern
 * stand-in for the legacy PrimeFaces {@code p:editor}. Emits the current HTML on every change; the
 * page validates + saves it. Kept thin and presentational so the page logic stays testable.
 */
const RichTextEditor: FC<RichTextEditorProps> = ({
  id,
  label,
  value,
  invalid,
  invalidText,
  onChange,
}) => {
  const editor = useEditor({
    extensions: [StarterKit],
    content: value,
    onUpdate: ({ editor: current }) => onChange(current.getHTML()),
  })

  return (
    <div className="home-content__editor">
      <span className="cds--label" id={`${id}-label`}>
        {label}
      </span>
      <div
        id={id}
        aria-labelledby={`${id}-label`}
        className={`home-content__editor-box${invalid ? ' home-content__editor-box--invalid' : ''}`}
      >
        <EditorContent editor={editor} />
      </div>
      {invalid && invalidText && <div className="cds--form-requirement">{invalidText}</div>}
    </div>
  )
}

export default RichTextEditor
