# Security Policy

## Supported versions

Security fixes are applied to the latest revision of `main`.

## Reporting a vulnerability

Please **do not** open a public issue for suspected vulnerabilities.

Use GitHub's private vulnerability reporting on this repository:

1. Open the **Security** tab.
2. Choose **Report a vulnerability**.
3. Include the affected endpoint or component, reproduction steps, impact and
   any suggested fix.

You can expect an initial response within a few days. Once a fix is available,
a coordinated disclosure can be arranged.

## Scope

In scope:

- Authentication and authorization flaws (JWT handling, refresh-token rotation,
  team-role enforcement, object-level access checks)
- Injection, path traversal and unsafe file handling in attachments
- Sensitive data exposure through API responses, logs or error messages
- Cross-site request forgery and CORS misconfiguration

Out of scope:

- Findings that require the attacker to already hold valid credentials with
  the privileges being exercised
- Missing rate limiting or other hardening items already documented as
  limitations in the README
- Vulnerabilities in third-party dependencies without a demonstrated impact on
  this application

## Deployment notes

The defaults in `application.yml` are intended for local development. For a
production deployment, always override `JWT_SECRET`, `DB_PASSWORD` and the CORS
settings, serve the API over HTTPS, and disable the demo seeder.
