(ns polis.viz
  (:require [polis.math :as math]
            [tech.v3.dataset :as ds]
            [tech.v3.datatype :as dt]
            [clojure.set :as set]
            [clojure.string :as string]
            [tech.v3.tensor :as tens]
            [tech.v3.datatype.statistics :as dt-stats]
            [tech.v3.dataset.tensor :as dtensor]
            [tech.v3.dataset.column :as dcol]
            [semantic-csv.core :as csv]
            [taoensso.timbre :as log]
            [polis.participants :as participants]))
            ;[oz.core :as oz]))



(def color-agree "#1b6244")


(defn pc-vars
  [conv]
  (let [eigenvals (:eigenvalues (:pca conv))
        sum (reduce + eigenvals)]
    (vec (take 5 (map (fn [eig] (/ eig sum)) eigenvals)))))

(def round math/round)

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
   (summary-table conv [:voters :groups :commenters :comments :votes :agrees :disagrees :votes-per-participant])))

(defn summary-list
  ([{:keys [summary]} keys]
   [:ul
    (for [k keys]
      (let [val (get summary k)
            val (cond-> val
                  (float? val) (round 2))]
        [:li [:strong (labels-map k)] ": "
         val]))])
  ([conv]
   (summary-list conv [:voters :groups :commenters :comments :votes :agrees :disagrees :votes-per-participant])))

(defn variance-report
  [conv]
  [:div
   [:md
    "Below, we can see the proportion of total variance explained by the x and y axes (the first two principal components) in the plot above:"]
   [:pprint (-> conv :pca :explained-variance vec)]])

(defn umap-pca-comparison
  [conv]
  {:title "Comparison of PCA to UMAP"
   :data {:values (->> conv :participants math/without-votes math/viz-values)}
   :transform [{:as :p-agree
                :calculate "(datum['n-agree'] + 1) / (datum['n-votes'] + 2)"}]
   :hconcat
   [{:title "UMAP"
     :mark {:type :point :tooltip {:content :data}}
     :selection {:brush {:type :interval}}
     :width 425
     :height 400
     :encoding {:x {:field :umap1, :type :quantitative, :scale {:zero false}}
                :y {:field :umap2, :type :quantitative, :scale {:zero false}}
                :color {:field :group-id
                        :type :nominal}
                :opacity {:field :n-votes :type :quantitative}}}
    {:title "PCA"
     :mark {:type :point :tooltip {:content :data}}
     :width 425
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


(defn- pie-charts-data
  ([{:as conv :keys [participants]}
    comments
    {:keys [group-ptpts-by comment-body keep-keys]
     :or {group-ptpts-by :group-id comment-body :comment-body}}]
   (->> participants
        (ds/group-by
         (fn [ptpt]
           (get ptpt group-ptpts-by))
         [group-ptpts-by])
        ;; for each participant group
        (mapcat
         (fn [[group ptpts]]
            ;; for each comment in comments
           (mapcat
            (fn [{:as cmnt :keys [comment-id]}]
              (let [cmnt-col (get ptpts (keyword (str comment-id)))
                    vote-counts
                    (->> (group-by identity cmnt-col)
                         (map (fn [[vote votes]]
                                [vote (count votes)]))
                         (into {}))
                    n-rows (ds/row-count ptpts)]
                (when (> n-rows 0)
                  (letfn [(pie-datum [counts]
                            (merge
                                ;(dissoc cmnt :agrees :disagrees :passes)
                             (select-keys cmnt (concat keep-keys [:comment-id comment-body]))
                             {:group group
                              :percent (* 100. (/ (:count counts) n-rows))}
                             counts))]
                    [(pie-datum {:vote :agree
                                 :count (get vote-counts 1 0)})
                     (pie-datum {:vote :disagree
                                 :count (get vote-counts -1 0)})
                     (pie-datum {:vote :pass
                                 :count (get vote-counts 0 0)})
                     (pie-datum {:vote :none
                                 :count (get vote-counts nil 0)})]))))
            (ds/mapseq-reader comments))))
        (map (fn [i datum]
               (assoc datum :i i))
             (range)))))

(defn comment-agree-order
  [{:keys [agrees votes]}]
  (- (/ (+ (or agrees 0) 1.)
        (+ (or votes 0) 2.))))

(defn wrap-lines
  [limit string]
  (vec
   (mapcat
    (fn [line]
      (reduce
       (fn [lines word]
         (if-let [lline (last lines)]
           (if (< limit (+ (count lline) (count word)))
             (conj lines word)
             (conj (vec (butlast lines))
                   (str lline " " word)))
           [word]))
       []
       (string/split line #"\s+")))
    (string/split string #"\n+"))))

(defn wrap-column-lines
  [limit comment-col]
  (dcol/new-column
   (dcol/column-name comment-col)
   (map (partial wrap-lines limit) comment-col)))

;(ds/update-column
  ;(ds/->dataset [{:a "This is a very freaking long sentence that I think should probably be broken down into pieces like a proper Englishman, God damned it. For the love of God, what would you have me do, good sir? Live with the heathens in Yorkshire?"}
                 ;{:a "This is a shorter sentence. God bless."}])
  ;:a
  ;(partial wrap-column-lines 70))

(defn apply-focus-group
  ([participants group-key focus-group other-label]
   (let [labels (fn [group]
                  ;; either return the focus group or other
                  (get #{focus-group} group "other"))]
     (participants/label-groups participants {:group-key group-key
                                              :labels labels})))
  ([participants group-key focus-group]
   (apply-focus-group participants group-key focus-group "other")))


(defn- label-size
  [participants group-ptpts-by]
  (let [sizes (->> (ds/group-by group-ptpts-by [group-ptpts-by] participants)
                   (map (fn [[group-name ds]] [group-name
                                               (str group-name " (" (ds/row-count ds) ")")]))
                   (into {}))]
    (participants/label-groups participants {:group-key group-ptpts-by
                                             :labels sizes})))

(defn comment-pies
  "Produces a vega-lite specification for a table of pie (donut, really) charts representing the vote breakdown per group.
  The comments argument may be either a tech.v3 dataset of comments, or a sequential (list, vector, etc) of comment ids."
  ([conv comments {:as opts
                   :keys [group-ptpts-by comment-body focus-group label-size?]
                   ;; Don't like this in relation to having to duplicate defaults above; consider just merging
                   ;; defaults into map and passing through
                   :or {group-ptpts-by :group-id comment-body :comment-body label-size? true}}]
   (let [comments (-> (cond
                        ;; assume we want the ids in order
                        (or (sequential? comments) (set? comments))
                        (math/select-by-vals (:comments conv) :comment-id comments)
                        :else
                        comments)
                      (ds/update-column comment-body (partial wrap-column-lines 140)))
         participants (cond-> (:participants conv)
                        focus-group (apply-focus-group group-ptpts-by focus-group)
                        label-size? (label-size group-ptpts-by))]
     {:data {:values (pie-charts-data (assoc conv :participants participants) comments opts)}
      ;:view {:stroke :white}
      :height (* 50 (ds/row-count comments))
      :width (* 50 (count (set (get (:participants conv) group-ptpts-by))))
      :mark {:type :arc :tooltip {:content :data}}
      :encoding {:x {:field :group :type :ordinal
                     :axis {:orient :top
                            :labelAngle -30}}
                 :y {:field comment-body :type :nominal
                     :sort (vec (get comments comment-body))
                     :axis {:orient :right
                            :labelLimit 700
                            :title false}}
                 ;; needs to update given more abstract comment-body if trying again...
                            ;:labelExpr "datum[\"comment-body\"]"}}
                 :radius {:value 20}
                 :radius2 {:value 8}
                 :theta {:field :percent
                         :type :quantitative
                         :scale {:domain [0 100]}}
                 :color {:field :vote
                         :scale {:domain [:agree :disagree :pass :none]
                                 :range [color-agree "red" "lightgrey" "white"]}}
                 :order {:field :order}}}))
  ([conv comments]
   (comment-pies conv comments {})))
