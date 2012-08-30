(ns pallet.core.jclouds-core-test)

(try
  (use '[pallet.core.api :only [version]])
  (catch Throwable _ ; java.lang.IllegalAccessError
    (try
      (use '[pallet.core :only [version]])
      (catch Throwable _
        (defn version [] "0.8.0-alpha.2-or-before")))))

(if (.startsWith (version) "0.7")
  (load "jclouds_core_test_0_7")
  (load "jclouds_core_test_0_8"))
