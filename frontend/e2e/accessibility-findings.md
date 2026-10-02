# ILCR Accessibility Findings (WCAG 2.1 AA)

**Audience:** the ILCR product owner, BA/QA, and the team taking over the application. You do not need to
know the codebase to read this page.

> **Snapshot dated 2026-10-02.** This page reflects the findings and ticket states as of that date,
> measured against `main` at commit `208882bd`. It is the most up-to-date picture at that point, not a
> permanent record. Tickets move after this date, so check the linked issue for its current state.

This page is the single summary of what accessibility testing found in the modernized ILCR application,
what has been fixed, and what is still open. The detailed evidence for each finding stays in the
per-use-case `defects.md` logs under [`features/`](features), which are linked from every row below.
For how the end-to-end suite is run, see the [E2E README](README.md). For the overall pass/fail picture,
see [E2E Test Results](e2e-test-results.md).

---

## 1. The standard and how it was tested

ILCR commits to **WCAG 2.1 Level AA** (requirement NFR1). The acceptance rule used throughout is: *zero
violations, or each remaining violation has a recorded disposition (a ticket or a documented decision).*

Findings come from three sources:

| Source | What it covers | What it cannot cover |
|---|---|---|
| **Automated scans in the E2E suite.** [axe-core](https://github.com/dequelabs/axe-core) runs inside the Playwright suite against each page state, with the WCAG tags `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`. Every accessibility scenario is tagged `@a11y`. The helper is [`pages/common/axe.ts`](pages/common/axe.ts). | Colour contrast at rest, ARIA validity, accessible names, form labels, landmarks, and other rule-checkable criteria. | Hover and focus states, unless a scenario deliberately puts the page in that state. Non-text contrast (1.4.11). Anything that needs human judgement. |
| **Measured follow-up.** Where a scan pointed at a problem, colours were measured from the rendered pixels in both themes. | Hover contrast, non-text contrast, and the dark theme. | Pages nobody looked at. |
| **Manual review with the BA.** The BA went through the schedules and admin screens one by one. The results were filed as individual tickets under the [UI Enhancements epic, #241](https://github.com/bcgov/nr-ilcr/issues/241). | Schedules 8 and 10, the Tree to Truck pages, and Table Maintenance, which have no automated scans yet. | — |

Two rules make the automated scans repeatable:

- **The pointer is parked before every scan.** Without that, the result would depend on which row the mouse
  happened to rest on. Hover states are tested only by scenarios that hover on purpose.
- **A known defect stays a failing test.** A scan that reproduces an open defect is tagged
  `@discovered-bug` or `@discovered-divergence` and left failing. It is never skipped or weakened, and it
  turns green by itself when the fix lands. `npm run test:gate` excludes these known reds. `npm test` runs
  them.

**Not covered by this page:** no audit with a screen reader or keyboard only is recorded in this
repository. The automated scans and the measurements above are the evidence.

---

## 2. Open findings

Ordered by impact. The **Ticket** column is where the fix is tracked. The **Evidence** column points at
the detailed write-up and the test that reproduces the finding.

| # | Finding (plain language) | WCAG | Where | Impact | Ticket | Evidence |
|---|---|---|---|---|---|---|
| A1 | **Validation errors are never announced to screen-reader users.** When a field is rejected, it turns red with a message underneath. A sighted user sees it. A screen-reader user is told nothing, so the form seems to have silently done nothing. The cause is in the `@carbon/react` `TextInput` invalid state: it points `aria-errormessage` at an element that is never announced. axe rule `aria-valid-attr-value`. | 4.1.3, 4.1.2 | **Every** form with field validation. Confirmed on Schedules 1, 2, 3, 4, 6, 8 and 11. | **Critical** (axe) | **None yet.** Recorded only in the E2E defect logs. | [sch11 `defects.md` BUG-1](features/sch11/uc-sch11-001-report-costs/defects.md) (red test), [sch6 `defects.md` BUG-1](features/sch6/uc-sch6-001-report-road-management-costs/defects.md) (red test). Schedules 2 and 4 deliberately do not re-scan that state: see GAP-4 in [sch2](features/sch2/uc-sch2-001-report-costs/defects.md) and [sch4](features/sch4/uc-sch4-001-report-transportation/defects.md). |
| A2 | **Hovering a table row makes its buttons and links too faint to read.** The row turns grey, but the blue and red labels of Edit, Copy, Delete, View and the in-row "(n):" sub-page links keep their colour. They drop to 3.79:1 against the 4.5:1 minimum. This is app-level theming, in both themes, so it needs one global fix. | 1.4.3 | Schedules 4, 5, 8 (including Tree to Truck), 10, 11, Table Maintenance (Schedules 1 and 3 have passed since their summary rows were shaded in #414) | Serious | Split per page: [#428](https://github.com/bcgov/nr-ilcr/issues/428) Sch 4, [#429](https://github.com/bcgov/nr-ilcr/issues/429) Sch 8, [#439](https://github.com/bcgov/nr-ilcr/issues/439) Sch 8 TtT, [#430](https://github.com/bcgov/nr-ilcr/issues/430) Sch 5, [#431](https://github.com/bcgov/nr-ilcr/issues/431) Sch 10, [#432](https://github.com/bcgov/nr-ilcr/issues/432) Sch 11, [#433](https://github.com/bcgov/nr-ilcr/issues/433) Table Maintenance, [#434](https://github.com/bcgov/nr-ilcr/issues/434) in-row links. Originally [#314](https://github.com/bcgov/nr-ilcr/issues/314), which holds the full measurements and root cause. | [sch4 `defects.md` BUG-1](features/sch4/uc-sch4-001-report-transportation/defects.md) (red test) |
| A3 | **A hovered button is almost the same grey as the hovered row.** Inside a hovered row, moving onto a button barely changes its shade, so you can't see which control you are on: 1.10:1 against 3:1. Fixing A2's colours does not fix this one; it needs a stronger hover treatment such as a border or the solid blue the Check Status button uses. Whether 1.4.11 strictly applies needs a specialist's call, because the button text still identifies the control. | 1.4.11 | Same pages as A2, plus the group links on Schedules 1 and 3. The outlined Delete button passes in the light theme. | Moderate | Same tickets as A2. Each asks for Edit and Copy to "behave the same as the Check Status button". | [sch4 `defects.md` BUG-3](features/sch4/uc-sch4-001-report-transportation/defects.md). No automated test: axe does not check this. |
| A4 | **The row being edited is highlighted blue, and its own text and buttons do not stand out on that blue.** In August 2026 the row's buttons measured 3.81:1 at rest on Schedules 4 and 8, against 4.5:1. The business decided to keep the highlight ([#319](https://github.com/bcgov/nr-ilcr/issues/319), reverted), so the fix is to darken the text and buttons on it. | 1.4.3 | Schedules 4, 5, 8, 10 | Serious | [#437](https://github.com/bcgov/nr-ilcr/issues/437) | [sch4 `defects.md` DIV-7](features/sch4/uc-sch4-001-report-transportation/defects.md). Since the row-editing fix in #514, the two automated scans of this state pass (2026-10-02 run), so the remaining work is the darker treatment the BA asked for in #437. |
| A5 | **Administrator-authored Home content can use any colour, including unreadable ones.** The seeded welcome message measures 2.15:1 (green) and 4.27:1 (magenta). This is content, not app styling, so the fix is a constraint in the editor. | 1.4.3 | Home page | Serious | [#423](https://github.com/bcgov/nr-ilcr/issues/423) restricts editor font colours to the legacy values that pass AA. Content already stored with failing colours must also be re-saved or cleaned. | [sec `defects.md` BUG-1](features/sec/uc-sec-001-working-context/defects.md) (2 red tests) |
| A6 | **A locked comment field still shows a "characters remaining" counter, faded to 1.72:1.** Text on an inactive control is exempt from 1.4.3, so this is probably not a strict failure. But the counter makes no sense on a field nobody can type into. The recommended fix is to hide it. | 1.4.3 (likely exempt) | Schedule 6 verified; 7A, 7B and 9 share the pattern | Minor | [#502](https://github.com/bcgov/nr-ilcr/issues/502) | [sch6 `defects.md` BUG-2](features/sch6/uc-sch6-001-report-road-management-costs/defects.md) (red test) |
| A7 | **Mandatory fields are not marked.** Nothing tells the user which fields are required until a save fails, and most required inputs do not expose `aria-required` to assistive technology. | 3.3.2 | App-wide | Moderate | [#354](https://github.com/bcgov/nr-ilcr/issues/354) | Ticket only |

The other tickets under [#241](https://github.com/bcgov/nr-ilcr/issues/241) are display and consistency
items such as button order, modal styling and alignment. They are not WCAG failures and are not repeated
here.

---

## 3. Resolved or closed

| Item | Outcome |
|---|---|
| [#314](https://github.com/bcgov/nr-ilcr/issues/314): table row and button hover contrast, app-wide | Closed as a tracking ticket. The BA and the team reviewed it page by page and split it into the tickets listed under A2 and A3, which remain open. |
| [#319](https://github.com/bcgov/nr-ilcr/issues/319): remove the edited-row highlight on Schedules 4 and 8 | Closed. The business reversed the decision and kept the highlight. Its contrast is now tracked as A4 / [#437](https://github.com/bcgov/nr-ilcr/issues/437). |
| [#321](https://github.com/bcgov/nr-ilcr/issues/321): table `aria-label` overridden by the table title | Fixed 2026-09-24. Cleanup only: every table already had a valid accessible name, so it was never a WCAG failure. |
| Schedule 5 had no accessibility scans at all | Fixed 2026-09-16: 7 scans added, all clean ([sch5 `defects.md` GAP-5](features/sch5/uc-sch5-001-report-camp-access-expenses/defects.md)). |
| Schedule 6 had no accessibility scans | Fixed 2026-09-18: 7 scans added. They found A1 and A6 ([sch6 `defects.md` GAP-1](features/sch6/uc-sch6-001-report-road-management-costs/defects.md)). |
| Mill Maintenance confirmation modal and notifications were not scanned | Fixed: scans added ([mill `defects.md` GAP-5](features/mill/uc-mill-001-maintain-mills/defects.md)). |

---

## 4. What the automated scans cover today

52 automated scans (`@a11y`) across 10 use-case suites. Results are from the full run on 2026-10-02:

| Area | Scans | Passed | Failing (all known, tracked above) |
|---|---|---|---|
| Home / working context ([sec](features/sec/uc-sec-001-working-context/defects.md)) | 3 | 3 | none, but see the A5 caution below |
| Schedule 1 ([sch1](features/sch1/uc-sch1-001-enter-save/defects.md)) | 2 | 2 | none |
| Schedule 2 ([sch2](features/sch2/uc-sch2-001-report-costs/defects.md)) | 4 | 4 | none |
| Schedule 3 ([sch3](features/sch3/uc-sch3-001-report-admin-costs/defects.md)) | 4 | 4 | none |
| Schedule 4 ([sch4](features/sch4/uc-sch4-001-report-transportation/defects.md)) | 9 | 8 | 1: hovered row (A2) |
| Schedule 5 ([sch5](features/sch5/uc-sch5-001-report-camp-access-expenses/defects.md)) | 7 | 7 | none |
| Schedule 6 ([sch6](features/sch6/uc-sch6-001-report-road-management-costs/defects.md)) | 7 | 5 | 2: validation errors (A1), locked comment counter (A6) |
| Schedule 11 ([sch11](features/sch11/uc-sch11-001-report-costs/defects.md)) | 5 | 4 | 1: validation errors (A1) |
| Mill Maintenance ([mill](features/mill/uc-mill-001-maintain-mills/defects.md)) | 6 | 6 | none |
| User Maintenance ([usr](features/usr/uc-usr-001-maintain-users/defects.md)) | 5 | 5 | none |
| **Total** | **52** | **48** | **4** |

> **Caution on A5 (Home).** The two Home scans are tagged as known reds but passed on 2026-10-02. That is
> **not** a fix. The local test stack runs with security off, and in that mode Home shows the *Licensee*
> welcome message, whose colours pass. The *Administrator* message stored in the database still carries
> the failing colours (`rgb(51, 204, 0)` and `rgb(204, 51, 204)`, checked 2026-10-02), and
> [#423](https://github.com/bcgov/nr-ilcr/issues/423) is still open. A green on these two scans does not
> prove A5 is fixed until the scans run as an administrator, or #423 lands and the stored content is
> cleaned.

**Not scanned yet, so the automated scans make no claim about these:**

- **Schedules 7A, 7B, 8, 9 and 10, Table Maintenance, Home Content administration, reports, printing and
  the data extract.** No E2E suite exists for them yet (see [E2E Test Results](e2e-test-results.md)).
  Schedules 8 and 10 and Table Maintenance were reviewed manually instead (A2–A4).
- **The validation-error state on Schedules 2 and 4.** Not scanned on purpose: it would only re-find A1.
  Once A1 is fixed, remove `aria-valid-attr-value` from `KNOWN_A11Y_RULES` in
  [`pages/common/axe.ts`](pages/common/axe.ts) and add those scans.
- **The success notification on Users and Mills.** It is the same component as the warning and error
  notifications, which scan clean ([usr `defects.md` GAP-1](features/usr/uc-usr-001-maintain-users/defects.md)).

---

## 5. Re-checking after a fix

```bash
cd frontend/e2e
npm test -- --grep @a11y      # every accessibility scan, including the known reds
```

When a fix lands, its red test turns green by itself. Then remove the `@discovered-*` tag from the
scenario, close the entry in that use case's `defects.md`, and update the table in section 2. For A1, also
remove the rule id from `KNOWN_A11Y_RULES`. A3 has no automated test, so re-measure it by hand.
