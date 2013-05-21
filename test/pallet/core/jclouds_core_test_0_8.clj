(ns pallet.core.jclouds-core-test
  (:require
   [pallet.action :as action]
   [pallet.build-actions :as build-actions]
   [pallet.common.logging.logutils :as logutils]
   [pallet.compute :as compute]
   [pallet.compute.jclouds :as jclouds]
   [pallet.compute.jclouds-ssh-test :as ssh-test]
   [pallet.compute.jclouds-test-utils :as jclouds-test-utils]
   [pallet.core :as core]
   [pallet.core.api :as core-api]
   [pallet.core.operations :as operations]
   [pallet.execute :as execute]
   [pallet.executors :as executors]
   [pallet.mock :as mock]
   [pallet.node :as node]
   [pallet.stevedore :as stevedore]
   [pallet.test-utils :as test-utils]
   [pallet.utils :as utils]
   [clojure.tools.logging :as logging]
   [clojure.string :as string])
  (:use
   clojure.test
   [pallet.actions :only [exec-script assoc-settings]]
   [pallet.api :only [cluster-spec group-spec plan-fn server-spec
                      lift converge]]
   [pallet.crate :only [get-settings]]
   [pallet.core.user :only [*admin-user*]]
   [pallet.algo.fsmop :only [failed? operate]]
   [pallet.test-utils :only [script-action clj-action test-session]]
   [pallet.action-plan :only [stop-execution-on-error]]
   [pallet.feature :only [when-feature]])
  (:import [org.jclouds.compute.domain NodeState OperatingSystem OsFamily]))

;; Allow running against other compute services if required
(def ^{:dynamic true} *compute-service* ["stub" "x" "x" ])

(use-fixtures
  :each
  (jclouds-test-utils/compute-service-fixture
   *compute-service*
   :extensions
   [(ssh-test/ssh-test-client ssh-test/no-op-ssh-client)]))

(use-fixtures :once (logutils/logging-threshold-fixture))

(defn seen-fn
  "Generate a local function, which uses an atom to record when it is called."
  [name]
  (let [seen (atom nil)
        seen? (fn [] @seen)]
    [(clj-action [session]
       (clojure.tools.logging/info (format "Seenfn %s" name))
       (is (not @seen))
       (reset! seen true)
       [session session])
     seen?]))

(defn running-nodes [nodes]
  (filter (complement node/terminated?) nodes))

(def test-component
  (script-action [session arg] [(str arg) session]))

(deftest node-count-adjuster-test
  (testing "destroy-server and destroy-group phases"
    (jclouds-test-utils/purge-compute-service)
    (let [service (jclouds-test-utils/compute)
          a-node (org.jclouds.compute2/create-node (.compute service) "aaa")
          nodes (compute/nodes service)
          node (first nodes)
          [action seen?] (seen-fn "destroy-server")
          [action-g seen-g?] (seen-fn "destroy-group")
          spec (core/group-spec :aaa :count 0
                                :phases {:destroy-server (plan-fn (action))
                                         :destroy-group (plan-fn (action-g))})
          servers [(assoc spec :node node)]]
      (is (= 1 (count nodes)))
      (let [op (operate
                (operations/node-count-adjuster
                 service
                 [spec]
                 servers
                 {}
                 {}
                 servers
                 (core-api/environment-execution-settings)))
            {:keys [old-nodes targets]} @op]
        (is (not (failed? op)))
        (is (seen?))
        (is (seen-g?))
        (is (zero? (count (running-nodes (compute/nodes service)))))
        (is (= 1 (count old-nodes)))
        (is (zero? (count targets))))))

  (testing "destroy-server phase"
    (jclouds-test-utils/purge-compute-service)
    (let [service (jclouds-test-utils/compute)
          a-node (org.jclouds.compute2/create-node (.compute service) "aaa")
          a2-node (org.jclouds.compute2/create-node (.compute service) "aaa")
          nodes (compute/nodes service)
          node (first nodes)
          [action seen?] (seen-fn "destroy-server")
          [action-g seen-g?] (seen-fn "destroy-group")
          spec (core/group-spec :aaa :count 1
                                :phases {:destroy-server (plan-fn (action))
                                         :destroy-group (plan-fn (action-g))})
          servers (map #(assoc spec :node %) nodes)]
      (is (= 2 (count nodes)))
      (let [op (operate
                (operations/node-count-adjuster
                 service
                 [spec]
                 servers
                 {}
                 {}
                 servers
                 (core-api/environment-execution-settings)))
            {:keys [old-nodes targets]} @op]
        (is (not (failed? op)))
        (is (seen?))
        (is (not (seen-g?)))
        (is (= 1 (count (running-nodes (compute/nodes service)))))
        (is (= 1 (count old-nodes)))
        (is (= 1 (count targets))))))

  (testing "create-group phase"
    (jclouds-test-utils/purge-compute-service)
    (let [service (jclouds-test-utils/compute)
          nodes (compute/nodes service)
          [action-g seen-g?] (seen-fn "create-group")
          spec {:phases {:create-group (plan-fn (action-g))}}]
      (is (zero? (count nodes)))
      (let [op (operate
                (operations/node-count-adjuster
                 service
                 [(core/group-spec :aaa :count 1 :extends [spec])]
                 []
                 {}
                 {}
                 nil
                 (core-api/environment-execution-settings)))
            {:keys [new-nodes targets]} @op]
        (is (not (failed? op)))
        (is (seen-g?))
        (is (= 1 (count (running-nodes (compute/nodes service)))))
        (is (= 1 (count new-nodes)))
        (is (= 1 (count targets)))))))

(deftest operation-lift-test
  (testing "operation/lift"
    (let [[localf seen?] (seen-fn "lift-test")
          spec (server-spec
                :phases {:p1 (plan-fn (exec-script ("ls" "/")))
                         :p2 (plan-fn (localf))})
          local (group-spec
                 "local"
                 :image {:os-family :ubuntu}
                 :extends [spec])
          targets [(assoc local :node (jclouds/make-localhost-node))]]
      (let [op (operate
                (operations/lift
                 targets
                 {:user (assoc *admin-user*
                          :username (test-utils/test-username)
                          :no-sudo true)}
                 {}
                 [:p1 :p2]
                 {}))
            {:keys [results targets plan-state]} @op]
        (is (not (failed? op)))
        (is (= 1 (count targets)))
        (is (= 2 (-> plan-state :node-values count)))
        (is (some
             (partial re-find #"bin")
             (->> (mapcat :result results) (map :out))))
        (is (seen?))))))

(deftest lift-test
  (testing "lift"
    (let [local (group-spec "local" :image {:os-family :ubuntu})
          [localf seen?] (seen-fn "lift-test")
          result (lift {local (jclouds/make-localhost-node)}
                       :phase [(plan-fn (exec-script ("ls" "/")))
                               (plan-fn (localf))]
                       :user (assoc *admin-user*
                               :username (test-utils/test-username)
                               :no-sudo true)
                       :compute nil
                       :async true)
          {:keys [error results targets]} @result]
      (is (not (failed? result)))
      (is (not error))
      (is (some
           (partial re-find #"bin")
           (->> (mapcat :result results) (map :out))))
      (is (seen?)))))

(deftest lift2-test
  (let [[localf seen?] (seen-fn "lift2-test")
        [localfy seeny?] (seen-fn "lift2-test y")
        x1 (group-spec :x1 :phases {:configure (plan-fn (localf))})
        y1 (group-spec :y1 :phases {:configure (plan-fn (localfy))})
        result (lift {x1 (jclouds/make-unmanaged-node "x" "localhost")
                      y1 (jclouds/make-unmanaged-node "y" "localhost")}
                     :user (assoc *admin-user*
                             :username (test-utils/test-username)
                             :no-sudo true)
                     :compute nil
                     :async true)
        {:keys [error results targets]} @result]
    (is (not (failed? result)))
    (is (not error))
    (is (seen?))
    (is (seeny?))))


(deftest converge-test
  (let [compute (jclouds-test-utils/compute-service)
        _   (jclouds-test-utils/purge-compute-service compute)
        hi (script-action [session] ["Hi" session])
        id "c-t"
        node (group-spec "c-t" :phases {:configure (plan-fn (hi))})
        op (converge {node 2}
                     :compute compute
                     :environment {:algorithms
                                   {:executor executors/echo-executor}}
                     :async true)
        {:keys [results error targets exception]} @op]
    (is (not (failed? op)))
    (is (nil? exception))
    (when exception
      (clojure.stacktrace/print-cause-trace exception))
    (is (not error))
    (is (some
         (partial re-find #"Hi")
         (mapcat :result results)))
    (is (= 2 (count targets)))
    (is (= 2 (count (running-nodes (compute/nodes compute)))))
    (is (every? node/image-user (map :node targets)))
    (testing "remove some instances"
      (let [op (converge {node 1}
                         :compute compute
                         :environment {:algorithms
                                       {:executor executors/echo-executor}}
                         :async true)
            {:keys [results error targets]} @op]
        (is (= 1 (count (running-nodes (map :node targets)))))
        (is (= 1 (count (running-nodes (compute/nodes compute)))))
        (is (some
             (partial re-find #"Hi")
             (mapcat :result results)))))
    ;; (testing "no instance count change with new-node-selector"
    ;;   (let [session (converge {node 1}
    ;;                           :compute (jclouds-test-utils/compute)
    ;;                           :node-set-selector #'core/new-node-set-selector
    ;;                           :environment {:executor executors/echo-executor})]
    ;;     (is (= 1 (count (running-nodes (:all-nodes session)))))
    ;;     (is (= 1 (count (running-nodes
    ;;                      (compute/nodes
    ;;                       (jclouds-test-utils/compute))))))
    ;;     (is (not (some
    ;;               #(= "Hi" %)
    ;;               (:configure (-> session :results first second)))))))
    ;; (testing ":settings and :configure phases are enforced"
    ;;   (let [session (converge {node 1}
    ;;                           :phase [:non-configure]
    ;;                           :compute (jclouds-test-utils/compute)
    ;;                           :node-set-selector #'core/new-node-set-selector
    ;;                           :environment {:executor executors/echo-executor})]
    ;;     (is (= [:settings :configure :non-configure] (:phase-list session)))))
    (testing "remove all instances"
      (let [session (converge
                     {node 0}
                     :compute compute
                     :environment {:algorithms
                                   {:executor executors/echo-executor}})]
        (is (= 0 (count (running-nodes (:all-nodes session)))))))))

(deftest lift-with-runtime-params-test
  ;; test that parameters set at execution time are propogated
  ;; between phases
  (let [get-runtime-param (script-action
                              [session]
                            [[{:language :bash}
                              (format "echo %s" (:x (get-settings :test)))]
                             session])
        node (group-spec
              "localhost"
              :phases {:configure (plan-fn (assoc-settings :test {:x "x"}))
                       :configure2 (plan-fn (get-runtime-param))})
        op (lift {node (jclouds/make-localhost-node)}
                 :phase [:configure :configure2]
                 :user (assoc *admin-user*
                         :username (test-utils/test-username)
                         :no-sudo true)
                 :compute (jclouds-test-utils/compute-service)
                 :async true)
        {:keys [results error targets]} @op]
    (is (not (failed? op)))
    (let [{:keys [out err exit]} (->
                                  (filter
                                   #(= :configure2 (:phase %))
                                   results)
                                  first
                                  :result
                                  first)]
      (is (= "x\n" out))
      (is (string/blank? err))
      (is (zero? exit)))))

(deftest cluster-test
  (let [compute (jclouds-test-utils/compute-service)]
    (jclouds-test-utils/purge-compute-service compute)
    (let [cluster (cluster-spec
                   "c"
                   :groups [(group-spec
                             "g1" :count 1 :image {:os-family :ubuntu})
                            (group-spec
                             "g2" :count 2 :image {:os-family :ubuntu})])]
      (testing "converge-cluster"
        (let [op (converge cluster :compute compute :async true)
              {:keys [results error targets exception new-nodes old-nodes]} @op]
          (is (not (failed? op)))
          (when exception
            (clojure.stacktrace/print-cause-trace exception))
          (is (= 3 (count targets)))
          (is (= 3 (count new-nodes)))
          (is (empty? old-nodes)))
        (is (= 3 (count
                  (running-nodes (compute/nodes compute))))))
      (testing "lift-cluster"
        (let [op (lift cluster :compute compute :async true)
              {:keys [results error targets exception new-nodes old-nodes]} @op]
          (is (not (failed? op)))
          (when exception
            (clojure.stacktrace/print-cause-trace exception))

          (is (empty? new-nodes))
          (is (= 3 (count targets)))
          (is (empty? old-nodes)))
        (is (= 3 (count
                  (running-nodes (compute/nodes compute))))))
      (testing "destroy-cluster"
        (let [op (converge {cluster 0} :compute compute :async true)
              {:keys [results error targets exception new-nodes old-nodes]} @op]
          (is (not (failed? op)))
          (when exception
            (clojure.stacktrace/print-cause-trace exception))
          (is (empty? targets))
          (is (empty? new-nodes))
          (is (= 3 (count old-nodes))))
        (is (= 0
               (count
                (running-nodes (compute/nodes compute)))))))))

(when-feature taggable-nodes
  (deftest tag-test
    (let [compute (jclouds-test-utils/compute-service)
          _   (jclouds-test-utils/purge-compute-service compute)
          group (group-spec "tagtest")
          op (converge {group 1} :compute compute :async true)
          {:keys [results error targets exception]} @op
          node (first (compute/nodes compute))
          tag-name "tag-test"
          tag-value {:a 1}]
      (is (not (failed? op)))
      (is node)
      (when (node/taggable? node)
        (is (nil? (node/tag node tag-name)))
        (is (not (some #(= tag-name %) (node/tags node))))
        (node/tag! node tag-name tag-value)
        (is (= tag-value (node/tag node tag-name)))
        (is (some #(= tag-name %) (node/tags node)))))))
