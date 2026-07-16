import { expect } from '@playwright/test'
import { baseURL } from '../utils'
import type { Page } from 'playwright'

export const dashboard_page = async (page: Page) => {
  await page.goto(baseURL)
  await expect(page.getByRole('banner', { name: 'Interior Logging Cost Reports (ILCR)' })).toBeVisible()
  await expect(page.getByText('ILCR Workspace')).toBeVisible()
  await expect(page.getByLabel('Mock user')).toBeVisible()
  await expect(page.getByText('ILCR_ADMIN').first()).toBeVisible()
  await expect(page.getByText('User ID')).toBeVisible()
  await expect(page.getByText('Name')).toBeVisible()
  await expect(page.getByText('Email')).toBeVisible()
  await page.getByRole('button', { name: 'Open menu' }).click()
  await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Submissions' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Mill Associations' })).toBeVisible()
  await expect(page.getByText('ilcr.dev@gov.bc.ca').first()).toBeVisible()
}
