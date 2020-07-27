(ns user
  (:require [tech.ml.dataset :as ds]
            [tech.v2.datatype :as dt]
            [libpython-clj.require :refer [require-python]]
            [libpython-clj.python :as py :refer [py. py.. py.-]]
            [oz.core :as oz]))


(require-python '[sklearn.datasets :as sk-data]
                '[sklearn.model_selection :as sk-model]
                '[numpy :as numpy]
                '[numba :as numba]
                '[pandas :as pandas]
                '[umap :as umap])


(comment
  (oz/view! {:data {:values [{:a 1 :b 4} {:a 2 :b 2}]}
             :mark :point
             :encoding {:x {:field :a}
                        :y {:field :b}}}
            :port 3860)
  (oz/build! {:from "notebooks/oz"
              :to "notebooks/build"}
             :port 3860)
  :end)


