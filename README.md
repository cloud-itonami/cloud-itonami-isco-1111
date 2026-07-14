# cloud-itonami-isco-1111

Open Occupation Blueprint for **ISCO-08 1111**: Legislators.

This repository designs a staff-support actor for a legislative office: a document advisor supports a legislator's office administrative workflow (briefing preparation, constituent correspondence, bill summarization) under a governor-gated actor, **while explicitly excluding any binding legislative authority** (voting, bill sponsorship, floor speech).

## Scope & Constraints

**This actor supports a legislator's OFFICE, not a legislator as a voting agent.** It is a staff-support tool, never a replacement for human legislative judgment or authority. The actor explicitly cannot:

- cast or influence votes
- represent itself as speaking for the legislator (proposals only)
- claim or exercise any binding legislative power
- disclose constituent data without verification and human review
- propose public statements or positions without explicit human review

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs the physical domain work**. Here a document advisor supports a legislator's office administrative workflow under an actor that proposes actions and an independent **Legislative Governor** that gates them. The governor never dispatches actions without human gating; escalation-category proposals (sensitive topics, conflict-of-interest flagging) require explicit human sign-off.

## Core Contract

```text
constituent inquiry + bill records + office context
        |
        v
Legislative Advisor -> Legislative Governor -> draft briefing/reply, or escalate for review
        |
        v
office actions (gated) + operating records + audit ledger
```

No automated advice can dispatch an office action the governor refuses, suppress an operating record, or disclose constituent/bill data without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation) (ISCO-08 `1111`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and [`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation (`:maturity :implemented`)

Full itonami Actor pattern (per ADR-2607011000 / CLAUDE.md's Actors section): a real [`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph) `StateGraph`, with the Advisor and Governor as distinct graph nodes and human-in-the-loop interrupt/resume via checkpointing.

```text
:intake -> :advise -> :govern -> :decide -+-> :commit            (:ok? true)
                                           +-> :request-approval   (:escalate? true, interrupt-before)
                                           +-> :hold               (:hard? true)
```

- `src/legislature/store.cljc` — `Store` protocol + `MemStore`: constituent records, bills, office requests, an append-only audit ledger.
- `src/legislature/advisor.cljc` — `Advisor` protocol; `mock-advisor` (deterministic, default) proposes an office operation from a request; `llm-advisor` wraps a `langchain.model/ChatModel` — either way the advisor only ever produces a `:propose`-effect proposal, never a committed record, and LLM parse failures always yield `confidence 0.0` (forces escalation, never fabricated confidence).
- `src/legislature/governor.cljc` — `LegislativeGovernor/check`: a pure function, wired as its own `:govern` node. Hard invariants (unregistered constituent, unverified bill, a proposal whose `:effect` isn't `:propose`) always route to `:hold`. Escalation invariants (sensitive topics, `:flag-conflict-of-interest`, or low advisor confidence) always route to `:request-approval` — an `interrupt-before` node that the graph checkpoints and only resumes on explicit human approval (`actor/approve!`).
- `src/legislature/actor.cljc` — `build-graph`, `run-request!`, `approve!`: the `langgraph.graph/state-graph` wiring itself.

Proposal ops (all `:effect :propose` only, closed allowlist):
- `:draft-briefing` — prepare a policy briefing document for legislator review.
- `:schedule-constituent-meeting` — propose a constituent meeting slot.
- `:draft-correspondence` — prepare a reply to constituent correspondence.
- `:summarize-bill` — prepare a neutral summary of pending legislation.
- `:flag-conflict-of-interest` — surface a potential conflict for the legislator's attention (always escalates).

```bash
clojure -M:test
```

This is what backs this repo's `:maturity :implemented` entry in [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation).

## License

AGPL-3.0-or-later.
