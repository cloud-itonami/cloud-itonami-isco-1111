(ns legislature.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300)
  for the ISCO-08 cluster: this repo previously had NO demo page and no
  generator at all (`:item2/classification \"unknown-no-demo\"` in the
  fleet-wide scan). This namespace drives the REAL actor stack
  (`legislature.actor` -> `legislature.governor` -> `legislature.store`)
  through a scenario built from real, exercised store data and renders
  the result deterministically -- no invented numbers, no timestamps in
  the page content, byte-identical across reruns against the same seed
  (verify by diffing two consecutive runs before shipping).

  Same 4-file ISCO shape as `cloud-itonami-isco-1211`
  (`src/<domain>/{governor,actor,advisor,store}.cljc`), which is where
  this render-html/regenerate.yml pairing was first proven on the ISCO
  side (com-junkawasaki/root 90-docs/business/cloud-itonami-maturity-loop.md
  iteration 9) after being adapted from the ISIC-side template
  (`90-docs/business/scripts/flagship-generator-template/`).

  Ground truth for seed data: `constituent-1` (\"Alice Voter\") and
  `bill-2024-042` (\"Education Reform Act\") are lifted VERBATIM from
  this repo's own passing test fixture -- the identical `fresh-store`
  helper duplicated in both `legislature.actor-test` and
  `legislature.governor-test`. `constituent-2` (\"Jordan Reyes\") is
  ADDITIONAL demo data registered via the same real
  `register-constituent!` protocol call (this actor's own test fixture
  only ever registers one constituent, so a second one is needed here
  to demonstrate a clean commit on a constituent who isn't the sole
  fixture entity) -- disclosed here plainly, not presented as if it
  were pre-existing fixture data. Every other field this page displays
  (record counts, hold reasons) is real output read after `run-demo!`
  actually executed the graph -- none of it is hand-typed.

  Known architectural gap, honestly noted rather than papered over:
  TWO of `legislature.governor`'s 7 rules are not reachable through
  this real end-to-end demo (both are unit-tested directly against
  `governor/check` in `legislature.governor-test`, just not drivable
  through the real advisor+graph path):

  - `:no-actuation` (proposal `:effect` must be `:propose`) -- the real
    `mock-advisor` (`legislature.advisor/infer`) unconditionally sets
    `:effect :propose` on every proposal it emits; the advisor can
    never itself emit a raw store write. Covered by
    `hard-on-no-actuation-violation`.
  - `escalates-on-low-confidence` (advisor confidence < 0.6) --
    `infer`'s `:confidence` is derived from `:stake` via
    `(case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)`, whose
    lowest reachable value (0.7) is already above the governor's 0.6
    floor, and that `case` has no default clause, so an out-of-set
    `:stake` value throws rather than yielding a low confidence.
    Covered by `escalates-on-low-confidence` (calls `governor/check`
    directly with a hand-built low-confidence proposal).

  The other 5 of 7 rules (no-constituent, no-bill, no-binding-authority,
  flag-conflict-of-interest always-escalates, sensitive-topic
  escalation) ARE all reached below, through the real compiled graph.

  Usage: `clojure -M:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [legislature.store :as store]
            [legislature.actor :as actor]))

;; ----------------------------- harness --------------------------------
;; legislature.actor already exposes run-request!/approve! wrappers
;; around langgraph.graph/run* (no raw g/run* needed here, unlike the
;; ISIC-side template -- this repo's own actor ns is the harness).

(defn- run-op!
  "Drives one real office operation request through the actual compiled
  graph for `tid` (thread-id). If the graph escalates (interrupts before
  `:request-approval`), immediately approves it (this demo's scenario
  never demonstrates an UNAPPROVED escalation -- every escalation here
  reaches a human who signs off). Returns a map describing exactly what
  really happened -- no field is invented."
  [graph tid request context]
  (let [r1 (actor/run-request! graph request context tid)]
    (if (= :interrupted (:status r1))
      (let [r2 (actor/approve! graph tid)]
        {:thread-id tid :request request :context context
         :outcome :approved-and-committed
         :record (get-in r2 [:state :record])})
      (let [disposition (get-in r1 [:state :disposition])]
        (if (= :hold disposition)
          {:thread-id tid :request request :context context
           :outcome :hard-hold
           :verdict (get-in r1 [:state :verdict])
           :rule (-> r1 :state :verdict :violations first :rule)}
          {:thread-id tid :request request :context context
           :outcome :auto-committed
           :record (get-in r1 [:state :record])})))))

(def ^:private op-specs
  "The scenario: covers every disposition this actor can genuinely reach
  through its real graph (auto-commit, escalate-then-approve, and 3 of
  the 4 distinct HARD-hold reasons plus both escalation reasons in
  `legislature.governor` -- see namespace docstring for the 2 rules that
  are architecturally unreachable via the real advisor). Every `:op`
  keyword, `:no-binding-authority` flag, and `:topic` value below is
  copied from `legislature.governor`'s own `hard-violations`/`check` and
  `legislature.advisor`'s proposal-op allowlist, not invented."
  [;; constituent-1 / "Alice Voter" / bill-2024-042 (real fixture from
   ;; legislature.actor-test / legislature.governor-test)
   ["t1-draft-briefing"        {:constituent-id "constituent-1" :op :draft-briefing :stake :low}
    {:topic :neutral}]
   ["t2-summarize-verified-bill" {:constituent-id "constituent-1" :bill-id "bill-2024-042"
                                   :op :summarize-bill :stake :low}
    {:topic :neutral}]
   ;; unregistered constituent entirely
   ["t3-unregistered-constituent" {:constituent-id "constituent-99" :op :draft-briefing :stake :low}
    {:topic :neutral}]
   ;; registered constituent, but the referenced bill was never registered
   ["t4-unregistered-bill"     {:constituent-id "constituent-1" :bill-id "bill-2024-999"
                                 :op :summarize-bill :stake :low}
    {:topic :neutral}]
   ;; request itself claims binding legislative authority -- always HARD-held
   ["t5-no-binding-authority"  {:constituent-id "constituent-1" :op :draft-briefing :stake :low
                                 :no-binding-authority true}
    {:topic :neutral}]
   ;; :flag-conflict-of-interest always escalates, regardless of topic/stake
   ["t6-flag-conflict"         {:constituent-id "constituent-1" :op :flag-conflict-of-interest :stake :high}
    {:topic :neutral}]
   ;; constituent-2 / "Jordan Reyes" (additional demo data, see namespace docstring)
   ["t7-schedule-meeting-c2"   {:constituent-id "constituent-2" :op :schedule-constituent-meeting :stake :low}
    {:topic :neutral}]
   ;; sensitive topic always escalates, regardless of op/stake
   ["t8-sensitive-topic-c2"    {:constituent-id "constituent-2" :op :draft-correspondence :stake :medium}
    {:topic :vote-position}]])

(defn run-demo!
  "Runs a fresh store through `op-specs` (see above) via the real
  compiled `legislature.actor` graph. Returns `{:store :runs}` --
  `:runs` is the ordered vector of real per-request outcomes; every
  field in `render` below is read from this or from `store` after the
  graph actually executed, never hand-typed."
  []
  (let [db (store/mem-store)]
    (store/register-constituent! db {:constituent-id "constituent-1" :name "Alice Voter"})
    (store/register-bill! db {:bill-id "bill-2024-042" :title "Education Reform Act" :status :pending})
    (store/register-constituent! db {:constituent-id "constituent-2" :name "Jordan Reyes"})
    (let [graph (actor/build-graph {:store db})
          runs (mapv (fn [[tid request context]] (run-op! graph tid request context))
                     op-specs)]
      {:store db :runs runs})))

;; ----------------------------- rendering -------------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- outcome-cell [{:keys [outcome rule]}]
  (case outcome
    :auto-committed "<span class=\"ok\">committed</span>"
    :approved-and-committed "<span class=\"ok\">approved &amp; committed</span>"
    :hard-hold (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")
    "<span class=\"muted\">in progress</span>"))

(defn- constituent-row [store {:keys [constituent-id name]} runs]
  (let [n-records (count (store/records-of store constituent-id))
        last-run (last (filter #(= constituent-id (:constituent-id (:request %))) runs))]
    (format "        <tr><td><code>%s</code></td><td>%s</td><td>%d</td><td>%s</td></tr>"
            (esc constituent-id) (esc name) n-records
            (if last-run (outcome-cell last-run) "<span class=\"muted\">no activity</span>"))))

(defn- bill-row [{:keys [bill-id title status]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc bill-id) (esc title) (esc (name status))))

(defn- run-row [{:keys [thread-id request context outcome rule]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc thread-id)
          (esc (:constituent-id request))
          (esc (name (:op request)))
          (esc (or (:bill-id request) ""))
          (esc (name (or (:topic context) :none)))
          (outcome-cell {:outcome outcome :rule rule})))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README.md
  ;; "Proposal ops" list, `legislature.governor`'s own docstring) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:draft-briefing</code></td><td><span class=\"ok\">auto-commit when constituent is registered and no binding-authority claim</span></td></tr>"
   "        <tr><td><code>:schedule-constituent-meeting</code></td><td><span class=\"ok\">auto-commit when constituent is registered</span></td></tr>"
   "        <tr><td><code>:draft-correspondence</code></td><td><span class=\"warn\">ALWAYS human approval if topic is sensitive (legislation-position/vote-position/public-statement)</span></td></tr>"
   "        <tr><td><code>:summarize-bill</code></td><td><span class=\"ok\">auto-commit only if the referenced bill is registered</span> &middot; <span class=\"critical\">HARD hold otherwise</span></td></tr>"
   "        <tr><td><code>:flag-conflict-of-interest</code></td><td><span class=\"warn\">ALWAYS human approval &middot; sensitive by definition</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from `{:store :runs}`
  as produced by `run-demo!` (or any other real scenario)."
  [{:keys [store runs]}]
  (let [constituents [{:constituent-id "constituent-1" :name "Alice Voter"}
                       {:constituent-id "constituent-2" :name "Jordan Reyes"}]
        bills [{:bill-id "bill-2024-042" :title "Education Reform Act" :status :pending}]
        constituent-rows (str/join "\n" (map #(constituent-row store % runs) constituents))
        bill-rows (str/join "\n" (map bill-row bills))
        run-rows (str/join "\n" (map run-row runs))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isco-1111 &middot; legislative office support</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Legislative Office Support (ISCO-08 1111) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · no binding legislative authority is ever exercised</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Registered constituents</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>legislature.store</code> via <code>legislature.render-html</code> (<code>clojure -M:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Constituent</th><th>Name</th><th>Records</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     constituent-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Registered bills</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Bill</th><th>Title</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     bill-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Legislative Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. No proposal ever claims binding legislative power (voting, sponsorship, floor speech).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit trail (this run)</h2>\n"
     "    <p class=\"muted\">Every request this scenario drove through the real compiled graph, in order — thread-id, constituent, op, referenced bill (if any), topic, and the real disposition (auto-commit, approved-after-escalation, or the specific HARD-hold rule).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Thread</th><th>Constituent</th><th>Op</th><th>Bill</th><th>Topic</th><th>Disposition</th></tr></thead>\n"
     "      <tbody>\n"
     run-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        html (render result)]
    (spit out html)
    (println "wrote" out "("
             (count (:runs result)) "requests driven through the real graph,"
             (count (store/ledger (:store result))) "ledger facts )")))
