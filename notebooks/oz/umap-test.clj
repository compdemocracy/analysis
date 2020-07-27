(ns umap-test
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

;; Note! This document is currently written in terms of the next iteration of the CLJ -> Hiccup mechanism

;; # UMAP Test!

;; This is a basic test of **UMAP** for dimensionality reduction!
 
;; First we're going to load up the data from sklearn.

(def iris (sk-data/load_iris))
iris
(py/att-type-map iris)
(py.- iris DESCR)


;; Next we're going to load up the iris-df

(def iris-df (pandas/DataFrame (py.- iris data) :columns (py.- iris feature_names)))

;; Is this how you display something as data?
[:pprint
 (py/att-type-map iris-df)]

(def iris-name-series
  (let [iris-name-map (zipmap (range 3)
                              (py.- iris target_names))]
    (pandas/Series (map (fn [item]
                          (get iris-name-map item))
                        (py.- iris target)))))

(py. iris-df __setitem__ "species" iris-name-series)

[:print
 (py/get-item iris-df "species")]

(defn to-dicts [df]
  "Get dict objects out of df, and convert to jvm seq of maps for json export"
  (py/->jvm (py. df to_dict "records")))

;; Playing around with getting a specific item
(py/get-item iris-df "species")

;; Here's what our data looks like

[:vega-lite
 {:data {:values (to-dicts iris-df)}
  :mark {:type :point :tooltip {:content :data}}
  :width 500
  ;:encoding {:x {:field "sepal-length"}
             ;:y {:field "sepal-width"}}}
  :encoding {:x {:field "sepal length (cm)"
                 :scale {:zero false}}
             :y {:field "sepal width (cm)"
                 :scale {:zero false}}
             :color {:field "species"}
             :shape {:field "species"}}}]


;; Now time to run UMAP!

(def matrix
  (py/as-numpy
    (py/as-tensor
      (py.
        (py/call-attr-kw iris-df
                         :drop
                         []
                         {:columns "species"})
        to_numpy))))

(def reducer (umap/UMAP))

(def embedding (py. reducer fit_transform matrix))

;(py.- embedding shape)

;(str (first embedding))

;; Here's what that looks like!

[:vega-lite
 {:data {:values (mapv vec embedding)}
  :mark :point
  :width 800
  :height 400
  :encoding {:x {:field "0"
                 :scale {:zero false}}
             :y {:field "1"
                 :scale {:zero false}}}}]

;; Currently, only the final form will be shown, so recreating in terms of current Oz support for displaying
;; the final form.
;; Eventually Oz will support rendering top-level `;;` comments as markdown, and will also render top-level
;; vector literals as hiccup, as implied here and in the comments and code blocks above.

;; Will want options to turn on/off showing the source code, marginalia style docs, etc.

[:div
  [:md "Here's what our data looks like"]
  [:vega-lite
   {:data {:values (to-dicts iris-df)}
    :mark {:type :point :tooltip {:content :data}}
    :width 500
    ;:encoding {:x {:field "sepal-length"}
               ;:y {:field "sepal-width"}}}
    :encoding {:x {:field "sepal length (cm)"
                   :scale {:zero false}}
               :y {:field "sepal width (cm)"
                   :scale {:zero false}}
               :color {:field "species"}
               :shape {:field "species"}}}]
  [:md "Here's the UMAP projection!"]
  [:vega-lite
   {:data {:values (mapv vec embedding)}
    :mark :point
    :width 800
    :height 400
    :encoding {:x {:field "0"
                   :scale {:zero false}}
               :y {:field "1"
                   :scale {:zero false}}}}]]

