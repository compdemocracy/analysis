(ns vtaiwan-uber-analysis
  (:require [polis.math :as math]
            [tech.ml.dataset :as ds]
            [tech.ml.dataset.column :as dcol]
            [semantic-csv.core :as csv]))

[:h1 "vTaiwan Uber Analysis"]

(def data
  (math/load-data "data/vtaiwan-uber-conv.ts-2015-08-28.exported-2020-07-02"))


(def analysis
  (-> data
      (math/apply-pca {:dimensions 2})
      (math/apply-umap {})))

[:md "First, we'll look at a basic PCA projection of the data:"]

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

[:md "Next, a vote matrix to evaluate sparsity"]

;(take 3 (-> analysis :participants math/without-votes ds/mapseq-reader))
;(take 2 (ds/mapseq-reader (ds/append-columns (:participants analysis) (-> analysis :pca :projection ds/columns))))
;(take 2 (ds/mapseq-reader (-> analysis :pca :projection)))

;(->> analysis :pca :projection (take 1))

;(ds/shape (:votes analysis))

[:vega-lite
 {:title "Vote Matrix"
  :data {:values (seq (->> analysis :votes ds/mapseq-reader))}
  :mark {:type :rect :tooltip {:content :data}}
  :width 1000
  :height 500
  :encoding {:x {:field :comment-id :type :ordinal}
             :y {:field :voter-id :type :ordinal}
             :color {:field :vote}}
  :config {:grid true :tick-band :extent}}]

[:md "Here, we can see that the moderated out comments have not been removed yet; Improving on this..."]



