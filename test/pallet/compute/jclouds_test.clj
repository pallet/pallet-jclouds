(ns pallet.compute.jclouds-test
  (:use
   clojure.test
   [pallet.core :only [server-spec]]
   [pallet.compute :only [nodes instantiate-provider]]
   [pallet.crate.automated-admin-user :only [automated-admin-user]]
   [pallet.live-test :only [images test-for test-nodes]])
  (:require
   [pallet.common.logging.logutils :as logutils]
   [pallet.compute.jclouds :as jclouds]
   [pallet.compute :as compute]
   [pallet.node :as node])
  (:import [org.jclouds.compute.domain NodeMetadata OsFamily OperatingSystem]))

(try
  (use '[pallet.api :only [plan-fn]])
  (catch Exception _
    (use '[pallet.phase :only [phase-fn] :rename {phase-fn plan-fn}])))

(use-fixtures :once (logutils/logging-threshold-fixture))

(deftest supported-providers-test
  (is (jclouds/supported-providers)))

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
  (is (not (node/running?
            (jclouds/make-node
             "a"
             :state
             org.jclouds.compute.domain.NodeMetadata$Status/TERMINATED))))
  (is (node/running?
       (jclouds/make-node
        "a"
        :state org.jclouds.compute.domain.NodeMetadata$Status/RUNNING))))

(deftest os-version-test
  (is (= "Some version"
         (node/os-version
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
         (node/os-family
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
      (is (node/running? n))
      (is (jclouds/compute-node? n))
      (is (= "localhost" (node/primary-ip n)))))
  (testing "with ssh-port specification"
    (is (= 2222
           (node/ssh-port
            (jclouds/make-unmanaged-node
             "atag" "localhost" :user-metadata {:ssh-port 2222})))))
  (testing "with image specification"
    (is (= :ubuntu
           (node/os-family
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
          {:bootstrap (plan-fn (automated-admin-user))}
          :image image :count 1)}
      (let [service (instantiate-provider :vmfest)
            node (first (:vmfest-test-host node-map))]
        (clojure.tools.logging/infof "node-map %s" node-map)
        (is node)
        (is (seq (nodes service)))))))
