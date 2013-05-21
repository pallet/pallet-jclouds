(ns pallet.compute.jclouds-test-utils
  "Test utils for jclouds"
  (:require
   pallet.compute))

(def ^{:dynamic true} *compute*)

(defn compute
  "Return the current compute service."
  [] *compute*)

(defn compute-service
  "Use jcloud's stub compute service, or some other if specified"
  ([] (compute-service "stub" "x" "x"))
  ([service account key & options]
     (apply pallet.compute/instantiate-provider
            service :identity account :credential key
            options)))

(defn compute-service-fixture
  "Use jcloud's stub compute service, or some other if specified"
  ([] (compute-service-fixture ["stub" "x" "x"]))
  ([[service account key] & options]
     (fn [f]
       (binding [*compute* (apply compute-service service account key options)]
         (f)))))

(defn purge-compute-service
  "Remove all nodes from the current compute service"
  ([compute]
     (doseq [node (pallet.compute/nodes compute)]
       (pallet.compute/destroy-node compute node)))
  ([]
     (purge-compute-service *compute*)))

(defn clean-compute-service-fixture
  "Remove all nodes from the compute service"
  [service account key & options]
  (fn [f]
    (purge-compute-service)
    (f)))
