{:dev {:dependencies
       [[ch.qos.logback/logback-classic "1.0.9"]
        [org.apache.jclouds/jclouds-all "1.7.1"]
        [org.apache.jclouds.driver/jclouds-sshj "1.7.1"]
        [org.apache.jclouds.driver/jclouds-slf4j "1.7.1"]]
       :plugins [[lein-pallet-release "RELEASE"]]}
 :provided {:dependencies
            [[org.clojure/clojure "1.5.1"]
             [com.palletops/pallet "0.9.0-SNAPSHOT"]
             [com.palletops/pallet "0.9.0-SNAPSHOT" :classifier "tests"]]}}
