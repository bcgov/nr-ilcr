import type { FC, ReactNode } from 'react'
import { useEffect } from 'react'
import { Breadcrumb, BreadcrumbItem, Column } from '@carbon/react'
import { Link } from '@tanstack/react-router'
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
      {breadCrumbs?.length ? (
        <Breadcrumb className="page-title-breadcrumb">
          {breadCrumbs.map((crumb) => (
            <BreadcrumbItem key={crumb.name}>
              <Link to={crumb.path}>{crumb.name}</Link>
            </BreadcrumbItem>
          ))}
        </Breadcrumb>
      ) : null}
      <div className="page-title-container">
        <div className="title-container">
          <h1>{title}</h1>
          {children}
        </div>
        {subtitle ? <p className="page-title-subtitle">{subtitle}</p> : null}
      </div>
    </Column>
  )
}

export default PageTitle
