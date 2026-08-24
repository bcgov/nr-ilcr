import { type Locator, type Page } from '@playwright/test';

/**
 * Cross-domain Carbon Design System DOM patterns — no domain vocabulary. Every Carbon
 * `invalid`/`invalidText` field (TextInput, TextArea, Select, DateInput, FilterableMultiSelect) renders
 * its error the same way, so this locator lives in common/ and is reused rather than re-derived per domain.
 */

/**
 * The Carbon requirement/error text rendered under a specific field, scoped to that field's form-item
 * so a short message (e.g. "Required.") is unambiguous. `id` is a `#`-prefixed selector.
 */
export function fieldError(page: Page, id: string): Locator {
  return page
    .locator(`xpath=//*[@id="${id.slice(1)}"]/ancestor::div[contains(@class,"cds--form-item")][1]`)
    .locator('.cds--form-requirement');
}

/**
 * Locate a field by its `#`-prefixed id, tolerating an id that STARTS WITH A DIGIT.
 *
 * `page.locator('#40-volume')` throws `'#40-volume' is not a valid selector` — a bare CSS id selector
 * may not begin with a digit, even though the HTML `id` attribute may. Schedule 4's category grid uses
 * the legacy cost-item code as its id prefix (`40-volume`, `47-distance`), so every one of its cells hits
 * this. The attribute form has no such restriction and is exactly equivalent.
 *
 * Common rather than per-domain: it is a pure DOM concern with no domain vocabulary, and both Schedule 4
 * page objects (the main grid and the sub-page rows) need it — so it is defined once here instead of
 * being re-inlined in each. Pass the same `#id` string the field-error helper above takes, so a page
 * object keeps ONE id constant per field.
 */
export function byId(page: Page, id: string): Locator {
  return page.locator(`[id="${id.slice(1)}"]`);
}
