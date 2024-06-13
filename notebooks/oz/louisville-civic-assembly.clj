(ns louisville-civic-assembly-analysis
  (:require [polis.math :as math]
            [oz.core :as oz]
            [polis.viz :as viz]
            [tech.v3.dataset :as ds]
            [tech.v3.tensor :as tens]
            [tech.v3.dataset.column :as dcol]
            [semantic-csv.core :as csv]
            [clojure.set :as set]
            [taoensso.timbre :as log]
            [libpython-clj2.python :as py :refer [py. py.. py.-]]))
            ;[tech.v3.dataset.join :as djoin]))

(def raw-data
  (math/load-data "data/louisville-civc-assembly.42ssceevsw.2020-10-23"))

(def data
  (-> raw-data
      (math/subset-grouped-participants)
      (math/mod-filter false)))

(/ 1 0)

;(require '[tech.v3.dataset.join :as djoin])
;(djoin/left-join)
(try
  (def analysis
    (-> data
        (math/apply-scikit-pca {:dimensions 2 :flip-axes [0]})))
        ;(math/apply-umap {})))
  (catch Throwable t
    (log/error t)))

[:h1 "Louisville Civic Assembly"]

[:md
 "[Polis](https://pol.is/home) is an open source [wiki-survey](https://roamresearch.com/#/app/polis-methods/page/DZfOLUSvE)
 platform for rapid, scalable, open ended feedback, in which [participants](https://roamresearch.com/#/app/polis-methods/page/me6hHfaqb)
 submit short [comments](https://roamresearch.com/#/app/polis-methods/page/9sgrt0LbX)
 which are sent out [semi-randomly](https://roamresearch.com/#/app/polis-methods/page/vIbPEejlQ)
 to other participants to vote on (by clicking agree, disagree or pass).
 Polis uses [statistical algorithms](https://roamresearch.com/#/app/polis-methods/page/ciPWF73Ss)
 to find patterns of [consensus](https://roamresearch.com/#/app/polis-methods/page/sl2uYQN7X)
 and [opinion groups](https://roamresearch.com/#/app/polis-methods/page/iJCEaDWYA)."]

[:md
 "This report looks at the data generated in an engagement run by Math & Democracy (pro-bono) in partnership with
 [University of Kentucky](http://uky.edu), [NPR](https://npr.org) and
 [The American Assembly](https://americanassembly.org/) (part of [Columbia University](https://columbia.edu)
 in February 2020.
 The poll asked residents of Louisville, Kentucky to respond to the question

> _What do you believe should change in Louisville to make it a better place to live, work and spend time?_"]


[:h3 "Basic statistics"]

[:md "Of the raw data collected, we have:"]

[viz/summary-table raw-data [:voters :commenters :comments :votes :agrees :disagrees :votes-per-participant :groups]]

[:md "After removing moderated out comments, and participants who voted on fewer than 7 comments, we have:"]

[viz/summary-table data [:voters :commenters :comments :votes :agrees :disagrees :votes-per-participant]]


[:md
 "Here we can see the distribution of these votes and comments over time as the conversation unfolded."]

(defn binned-votes
  ([{:keys [votes participants]} {:keys [interval-in-hours split-by-keys agg-fn]}]
   (let [interval-in-hours (or interval-in-hours 1)
         interval-ms (int (* interval-in-hours 60 60 1000))
         ptpt-split-keys (set/intersection (set (keys participants)) (set split-by-keys))
         votes (if (seq ptpt-split-keys)
                 (ds/left-join [:voter-id :participant]
                               votes
                               (ds/select-columns participants (concat [:participant] ptpt-split-keys)))
                 votes)]
     (->> votes
          (ds/group-by
           (fn [{:as row :keys [timestamp]}]
             (let [start-ts (* interval-ms (quot timestamp interval-ms))]
               (cond->
                {:start-timestamp start-ts
                 :end-timestamp (+ start-ts interval-ms)}
                 split-by-keys (merge (select-keys row split-by-keys)))))
               ;split-by-keys (merge (into {} (map (fn [k] [k (get row k)]) split-by-keys))))))
           (concat [:timestamp] split-by-keys))
          (map (fn [[window ds]]
                 (merge window
                        {:count (ds/row-count ds)}
                        (when agg-fn (agg-fn ds))))))))
  ([conv] (binned-votes conv {})))

(defn binned-comments
  ([{:keys [comments participants]} {:keys [interval-in-hours split-by-keys agg-fn]}]
   (let [interval-in-hours (or interval-in-hours 1)
         interval-ms (int (* interval-in-hours 60 60 1000))
         ptpt-split-keys (set/intersection (set (keys participants)) (set split-by-keys))
         votes (if (seq ptpt-split-keys)
                 (ds/left-join [:author-id :participant]
                               comments
                               (ds/select-columns participants (concat [:participant] ptpt-split-keys)))
                 comments)]
     (->> votes
          (ds/group-by
           (fn [{:as row :keys [timestamp]}]
             (let [start-ts (* interval-ms (quot timestamp interval-ms))]
               (cond->
                {:start-timestamp start-ts
                 :end-timestamp (+ start-ts interval-ms)}
                 split-by-keys (merge (select-keys row split-by-keys)))))
               ;split-by-keys (merge (into {} (map (fn [k] [k (get row k)]) split-by-keys))))))
           (concat [:timestamp] split-by-keys))
          (map (fn [[window ds]]
                 (merge window
                        {:count (ds/row-count ds)}
                        (when agg-fn (agg-fn ds))))))))
  ([conv] (binned-comments conv {})))


[:vega-lite {:title "Vote activity over time - by cohort"
             :data {:values (binned-votes analysis {:interval-in-hours 2 :split-by-keys []})}
             :width 900
             :height 200
             :mark {:type :bar :tooltip {:content :data}}
             :encoding {:x {:field :start-timestamp :type :temporal}
                        :x2 {:field :end-timestamp :type :temporal}
                        :y {:field :count :type :quantitative}}}]
                        ;:color {:field :cohort :type :nominal
                                ;:scale {:range ["black" "#4F69AC" "#C52424" "lightgrey"]}}}}]

[:vega-lite {:title "Comment submission over time - by moderation status"
             :data {:values (binned-comments raw-data {:interval-in-hours 2 :split-by-keys [:moderated]})}
             :width 900
             :height 200
             :mark {:type :bar :tooltip {:content :data}}
             :encoding {:x {:field :start-timestamp :type :temporal}
                        :x2 {:field :end-timestamp :type :temporal}
                        :y {:field :count :type :quantitative}
                        :color {:field :moderated :type :nominal
                                :scale {:domain [-1 0 1]
                                        :range ["darkgrey" "lightgrey" "green"]}}}}]

;[:vega-lite {:title "Average probability of agree votes over time"
             ;:data {:values (binned-votes analysis {:interval-in-hours 2 :agg-fn (fn [ds] {:agree-prob (agree-prob ds)})})}
             ;:width 900
             ;:height 200
             ;:mark {:type :line :tooltip {:content :data}}
             ;:encoding {:x {:field :start-timestamp :type :temporal}
                        ;:y {:field :agree-prob :type :quantitative}}}]

[:h3 "Comment overview"]

[:md
 "Next, we'll take a look at the variance in the data by plotting comments according to the number of agrees and disagrees.
 This data is plotted in a log plot due to the highly skewed nature of vote count distribution per comment.
 The grey line separates comments which were predominantly agreed with (bottom right) from those predominantly disagreed with (bottom left)."]

[:vega-lite
 {:title "Comments by vote"
  :data {:values (math/mapseqs (:comments data))}
  :width 900
  :height 900
  :layer [{:mark {:type :point :tooltip {:content :data}}
           :encoding {:x {:field :agrees
                          :type :quantitative
                          :scale {:type :log}}
                      :y {:field :disagrees
                          :type :quantitative
                          :scale {:type :log}}}}
          {:mark {:type :line :color :lightgrey}
           :data  {:values [{:agrees 1 :disagrees 1} {:agrees 500 :disagrees 500}]}
           :encoding {:x {:field :agrees
                          :type :quantitative
                          :scale {:type :log}}
                      :y {:field :disagrees
                          :type :quantitative
                          :scale {:type :log}}}}]}]

[:md
 "Note that comments with far more disagrees than agrees had overall much lower vote counts.
 This is a direct result of the comment routing architecture of Polis, which deprioritizes comments which most people disagree with."]

[:md
 "We can take these votes and arrange them into a matrix, where rows correspond to participants and columns correspond to statements.
 This allows us to think of participants as having positions in a high dimensional space (dimensionality equal to the number of comments)."]

[:vega-lite (viz/vote-matrix analysis)]

[:h2 "Overall opinion landscape"]

[:md
 "While the above visualization may be impressive, it's not particularly useful as far as understanding how participants opinions relate to each other.
 To better understand this, we can apply a _dimensionality reduction_ algorithm, which allows us to capture as much of the variance within the data as we can within a lower dimensional space.
 Specifically, reducing to 2-dimensions allows us to plot participants locations in relation to each other in an _opinion space_, where participants are close together if they tend to agree, and further apart if they tend to disagree.
 Here, we're also coloring according to a K-means clustering of the participants into _opinion groups_,
 which lets us ask questions about what's important to different groups, and better understand the opinion landscape."]

[:vega-lite
 (-> (viz/pca-plot analysis)
     (assoc :title "PCA projection of participants")
     (assoc-in [:encoding :color :title] :group))]
      ;(assoc-in [:encoding :x :field] :sa-pc1)
      ;(assoc-in [:encoding :y :field] :sa-pc2))]

[:div
 [:md
  "Below, we can see the proportion of total variance explained by the x and y axes (the first two principal components) in the plot above:"]
 [:pre (with-out-str (clojure.pprint/pprint (->> analysis :pca :explained-variance py/->jvm vec (mapv (fn [x] (viz/round x 3))))))]]
;[viz/variance-report analysis]

[:md "The sharp decline in variance explained, from roughly 16% to 3% suggests a very sharp divide associated with comments corresponding to position along the X-axis, very dominant in predicting participants positions in the opinion landscape relative to other comments."]

(defn equal-range
  [col]
  (let [val (apply max (map (fn [x] (Math/abs x)) col))]
    [(- val) val]))

(defn pca-summary
  ([{:as conv :keys [comments participants]}
    {:keys [n-comments] :or {n-comments 100}}]
   (let [representative-comments
         (->> (:comments conv)
              (ds/sort-by
               (fn [{:keys [pc1 pc2]}]
                 (- (Math/sqrt (+ (Math/pow pc1 2) (Math/pow pc2 2))))))
              (ds/head n-comments)
              (ds/sort-by :pc1))]
     {:title "PCA participant projection & comment loadings"
      :width 900
      :height 500
      :layer [{:data {:values (->> participants math/without-votes math/viz-values)}
               :mark {:type :point :tooltip {:content :data}}
               :transform [{:as :p-agree
                            :calculate "(datum['n-agree'] + 1) / (datum['n-votes'] + 2)"}]
               :encoding {:x {:field :pc1 :type :quantitative
                              :scale {:domain (equal-range (:pc1 participants))}}
                          :y {:field :pc2 :type :quantitative
                              :scale {:domain (equal-range (:pc2 participants))}}
                          :color {:field :group-id
                                  :type :nominal}
                          :opacity {:field :n-votes :type :quantitative}}}
              {:data {:values (math/viz-values representative-comments)}
               :transform [{:as :angle
                            :calculate "atan2(datum.pc2, datum.pc1) * 180 / PI"}]
               :mark {:type :point
                      :shape :wedge
                      ;:filled :true
                      :color :black
                      :size 50
                      :tooltip {:content :data}}
               :encoding {:x {:field :pc1
                              :type :quantitative
                              :scale {:domain (equal-range (:pc1 comments))}}
                          :y {:field :pc2
                              :type :quantitative
                              :scale {:domain (equal-range (:pc2 comments))}}
                          :angle {:field :angle
                                  :type :quantitative
                                  ;:scale {:domain [-180 180] :range [-90 270]}
                                  :scale {:domain [180 -180] :range [-90 270]}}}}]
      :resolve {:scale {:x :independent;}}]}]
                        :y :independent}}}));}}]}]
  ([conv]
   (pca-summary conv {})))


[:md
 "We can also take a look at this projection of participants in tandem with a projection
 a visualization of how each comment contributes to participants position in the opinion space.
 The arrows below represent the direction and magnitude a participant will be pushed in if they
 agree with a particular comment.
 You can hover over the arrow mark to see more about a particular comment of interest."]

[:vega-lite (pca-summary analysis {:n-comments 100})]

;(oz/view!
  ;[:vega-lite (assoc (pca-summary analysis {:n-comments 100})
                     ;:width 2000
                     ;:height 1600)])

(defn pc-repful-comments
  ([conv {:keys [pcs n-comments] :or {pcs [:pc1] n-comments 20}}]
   (let [pcs (if (coll? pcs) pcs [pcs])]
     (->> (:comments conv)
          (ds/sort-by
           (fn [row]
             (- (Math/sqrt (apply + (map (fn [pc] (Math/pow (get row pc) 2))
                                         pcs))))))
          (ds/head n-comments)
          (ds/sort-by (first pcs)))))
  ([conv]
   (pc-repful-comments conv {})))

[:md "The comments most strongly correlated with position along the X-axis:"]
[:vega-lite
 (-> (viz/comment-pies analysis
                       (pc-repful-comments analysis {:pcs [:pc1]}))
     (assoc :title {:text "Voting patterns for comments correlated with position along the X-axis"
                    :align :left})
     (assoc-in [:encoding :x :axis :labelAngle] -30)
     (assoc-in [:encoding :x :title] nil))]

[:md "The comments most strongly correlated with position along the Y-axis:"]
[:vega-lite
 (-> (viz/comment-pies analysis
                       (pc-repful-comments analysis {:pcs [:pc2]}))
     (assoc :title {:text "Voting patterns for comments most correlated with position along the Y-axis"
                    :align :left})
     (assoc-in [:encoding :x :axis :labelAngle] -30)
     (assoc-in [:encoding :x :title] nil))]


[:md "The most agreed on comments:"]
[:vega-lite
 (-> (viz/comment-pies analysis
                       (->> (:comments analysis)
                            (ds/sort-by viz/comment-agree-order)
                            (ds/head 15)))
     (assoc :title {:text "Voting patterns for majority comments"
                    :align :left})
     (assoc-in [:encoding :x :axis :labelAngle] -30)
     (assoc-in [:encoding :x :title] nil))]

;; Should group be a group-id,
(defn relative-rate-ratio
  ([{:as conv :keys [participants]} group-id comment-id]
   (let [in-fn (fn [row] (= group-id (:group-id row)))
         out-fn (fn [row] (not= group-id (:group-id row)))
         in-group (ds/filter in-fn [:group-id] participants)
         out-group (ds/filter out-fn [:group-id] participants)]
     (relative-rate-ratio conv in-group out-group comment-id)))
  ([{:keys [comments participants]} in-group out-group comment-id]
   (let [in-freqs (frequencies (get in-group (keyword (str comment-id))))
         out-freqs (frequencies (get out-group (keyword (str comment-id))))
         in-agree-prob  (/ (+ 1. (get in-freqs 1 0))
                           (+ 2. (get in-freqs 1 0) (get in-freqs 0 0) (get in-freqs -1 0)))
         out-agree-prob (/ (+ 1. (get out-freqs 1 0))
                           (+ 2. (get out-freqs 1 0) (get out-freqs 0 0) (get out-freqs -1 0)))]
     ;(log/info "In agree prob: " in-agree-prob)
     ;(log/info "Out agree prob:" out-agree-prob)
     (/ in-agree-prob out-agree-prob))))

(relative-rate-ratio analysis :pro-uber 38)
(relative-rate-ratio analysis :pro-uber 62)


;; Hmnm... realizing it's really inefficient for the function above to do the group ds splitting; probably
;; much better to be able to do this once below, and then pass these in/out groups to the fn above
(defn comments-by-rrr
  [{:as conv :keys [comments participants]} group-id]
  (let [in-fn (fn [row] (= group-id (:group-id row)))
        out-fn (fn [row] (not= group-id (:group-id row)))
        in-group (ds/filter in-fn [:group-id] participants)
        out-group (ds/filter out-fn [:group-id] participants)]
    (->> comments
         (ds/sort-by (comp - (partial relative-rate-ratio conv in-group out-group) :comment-id)))))
:recompute

;(->> (comments-by-rrr analysis 0)
     ;(ds/head 15))

;[:md "Comments representative of group 0"]
;[:vega-lite
 ;(-> (viz/comment-pies analysis
       ;(->> (comments-by-rrr analysis 0)
            ;(ds/head 10))))]


;[:md "Comments representative of group 1"]
;[:vega-lite
 ;(-> (viz/comment-pies analysis
       ;(->> (comments-by-rrr analysis 1)
            ;(ds/head 10))))]

;[:md "Comments representative of group 2"]
;[:vega-lite
 ;(-> (viz/comment-pies analysis
       ;(->> (comments-by-rrr analysis 2)
            ;(ds/head 10))))]

;[:md "Comments representative of group 3"]
;[:vega-lite
 ;(-> (viz/comment-pies analysis
       ;(->> (comments-by-rrr analysis 3)
            ;(ds/head 10))))]
