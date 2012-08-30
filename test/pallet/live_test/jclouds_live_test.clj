(ns pallet.live-test.jclouds-live-test
  (:use
   clojure.test
   [pallet.node :only [group-name]]
   [pallet.compute.jclouds-test-utils :only [purge-compute-service]])
  (:require
   [pallet.live-test :as live-test]
   [pallet.core :as core]
   [pallet.common.logging.logutils :as logutils]
   [pallet.compute :as compute]))

(use-fixtures :once (logutils/logging-threshold-fixture))

(deftest node-types-test
  (is (= {:repo {:group-name :repo :base-group-name :repo
                 :image {:os-family :ubuntu}
                 :count 1 :phases {}
                 :session-type nil}}
         (live-test/node-types
          {:repo {:image {:os-family :ubuntu}
                  :count 1
                  :phases {}}}))))

(deftest live-test-test
  (let [compute (compute/compute-service "stub" :identity "x" :credential "x")]
    (purge-compute-service compute)
    (live-test/set-service! compute)
    (testing "without prefix"
      (live-test/with-live-tests
        (doseq [os-family [:centos]]
          (live-test/test-nodes
           [compute node-map node-types]
           {:repo {:image {:os-family os-family}
                   :count 1
                   :phases {}}}
           (let [node-list (compute/nodes compute)]
             (is (= 1 (count ((group-by group-name node-list) "repo")))))))))
    (testing "with prefix"
      (live-test/with-live-tests
        (doseq [os-family [:centos]]
          (live-test/test-nodes
           [compute node-map node-types]
           {:repo {:image {:os-family os-family :prefix "1"}
                   :count 1
                   :phases {}}}
           (let [node-list (compute/nodes compute)]
             (is (= 1
                    (count ((group-by group-name node-list) "repo1")))))))))))
