# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for a security problem. Report it privately through GitHub:
**Security → Report a vulnerability** on this repository (private vulnerability reporting).

Include what you found, how to reproduce it and which version it affects. You will get an answer
as soon as possible; the project is maintained by one person, so please allow a few days.

## Scope and design notes

BEZA Hub is a local-only Android reader: no accounts, no backend, no analytics. Feeds, web pages and
their links are treated as untrusted:

- only `http`/`https` addresses are opened, fetched, played or shown as images (`UrlSafety.kt`);
- plain `http` traffic is disabled (`network_security_config.xml`) and redirects never leave `https`;
- feed downloads and episode downloads have size limits;
- downloaded episodes live in the app's private storage and are excluded from backups.
