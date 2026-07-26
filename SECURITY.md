# Security Policy

## Supported Versions

We take the security of the APEX-UPI ecosystem very seriously, especially given the financial nature of the transaction switch. We actively maintain and provide security updates for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability within the APEX-UPI architecture (including the PSP switch, ledger services, Kafka brokers, or transaction orchestrator), please follow our responsible disclosure guidelines.

**Do not report security vulnerabilities through public GitHub issues.**

Instead, please report it directly by emailing our security team at: **apexupi.hpe@gmail.com**

Please include the following information in your report:
- A detailed description of the vulnerability and its potential financial or data impact.
- Step-by-step instructions to reproduce the vulnerability.
- Any relevant logs, packet captures, or code snippets.

You should receive a response acknowledging receipt of your vulnerability report within 48 hours. We will keep you updated on the progress of the fix and coordinate a public disclosure date if necessary.

## Security Scope
The scope of our active vulnerability monitoring encompasses:
- Core banking services and NPCI adapter interfaces
- Idempotency layer bypasses and Redis cache poisoning
- Kafka event payload tampering or message injection
- Cross-tenant data leakage in TPAP multi-tenancy routing
