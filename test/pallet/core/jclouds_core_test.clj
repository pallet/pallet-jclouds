(ns pallet.core.jclouds-core-test)

(try
  (require '[pallet.api :refer [version]])
  (catch Throwable e ; java.lang.IllegalAccessError
    (require '[pallet.core :refer [version]])))

(if (and (string? (version)) (.startsWith (version) "0.7"))
  (load "jclouds_core_test_0_7")
  (load "jclouds_core_test_0_8"))
