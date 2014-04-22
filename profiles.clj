{:dev {:dependencies
       [[org.clojure/clojure "1.4.0"]
        [com.palletops/pallet "0.8.0-RC.8"]
        [com.palletops/pallet "0.8.0-RC.8" :classifier "tests"]
        [ch.qos.logback/logback-classic "1.0.9"]

        [org.apache.jclouds/jclouds-all "1.7.1"]
        [org.apache.jclouds.driver/jclouds-sshj "1.7.1"]
        [org.apache.jclouds.driver/jclouds-slf4j "1.7.1"]]

       :plugins [[codox/codox.leiningen "0.6.4"]
                 [lein-marginalia "0.7.1"]
                 [lein-pallet-release "RELEASE"]]
       :pallet-release
       {:url "https://pbors:${GH_TOKEN}@github.com/pallet/pallet-jclouds.git",
        :branch "master"}}
 :no-checkouts {:checkout-shares ^:replace []} ; disable checkouts
 }
