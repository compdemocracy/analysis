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


(comment

  (def iris (sk-data/load_iris))
  iris
  (py/att-type-map iris)
  (py.- iris DESCR)

  (def iris-df (pandas/DataFrame (py.- iris data) :columns (py.- iris feature_names)))

  (py/att-type-map iris-df)

  (def iris-name-series
    (let [iris-name-map (zipmap (range 3)
                                (py.- iris target_names))]
      (pandas/Series (map (fn [item]
                            (get iris-name-map item))
                          (py.- iris target)))))

  (py. iris-df __setitem__ "species" iris-name-series)
  (py/get-item iris-df "species")

  (defn to-dicts [df]
    (py/->jvm (py. df to_dict "records")))

  (oz/view!
    [:div
     [:h1 {:style {:color "green"}} "Yo dude"]
     [:h2 "Got vegemite?"]
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
                  :shape {:field "species"}}}]]
   :port 3860)


  (py/get-item iris-df "species")


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


  (oz/view! {:data {:values (mapv vec embedding)}
             :mark :point
             :width 800
             :height 400
             :encoding {:x {:field "0"
                            :scale {:zero false}}
                        :y {:field "1"
                            :scale {:zero false}}}}))



