(ns vtaiwan-uber-analysis
  (:require [polis.math :as math]
            [tech.ml.dataset :as ds]
            [tech.ml.dataset.column :as dcol]
            [semantic-csv.core :as csv]))


(def data
  (math/load-data "data/vtaiwan-uber-conv.ts-2015-08-28.exported-2020-07-02"))


(def analysis
  (-> data
      (math/apply-pca {:dimensions 2})
      (math/apply-umap {})))

;(take 3 (-> analysis :participants math/without-votes ds/mapseq-reader))
;(take 2 (ds/mapseq-reader (ds/append-columns (:participants analysis) (-> analysis :pca :projection ds/columns))))
;(take 2 (ds/mapseq-reader (-> analysis :pca :projection)))

;(->> analysis :pca :projection (take 1))

;(ds/shape (:votes analysis))

[:div
 [:h1 "vTaiwan Uber Analysis"]
 [:vega-lite
  {:title "PCA Projection"
   :data {:values (->> analysis :participants math/without-votes ds/mapseq-reader (map #(into {} %)))}
   :mark {:type :point :tooltip {:content :data}}
   :transform [{:as :p-agree
                :calculate "(datum['n-agree'] + 1) / (datum['n-votes'] + 2)"}]
   :width 800
   :height 450
   :encoding {:x {:field :pc1}
              :y {:field :pc2}
              :color {:field :p-agree
                      :type :quantitative}}}]
 [:vega-lite
  {:title "UMAP Projection"
   :data {:values (seq (->> analysis :votes ds/mapseq-reader))}
   :mark :rect
   :width 1000
   :height 500
   :encoding {:x {:field :comment-id :type :ordinal}
              :y {:field :voter-id :type :ordinal}
              :color {:field :vote}}
   :config {:grid true :tick-band :extent}}]]



