(ns pallet.core.jclouds-core-test
  (:use
   [pallet.core :only [version]]))

(if (.startsWith (version) "0.7")
  (load "jclouds_core_test_0_7")
  (load "jclouds_core_test_0_8"))
