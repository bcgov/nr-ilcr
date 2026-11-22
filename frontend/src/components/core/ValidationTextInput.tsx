import { TextInput } from '@carbon/react'
import type { ComponentProps } from 'react'

export default function ValidationTextInput(props: ComponentProps<typeof TextInput>) {
  if (!props.invalid || !props.invalidText || props.disabled || props.readOnly) {
    return <TextInput {...props} />
  }

  // Carbon renders this message once and references it with aria-errormessage.
  // Also expose it as a description when the user navigates back to the field.
  const errorId = `${props.id}-error-msg`
  const describedBy = [props['aria-describedby'], errorId].filter(Boolean).join(' ')
  return <TextInput {...props} aria-describedby={describedBy} />
}
