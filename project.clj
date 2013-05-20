(def clojure-ver "1.4.0")
(def pallet-ver "0.8.0-beta.10")
(def jclouds-ver "1.5.5")

(defproject com.palletops/pallet-jclouds "1.5.3-SNAPSHOT"
  :description "A pallet provider for using jclouds."
  :url "https://github.com/pallet/pallet-vmfest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :java-source-paths ["src"]
  :dependencies [[org.clojure/clojure ~clojure-ver :scope "provided"]
                 [com.palletops/pallet ~pallet-ver :scope "provided"]
                 [org.jclouds/jclouds-compute ~jclouds-ver]
                 [org.jclouds/jclouds-blobstore ~jclouds-ver]]

  ;; You will need the jclouds provider specific jars for your project
  ;; The simplest is to pull in everything, but you can use individual
  ;; provider dependencies.

  :classifiers {:all {:dependencies
                      [[org.jclouds/jclouds-all ~jclouds-ver]
                       [org.jclouds.driver/jclouds-sshj ~jclouds-ver]
                       [org.jclouds.driver/jclouds-slf4j ~jclouds-ver]]}})
