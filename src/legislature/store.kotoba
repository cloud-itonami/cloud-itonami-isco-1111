(ns legislature.store
  "SSoT for the ISCO-08 1111 legislative office support actor. Store is a
  protocol injected into the `legislature.actor` StateGraph — `MemStore`
  is the default, deterministic, zero-dep backend; a Datomic/kotoba-server-
  backed implementation can be swapped in without touching the actor or
  governor (itonami actor pattern, per ADR-2607011000 / CLAUDE.md Actors
  section).

  Domain:

    constituent  — a registered constituent or stakeholder
                   (:constituent-id, :name)
    bill         — a bill under consideration (:bill-id, :title, :status)
    record       — a committed office record (briefing drafted, constituent
                   meeting scheduled, correspondence drafted, bill
                   summarized, conflict flagged) — written ONLY via
                   commit-record!, never mutated in place
    ledger       — an append-only audit trail of every proposal/verdict/
                   disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (constituent [s constituent-id])
  (bill [s bill-id])
  (records-of [s constituent-id])
  (ledger [s])
  (register-constituent! [s constituent])
  (register-bill! [s bill])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (constituent [_ constituent-id] (get-in @a [:constituents constituent-id]))
  (bill [_ bill-id] (get-in @a [:bills bill-id]))
  (records-of [_ constituent-id] (filter #(= constituent-id (:constituent-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-constituent! [s constituent]
    (swap! a assoc-in [:constituents (:constituent-id constituent)] constituent) s)
  (register-bill! [s bill]
    (swap! a assoc-in [:bills (:bill-id bill)] bill) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:constituents {} :bills {} :records [] :ledger []} seed)))))
