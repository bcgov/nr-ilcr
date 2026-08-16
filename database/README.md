# database — fixture-seeded Oracle in the `-tools` namespace

A throwaway Oracle-Free instance deployed to the shared **`<plate>-tools`** namespace (not dev) and
populated with the **same test-scope Flyway fixtures the `*IT` acceptance suite uses**
(`backend/src/test/resources/db` — same image, same `THE` app user, and same Flyway ordering
semantics as `support/AbstractOracleIT`'s Testcontainer). Adapted from the
[nr-waste-plus / nr-forest-client `legacydb`](https://github.com/bcgov/nr-waste-plus/tree/main/legacydb)
pattern, minus the parts that only serve their per-PR PDB lifecycle.

## How it works

1. **Build** (`pr-open.yml`, package `database`): the image is `gvenzl/oracle-free:23.9-slim-faststart`
   with the fixture scripts baked in at `/opt/oracle/sql`. The build context is the repo root so the
   scripts stay single-sourced with the IT suite — no copies, no curl-the-repo-zip.
2. **Deploy** (`reusable-deploy.yml`, job `database`, runs before backend/frontend): applies
   `openshift.deploy.yml` to the tools namespace with `overwrite: false` (`oc create`), so the
   Secret / Deployment / Service / NetworkPolicy are created **once** and never clobbered — one
   shared instance serves every PR zone and environment.
3. **Populate**: the same template carries a Flyway migration **Job** named with a per-run
   `MIGRATE_SUFFIX`, so a fresh Job is created on every deploy (Jobs are immutable — a fixed name
   would collide). An init container copies the baked-in scripts out of the database image
   (tagged with this run's build, so a PR's fixture changes are applied by that PR's deploy);
   `flyway/flyway` then migrates. Flyway's schema history makes unchanged re-runs no-ops. The
   workflow polls the Job to completion — failing fast on a failed migration, with logs — before
   the backend/frontend deploys start.

## Design notes (why it looks this way)

Informed by the nr-forest-client team's field report in
[gvenzl/oci-oracle-free#59](https://github.com/gvenzl/oci-oracle-free/issues/59):

- **`usermod` in the Dockerfile is required.** OpenShift's restricted SCC runs the container with
  the namespace's assigned UID regardless of the image `USER`, and Oracle needs that UID to resolve
  to the oracle passwd entry. Set the `DATABASE_OPENSHIFT_UID` repo variable to your tools
  namespace's range start (see below).
- **No PVC, deliberately.** Persisted datafiles end up owned by a previous deployment's UID and the
  database "freaks out" on the next rollout. A restarted pod comes back empty (fresh Oracle init)
  and is repopulated by the next deploy's migration Job.
- **Memory:** 2Gi is the floor, 4Gi comfortable — hence requests 2Gi / limits 4Gi.
- The cross-namespace NetworkPolicy matches on `kubernetes.io/metadata.name`, which Kubernetes sets
  on every namespace automatically — no reliance on platform-specific namespace labels.

## Required GitHub configuration (new)

| Kind   | Name                     | Value                                                          |
| ------ | ------------------------ | -------------------------------------------------------------- |
| secret | `OC_NAMESPACE_TOOLS`     | `<plate>-tools`                                                 |
| secret | `OC_TOKEN_TOOLS`         | pipeline ServiceAccount token **for the tools namespace**       |
| secret | `DATABASE_PASSWORD`      | password for the fixture DB's `THE` user (NOT the real Oracle `ORACLEDB_PASSWORD`) |
| var    | `DATABASE_OPENSHIFT_UID` | first value of `oc get namespace <plate>-tools -o jsonpath='{.metadata.annotations.openshift\.io/sa\.scc\.uid-range}'` (defaults to `1010470000`) |

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
- **Migration Jobs clean themselves up** (`ttlSecondsAfterFinished: 86400`, and
  `activeDeadlineSeconds` kills a hung one); inspect a recent run with
  `oc logs job/nr-ilcr-tools-database-migrate-<run-id>-<attempt> -n <plate>-tools`.
