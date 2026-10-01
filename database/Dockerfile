# Shared Oracle for the TOOLS namespace. Same image the backend integration
# tests run against (see backend .../support/AbstractOracleIT.java), so the
# deployed database matches what the test suite exercises.
FROM gvenzl/oracle-free:23.9-slim-faststart

# OpenShift's restricted SCC ignores the image USER and runs the container as
# the first UID of the namespace's assigned range. Remap the oracle user to
# that UID so sqlplus "/ as sysdba" OS-auth still resolves to oracle (dba);
# file access comes from the oracle group ids added as supplementalGroups in
# database/openshift.deploy.yml. Look the value up per namespace with:
#   oc get ns <plate>-tools -o jsonpath='{.metadata.annotations.openshift\.io/sa\.scc\.uid-range}'
# (Same approach as nr-waste-plus legacydb.)
# This repo's tools namespace range is 1004950000/10000.
ARG OPENSHIFT_UID=1004950000
USER root
RUN usermod -u ${OPENSHIFT_UID} oracle
USER oracle

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 CMD ["sh", "-c", "${ORACLE_BASE}/healthcheck.sh >/dev/null || exit 1"]

ENTRYPOINT ["container-entrypoint.sh"]
