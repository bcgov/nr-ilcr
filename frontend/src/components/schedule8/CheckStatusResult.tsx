import type { FC } from 'react'
import type { Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import { InlineNotification } from '@carbon/react'

// Renders the Schedule 8 Check Status outcome as a list of banners: the schedule/page-level success
// messages, then a warning per unmet page-field and per unmet sample-field issue. Shared by the page
// level (index) and the single-page sample level (SamplePage), which each wrap it in their own layout
// container (a grid Column vs. a plain div). Text is passed verbatim from the API (AD-8).
const CheckStatusResult: FC<{ result: Schedule8CheckStatusResponse }> = ({ result }) => (
  <>
    {result.messages.map((msg) => (
      <InlineNotification
        key={`sch-${msg.key}-${msg.text}`}
        kind="success"
        lowContrast
        title="Check Status"
        subtitle={msg.text}
      />
    ))}
    {result.pages.flatMap((page) => [
      ...page.issues.map((issue) => (
        <InlineNotification
          key={`page-${page.id}-${issue.field}`}
          kind="warning"
          lowContrast
          title={`Page — ${issue.field}`}
          subtitle={issue.message.text}
        />
      )),
      ...page.samples.flatMap((sample) =>
        sample.issues.map((issue) => (
          <InlineNotification
            key={`sample-${sample.id}-${issue.field}`}
            kind="warning"
            lowContrast
            title={`Sample — ${issue.field}`}
            subtitle={issue.message.text}
          />
        )),
      ),
    ])}
  </>
)

export default CheckStatusResult
