(ns umap-test
  (:require [tech.v3.dataset :as ds]
            [tech.v3.datatype :as dt]
            [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :as py :refer [py. py.. py.-]]
            [oz.core :as oz]))


(require-python '[sklearn.datasets :as sk-data]
                '[sklearn.model_selection :as sk-model]
                '[numpy :as numpy]
                '[numba :as numba]
                '[pandas :as pandas]
                '[umap :as umap])

;; Note! This document is currently written in terms of the next iteration of the CLJ -> Hiccup mechanism

[:h1 "UMAP Test!"]

[:md
 "This is a basic test of **UMAP** for dimensionality reduction!

 First we're going to load up the iris dataset from sklearn.
 "]

(def iris (sk-data/load_iris))
iris
(py/att-type-map iris)
;(py.- iris DESCR)

[:pre (py.- iris DESCR)]


[:md
 "Wow! That's quite a lot :-D.
  Next we're going to translate this into a pandas dataframe:"]

(def iris-df (pandas/DataFrame (py.- iris data) :columns (py.- iris feature_names)))


(def iris-name-series
  (let [iris-name-map (zipmap (range 3)
                              (py.- iris target_names))]
    (pandas/Series (map (fn [item]
                          (get iris-name-map item))
                        (py.- iris target)))))

(py. iris-df __setitem__ "species" iris-name-series)

;[:print
 ;(py/get-item iris-df "species")]

(defn to-dicts [df]
  "Get dict objects out of df, and convert to jvm seq of maps for json export"
  (py/->jvm (py. df to_dict "records")))

;; Playing around with getting a specific item
(py/get-item iris-df "species")

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

[:md "Now time to run UMAP!"]

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

(py.- embedding shape)

(str (first embedding))

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
