# ILCR End-to-End (E2E) Test Results

**Last updated:** 2026-10-02

**Audience:** the ILCR product owner, BA/QA, and the team taking over the application. You do not need to
know the codebase to read this page.

> **This is a dated snapshot.** This page records the full suite run of that date against `main` at
> commit `208882bd`. It is the most up-to-date picture at that point, not a permanent record. To get
> current numbers, re-run the suite (section 6). The per-use-case `coverage.md` files carry their own
> dated counts. When you refresh this page, update the **Last updated** date and the commit.

This page is the single summary of the E2E suite's results. How the suite is built and run is in the
[E2E README](README.md). The accessibility results are summarised separately in
[Accessibility Findings](accessibility-findings.md).

---

## 1. Headline

| | |
|---|---|
| **Tests run** | **633**: 397 feature tests, 235 preflight data checks, 1 smoke test |
| **Passed** | **629**, counting one test that passed on re-run (see below) |
| **Failed, known and tracked** | **4**. All four are accessibility defects with a recorded disposition. They fail on purpose until the app is fixed. |
| **Failed, new or unexplained** | **0** |
| **Gate result** | **Pass.** Every test outside the tests tagged as known reds passed. `npm run test:gate` runs exactly that set. |

One Schedule 1 test ("No mill and reporting year selected suppresses the input form") timed out while
the browser context was starting up. It passed when re-run on its own. That is a local load hiccup, not an
application defect.

**Environment:** a local stack (React frontend on `:3000`, Spring Boot backend on `:8080`, security off with
mock sign-in) against an Oracle database seeded with real extracted test data plus the patches in
[`real-test-data-patches/`](real-test-data-patches). Browser: Google Chrome via Playwright. Run time:
21 minutes, in parallel.

**In CI**, the same suite runs on every pull request that touches deployable code. It runs against a
database rebuilt by Flyway, using `npm run test:gate`. See *CI* in the [README](README.md).

---

## 2. Results by use case

Each use case has a `coverage.md` (what was tested, and why anything was not) and a `defects.md` (what was
found). Both are linked below. The guides [`coverage-guide.md`](coverage-guide.md) and
[`defects-guide.md`](defects-guide.md) explain how to read them.

| Use case | Screen | Tests | Passed | Known reds | Coverage / defects |
|---|---|---|---|---|---|
| UC-SEC-001 | Home: select working mill and reporting year | 13 | 13 | 0 (see note) | [coverage](features/sec/uc-sec-001-working-context/coverage.md) · [defects](features/sec/uc-sec-001-working-context/defects.md) |
| UC-SCH1-001 | Schedule 1: Average Cost of Logging | 42 | 42 | 0 | [coverage](features/sch1/uc-sch1-001-enter-save/coverage.md) · [defects](features/sch1/uc-sch1-001-enter-save/defects.md) |
| UC-SCH2-001 | Schedule 2: Purchased and Private Log Costs and Sales | 41 | 41 | 0 | [coverage](features/sch2/uc-sch2-001-report-costs/coverage.md) · [defects](features/sch2/uc-sch2-001-report-costs/defects.md) |
| UC-SCH3-001 | Schedule 3: Administration Costs | 49 | 49 | 0 | [coverage](features/sch3/uc-sch3-001-report-admin-costs/coverage.md) · [defects](features/sch3/uc-sch3-001-report-admin-costs/defects.md) |
| UC-SCH4-001 | Schedule 4: Special Log Transportation Costs | 92 | 91 | 1 | [coverage](features/sch4/uc-sch4-001-report-transportation/coverage.md) · [defects](features/sch4/uc-sch4-001-report-transportation/defects.md) |
| UC-SCH5-001 | Schedule 5: Camp and Access Expenses | 39 | 39 | 0 | [coverage](features/sch5/uc-sch5-001-report-camp-access-expenses/coverage.md) · [defects](features/sch5/uc-sch5-001-report-camp-access-expenses/defects.md) |
| UC-SCH6-001 | Schedule 6: Road Management Costs | 39 | 37 | 2 | [coverage](features/sch6/uc-sch6-001-report-road-management-costs/coverage.md) · [defects](features/sch6/uc-sch6-001-report-road-management-costs/defects.md) |
| UC-SCH11-001 | Schedule 11: Basic Silviculture Costs | 31 | 30 | 1 | [coverage](features/sch11/uc-sch11-001-report-costs/coverage.md) · [defects](features/sch11/uc-sch11-001-report-costs/defects.md) |
| UC-MILL-001 | Mill Maintenance: mills, status and user associations | 27 | 27 | 0 | [coverage](features/mill/uc-mill-001-maintain-mills/coverage.md) · [defects](features/mill/uc-mill-001-maintain-mills/defects.md) |
| UC-USR-001 | User Maintenance | 12 | 12 | 0 | [coverage](features/usr/uc-usr-001-maintain-users/coverage.md) · [defects](features/usr/uc-usr-001-maintain-users/defects.md) |
| UC-USR-002 | Maintain a user from a mill record | 12 | 12 | 0 | [coverage](features/usr/uc-usr-002-user-from-mill/coverage.md) · [defects](features/usr/uc-usr-002-user-from-mill/defects.md) |
| — | App-shell smoke test (no database) | 1 | 1 | 0 | [`features/shell/`](features/shell) |
| — | Preflight: pinned test data still resolves | 235 | 235 | 0 | [`preflight/`](preflight) |
| **Total** | | **633** | **629** | **4** | |

The Schedule 1 count includes the timed-out test after its passing re-run.

**Note on tagged tests that passed.** In the 2026-10-02 run, 8 tests were tagged as known reds
(`@discovered-bug` / `@discovered-divergence`). Four failed as expected (section 3). The other four passed:

- **Schedule 4, 2 editing-row scans.** These have passed since #514, which disables a row's actions while it
  is open in the editor. Their tags were removed on 2026-10-02 (Schedule 4 DIV-7 closed), so they now run in
  `npm run test:gate` as regression checks. A re-run of Schedule 4's accessibility scans the same day confirmed
  both green.
- **Home, 2 scans (A5).** Still tagged. They pass only because of the local test setup, not because of a fix.
  See the A5 caution in
  [Accessibility Findings](accessibility-findings.md#4-what-the-automated-scans-cover-today).

That leaves 6 tagged tests: the 4 in section 3 plus the 2 Home scans.

---

## 3. The known failing tests

These fail on purpose. Each one reproduces a confirmed defect, has an entry in its use case's
`defects.md`, and turns green by itself when the fix lands. None is a functional or data defect. All
four are accessibility findings.

| Test | Use case | Finding | Ticket |
|---|---|---|---|
| A hovered row keeps its action labels readable | Schedule 4 | Row-hover contrast (A2) | [#428](https://github.com/bcgov/nr-ilcr/issues/428) and siblings, see A2 |
| The Add panel's validation errors reach assistive technology | Schedule 6 | Validation errors not announced (A1) | [#546](https://github.com/bcgov/nr-ilcr/issues/546) |
| The read-only schedule has no WCAG 2.1 AA violations | Schedule 6 | Locked comment counter (A6) | [#502](https://github.com/bcgov/nr-ilcr/issues/502) |
| The validation-error state announces its errors to assistive technology | Schedule 11 | Validation errors not announced (A1) | [#546](https://github.com/bcgov/nr-ilcr/issues/546) |

The A-numbers refer to [Accessibility Findings](accessibility-findings.md).

Defects found by the suite that have since been fixed are recorded, closed, in the `defects.md` files. In
each of those cases the test that used to fail now runs in the gate as a regression check.

---

## 4. How much of the application the suite covers

The requirements for each use case are broken into **slices**. A slice is one numbered, testable piece
of behaviour, such as "save with a required field empty shows an error". A slice is **covered** when at
least one test exercises it.

| | |
|---|---|
| Use cases in the requirements baseline | **49** |
| Use cases with an E2E suite | **11** (the table in section 2) |
| Slices in the 11 started use cases | **224**: 216 covered, 5 not applicable to the new app, 3 not yet tested |
| Slices in the whole baseline | **770** |
| **Covered, whole baseline** | **216 of 770 (28%)** |

**Every started use case is complete, or close to it.** The 3 untested slices are documented
gaps, not oversights:

- **UC-MILL-001** S06, adding a user to the Associated Auditors panel. The panel was retired by a
  business decision, so there is nothing to test
  ([`coverage.md`](features/mill/uc-mill-001-maintain-mills/coverage.md)).
- **UC-USR-001** S05 and S14, the ADAM user-details dialog. It was not built, because the directory's
  account-detail call it needs was never implemented
  ([`defects.md`](features/usr/uc-usr-001-maintain-users/defects.md) GAP-2).

**Areas with no E2E suite yet:**

| Area | Use cases | Slices |
|---|---|---|
| Schedules 7A, 7B, 8, 9 and 10 | 5 | 169 |
| Ministry check, verify and status workflow (UC-CHK) | 19 | 227 |
| Printing schedule PDFs (UC-PRT) | 3 | 48 |
| Management reports (UC-MRPT) | 4 | 35 |
| Select working context as Auditor or Administrator (UC-SEC-002/003) | 2 | 15 |
| CSV data extract (UC-EXT) | 1 | 15 |
| Code table maintenance (UC-CODE) | 1 | 13 |
| Role-keyed Home messages (UC-CNT) | 1 | 10 |
| Open a new reporting year (UC-RY) | 1 | 8 |
| View or maintain a mill record (UC-MILL-002) | 1 | 6 |

**Read these numbers with two caveats:**

- **770 is a ceiling, not a fixed target.** Several use cases, the UC-CHK group especially, repeat the
  same behaviour for different roles. Those slices partly merge when a suite is written, so the total
  is expected to drift down.
- **Not tested end to end does not mean not tested.** Areas without an E2E suite still have backend
  integration tests and frontend unit tests in this repository. This page only measures browser-level
  coverage.

---

## 5. Known limits of the suite

- **Single-role sign-in.** The suite runs with mock sign-in on localhost. Most "the wrong role is refused"
  cases are therefore recorded as `blocked` coverage gaps. Server-side role enforcement is covered by
  backend integration tests instead.
- **Local and CI data differ.** Local runs use the real-data extract. CI uses a smaller Flyway-built
  seed. The preflight checks that every pinned test record exists in both, but a test can still behave
  differently between the two.
- **Re-extracting the test data is a re-grounding event.** Real data changes, so pinned test records
  must be re-checked. The 235 preflight checks fail fast with one clear message if one has drifted.

---

## 6. Reproducing these results

Bring up the stack as described in [README → Prerequisites](README.md#prerequisites--bring-up-the-full-stack),
then:

```bash
cd frontend/e2e
npm test               # everything, including the known reds (exits 1 by design while any are open)
npm run test:gate      # the pass/fail gate: everything except the known reds
npm run report         # open the HTML report of the last run
```

Count tests with `npx playwright test --list` rather than trusting a written number. See *Counting
tests* in the [README](README.md).
