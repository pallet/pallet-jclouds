(def clojure-ver "1.4.0")
(def pallet-ver "0.8.0-RC.8")
(def jclouds-ver "1.7.2")

(defproject com.palletops/pallet-jclouds "1.7.3"
  :description "A pallet provider for using jclouds."
  :url "https://github.com/pallet/pallet-jclouds"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :java-source-paths ["src"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :dependencies [[org.clojure/clojure ~clojure-ver :scope "provided"]
                 [com.palletops/pallet ~pallet-ver :scope "provided"]
                 [com.palletops/clj-jclouds "0.1.1"]
                 [org.apache.jclouds/jclouds-compute ~jclouds-ver]
                 [org.apache.jclouds/jclouds-blobstore ~jclouds-ver]]

  ;; You will need the jclouds provider specific jars for your project
  ;; The simplest is to pull in everything, but you can use individual
  ;; provider dependencies.
  :classifiers {:all {:dependencies
                      [[org.apache.jclouds/jclouds-all ~jclouds-ver]
                       [org.apache.jclouds.driver/jclouds-sshj ~jclouds-ver]
                       [org.apache.jclouds.driver/jclouds-slf4j ~jclouds-ver]]}})
