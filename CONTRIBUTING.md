# Contributing to APEX-UPI

First off, thank you for considering contributing to APEX-UPI! This document outlines the guidelines and best practices for contributing to this project. 

As a high-concurrency PSP Switch and Ledger Engine designed for HPE NonStop platforms, we adhere to strict engineering standards to ensure the highest levels of performance, reliability, and code quality.

---

## 1. Branch Naming Conventions

We follow a structured branching model. Please use the following prefixes for your branch names:

- `feature/<ticket-id>-short-description` - For new features or significant enhancements
- `fix/<ticket-id>-short-description` - For bug fixes and patches
- `docs/<ticket-id>-short-description` - For documentation updates
- `chore/<ticket-id>-short-description` - For routine tasks, dependency upgrades, or tooling changes

*Example: `feature/UPI-102-add-rate-limiting`*

---

## 2. Commit Messages (Conventional Commits)

We enforce the [Conventional Commits](https://www.conventionalcommits.org/) specification for all commit messages. This ensures a readable history and enables automated changelog generation.

### Format
```
<type>(<scope>): <description>
```

### Examples
- `feat(tpap-ingress): add rate limiting per TPAP`
- `fix(tpap-ingress): return 409 on duplicate txnId`
- `docs(psp-switch): update idempotency section in architecture spec`
- `test(cbs): add balance inquiry edge cases`
- `chore(infra): upgrade Kafka to version 7.6.0`

---

## 3. Pull Request Checklist

Before submitting a Pull Request, please ensure the following requirements are met:

- [ ] **Testing:** Unit tests and/or integration tests have been added or updated for every changed behavior.
- [ ] **Documentation:** System documentation (e.g., `01_ARCHITECTURE_SPEC.md`) is updated if behavior has changed.
- [ ] **Architecture:** A new Architecture Decision Record (ADR) is created if a significant architectural change was made.
- [ ] **Changelog:** `CHANGELOG.md` is updated under the `[Unreleased]` section.
- [ ] **Code Quality:** Checkstyle, PMD, and SpotBugs all pass locally without errors.

---

## 4. Continuous Integration (CI) Gates

Our automated CI pipeline will enforce the following quality gates on every Pull Request:

1. **Checkstyle:** Strict adherence to Google Java Style conventions.
2. **PMD:** Static code analysis to detect code smells and bad practices.
3. **SpotBugs:** Bytecode analysis to detect common bug patterns.
4. **Unit Tests:** Comprehensive JUnit 5 testing (must run without external dependencies).
5. **Integration Tests:** End-to-end testing against real Kafka, Redis, and PostgreSQL instances using `docker-compose.ci.yml`.
6. **Code Coverage (JaCoCo):** A strict gate requiring a minimum of **80% line coverage** for all new code.

---

## 5. Development Environment Setup

Please refer to the `Documentation/Deployment Docs/` directory for detailed instructions on setting up your local environment, database schemas, and necessary infrastructure dependencies (Kafka, Redis, PostgreSQL).