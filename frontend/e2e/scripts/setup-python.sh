#!/usr/bin/env bash
# Reproducible Python environment for the data-backed DB restore/seed scripts (thin-mode
# python-oracledb). Creates a local .venv under scripts/ and installs the pinned requirements, so a
# fresh checkout can run the manual S13/S24 coverage without depending on each developer's global
# interpreter state. Idempotent: re-running just re-syncs the pinned deps.
#
# Usage:  cd frontend/e2e && npm run setup:python   (or: bash scripts/setup-python.sh)
# The suite's DB runner (steps/sch1/schedule1DbRestore.ts) auto-detects scripts/.venv, so no manual
# PYTHON export is needed once this has run.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
venv="$here/.venv"

# Prefer python3; fall back to python (Windows launcher / git-bash).
py="${PYTHON_BOOTSTRAP:-python3}"
command -v "$py" >/dev/null 2>&1 || py="python"
command -v "$py" >/dev/null 2>&1 || {
  echo "ERROR: no python3/python on PATH. Install Python 3.9+ and re-run." >&2
  exit 1
}

"$py" -m venv "$venv"

# venv layout differs on Windows (Scripts) vs POSIX (bin).
if [ -x "$venv/bin/python" ]; then
  vpy="$venv/bin/python"
else
  vpy="$venv/Scripts/python.exe"
fi

"$vpy" -m pip install --quiet --upgrade pip
"$vpy" -m pip install --quiet -r "$here/requirements.txt"

echo "Python env ready: $venv"
"$vpy" -c "import oracledb; print('python-oracledb', oracledb.__version__)"
