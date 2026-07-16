# Security Policy

## Repository status

This repository is public. Do not commit credentials, tokens, certificates, private keys, customer data, production exports or private infrastructure details.

## Supported versions

The project is in foundation stage and has no supported production release yet. Security fixes should target the default branch and any explicitly maintained release branches once they exist.

## Reporting a vulnerability

Do not open a public issue containing exploitable details or sensitive information. Contact the repository owner privately through an agreed channel and include:

- affected component;
- reproducible impact;
- prerequisites;
- suggested mitigation, when available;
- whether any real data or credentials may have been exposed.

## Development requirements

- Use environment variables or external secret stores.
- Keep `.env` and credential files outside version control.
- Use anonymized test fixtures.
- Treat tenant isolation and authorization bypasses as security defects.
- Avoid logging tokens, passwords, complete payment data or sensitive personal fields.
- Rotate any credential immediately if it is committed, even if the commit is later removed.
- Review third-party dependencies and container images through automated checks.

## Public repository warning

Before moving proprietary implementations into this repository, decide whether the source should remain public and define an explicit license. Absence of a license does not replace access control or prevent source disclosure.
