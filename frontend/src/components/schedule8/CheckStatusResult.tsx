import type { FC } from 'react'
import type { Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import { InlineNotification } from '@carbon/react'

// Renders the Schedule 8 Check Status outcome as a list of banners: the schedule/page-level success
// messages, then a warning per unmet page-field and per unmet sample-field issue. Shared by the page
// level (index) and the single-page sample level (SamplePage), which each wrap it in their own layout
// container (a grid Column vs. a plain div). Text is passed verbatim from the API (AD-8).
//
// Each warning's title names WHERE the finding is — the page's legacy title and, for a sample, the
// sample's — before the field (#461): "Page # 1  -TSA: TSA5 -CP: cp123 — Supply Block". Legacy printed
// the page title above each page's findings; without it two pages missing the same field produced two
// identical notices. The labels come off the wire (the server numbers pages over the whole document,
// so the single-page scope still says "Page # 2"); a verdict without them falls back to the ordinal.
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
    {result.pages.flatMap((page, pageIndex) => {
      const pageLabel = page.pageLabel ?? `Page # ${pageIndex + 1}`
      return [
        ...page.issues.map((issue) => (
          <InlineNotification
            key={`page-${page.id}-${issue.field}`}
            kind="warning"
            lowContrast
            title={`${pageLabel} — ${issue.field}`}
            subtitle={issue.message.text}
          />
        )),
        ...page.samples.flatMap((sample, sampleIndex) => {
          const sampleLabel = sample.sampleLabel ?? `Sample # ${sampleIndex + 1}`
          return sample.issues.map((issue) => (
            <InlineNotification
              key={`sample-${sample.id}-${issue.field}`}
              kind="warning"
              lowContrast
              title={`${pageLabel} — ${sampleLabel} — ${issue.field}`}
              subtitle={issue.message.text}
            />
          ))
        }),
      ]
    })}
  </>
)

export default CheckStatusResult
