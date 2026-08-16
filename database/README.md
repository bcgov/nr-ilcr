# database — fixture-seeded Oracle in the `-tools` namespace

Mirrors [bcgov/nr-waste-plus `legacydb`](https://github.com/bcgov/nr-waste-plus/tree/main/legacydb):
a throwaway Oracle-Free instance deployed to the shared **`<plate>-tools`** namespace (not dev) and
populated with the **same test-scope Flyway fixtures the `*IT` acceptance suite uses**
(`backend/src/test/resources/db` — same image, same `THE` app user, and same Flyway ordering
semantics as `support/AbstractOracleIT`'s Testcontainer).

## How it works

1. **Build** (`pr-open.yml`, package `database`): the image is `gvenzl/oracle-free:23.9-slim-faststart`
   with the fixture scripts baked in at `/opt/oracle/sql`. The build context is the repo root so the
   scripts stay single-sourced with the IT suite — no copies, no curl-the-repo-zip (the waste-plus
   CronJob approach this replaces).
2. **Deploy** (`reusable-deploy.yml`, job `database`, runs before backend/frontend): applies
   `openshift.deploy.yml` to the tools namespace with `overwrite: false` (`oc create`), so the
   Secret / Deployment / Service / NetworkPolicy are created **once** and never clobbered — one
   shared instance serves every PR zone and environment.
3. **Populate**: the same template carries a Flyway migration **Job** named with a per-run
   `MIGRATE_SUFFIX`, so a fresh Job is created on every deploy (Jobs are immutable — a fixed name
   would collide). An init container copies the baked-in scripts out of the database image
   (tagged with this run's build, so a PR's fixture changes are applied by that PR's deploy);
   `flyway/flyway` then migrates. Flyway's schema history makes unchanged re-runs no-ops. The
   workflow waits for Job completion before the backend/frontend deploys start.

## Required GitHub configuration (new)

| Kind   | Name                     | Value                                                          |
| ------ | ------------------------ | -------------------------------------------------------------- |
| secret | `OC_NAMESPACE_TOOLS`     | `<plate>-tools`                                                 |
| secret | `OC_TOKEN_TOOLS`         | pipeline ServiceAccount token **for the tools namespace**       |
| secret | `DATABASE_PASSWORD`      | password for the fixture DB's `THE` user (NOT the real Oracle `ORACLEDB_PASSWORD`) |
| var    | `DATABASE_OPENSHIFT_UID` | optional; first value of `oc get namespace <plate>-tools -o jsonpath='{.metadata.annotations.openshift\.io/sa\.scc\.uid-range}'` (defaults to `1010470000`) |

Per repo convention (no `ENABLE_*` gates), the `database` job is not feature-flagged: it fails
visibly until these secrets exist.

## Connecting

From the dev namespace (allowed by the template's NetworkPolicy) or inside tools:

```
jdbc:oracle:thin:@//nr-ilcr-tools-database.<plate>-tools.svc.cluster.local:1521/FREEPDB1
```

as `THE` / `$DATABASE_PASSWORD`. The deployed backend still points at the real Oracle via
`ORACLEDB_HOST`/`ORACLEDB_SERVICENAME` vars — pointing PR sandboxes at this instance is a
follow-up, not part of this wiring.

## Operations

- **Re-provision** (new image spec, wedged instance): objects are create-once, so delete and re-run
  any deploy:
  `oc delete deployment,service,secret,networkpolicy,job -l app=nr-ilcr-tools -n <plate>-tools`
- **Data loss on restart is by design**: there is no PVC (waste-plus parity). A restarted pod comes
  back empty (fresh Oracle init) and is repopulated by the next deploy's migration Job.
- **Migration Jobs clean themselves up** (`ttlSecondsAfterFinished: 86400`); inspect a recent one
  with `oc logs job/nr-ilcr-tools-database-migrate-<run-id>-<attempt> -n <plate>-tools`.
- `removeDatabase` (baked into the image, from waste-plus verbatim) drops an app user and/or PDB and
  tidies RMAN/FRA/diagnostic leftovers:
  `oc exec deploy/nr-ilcr-tools-database -- /opt/oracle/removeDatabase <user> [pdb]`.
  waste-plus's `cleanDatabase` was not carried over — it deletes waste-plus-specific
  `FOREST_CLIENT` rows and has no ILCR equivalent.
