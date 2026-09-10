(ns legislature.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [legislature.store :as store]
            [legislature.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-constituent! st {:constituent-id "constituent-1" :name "Alice Voter"})
    (store/register-bill! st {:bill-id "bill-2024-042" :title "Education Reform Act" :status :pending})
    st))

(deftest ok-on-clean-briefing-request
  (let [st (fresh-store)
        proposal {:op :draft-briefing :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:constituent-id "constituent-1"} {:topic :neutral} proposal st)]
    (is (:ok? v))
    (is (not (:hard? v)))
    (is (not (:escalate? v)))))

(deftest hard-on-unregistered-constituent
  (let [st (fresh-store)
        proposal {:op :draft-briefing :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:constituent-id "no-such-constituent"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-constituent (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-bill
  (let [st (fresh-store)
        proposal {:op :summarize-bill :effect :propose :confidence 0.9 :stake :low}
        v (governor/check {:bill-id "bill-no-such"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-bill (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        proposal {:op :draft-briefing :effect :direct-write :confidence 0.9 :stake :low}
        v (governor/check {:constituent-id "constituent-1"} {} proposal st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-on-flag-conflict-of-interest
  (let [st (fresh-store)
        proposal {:op :flag-conflict-of-interest :effect :propose :confidence 0.9 :stake :high}
        v (governor/check {:constituent-id "constituent-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-topic-sensitivity
  (let [st (fresh-store)
        proposal {:op :draft-correspondence :effect :propose :confidence 0.9 :stake :medium}
        v (governor/check {:constituent-id "constituent-1"} {:topic :legislation-position} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest escalates-on-low-confidence
  (let [st (fresh-store)
        proposal {:op :draft-briefing :effect :propose :confidence 0.2 :stake :low}
        v (governor/check {:constituent-id "constituent-1"} {} proposal st)]
    (is (:escalate? v))
    (is (not (:hard? v)))))

(deftest store-records-and-ledger-append-only
  (let [st (fresh-store)]
    (store/commit-record! st {:constituent-id "constituent-1" :op :draft-briefing})
    (store/append-ledger! st {:disposition :commit})
    (is (= 1 (count (store/records-of st "constituent-1"))))
    (is (= 1 (count (store/ledger st))))))
