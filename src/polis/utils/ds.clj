(ns polis.utils.ds
  "Core Polis data analysis API, implemented with tech.v3.dataset and libpython-clj2 for python interop."
  (:require [tech.v3.dataset :as ds]
            ;[tech.v3.dataset.pca :as dpca]
            [libpython-clj2.python.np-array] ;; this is for side effects plugging numpy into tech.v3
            [tech.v3.dataset.math :as dmath]
            [tech.v3.libs.smile.matrix :as smile-mat]
            [tech.v3.dataset.neanderthal :as dnean]
            [tech.v3.dataset.join :as djoin]
            [tech.v3.dataset.column :as dcol]
            [tech.v3.dataset.tensor :as dtensor]
            [tech.v3.datatype :as dt]
            [tech.v3.tensor :as tens]
            [tech.v3.datatype.functional :as dfn]
            [clojure.java.io :as io]
            [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :as py :refer [py. py.. py.-]]
            [semantic-csv.core :as csv]
            [clojure.spec.alpha :as s]
            ;[fastmath.stats :as fast-stats]
            [taoensso.timbre :as log]
            [clojure.set :as set]
            ;[kixi.stats.core :as kixi]
            [polis.participants :as participants]
            [polis.math.matrix :as matrix])
            ;[polis.db :as db])
  (:import [smile.stat.hypothesis CorTest]
           [org.apache.commons.math3.stat.inference TTest MannWhitneyUTest GTest]))
