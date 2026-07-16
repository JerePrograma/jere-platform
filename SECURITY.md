# Security Policy

## Repository status

This repository is publicly visible and uses a proprietary all-rights-reserved license. Public visibility does not make the source open source and does not make it suitable for storing confidential implementations.

Do not commit:

- credentials, tokens, certificates or private keys;
- customer or employee data;
- production exports, logs or database dumps;
- private infrastructure details;
- proprietary customer-specific logic;
- regulated financial, health or tax data.

## Supported versions

The project is in foundation stage and has no supported production release. Security fixes target the default branch until release branches are explicitly introduced.

## Reporting a vulnerability

Do not open a public issue containing exploitable details. Contact the repository owner privately and include the affected component, reproducible impact, prerequisites and suggested mitigation.

## Development requirements

- Use environment variables or external secret stores.
- Keep `.env` and credential files outside version control.
- Use anonymized, repeatable fixtures.
- Treat tenant isolation and authorization bypasses as security defects.
- Avoid logging credentials, session tokens, complete payment data or sensitive personal fields.
- Rotate any credential immediately if committed.
- Run dependency and secret checks in CI.
- Keep proprietary vertical implementations outside this public repository until visibility is changed.

## Security boundary

The current codebase is a generic runtime foundation. Before production workloads are introduced, add threat modelling for tenancy, authentication, authorization, audit, outbox processing and backup recovery.
