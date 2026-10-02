import type { FC } from 'react'
import { InlineNotification } from '@carbon/react'
import type { BannerEntry } from '@/utils/legacyValidationBanner'

type LevelBannersProps = {
  /** The API's verbatim success line from the last write (AD-8). */
  readonly message: string | null
  /** A failed action: the API's ProblemDetail detail. */
  readonly error: string | null
  /** The client-side validation banner, one legacy line per failing field (#359 group C). */
  readonly entries: readonly BannerEntry[]
}

/**
 * The top-of-level banner stack of the sample and rates levels: last write's result, last failure,
 * then one "Action failed" box per failing field, as legacy listed each in its `p:messages` and as
 * `core/ScheduleBanners` renders them on a page grid. These levels render inside a single grid
 * column, so plain notifications stand in for that component's grid columns.
 */
const LevelBanners: FC<LevelBannersProps> = ({ message, error, entries }) => (
  <>
    {message && (
      <InlineNotification kind="success" lowContrast title="Success" subtitle={message} />
    )}
    {error && (
      <InlineNotification kind="error" lowContrast title="Action failed" subtitle={error} />
    )}
    {entries.map((entry) => (
      <InlineNotification
        key={entry.key}
        kind="error"
        lowContrast
        title="Action failed"
        subtitle={entry.line}
      />
    ))}
  </>
)

export default LevelBanners
