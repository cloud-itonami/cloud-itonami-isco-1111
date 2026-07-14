# Contributing

Thank you for considering contributing to this open-occupation blueprint. This is a collaboration around a staff-support actor for legislative offices, designed with transparency and audit accountability.

## Core principles

- **Transparency**: Proposals are documented in the audit ledger; decisions are traceable.
- **No direct authority**: This actor supports legislative staff — it never exercises binding power.
- **Safety first**: Security and governance checks are independent layers, not swappable.

## How to contribute

1. Fork and create a branch for your change.
2. For code changes:
   - Follow the existing `.cljc` portable-code pattern (no platform-specific code in core).
   - Ensure all tests pass: `clojure -M:test`.
   - Add tests for new behavior.
3. For governance/architecture changes:
   - Start with an ADR in `docs/adr/` (follow the ADR template).
   - Discuss in issues before submitting a PR.
4. Ensure data sensitivity: do not commit real constituent, legislator, or bill data in tests or examples.

## Pull requests

- Keep PRs focused: one concern per PR.
- Provide a clear summary of what you've changed and why.
- Link to any related issues or ADRs.

## Testing

```bash
clojure -M:test
```

All tests must pass. New functionality should include tests.

## Licensing

By contributing, you agree that your contribution is licensed under the AGPL-3.0-or-later license (see LICENSE).
