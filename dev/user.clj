
(ns user
  (:require
   [tech.v3.dataset :as ds]
   [tech.v3.datatype :as dt]
   [oz.core :as oz]
            ;[polis.math :as math]
            ;[polis.db :as db
   [libpython-clj2.require :refer [require-python]]
   [libpython-clj2.python :as py :refer [py. py.. py.-]]
   [libpython-clj2.python.np-array :as np-array]))

;(require-python '[numpy :as np])

;(def test-ary (np/array [[1 2] [3 4]]))

;test-ary


(def autosize-fix-styles
  "
div .vega-embed .chart-wrapper {
  width: auto;
  height: auto;
}")

(defn template-fn
  [hiccup-content]
  [:div {:style {:max-width "1000px" :margin "auto"}}
   [:style autosize-fix-styles]
   hiccup-content])

;(oz/start-server! 3860)

(oz/build! [;{:from "notebooks/oz"
             ;:to "notebooks/build"
             ;:header-extras [[:style autosize-fix-styles]]
             ;:template-fn template-fn}
            {:from "local/notebooks"
             :to "local/build"
             :header-extras [[:style autosize-fix-styles]]
             :template-fn template-fn}]
           :template-fn template-fn
           :port 3860)

(comment
  ;; You can also
  (oz/view! {:data {:values [{:a 1 :b 4} {:a 2 :b 2}]}
             :mark :point
             :encoding {:x {:field :a :type :quantitative}
                        :y {:field :b :type :quantitative}}}
            :port 3860)
  :end)
