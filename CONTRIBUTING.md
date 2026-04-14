## Branch naming
- feature/<ticket-id>-short-description
- fix/<ticket-id>-short-description
- docs/<ticket-id>-short-description

## Commit messages (Conventional Commits)
- feat(tpap-ingress): add rate limiting per TPAP
- fix(tpap-ingress): return 409 on duplicate txnId
- docs(tpap-ingress): update idempotency section in spec
- test(tpap-ingress): add balance inquiry edge cases
- chore(infra): upgrade Kafka to 7.6.0

## PR checklist (also in PULL_REQUEST_TEMPLATE.md)
- [ ] Tests added or updated for every changed behaviour
- [ ] `01_ARCHITECTURE_SPEC.md` updated if behaviour changed
- [ ] New ADR created if an architectural decision was made
- [ ] `CHANGELOG.md` updated under [Unreleased]
- [ ] Checkstyle, PMD, SpotBugs all pass locally

## What the CI checks
1. Checkstyle (Google Java Style)
2. PMD (code smell rules)
3. SpotBugs (bug pattern detection)
4. Unit tests (JUnit 5, no external deps)
5. Integration tests (JUnit 5, real Kafka/Redis/Postgres via docker-compose.ci.yml)
6. JaCoCo coverage gate (80% line coverage minimum)