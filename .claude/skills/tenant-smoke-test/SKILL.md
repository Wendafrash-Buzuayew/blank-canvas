---
name: tenant-smoke-test
description: Run the QRServe tenant-isolation/RBAC runtime smoke test (backend/scripts/smoke-tenant-isolation.sh) against a running local stack. Use before merging any change that touches SecurityConfig, @PreAuthorize, tenant scoping, or STOMP destination authorization.
---

# tenant-smoke-test

`backend/scripts/smoke-tenant-isolation.sh` is this repo's runtime regression
test for the exact bug class `docs/codebase-review.md` found repeatedly:
`@PreAuthorize` SpEL typos, the `ROLE_` prefix contract, cross-tenant data
leakage, privilege escalation, and STOMP topic authorization. Unit tests cover
the authorization *logic*; this script covers what can only fail at runtime —
see the comment block at the top of the script for the three specific failure
modes it targets.

It's a plain bash script (`set -uo pipefail`, `curl`/`jq`, optional
`websocat`) — run it via WSL bash on this machine (`bash backend/scripts/...`),
not PowerShell.

## When to run this

Before merging any change touching:
- `backend/shared/security/**` (`SecurityConfig`, `JwtTokenProvider`, `UserPrincipal`)
- Any controller's `@PreAuthorize` annotations
- Tenant-scoping logic in a service (`merchantId` checks)
- `StompAuthInterceptor` or STOMP destination routing

This is exactly the surface where `docs/codebase-review.md` found critical
findings that unit tests alone did not catch.

## Prerequisites

1. **A running stack.** `docker compose up -d` from `backend/`, or the services
   running locally against Postgres/Redis. The script checks
   `GET /actuator/health` first and fails fast if the gateway isn't reachable.
2. **Required secrets exported** (services fail fast without them — see
   `backend/.env.example`):
   ```bash
   export JWT_SECRET=...
   export QR_SIGNATURE_SECRET=...
   ```
3. **Two merchants with separate `MERCHANT_OWNER` users**, created ahead of time
   — the whole point of the tenant-isolation checks is proving owner A cannot
   see merchant B's data, so both must already exist:
   ```bash
   export OWNER_A_EMAIL=owner-a@example.com
   export OWNER_A_PASS=...
   export OWNER_B_EMAIL=owner-b@example.com
   export OWNER_B_PASS=...
   export MERCHANT_B_ID=<merchant B's UUID>
   ```
   If these two users/merchants don't exist yet in the target environment,
   create them first via `POST /api/auth/users` (as an existing SUPER_ADMIN) or
   whatever seed path this environment uses — ask the user if unclear which to
   use for a given environment.
4. Optional: `websocat` installed, to also exercise the STOMP destination-authz
   checks (section 7). Without it, that section is skipped with a note that
   `StompAuthInterceptorTest` (12 tests) covers the same logic at the unit level.

## Running it

```bash
bash backend/scripts/smoke-tenant-isolation.sh [GATEWAY_URL]
```

`GATEWAY_URL` defaults to `http://localhost:8081`. Pass an explicit URL when
testing against a different environment (e.g. a staging gateway).

## Reading the output

Each check prints `PASS`/`FAIL` with a one-line description of what it proved,
grouped into: token hygiene, `@PreAuthorize` wiring, tenant isolation,
privilege escalation, analytics tenant scoping, "public surface stays public"
(the inverse check — things that *should* be reachable without auth, and
things that shouldn't), and STOMP authorization. The summary line at the end
(`N passed, N failed`) and the exit code (0 iff all passed) are what to report
back — don't just skim for PASS and miss a FAIL further up.

A `FAIL` here means real, currently-exploitable behavior on the target
environment — treat it with the same severity as the findings in
`docs/codebase-review.md`, not as a flaky test.
