(ns pallet.blobstore.jclouds-test
  (:use clojure.test)
  (:require
   [pallet.common.logging.logutils :as logutils]
   [pallet.blobstore.jclouds :as jclouds]
   [pallet.blobstore :as blobstore]))

(use-fixtures :once (logutils/logging-threshold-fixture))
