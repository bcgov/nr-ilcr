# Fixture-seeded Oracle for the tools namespace (waste-plus `legacydb` pattern).
# Built from the REPO ROOT context (see pr-open.yml build_context) so the test-scope
# Flyway fixtures can be baked in and stay single-sourced with the *IT suite.
FROM gvenzl/oracle-free:23.9-slim-faststart

# The same Flyway scripts AbstractOracleIT applies to its Testcontainer. The migration
# Job in openshift.deploy.yml copies them out of this image and runs Flyway over them.
COPY --chown=oracle:oracle backend/src/test/resources/db ${ORACLE_BASE}/sql

# Helper to drop an app user / PDB and tidy RMAN/FRA/diag leftovers (from nr-waste-plus)
COPY --chown=oracle:oracle database/removeDatabase ${ORACLE_BASE}/removeDatabase
RUN chmod +x ${ORACLE_BASE}/removeDatabase

# Match the oracle user's UID to the tools namespace's assigned range so OpenShift's
# restricted SCC UID resolves to the oracle passwd entry. Find the right value with:
#   oc get namespace <plate>-tools -o jsonpath='{.metadata.annotations.openshift\.io/sa\.scc\.uid-range}'
# and override via the DATABASE_OPENSHIFT_UID repository variable (see pr-open.yml).
ARG OPENSHIFT_UID=1010470000
USER root
RUN usermod -u ${OPENSHIFT_UID} oracle
USER oracle

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 CMD ["sh", "-c", "${ORACLE_BASE}/healthcheck.sh >/dev/null || exit 1"]

ENTRYPOINT ["container-entrypoint.sh"]
