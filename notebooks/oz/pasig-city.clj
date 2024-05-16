(ns pasig-city-analysis
  (:require [polis.math :as math]
            [oz.core :as oz]
            [polis.viz :as viz]
            [tech.ml.dataset :as ds]
            [tech.v2.tensor :as tens]
            [tech.ml.dataset.column :as dcol]
            [semantic-csv.core :as csv]
            [clojure.set :as set]
            [taoensso.timbre :as log]))

:recompute

(def translations
  (-> (ds/->dataset "local/data/philippine-open-streets.4kaf74ejdk.2020-10-20/comment-translations.csv"
                    {:key-fn keyword})
      (ds/select-columns [:comment-id :comment-body :google-translation :human-translation :translator])
      (ds/mapseq-reader)
      (->> (map (fn [{:as row :keys [google-translation human-translation comment-body]}]
                  (assoc row
                         :translation (or human-translation google-translation comment-body)))))
      (ds/->dataset)))

;(frequencies (:translator translations))
;; {"Olga Cheng" 26, "Cassandra L." 14, "Bonnie Chan" 8, "Tzu-Chi Yen" 20, "Arne Brasseur" 1, nil 128}
;; {"Olga Cheng" 33, "Cassandra L." 22, "Bonnie Chan" 8, "Tzu-Chi Yen" 21, "Arne Brasseur" 1, nil 112}

(def raw-data
  (-> (math/load-data "local/data/philippine-open-streets.4kaf74ejdk.2020-10-20")
      (update :comments (fn [comments]
                          (-> (ds/left-join :comment-id comments translations)
                              (ds/remove-columns [:right.votes :right.comment-body]))))))

;(defn name-groups [{:as conv :keys [participants]}]
  ;(let [named-groups (dcol/new-column :group-id (map {0 :anti-uber 1 :pro-uber} (:group-id participants)))]
    ;(assoc-in conv [:participants :group-id] named-groups)))

(def data
  (-> raw-data
      (math/subset-grouped-participants)
      ;(name-groups)
      (math/mod-filter false)))

(defn sparsity-aware-correction
  [{:as conv :keys [participants comments]}]
  (let [n-comments (ds/row-count comments)
        sa-proj
        (-> participants
            (ds/select-columns [:pc1 :pc2 :n-votes])
            (ds/mapseq-reader)
            (->> (map (fn [{:keys [pc1 pc2 n-votes]}]
                        (let [scaling-factor (Math/sqrt (/ n-comments n-votes))]
                          {:sa-pc1 (* scaling-factor pc1)
                           :sa-pc2 (* scaling-factor pc2)}))))
            (ds/->dataset))]
    (update conv :participants ds/append-columns (ds/columns sa-proj))))

(def analysis
  (-> data
      (math/apply-scikit-pca{:dimensions 2 :flip-axes [0]})
      (math/apply-umap {})
      (sparsity-aware-correction)))

[:h1 "Pasig City Open Streets Conversation"]

[:md
 "[Polis](https://pol.is/home) is an open source [wiki-survey](https://roamresearch.com/#/app/polis-methods/page/DZfOLUSvE)
 platform for rapid, scalable, open ended feedback, in which [participants](https://roamresearch.com/#/app/polis-methods/page/me6hHfaqb)
 submit short [comments](https://roamresearch.com/#/app/polis-methods/page/9sgrt0LbX)
 which are sent out [semi-randomly](https://roamresearch.com/#/app/polis-methods/page/vIbPEejlQ)
 to other participants to vote on (by clicking agree, disagree or pass).
 Polis uses [statistical algorithms](https://roamresearch.com/#/app/polis-methods/page/ciPWF73Ss)
 to find patterns of [consensus](https://roamresearch.com/#/app/polis-methods/page/sl2uYQN7X)
 and [opinion groups](https://roamresearch.com/#/app/polis-methods/page/iJCEaDWYA)."]

;[:md
 ;"This report looks at the data generated in an engagement run by the government of Taiwan
 ;in August of 2015 concerning how Uber should be regulated in the nation.
 ;People's opinions, as reflected in this data, were then fed into a series of in person
 ;consultations with stakeholders, as part of the nation's [vTaiwan](https://info.vtaiwan.tw/)
 ;deliberative process, and points of consensus were used to craft legislation which was broadly
 ;viewed as fair to all parties (including the traditional Taxi companies and the citizens of Taiwan)."]

[:h3 "Basic statistics"]

[:md "Of the raw data collected, we have:"]

[viz/summary-table raw-data]

[:md "After removing moderated out comments, and participants who voted on fewer than 7 comments, we have:"]

[viz/summary-table data]


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
 The red line separates comments which were predominantly agreed with (bottom right) from those predominantly disagreed with (bottom left)."]

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
 This allows us to think of participants as having positions in high dimensional space (dimensionality equal to the number of comments)."]

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

[:vega-lite (pca-summary analysis {:n-comments 100})]

;(oz/view!
  ;[:vega-lite (assoc (pca-summary analysis {:n-comments 100})
                     ;:width 2000
                     ;:height 1600)])

[viz/variance-report analysis]

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
                       (pc-repful-comments analysis {:pcs [:pc1]})
                       {:comment-body :translation
                        :keep-keys [:translator]})
     (assoc :title {:text "Voting patterns for comments most correlated with PC1"
                    :align :left})
     (assoc-in [:encoding :x :axis :labelAngle] -30)
     (assoc-in [:encoding :x :title] nil))]

[:md "The comments most strongly correlated with position along the X-axis:"]
[:vega-lite
 (-> (viz/comment-pies analysis
                       (pc-repful-comments analysis {:pcs [:pc2]})
                       {:comment-body :translation
                        :keep-keys [:translator]})
     (assoc :title {:text "Voting patterns for comments most correlated with PC1"
                    :align :left})
     (assoc-in [:encoding :x :axis :labelAngle] -30)
     (assoc-in [:encoding :x :title] nil))]

[:md "The comments with highest overall pc loading between pcs 1 and 2"]
;(oz/view!
[:vega-lite
 (-> (viz/comment-pies analysis
                       (pc-repful-comments analysis {:pcs [:pc1 :pc2]})
                       {:comment-body :translation
                        :keep-keys [:translator]})
     (assoc :title {:text "Voting patterns for comments with the highest PC loadings"
                    :align :left})
     (assoc-in [:encoding :x :axis :labelAngle] -30)
     (assoc-in [:encoding :x :title] nil))]


[:md "The most agreed on comments:"]
[:vega-lite
 (-> (viz/comment-pies analysis
       (->> (:comments analysis)
            (ds/sort-by viz/comment-agree-order)
            (ds/head 15))
       {:comment-body :translation
        :keep-keys [:translator]})
     (assoc :title {:text "Voting patterns for majority comments"
                    :align :left})
     (assoc-in [:encoding :x :axis :labelAngle] -30)
     (assoc-in [:encoding :x :title] nil))]

[:md "Comments representative of group 0"]
[:vega-lite
 (-> (viz/comment-pies analysis
       (->> (math/comments-by-rrr analysis 0)
            (ds/head 10))
       {:comment-body :translation
        :keep-keys [:translator]}))]


[:md "Comments representative of group 1"]
[:vega-lite
 (-> (viz/comment-pies analysis
       (->> (math/comments-by-rrr analysis 1)
            (ds/head 10))
       {:comment-body :translation
        :keep-keys [:translator]}))]

[:md "Comments representative of group 2"]
[:vega-lite
 (-> (viz/comment-pies analysis
       (->> (math/comments-by-rrr analysis 2)
            (ds/head 10))
       {:comment-body :translation
        :keep-keys [:translator]}))]

[:md "Comments representative of group 3"]
[:vega-lite
 (-> (viz/comment-pies analysis
       (->> (math/comments-by-rrr analysis 3)
            (ds/head 10))
       {:comment-body :translation
        :keep-keys [:translator]}))]
