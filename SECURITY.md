# Security Policy

## Supported Versions

NR ILCR is currently a modernization scaffold. Support expectations will be formalized before production onboarding.

## Reporting a Vulnerability

Do not open public GitHub issues for security vulnerabilities. Report vulnerabilities through the repository Security tab and private advisory flow.

## Baseline Controls

This scaffold keeps the BC Gov OpenShift hardening posture from the template:

- Frontend traffic is served through Caddy with Coraza WAF and secure response headers.
- Backend and frontend OpenShift manifests run as non-root, drop Linux capabilities, disable privilege escalation, and use read-only root filesystems.
- Network policies limit ingress to frontend and frontend-to-backend traffic.
- Dependency, container, and repository scans run from GitHub Actions.
- Runtime database credentials and FAM/Cognito configuration must come from environment variables or OpenShift secrets once those integrations are implemented. The current local scaffold keeps only sanitized examples in Git; real local credentials belong in ignored `.env` files.

## Vulnerability Triage SLAs

- Critical: triage within 24 hours.
- High: triage within 1 week.
- Medium: triage within 2 weeks.
- Low: triage in the next planned maintenance cycle.
