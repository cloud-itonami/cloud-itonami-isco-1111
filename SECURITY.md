# Security Policy

## Reporting security vulnerabilities

If you discover a security vulnerability in this codebase, **do not open a public issue**. Instead, please report it privately to the maintainers through the gftdcojp organization or by contacting jun784@gmail.com.

Please include:

- A description of the vulnerability.
- Steps to reproduce (if applicable).
- The potential impact.

We take security seriously and will respond to all reports promptly.

## Scope

This project's security model centers on:

1. **Governance isolation**: The Advisor and Governor are separate systems; no proposal can bypass the governor.
2. **Audit accountability**: Every proposal, verdict, and disposition is logged immutably.
3. **No binding authority**: The actor never executes binding legislative actions on behalf of the legislator.

For operators: constituent data, legislator records, and bill metadata should be stored outside this repository, in a secure backend (e.g., Datomic with role-based access control, kotoba-server with audit logging).

## Known limitations

- The reference implementation (`MemStore`) is deterministic and in-memory; it is not suitable for production without a persistent, auditable backend.
- LLM advisors depend on the security of the underlying language model; prompt injection risks should be mitigated at the application level.

## Version support

Only the latest release receives security updates. Operators should regularly upgrade dependencies and stay informed of any published advisories.
