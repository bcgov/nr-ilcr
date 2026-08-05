import type { FC, ReactNode } from 'react'
import { useEffect } from 'react'
import { Breadcrumb, BreadcrumbItem, Column } from '@carbon/react'
import { Link } from '@tanstack/react-router'
import ContextBanner from '@/components/Layout/ContextBanner'
import './index.scss'

export type BreadCrumb = {
  name: string
  path: string
}

type PageTitleProps = {
  breadCrumbs?: BreadCrumb[]
  children?: ReactNode
  subtitle?: string
  title: string
}

const PageTitle: FC<PageTitleProps> = ({ breadCrumbs, children, subtitle, title }) => {
  useEffect(() => {
    document.title = `${title} | ILCR`
  }, [title])

  return (
    <Column className="page-title-col" sm={4} md={8} lg={16}>
      {/* Breadcrumb + title on the left, the working-context banner on the right — one row. */}
      <div className="page-title-row">
        {/* Breadcrumb, title, and subtitle on one horizontal line, all the same size (legacy
            "ILCR -> Schedule N: <description>"). */}
        <div className="page-title-line">
          {breadCrumbs?.length ? (
            <Breadcrumb className="page-title-breadcrumb">
              {breadCrumbs.map((crumb) => (
                <BreadcrumbItem key={crumb.name}>
                  <Link to={crumb.path}>{crumb.name}</Link>
                </BreadcrumbItem>
              ))}
            </Breadcrumb>
          ) : null}
          {/* Title, plus the description joined with a colon when present ("Schedule 1: Average Cost
              of Logging") — legacy one-segment wording; sub-pages pass no subtitle, so just the title. */}
          <h1 className="page-title-heading">{subtitle ? `${title}: ${subtitle}` : title}</h1>
          {children}
        </div>
        <ContextBanner />
      </div>
    </Column>
  )
}

export default PageTitle
