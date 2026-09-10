(ns legislature.governor
  "LegislativeGovernor — the independent safety/traceability layer for the
  ISCO-08 1111 legislative office support actor. Wired as its own `:govern`
  node in `legislature.actor`'s StateGraph, downstream of `:advise` — the
  Advisor has no notion of constituent provenance, bill verification, or
  sensitive-topic risk, so this MUST be a separate system able to reject a
  proposal (itonami actor pattern, per ADR-2607011000 / CLAUDE.md Actors
  section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. constituent provenance  — if constituent-id is provided, it must
       be registered.
    2. bill verification       — if bill-id is provided, it must be
       registered and verified.
    3. no-actuation            — proposal :effect must be :propose.
    4. no-binding-authority    — no :op that claims or exercises binding
       legislative power.

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    5. :flag-conflict-of-interest always escalates (sensitive by definition).
    6. topic sensitivity       — topics tagged as legally/politically
       sensitive must escalate for legislator review.
    7. low confidence (< `confidence-floor`)."
  (:require [legislature.store :as store]))

(def confidence-floor 0.6)
(def ^:private sensitive-topics #{:legislation-position :vote-position :public-statement})
(def ^:private escalating-ops #{:flag-conflict-of-interest})

(defn- hard-violations [{:keys [proposal request]} constituent-record bill-record]
  (cond-> []
    (and (some? (:constituent-id request))
         (nil? constituent-record))
    (conj {:rule :no-constituent :detail "unregistered constituent"})

    (and (some? (:bill-id request))
         (nil? bill-record))
    (conj {:rule :no-bill :detail "unregistered or unverified bill"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect must be :propose only (no direct dispatch)"})

    (get request :no-binding-authority)
    (conj {:rule :no-binding-authority :detail "actor has no binding legislative power (voting, sponsorship, floor speech)"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a `store`
  implementing `legislature.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [constituent-record (when (some? (:constituent-id request))
                              (store/constituent store (:constituent-id request)))
        bill-record (when (some? (:bill-id request))
                      (store/bill store (:bill-id request)))
        hard (hard-violations {:proposal proposal :request request}
                              constituent-record bill-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        flagging-conflict? (contains? escalating-ops (:op proposal))
        topic-sensitive? (contains? sensitive-topics (:topic context))
        risky? (or flagging-conflict? topic-sensitive?)]
    {:ok? (and (not hard?) (not low?) (not risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky?))}))
