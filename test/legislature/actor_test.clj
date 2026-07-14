(ns legislature.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [legislature.actor :as actor]
            [legislature.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-constituent! st {:constituent-id "constituent-1" :name "Alice Voter"})
    (store/register-bill! st {:bill-id "bill-2024-042" :title "Education Reform Act" :status :pending})
    st))

(deftest commits-a-clean-low-risk-request
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:constituent-id "constituent-1" :op :draft-briefing :stake :low}
        result (actor/run-request! graph request {:topic :neutral} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "constituent-1"))))))

(deftest holds-on-unregistered-constituent-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:constituent-id "no-such-constituent" :op :draft-briefing :stake :low}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "no-such-constituent")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; flagging conflict always escalates (governor invariant)
        request {:constituent-id "constituent-1" :op :flag-conflict-of-interest :stake :high}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "constituent-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "constituent-1")))))))

(deftest interrupts-on-sensitive-topic
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:constituent-id "constituent-1" :op :draft-correspondence :stake :medium}
        interrupted (actor/run-request! graph request {:topic :vote-position} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "constituent-1")))))
