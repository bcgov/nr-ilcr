# Fixture-seeded Oracle for the tools namespace. Built from the REPO ROOT context
# (see pr-open.yml build_context) so the test-scope Flyway fixtures can be baked in
# and stay single-sourced with the *IT suite.
FROM gvenzl/oracle-free:23.9-slim-faststart

# The same Flyway scripts AbstractOracleIT applies to its Testcontainer. The migration
# Job in openshift.deploy.yml copies them out of this image and runs Flyway over them.
COPY --chown=oracle:oracle backend/src/test/resources/db ${ORACLE_BASE}/sql

# Match the oracle user's UID to the tools namespace's assigned range: OpenShift's
# restricted SCC runs the container with the namespace UID regardless of the image
# USER, and Oracle needs that UID to resolve to the oracle passwd entry (see
# gvenzl/oci-oracle-free#59). Find the right value with:
#   oc get namespace <plate>-tools -o jsonpath='{.metadata.annotations.openshift\.io/sa\.scc\.uid-range}'
# and override via the DATABASE_OPENSHIFT_UID repository variable (see pr-open.yml).
ARG OPENSHIFT_UID=1010470000
USER root
RUN usermod -u ${OPENSHIFT_UID} oracle
USER oracle
