(ns user
  (:require [tech.ml.dataset :as ds]
            [tech.v2.datatype :as dt]
            [polis.math :as math]
            [libpython-clj.require :refer [require-python]]
            [libpython-clj.python :as py :refer [py. py.. py.-]]
            [oz.core :as oz]))

(oz/start-server! 3860)


(comment
  ;; You can also 
  (oz/view! {:data {:values [{:a 1 :b 4} {:a 2 :b 2}]}
             :mark :point
             :encoding {:x {:field :a}
                        :y {:field :b}}}
            :port 3860)

  (oz/build! [{:from "notebooks/oz"
               :to "notebooks/build"}
              {:from "local/notebooks"
               :to "local/build"}]
             :port 3860)
  :end)


