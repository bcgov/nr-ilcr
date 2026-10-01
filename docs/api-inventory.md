# API Inventory

Every endpoint of the ILCR backend, with the permission it requires. Verified against the code on 2026-10-01.
When you add or change an endpoint, update this page in the same pull request.

## How access is decided

- **Authentication.** Every `/api/v1` endpoint requires a signed-in caller. Deployed environments validate the
  FAM / AWS Cognito ID token. Locally, with `ILCR_SECURITY_ENABLED=false`, the caller is a mock principal built
  from the `X-Mock-Groups` header.
- **Permission action.** Most endpoints also check a permission action with
  `@PreAuthorize("@permissions.hasPermission(authentication, '<ACTION>')")`. The bracketed names below are those
  actions, defined in `backend/src/main/java/ca/bc/gov/nrs/ilcr/security/Action.java`. "authenticated" means
  any signed-in caller.
- **Roles.** `security/SchedulePermissions.java` maps each role to its actions:

  | Role | Actions |
  |---|---|
  | `ILCR_ADMIN` | VIEW_SCHEDULE, EDIT_SCHEDULE, SET_REPORT_STATUS, MAINTAIN_MILLS, MAINTAIN_USERS, MAINTAIN_CODE_TABLES, OPEN_REPORTING_YEAR, EDIT_HOME_CONTENT, GENERATE_MILL_REPORTS, GENERATE_DATA_EXTRACT |
  | `ILCR_SUBMITTER` | VIEW_SCHEDULE, EDIT_SCHEDULE, SUBMIT_REPORT, GENERATE_MILL_REPORTS |

  Unknown roles and actions are denied.
- **Schedule writes** must also pass the role × status editability matrix (`security/ScheduleEditability.java`).
  A submitter edits at Draft only; an administrator edits at Submitted and Verified. A refusal is a 409.
- **Mill scope.** A submitter can reach a mill only through an active assignment in `THE.ILCR_MILL_USER_XREF`.
  Administrators are not scoped. Reports filtered by mill return only the caller's mills.

## Conventions

- Request mappings are declared on the `api/*Api.java` interface of each feature package under
  `backend/src/main/java/ca/bc/gov/nrs/ilcr/`. The exceptions are `messages/MessageApi.java` and
  `web/RootController.java`.
- Schedule endpoints take the working context as query parameters: `millId` and `year`.
- Writes carry the `revisionCount` the client last read. A stale revision is answered with 409.
- Errors are RFC 7807 `application/problem+json`, with message text from `messages.properties`.

## Platform

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/api` | App name, version, UP | public |
| GET | `/api/health`, `/api/health/readiness`, `/api/health/liveness` | Actuator probes | public |
| GET | `/api/info`, `/api/prometheus` | Build info, metrics | public |
| GET | `/api/v1/me` | Caller identity and roles | authenticated |
| GET | `/api/v1/messages?key=` | Resolve an allow-listed message-bundle key | [VIEW_SCHEDULE] |

## Working context (Home)

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/api/v1/mills` | Mills for the Home picker, scoped to the caller | authenticated |
| GET | `/api/v1/reporting-years` | Opened reporting years, newest first | authenticated |
| GET | `/api/v1/mill-context?millId&year` | Resolve and validate the working context | authenticated + mill scope |

## Schedules

All schedule reads and Check Status calls need [VIEW_SCHEDULE]. All writes need [EDIT_SCHEDULE] and must pass the
editability matrix.

| Schedule | Base path | Endpoints |
|---|---|---|
| 1 Average Cost of Logging | `/api/v1/schedule1` | GET, PUT (save + recompute), DELETE, POST `/check-status` |
| 1 Other Costs | `/api/v1/schedule1/other-costs` | GET, POST, PUT (batch), PUT `/{id}`, DELETE `/{id}` |
| 2 Purchased and Private Log Costs | `/api/v1/schedule2` | GET, PUT, DELETE, POST `/check-status` |
| 3 Forest Management Administration | `/api/v1/schedule3` | GET, PUT, DELETE, POST `/check-status` |
| 3 Other Acceptable Costs | `/api/v1/schedule3/other-acceptable-costs` | GET, POST, PUT (batch), PUT `/{id}`, DELETE `/{id}` |
| 3 Included Unacceptable Costs | `/api/v1/schedule3/included-unacceptable-costs` | GET, POST, PUT (batch), PUT `/{id}`, DELETE `/{id}` |
| 4 Special Log Transportation | `/api/v1/schedule4` | GET; PUT / DELETE `/locations`; POST `/locations/{locationId}/rows`; PUT / DELETE `/locations/{locationId}/rows/{rowId}`; POST `/check-status` |
| 5 Camp and Access Expenses | `/api/v1/schedule5` | GET; POST `/camps`; PUT / DELETE `/camps/{campId}`; GET / PUT `/camps/{campId}/other-camp-expenses` and `/other-access-expenses`; DELETE `…/{rowId}` on each; POST `/check-status` |
| 6 Road Management | `/api/v1/schedule6` | GET, PUT, POST `/records`, DELETE `/records/{recordId}`, POST `/check-status` |
| 7A Bridges | `/api/v1/schedule7a` | GET; POST `/bridges`; PUT `/bridges` (save all); PUT / DELETE `/bridges/{id}`; POST `/check-status` |
| 7B Culverts | `/api/v1/schedule7b` | GET; POST `/culverts`; PUT `/culverts` (save all); PUT / DELETE `/culverts/{id}`; POST `/check-status` |
| 8 Tree to Truck | `/api/v1/schedule8` | GET; GET `/options`; PUT `/pages`; DELETE `/pages/{id}`; PUT `/pages/{pageId}/samples`; DELETE `/pages/{pageId}/samples/{id}`; POST `/samples/{sampleId}/rates`; PUT / DELETE `/samples/{sampleId}/rates/{rowId}`; POST `/check-status`; POST `/pages/{pageId}/check-status` |
| 9 Miscellaneous and Unique Costs | `/api/v1/schedule9` | GET; POST `/records`; PUT / DELETE `/records/{id}`; POST `/check-status` |
| 10 New Road Construction | `/api/v1/schedule10` | GET; POST `/pages`; PUT / DELETE `/pages/{pageId}`; POST `/pages/{pageId}/copy`; POST `/pages/{pageId}/road-details`; PUT / DELETE `/pages/{pageId}/road-details/{roadDetailId}`; POST `/check-status` |
| 11 Basic Silviculture | `/api/v1/schedule11` | GET; POST `/locations`; PUT `/locations` (page-level save); POST `/check-status`; GET `/biogeoclimatic-catalogue` |

## Check Status and report status

Schedules 1-10 and Schedule 11 are separate status tracks, so each transition has a `/schedule11/` twin. Every
transition runs the Check Status validation first.

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/api/v1/check-status` | Validation sweep over every schedule, both tracks | [VIEW_SCHEDULE] |
| POST | `/api/v1/check-status/submit` | Schedules 1-10: Draft → Submitted | [SUBMIT_REPORT] + active mill assignment |
| POST | `/api/v1/check-status/schedule11/submit` | Schedule 11: Draft → Submitted | [SUBMIT_REPORT] + active mill assignment |
| POST | `/api/v1/check-status/verify`, `/schedule11/verify` | Submitted → Verified | [SET_REPORT_STATUS] |
| POST | `/api/v1/check-status/set-to-draft`, `/schedule11/set-to-draft` | Submitted → Draft | [SET_REPORT_STATUS] |
| POST | `/api/v1/check-status/set-to-submit`, `/schedule11/set-to-submit` | Verified → Submitted | [SET_REPORT_STATUS] |

## Reports, print and extract

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/api/v1/reports/schedule9?millId&year` | Schedule 9 PDF | [VIEW_SCHEDULE] |
| POST | `/api/v1/reports/print` | Selected schedules as one bookmarked PDF | [VIEW_SCHEDULE] |
| GET | `/api/v1/reports/mill-information?year` | Mill Information report PDF (scoped for submitters) | [GENERATE_MILL_REPORTS] |
| GET | `/api/v1/reports/mill-information/{millId}?year` | One mill's drill-down PDF | [GENERATE_MILL_REPORTS] |
| GET | `/api/v1/reports/mill-status?year` | Mill Status Report rows (JSON) | [GENERATE_MILL_REPORTS] |
| POST | `/api/v1/reports/data-extract` | CSV Data Extract | [GENERATE_DATA_EXTRACT] |

## Administration

| Method | Path | Purpose | Access |
|---|---|---|---|
| GET | `/api/v1/admin/mills` | Search mills | [MAINTAIN_MILLS] |
| GET | `/api/v1/admin/mills/importable` | Mills available to import | [MAINTAIN_MILLS] |
| GET | `/api/v1/admin/mills/{millId}` | Mill record | [MAINTAIN_MILLS] |
| GET | `/api/v1/admin/mills/{millId}/contact-options` | Contact choices | [MAINTAIN_MILLS] |
| POST | `/api/v1/admin/mills/{millId}/import`, `/activate`, `/deactivate` | Import, activate, deactivate a mill | [MAINTAIN_MILLS] |
| PUT | `/api/v1/admin/mills/{millId}/contacts` | Save mill contacts | [MAINTAIN_MILLS] |
| GET / POST | `/api/v1/admin/mills/{millId}/users` | List / add the mill's user associations | [MAINTAIN_MILLS] |
| POST | `/api/v1/admin/mills/{millId}/users/{userGuid}/activate`, `/deactivate` | Toggle an association | [MAINTAIN_MILLS] |
| GET | `/api/v1/mills/{millId}/submitters` | Submitters assigned to a mill | [MAINTAIN_USERS] |
| GET | `/api/v1/submitters/{userGuid}/mills` | A submitter's mill assignments | [MAINTAIN_USERS] |
| POST | `/api/v1/mills/{millId}/submitters` | Assign (provisions the account if new) | [MAINTAIN_USERS] |
| PATCH | `/api/v1/mills/{millId}/submitters/{userGuid}` | End an assignment | [MAINTAIN_USERS] |
| PATCH | `/api/v1/submitters/{userGuid}` | Activate / deactivate an account | [MAINTAIN_USERS] |
| GET | `/api/v1/users/lookup` | Directory search. Behind `ILCR_USER_LOOKUP_ENABLED` (off by default); answers 404 while off | [MAINTAIN_USERS] |
| GET / POST | `/api/v1/admin/reporting-years` | Page state / open the next reporting year | [OPEN_REPORTING_YEAR] |
| GET / PUT | `/api/v1/home-content` | Read / save all three role messages | [EDIT_HOME_CONTENT] |
| GET | `/api/v1/home-content/mine` | The caller's role message | authenticated |
| GET | `/api/v1/code-tables` | List maintainable code tables | [MAINTAIN_CODE_TABLES] |
| GET / PUT | `/api/v1/code-tables/{tableKey}/entries` | Read / upsert entries | [MAINTAIN_CODE_TABLES] |
