(ns polis.viz
  (:require [polis.math :as math]
            [tech.ml.dataset :as ds]
            [tech.v2.datatype :as dt]
            [clojure.set :as set]
            [tech.v2.tensor :as tens]
            [tech.v2.datatype.statistics :as dt-stats]
            [tech.ml.dataset.tensor :as dtensor]
            [tech.ml.dataset.column :as dcol]
            [semantic-csv.core :as csv]))
            ;[oz.core :as oz]))



(def color-agree "#1b6244")


(defn pc-vars
  [conv]
  (let [eigenvals (:eigenvalues (:pca conv))
        sum (reduce + eigenvals)]
    (vec (take 5 (map (fn [eig] (/ eig sum)) eigenvals)))))

(defn round
  [x dec-places]
  (let [tens (Math/pow 10 dec-places)]
    (double (/ (int (* x tens)) tens))))


(defn td
  [& contents]
  (into [:td {:style {:padding-right 15}}]
        contents))

(defn th
  [& contents]
  (into [:th {:style {:padding-right 15}}]
        contents))

(defn vote-distribution-plot
  [conv]
  {:title "Vote History"
   :data {:values (-> conv :votes math/viz-values)}
   :width 900
   :height 200
   :mark {:type :bar :tooltip {:content :data}}
   :encoding {:x {:field :timestamp
                  :bin true
                  :timeUnit :yearmonthdatehours
                  :type :temporal}
              :y {:aggregate :count}}})
              ;:color {:field "changed-vote?"}}}]}]

(defn pca-plot [conv]
  {:title "PCA Projection"
   :data {:values (->> conv :participants math/without-votes math/viz-values)}
   :mark {:type :point :tooltip {:content :data}}
   :transform [{:as :p-agree
                :calculate "(datum['n-agree'] + 1) / (datum['n-votes'] + 2)"}]
   ;:width 2000
   ;:height 1200
   :width 900
   :height 500
   :encoding {:x {:field :pc1 :type :quantitative}
              :y {:field :pc2 :type :quantitative}
              :color {:field :group-id
                      :type :nominal}
              :opacity {:field :n-votes :type :quantitative}}})

(defn vote-matrix [conv]
  {:title "Vote Matrix"
   :data {:values (seq (->> conv :votes ds/mapseq-reader))}
   :mark {:type :rect :tooltip {:content :data}}
   :width 900
   :height 700
   :encoding {:x {:field :comment-id :type :ordinal
                  :axis {:labelOverlap :greedy}}
              :y {:field :voter-id :type :ordinal
                  :axis {:labelOverlap :greedy}}
              :color {:field :vote
                      :scale {:range [:red :lightgrey "#096b66"]}}}
   :config {:grid true :tick-band :extent}})


(defn round
  [x dec-places]
  (let [tens (Math/pow 10 dec-places)]
    (double (/ (int (* x tens)) tens))))


(defn td
  [& contents]
  (into [:td {:style {:padding-right 15}}]
        contents))

(defn th
  [& contents]
  (into [:th {:style {:padding-right 15}}]
        contents))

(def labels-map
  {:voters "Participants"
   :groups "Groups"
   :commenters "Commenters"
   :comments "Comments"
   :votes "Votes"
   :agrees "Agrees"
   :disagrees "Disagrees"
   :votes-per-participant "Votes / participant (avg)"})

(defn summary-table
  ([{:keys [summary]} keys]
   [:table
    [:tr
     (for [k keys]
       [th (labels-map k)])]
    [:tr
     (for [k keys]
       (let [val (get summary k)
             val (cond-> val
                   (float? val) (round 2))]
         [td val]))]])
  ([conv]
   (summary-table conv [:voters :groups :commenters :comments :votes :votes-per-participant])))


(defn variance-report
  [conv]
  [:div
    [:md
     "Below, we can see the proportion of total variance explained by the x and y axes (the first two principal components) in the plot above:"]

    [:pprint (-> conv :pca :explained-variance)]])

(defn umap-pca-comparison
  [conv]
  {:title "Comparison of PCA to UMAP"
   :data {:values (->> conv :participants math/without-votes math/viz-values)}
   :transform [{:as :p-agree
                :calculate "(datum['n-agree'] + 1) / (datum['n-votes'] + 2)"}]
   :hconcat
   [
    {:title "UMAP"
     :mark {:type :point :tooltip {:content :data}}
     :selection {:brush {:type :interval}}
     :width 450
     :height 400
     :encoding {:x {:field :umap1, :type :quantitative, :scale {:zero false}}
                :y {:field :umap2, :type :quantitative, :scale {:zero false}}
                :color {:field :group-id
                        :type :nominal}
                :opacity {:field :n-votes :type :quantitative}}}
    {:title "PCA"
     :mark {:type :point :tooltip {:content :data}}
     :width 450
     :height 400
     ;:transform [{:filter {:selection :brush}}]
     :encoding {:x {:field :pc1 :type :quantitative}
                :y {:field :pc2 :type :quantitative}
                :color {:condition {:selection "brush"
                                    :field :group-id
                                    :type :nominal}}
                :opacity {:condition {:selection :brush
                                      :value 1.0}
                          :value 0.1}}}]})
