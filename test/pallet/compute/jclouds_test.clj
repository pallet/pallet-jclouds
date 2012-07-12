(ns pallet.compute.jclouds-test
  (:use
   clojure.test
   [pallet.core :only [server-spec]]
   [pallet.compute :only [nodes compute-service]]
   [pallet.crate.automated-admin-user :only [automated-admin-user]]
   [pallet.live-test :only [images test-for test-nodes]]
   [pallet.phase :only [phase-fn]])
  (:require
   [pallet.common.logging.logutils :as logutils]
   [pallet.compute.jclouds :as jclouds]
   [pallet.compute :as compute]
   [pallet.node :as node])
  (:import [org.jclouds.compute.domain NodeState OsFamily OperatingSystem]))

(use-fixtures :once (logutils/logging-threshold-fixture))

(deftest supported-providers-test
  (is (jclouds/supported-providers)))

(deftest node-counts-by-tag-test
  (is (= {:a 2}
         (compute/node-counts-by-tag
          [(jclouds/make-node "a") (jclouds/make-node "a")]))))

(deftest compute-node?-test
  (is (not (jclouds/compute-node? 1)))
  (is (jclouds/compute-node? (jclouds/make-node "a")))
  (is (every?
       jclouds/compute-node?
       [(jclouds/make-node "a") (jclouds/make-node "b")])))

(deftest print-method-test
  (is (= "             a\t  null\n\t\t ubuntu Some arch Ubuntu Desc\n\t\t RUNNING\n\t\t public:   private: "
         (with-out-str (print (jclouds/make-node "a"))))))


(deftest running?-test
  (is (not (compute/running?
            (jclouds/make-node "a" :state NodeState/TERMINATED))))
  (is (compute/running?
       (jclouds/make-node "a" :state NodeState/RUNNING))))

(deftest os-version-test
  (is (= "Some version"
         (compute/os-version
          (jclouds/make-node
           "t"
           :operating-system (OperatingSystem.
                              OsFamily/UBUNTU
                              "Ubuntu"
                              "Some version"
                              "Some arch"
                              "Desc"
                              true))))))

(deftest os-family-test
  (is (= :ubuntu
         (compute/os-family
          (jclouds/make-node
           "t"
           :operating-system (OperatingSystem.
                              OsFamily/UBUNTU
                              "Ubuntu"
                              "Some version"
                              "Some arch"
                              "Desc"
                              true))))))

(deftest make-unmanaged-node-test
  (testing "basic tests"
    (let [n (jclouds/make-unmanaged-node "atag" "localhost")]
      (is n)
      (is (compute/running? n))
      (is (jclouds/compute-node? n))
      (is (= "localhost" (compute/primary-ip n)))))
  (testing "with ssh-port specification"
    (is (= 2222
           (compute/ssh-port
            (jclouds/make-unmanaged-node
             "atag" "localhost" :user-metadata {:ssh-port 2222})))))
  (testing "with image specification"
    (is (= :ubuntu
           (compute/os-family
            (jclouds/make-unmanaged-node
             "atag" "localhost"
             :image "id"
             :operating-system (OperatingSystem. OsFamily/UBUNTU "Ubuntu"
                                                 "Some version" "Some arch"
                                                 "Desc" true)))))))

(deftest live-test
  (test-for [image (images)]
    (test-nodes
        [compute node-map node-types [:configure-dev :install :configure]]
        {:vmfest-test-host
         (server-spec
          :phases
          {:bootstrap (phase-fn (automated-admin-user))}
          :image image :count 1)}
      (let [service (compute-service :vmfest)
            node (first (:vmfest-test-host node-map))]
        (clojure.tools.logging/infof "node-map %s" node-map)
        (is node)
        (is (seq (nodes service)))))))
