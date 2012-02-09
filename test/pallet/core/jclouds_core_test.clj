(ns pallet.core.jclouds-core-test
  (:use
   [pallet.core :only [version]]))

(if (.startsWith (version) "0.6")
  (load "jclouds_core_test_0_6")
  (load "jclouds_core_test_0_7"))
