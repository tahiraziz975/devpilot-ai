# DevPilot AI technical audit and pause report

**Audit date:** 2026-11-14  
**Scope:** Source, configuration, docs, tests, CI, tracked dataset/model layout,
and locally available validation. No external services, credentials, or
production database were used.

## Executive summary

The application is a small React, Spring Boot, MySQL, and FastAPI system. The
core paths are implemented: authentication, issue classification, per-user
prediction history, optional Google OAuth, GitHub App connection with
read-only repository inspection, and Groq chat. Local validation succeeded
for frontend build, backend tests, Python tests, and a read-only model
evaluation.

One verified repository inspection bug was fixed: reading a file depended on a
directory listing capped at 200 results. That could reject a valid source file
in a large directory. File retrieval now goes directly to GitHub's exact-path
contents API. The response maps a genuine missing file to 404. Default Hibernate
SQL echo was also disabled; this prevents normal startup/request SQL from
flooding logs. It can be enabled temporarily with `SPRING_JPA_SHOW_SQL=true`.
Documentation now reflects actual behavior and the architecture diagram no
longer points to a developer-local image path.

This is suitable to pause as a development or portfolio project, with the
limitations below kept visible. It is not validated as a production deployment:
there is no deployment/container setup, schema migration workflow, live-service
integration run, or dependency vulnerability scan in this checkout.

## Architecture and workflows

The React frontend calls a Spring Boot API. Spring Boot uses MySQL for users,
settings, integrations, and prediction history; forwards classification input
to the Python FastAPI service; calls Groq for chat; and uses GitHub App
credentials to mint short-lived installation tokens. GitHub repository reads
are limited to the current user's enabled repositories and use metadata/contents
read permissions. No Docker or Compose files are present. See
[architecture.md](architecture.md).

The principal paths were traced through controllers, services, repositories,
clients, frontend callers, and tests. The application does not implement
similar-issue retrieval, feedback delivery, profile editing persistence, or a
production deployment workflow; current UI/docs should not present these as
working backend capabilities.

## Findings and remediation

| ID | Category | Severity | Evidence and disposition |
|---|---|---:|---|
| AUD-01 | GitHub repository inspection | Medium | `GitHubRepositoryService.readFile` first searched a directory list capped at 200 entries. A file omitted by that cap was rejected despite being present. Removed the listing gate and added service-level regression coverage plus an HTTP-level test that maps GitHub 404 to a real 404. |
| AUD-02 | Logging | Low | `spring.jpa.show-sql=true` printed every Hibernate statement in normal use. Changed it to default false with an environment override. This reduces log noise; it does not change or optimize the underlying database queries. |
| AUD-03 | Documentation | Low | The architecture page referenced `../../../Downloads/mermaid-diagram.png`, which is not a project artifact. The README also claimed chat never sends repository content despite implemented opt-in repository tools. Replaced the broken diagram with a Mermaid architecture and corrected the behavior/setup notes. |
| AUD-04 | Database lifecycle | Medium, unresolved | `spring.jpa.hibernate.ddl-auto=update` is enabled and no migration tool/scripts are present. This is convenient for local development but does not provide reviewed, reversible production schema changes. Retain for now to avoid changing startup behavior; add a migration workflow before production deployment. |
| AUD-05 | ML class balance | Medium, unresolved | Re-evaluation on the same stratified 15,000-record validation sample used by the saved bundle achieved accuracy 0.8188, macro F1 0.6968, weighted F1 0.8238. F1 was 0.58 for documentation and 0.50 for question; these minority classes need careful interpretation. No model retraining/artifact replacement was performed. |
| AUD-06 | Test breadth | Medium, unresolved | There are backend and ML tests and CI build jobs, but no frontend unit/UI tests, no DB-backed integration suite, and no live Groq/GitHub/ML-service integration verification. External credentials/services were unavailable and not fabricated. |
| AUD-07 | Deployment readiness | Medium, unresolved | No Docker/Compose or production profile is present. `ddl-auto=update` and the unauthenticated ML API require a private deployment boundary. Keep the ML service on loopback/private networking. |
| AUD-08 | Security validation | Informational, unresolved | Code review confirmed session checks on chat/history/account-scoped endpoints, user-filtered history queries, protected GitHub selection, short-lived tokens, read-only GitHub permissions, input size bounds, and secret environment configuration. No automated dependency/security scan or adversarial live-service assessment was run. Security is not certified. |
| AUD-09 | Settings persistence | Low, unresolved | The email-updates preference is loaded from MySQL but its UI toggle only changes React state; compact-results is also client-side only. The UI says preferences are saved for the session. Persisting these choices would require an API extension and is left as future work rather than presented as completed behavior. |

No confirmed critical or high-severity vulnerability was found in the inspected
local code. This statement is limited to static review and testable local
behavior, not a penetration test.

## ML evaluation

The saved versioned artifact pairs the vectorizer and classifier and validates
their feature/class metadata at load time. Training uses a fixed random seed,
stratified samples, a paired artifact, and prints accuracy, macro/weighted F1,
and per-class metrics. The existing artifact was evaluated without retraining:

| Class | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| bug | 0.90 | 0.86 | 0.88 | 7,872 |
| documentation | 0.53 | 0.65 | 0.58 | 668 |
| feature | 0.84 | 0.82 | 0.83 | 5,559 |
| question | 0.44 | 0.58 | 0.50 | 901 |

The validation score is useful evidence, not an unbiased final test score: this
report reproduces the configured validation sample, and no independent held-out
test evaluation was performed. Predicted probabilities are model scores and
have not been separately calibrated.

## Security and privacy assessment

- Passwords are encoded with BCrypt. Authentication and protected features use
  a server-side session identity; prediction history queries are scoped by that
  user ID.
- GitHub connection state is random, one-time, time-limited, and verified
  against the App API. The agent uses installation tokens with metadata and
  contents read permission, does not persist tokens, and exposes no write tool.
- Chat is limited to signed-in users. Greeting/general requests are routed
  without opening a repository session. Only explicitly enabled repositories
  may be inspected. Repository contents are marked as untrusted prompt data.
- No secrets are committed in the inspected application configuration; runtime
  credentials are environment values. Database password is required at startup.
- The Python API has no user authentication; deployment must keep it private.
  The backend security chains disable CSRF. Current JSON body endpoints and
  explicit trusted-origin checks provide some practical barriers, but a
  defense-in-depth CSRF token policy and production cookie/security settings
  should be established before exposing a deployment across origins.
- No runtime secret scan, dependency vulnerability scan, or production
  authorization test against a live database was performed.

## Validation results

- Backend: `mvn -B verify` — **24 tests passed**, 0 failures/errors; executable JAR packaged.
- Frontend: `npm run build` — **passed** (Vite production build).
- ML: `PYTHONPATH=src .venv/bin/pytest -q tests` — **4 passed**.
- Model: evaluation on 15,000 validation records completed; metrics above.
- Not run: live MySQL/API end-to-end tests, live Groq/GitHub requests, browser
  UI automation, frontend unit tests (none are defined), dependency audit, or
  Docker validation (no Docker configuration exists).

## Scorecard

Scores describe the checked-out implementation and evidence above; they are
not security certification or production-readiness ratings.

| Area | Score | Basis |
|---|---:|---|
| Architecture | 7/10 | Clear service boundaries; no production deployment topology. |
| Code quality | 6/10 | Understandable focused modules; some presentational/service scope gaps remain. |
| Frontend | 6/10 | Builds, but no automated component or browser tests. |
| Backend | 7/10 | Core workflows and 22 unit tests; no DB-backed integration verification. |
| Machine learning | 7/10 | Reproducible paired artifact and metrics; minority-class F1 is weaker and final test set is not evaluated here. |
| AI integration | 7/10 | Bounded read-only tools, general/project routing, and mocked API tests; no live provider verification. |
| Security | 6/10 | Good account scoping and read-only GitHub design; CSRF/deployment hardening and scans remain. |
| Database | 5/10 | User-scoped repositories and history; no migrations, and automatic DDL update remains. |
| Testing | 6/10 | Backend and ML suites pass; frontend and deployed integration coverage absent. |
| Documentation | 7/10 | Setup and architecture corrected; operational and API documentation is still limited. |
| Deployment readiness | 4/10 | No container/production profile, migrations, or live integration validation. |
| Portfolio presentation | 7/10 | Demonstrates full-stack, ML, OAuth/GitHub, and LLM integration with honest limits. |

## Pause checklist

### P0 — Must resolve before pausing

- None identified for a local development/portfolio pause after the validated
  fixes.

### P1 — Before production or public hosted use

- Add versioned, reversible database migrations and stop relying on Hibernate
  schema auto-update.
- Define production session cookie, HTTPS, origin, CSRF, CORS, and rate-limit
  policies; keep the ML API private.
- Run dependency and secret scans, plus a security review using the intended
  deployment configuration.
- Add frontend, database-backed, and service integration tests.

### P2 — Useful next improvements

- Evaluate the untouched independent test split and consider calibration or
  data improvements for `question` and `documentation`.
- Add frontend behavior tests and document supportable local database setup.

### Future enhancements

- Similar-issue retrieval, feedback delivery, persistent profile editing, and
  containerized deployment are not implemented and can remain future work.

## Final verdict

1. **Stable enough to pause?** Yes for local development and portfolio use after
   the fixes and checks in this report.
2. **Critical/high unresolved?** None confirmed in the inspected code. Medium
   production-readiness issues remain.
3. **Major workflows?** Classification, history, chat, and GitHub read-only
   setup have code paths and tests; live service flows were not exercised.
4. **Security reasonable for intended use?** Reasonable for a locally run/demo
   application with private service boundaries; not yet production-hardened.
5. **Practical ML engineering?** Yes: reproducible preprocessing/training,
   paired artifact validation, API inference, and evaluation metrics. Minority
   labels need improvement and independent test evaluation.
6. **Maintainable AI integration?** The provider client and read-only tool
   boundary are tested and reasonably modular; live-provider behavior remains
   unverified.
7. **Professionally presentable?** Yes as a portfolio prototype, with the
   documented limitations retained.
8. **Before public hosting:** migrations, deployment hardening, security scans,
   rate limiting, and end-to-end verification are required.
