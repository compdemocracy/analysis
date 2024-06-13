(ns polis.math
  "Core Polis data analysis API, implemented with tech.v3.dataset and libpython-clj2 for python interop."
  (:require [tech.v3.dataset :as ds]
            ;[tech.v3.dataset.pca :as dpca]
            [libpython-clj2.python.np-array] ;; this is for side effects plugging numpy into tech.v3
            [tech.v3.dataset.math :as dmath]
            [tech.v3.libs.smile.matrix :as smile-mat]
            [tech.v3.dataset.neanderthal :as dnean]
            [tech.v3.dataset.join :as djoin]
            [tech.v3.dataset.column :as dcol]
            [tech.v3.dataset.tensor :as dtensor]
            [tech.v3.datatype :as dt]
            [tech.v3.tensor :as tens]
            [tech.v3.datatype.functional :as dfn]
            [clojure.java.io :as io]
            [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :as py :refer [py. py.. py.-]]
            [semantic-csv.core :as csv]
            [clojure.spec.alpha :as s]
            ;[fastmath.stats :as fast-stats]
            [taoensso.timbre :as log]
            [clojure.set :as set]
            ;[kixi.stats.core :as kixi]
            [polis.participants :as participants]
            [polis.math.matrix :as matrix])
            ;[polis.db :as db])
  (:import [smile.stat.hypothesis CorTest]
           [org.apache.commons.math3.stat.inference TTest MannWhitneyUTest GTest]))


;; naming & api questions
;; * filter by topic vs select by topic? when do we use select vs filter?
;; * filter-by-topic takes comments ds; when do we take the whole conv vs not, and how does this interact with naming? ^
;; * what order should filter etc take? match the functions in ds & clojure core?

;; Require python libraries

(log/info "Establishing python link")
;(import-python)

(require-python '[numpy :as numpy])
(require-python '[sklearn :as sklearn])
(println "sklearn-version:" sklearn/__version__)

(require-python '[sklearn.model_selection :as sk-model]
                '[sklearn.decomposition :as sk-decomp]
                '[numpy :as numpy]
                '[pandas :as pandas]
                '[umap :as umap]
                '[umap_metric :as umap_metric :reload])

;; For some reason numba doesn't load on first try
(try
  (require-python '[numba :as numba])
  (catch Throwable t
    (require-python '[numba :as numba])))
(log/info "Python connected")


;;; Implement some basic column statistics

(defn column-stats
  [dataset]
  (->> dataset
       ds/brief
       (map (fn [stats]
              [(:col-name stats) stats]))
       (into {})))

;(csv/->int "4")

(defn vote-column-names [ds]
  (filter
   (comp (partial re-matches #"^?\d*$") name)
   (ds/column-names ds)))

(def comment-id-colname->int
  (comp csv/->int name))

(defn list-subtract [xs ys]
  (filterv (comp not (set ys))
           xs))

(defn nonvote-column-names [ds]
  (list-subtract (ds/column-names ds)
                 (vote-column-names ds)))

;; TODO move to participants ns
(defn without-votes
  [participants-ds]
  (ds/select-columns participants-ds (nonvote-column-names participants-ds)))

;; TODO move to participants ns
(defn select-votes
  [participants-ds]
  (ds/select-columns participants-ds (vote-column-names participants-ds)))

(defn viz-values
  [ds]
  (->> ds ds/mapseq-reader (map #(into {} %))))

;; This could likely be made much more performant
(defn impute-means
  "Returns a new dataset with imputed means"
  [dataset]
  (let [stats (column-stats dataset)
        colnames (vote-column-names dataset)]
    (-> dataset
        (ds/mapseq-reader)
        (->> (pmap
              (fn [row]
                (->> row
                     (map
                      (fn [[k v]]
                        (if v
                          [k v]
                          [k (or (:mean (get stats k)) 0)])))
                     (into {})))))
        (ds/->dataset)
        (ds/select-columns colnames))))


(defn matrix-cast [ds to-type]
  (reduce
   (fn [ds' colname]
     (ds/column-cast ds' colname to-type))
   ds
   (ds/column-names ds)))

;; This doesn't quite work because it places the computed means in as ints (need to have col types updated for
;; this to work); However, annecotally, may be 10x performance boost (3 sec to 300ms for one dataset)
(defn impute-means2
  [ds]
  (-> (matrix-cast ds :float64)
      (ds/replace-missing
       :all :value (fn [col] (/ (reduce + col) (float (count col)))))))

(defn- file-dataset
  [export-dir filename]
  (ds/->dataset (str export-dir "/" filename)
                {:key-fn keyword}))


(defn load-summary [filename]
  (-> (->> (csv/slurp-csv filename :mappify false)
           (map (fn [[k v]] [(keyword k) v]))
           (into {}))
      (update :commenters csv/->int)
      (update :views csv/->int)
      (update :voters csv/->int)
      (update :comments csv/->int)
      (update :voters-in-conv csv/->int)
      (update :groups csv/->int)))

;(load-summary "local/data/engage-britain-live.3yjmwkrw4c.2020-06-03.0700/summary.csv")


(defn update-summary
  [{:as conv :keys [comments participants votes]}]
  (let [n-votes (ds/row-count votes)
        n-ptpts (ds/row-count participants)
        derived-counts
        {:voters n-ptpts
         :votes n-votes
         :comments (ds/row-count comments)
         :commenters (count (set/intersection (set (:author-id comments))
                                              (set (:participant participants))))
         :agrees (count (filter (partial = 1) (:vote votes)))
         :disagrees (count (filter (partial = -1) (:vote votes)))
         :passes (count (filter (partial = 0) (:vote votes)))
         :votes-per-participant (double (/ n-votes n-ptpts))}]
    (-> conv
        (update :summary merge derived-counts)
        (dissoc :voters-in-conv))))

(defn filter-by-vals
  "Filters the given dataset to just the entires where `colname` is among `vals`, leaving rows in the same order as in the original dataset"
  [ds colname vals]
  (let [filter-fn (comp (set vals) #(get % colname))]
    (ds/filter ds filter-fn)))

(defn select-by-vals
  "Selects rows from the given dataset based on `colname` and `vals`, returning rows in the order specified by `vals` (in contrast with `filter-by-vals`).
  Will return multiple results per val if they exist."
  [ds column vals]
  (-> (ds/group-by (fn [row] (get row column))
                   [column] ds)
      (map vals)
      (->> (apply ds/concat-copying))))


(defn remove-by-vals
  "Removes rows from the dataset which do not match the given `colname` and `vals`."
  [ds colname vals]
  (let [filter-fn (comp not (set vals) #(get % colname))]
    (ds/filter ds filter-fn)))

(defn id-keyword [id]
  (if (keyword? id)
    id
    (keyword (str id))))

;; TODO REname as subset-comments; or is there difference between filter & subset?
(defn filter-comments
  [{:keys [summary comments votes participants matrix]} comment-ids]
  (let [keep-comment-colnames (set (map (comp keyword str) comment-ids))
        keep-comments (filter-by-vals comments :comment-id comment-ids)
        keep-votes (filter-by-vals votes :comment-id comment-ids)
        drop-comment-colnames (set/difference (set (keys matrix)) keep-comment-colnames)
        keep-matrix (ds/unordered-select matrix keep-comment-colnames :all)]
    (update-summary
     {:summary summary
      :comments keep-comments
      :votes keep-votes
      :participants (ds/remove-columns participants drop-comment-colnames)
      :matrix keep-matrix})))

(defn mod-filter
  [{:as conv :keys [comments]} strict?]
  (let [filter-fn (comp (if strict? (partial = 1) #{1 0})
                        :moderated)
        keep-comments (ds/filter comments filter-fn)
        keep-comment-ids (set (:comment-id keep-comments))]
    (filter-comments conv keep-comment-ids)))


;(defn vote-count-filter
  ;[{:as conv :keys [summary comments votes participants]} min-votes]
  ;(let [keep-participants
        ;(ds/filter participants (comp (partial <= min-votes) :n-votes))]
    ;(update-summary
      ;{})))

;(:name (meta (first (ds/columns (ds/->dataset [{:a 1 :b 2} {:a 3 :b 4}])))))

(defn column-name [dcol]
  (:name (meta dcol)))

;; This isn't working yet; Need to fix!
;; TODO
(defn update-comment-vote-counts
  [{:as conv :keys [comments participants]}]
  (let [votes-table (select-votes participants)
        vote-counts
        (-> (ds/columns votes-table)
            (->> (map
                  (fn [cmnt-col]
                    (->> (group-by identity cmnt-col)
                         (map (fn [[vote votes]]
                                [vote (count votes)]))
                         (filter first)
                         (into {:comment-id (comment-id-colname->int (column-name cmnt-col))})))))
            (ds/->dataset)
            (ds/rename-columns {1 :agrees -1 :disagrees 0 :passes})
            (ds/column-map :votes
                           (fn [& xs] (apply + (keep identity xs)))
                           [:agrees :disagrees :passes]))
        updated-comments (djoin/left-join :comment-id
                                          (dissoc comments :agrees :disagrees :passes)
                                          vote-counts)]
    (assoc conv :comments (dissoc updated-comments :right.comment-id))))

;(def data
  ;(-> "local/data/engage-britain-live.3yjmwkrw4c.2020-06-28.0700"
      ;(load-data)))
;(def data'
  ;(-> "local/data/engage-britain-live.3yjmwkrw4c.2020-06-28.0700"
      ;(load-data)))
;(:agrees (:comments data))
;(:agrees (:comments data'))

;(def analysis
  ;(apply-scikit-pca data {:dimensions 2}))
;(:comments analysis)

;(let [{:keys [participants comments]} data
      ;votes-table (select-votes participants)
      ;vote-counts
      ;(-> votes-table
          ;(ds/columns)
          ;(->> (map
                 ;(fn [cmnt-col]
                   ;(->> (group-by identity cmnt-col)
                        ;(map (fn [[vote votes]]
                               ;[vote (count votes)]))
                        ;(filter first)
                        ;(into {:comment-id (comment-id-colname->int (dcol/column-name cmnt-col))})))))
          ;(ds/->dataset)
          ;(ds/rename-columns {1 :agrees -1 :disagrees 0 :passes})
          ;(ds/column-map :votes + :agrees :disagrees :passes))]
  ;(djoin/left-join :comment-id
                ;(dissoc comments :agrees :disagrees :passes)
                ;vote-counts))


;(dcol/column-name (first (ds/columns (ds/->dataset [{:a 1}]))))

(defn load-data
  [export-dir]
  (let [load-data' (partial file-dataset export-dir)
        ptpts-ds (load-data' "participants-votes.csv")]
    (-> {:summary (load-summary (str export-dir "/summary.csv"))
         :comments (ds/sort-by (load-data' "comments.csv") :comment-id)
         :votes (load-data' "votes.csv")
         :participants ptpts-ds
         :matrix (-> ptpts-ds select-votes impute-means)}
        update-comment-vote-counts
        update-summary)))

(defn explained-variance
  [eigenvals]
  (let [sum (reduce + eigenvals)]
    (map (fn [eig] (/ eig sum)) eigenvals)))

(defn apply-pca
  [{:as conv :keys [matrix]}
   {:keys [dimensions flip-axes] :or {dimensions 2}}]
  (let [;pca-results (dpca/pca-dataset matrix)
        ;pca-results (dmath/fit-pca matrix {:n-components dimensions})
        pca-results (dnean/fit-pca matrix {:n-components dimensions})
        ;pca-proj (dmath/transform-pca matrix pca-results dimensions :float64)
        ;pca-proj (dmath/transform-pca matrix pca-results)
        pca-proj (dnean/transform-pca matrix pca-results)
        pca-proj (ds/rename-columns pca-proj {0 :pc1 1 :pc2})]
    (-> conv
        (assoc :pca
               (assoc pca-results
                      :projection pca-proj
                      :explained-variance (explained-variance (:eigenvalues pca-results))))
        (update :participants ds/append-columns (ds/columns pca-proj)))))

(defn row-major-tensor->dataset
  "Takes a row-major tensor and casts as a dataset with given colnames"
  ([tensor colnames]
   (ds/rename-columns (dtensor/tensor->dataset tensor)
                      (into {} (map vector (range) colnames))))
  ([tensor]
   (dtensor/tensor->dataset tensor)))

(defn np-array->dataset
  "Takes a row-major numpy tensor and casts as a dataset with given colnames"
  ([np-array colnames]
   (row-major-tensor->dataset
    (py/->jvm np-array)
    colnames))
  ([np-array]
   (row-major-tensor->dataset
    (py/->jvm np-array))))


;(-> [:1 :2 :3 :45]
    ;(->> (map comment-id-colname->int))
    ;(tens/->tensor :datatype :int32)
    ;(->> (dcol/new-column :comment-id)))

;(djoin/left-join :a (ds/->dataset [{:a 1 :b 2} {:a 3 :b 4}])
                 ;(ds/->dataset [{:a 1 :c 5} {:a 3 :c 8}]))

;(py/py. (py/->python (tens/->tensor [1 2 3 4] :datatype :float64))
        ;__mul__ -1)


(py.-
 (py. (sk-decomp/PCA 2) fit
      (py/->python [[5 4 3 1] [1 2 3 4]
                    [5 2 3 7] [-1 2 6 4]]))
 components_)


(defn flip-np-rows
  [row-major-array indexes]
  (reduce
   (fn [eigs axis]
     (py/set-item! eigs axis (py. (py/get-item eigs axis) __mul__ -1)))
   row-major-array
   indexes))

(defn flip-np-cols
  [col-major-array indexes]
  (py.
   (flip-np-rows
    (py. col-major-array swapaxes 0 1)
    indexes)
   swapaxes 0 1))

;(py/
  ;(py/->python (tens/->tensor [[5 4 3 1]
                               ;[1 2 3 4]
                               ;[5 2 3 7]
                               ;[-1 2 6 4]])))

;(require 'libpython-clj2.python.copy
;(libpython-clj2.python (numpy/array [[1 2] [3 4]]))
;(libpython-clj2.python.np-array/ (numpy/array [[1 2] [3 4]]))

;(py/->python (tens/->tensor [[1 2 3] [3 4 5] [3 4 5]] :datatype :float64))
;(py. (py/->python (tens/->tensor [[1 2 3] [3 4 5] [3 4 5]] :datatype :float64))
     ;swapaxes 0 1)

;(py. (tens/->tensor [[1 2 3] [3 4 5] [3 4 5]])
     ;swapaxes 0 1)

;(py/->jvm
  ;(flip-np-cols
    ;(py/->python (tens/->tensor [[5 4 3 1]
                                 ;[1 2 3 4]
                                 ;[5 2 3 7]
                                 ;[-1 2 6 4]]))
    ;[0 2])


;(py/->jvm
  ;(flip-np-rows
    ;(py/->python [[5 4 3 1] [1 2 3 4]
                 ;[5 2 3 7] [-1 2 6 4]])
    ;[0 2]))


(defn apply-scikit-pca
  [{:as conv :keys [matrix comments]}
   {:keys [dimensions flip-axes] :or {dimensions 2}}]
  (let [np-matrix (py/->python (dtensor/dataset->tensor matrix :float64))
        ;_ (log/info np-matrix)
        sk-pca (py. (sk-decomp/PCA dimensions) fit np-matrix)
        eigenvectors (flip-np-rows (py.- sk-pca components_) flip-axes)
        ;sk-pca (py/set-item! sk-pca "components_" eigenvectors)
        sk-pca (py/set-attr! sk-pca "components_" eigenvectors)
        ;; Flipping np cols instead of the appropriate dataset cols is probably inefficient, but was
        ;; convenient
        pca-proj (-> (flip-np-cols (py. sk-pca fit_transform np-matrix) flip-axes)
                     (np-array->dataset [:pc1 :pc2]))
        comment-id-column (-> (ds/column-names matrix)
                              (->> (map comment-id-colname->int))
                              (tens/->tensor :datatype :int32)
                              (->> (dcol/new-column :comment-id)))
        comment-pc-loadings (-> (py/->jvm eigenvectors)
                                (tens/transpose [1 0])
                                (dtensor/tensor->dataset)
                                (ds/rename-columns
                                 {0 :pc1
                                  1 :pc2})
                                (ds/add-column comment-id-column))
        updated-comments (djoin/left-join
                          :comment-id
                          (dissoc comments :pc1 :pc2)
                          comment-pc-loadings)]
    (-> conv
        (assoc :pca
               {:projection pca-proj
                :eigenvectors eigenvectors
                :explained-variance (-> (py.- sk-pca explained_variance_ratio_) py/->jvm vec)})
        (update :participants ds/append-columns (ds/columns pca-proj))
        (assoc :comments (dissoc updated-comments :right.comment-id)))))
        ;(update :comments ds/append-columns (ds/columns comment-pc-loadings)))))


;; This umap is broken because missing vals don't make it through as nan as had been hoped
;(defn apply-umap
  ;[{:as conv :keys [participants]}
   ;{:keys [dimensions] :or {dimensions 2}}]
  ;(let [reducer (py/call-attr-kw umap/UMAP [] {:n_components dimensions
                                          ;:n_neighbors 10
                                          ;:metric umap_metric/sparsity_aware_dist})
        ;matrix (select-votes participants)
        ;np-matrix (py/->python (dtensor/dataset->tensor matrix :float64))
        ;embedding (np-array->dataset (py. reducer fit_transform np-matrix)
                                     ;[:umap1 :umap2])]
    ;(update conv :participants ds/append-columns (ds/columns embedding))))


(defn apply-umap
  [{:as conv :keys [participants]}
   {:keys [dimensions] :or {dimensions 2}}]
  (let [reducer (umap/UMAP :n_components dimensions
                           :n_neighbors 10
                           :metric umap_metric/sparsity_aware_dist2)
        matrix (ds/replace-missing (select-votes participants) :all :value 0)
        np-matrix (py/->python (dtensor/dataset->tensor matrix :float64))
        embedding (np-array->dataset (py. reducer fit_transform np-matrix)
                                     [:umap1 :umap2])]
    (update conv :participants ds/append-columns (ds/columns embedding))))


(defn mapseqs [ds]
  (->> ds ds/mapseq-reader (map #(into {} %))))

;(defn apply-comments-stats)

(defn apply-analysis
  [conv]
  (-> conv
      (apply-pca {:dimensions 2})
      (apply-umap {})))


(defn subset-participants
  "Filter can be a seqable of keep-pids or a function of a participant row to a truth(y) value corresponding to whether to keep the row or not.
  After subsetting participants, the votes, matrix and basic stats and summary values are updated, but PCA and other algorithms are not rerun."
  [{:as conv :keys [participants votes]} filter]
  (cond
    ;; assume a seqable of pids
    (seqable? filter)
    (subset-participants conv (comp (set filter) :participant))
    ;; assume a filter function for ds/filter
    (or (keyword? filter) (fn? filter))
    (let [keep-participants (ds/filter participants filter)
          keep-votes (filter-by-vals votes :voter-id (:participant keep-participants))
          keep-matrix (impute-means (select-votes keep-participants))]
      (-> conv
          (merge
           {:participants keep-participants
            :votes keep-votes
            :matrix keep-matrix})
          update-comment-vote-counts
          update-summary))))


(defn zero-metadata-matrix-columns
  [{:as conv :keys [matrix]} meta-ids]
  (let [colnames (map id-keyword meta-ids)
        zero-fn (fn [col]
                  (dcol/new-column (dcol/column-name col)
                                   (repeat (ds/row-count matrix) 0)))
        new-matrix (ds/update-columns matrix colnames zero-fn)]
    (assoc conv :matrix new-matrix)))


(defn subset-grouped-participants
  [conv]
  (subset-participants conv (comp not nil? :group-id)))

(defn subset-by-vote
  [{:as conv :keys [participants]} comment-id vote]
  (let [participants
        (filter-by-vals participants
                        (keyword (str comment-id))
                        (if (seqable? vote) vote [vote]))
        participant-ids (:participant participants)]
    ;; Calling to the other subset-* function ensures that the full conversation update happens
    (subset-participants conv participant-ids)))

;; subset-by-votes for multiple comment-id & val vectors, if needed...
;; * comment-id->vote mapping?


(defn get-by-id
  [conv collection id]
  (let [ds-key (case collection
                 :comments :comment-id
                 :participants :participant)]
    (-> conv
        (get collection)
        (select-by-vals ds-key [id])
        (ds/mapseq-reader)
        first)))

(defn centered-matrix
  [ds]
  (-> ds
      ;; here, we're imputing means for missing values, and then subtracting means; inefficient recomputation
      ;; of the means; could in theory be doing something smarter
      (impute-means)
      (ds/columns)
      (->> (map (fn [col] (dfn/- col (:mean (dcol/stats col [:mean]))))))
      ;; converting into a dataset just to convert to tensor seems silly and innefficient, but writing this
      ;; way for convenience (and out of ignorance of the finer workings of tech.v3.datatype
      (ds/new-dataset)
      (dtensor/dataset->tensor :float64)))


(defn mget
  [m i j]
  (-> m
      (dt/get-value i)
      (dt/get-value j)))

(defn trace
  [m]
  (let [shape (:shape (tens/tensor->dimensions m))
        n (apply min shape)]
    (->> (range n)
         (map (fn [i] (mget m i i)))
         (reduce +))))

;; this implementation will generally be much less efficient, given number of participants
;(defn rv-coefficient
  ;[ds1 ds2]
  ;(when (or (> (ds/row-count ds1) 1)
            ;(> (ds/row-count ds2) 1))
    ;(let [x (centered-matrix ds1)
          ;y (centered-matrix ds2)
          ;x' (tens/transpose x [1 0])
          ;y' (tens/transpose y [1 0])
          ;xx' (tens/matrix-multiply x x')
          ;yy' (tens/matrix-multiply y y')]
      ;(/ (trace (tens/matrix-multiply xx' yy'))
         ;(Math/sqrt
           ;(* (trace (tens/matrix-multiply xx' xx'))
              ;(trace (tens/matrix-multiply yy' yy'))))))))

(defn rv-coefficient
  [ds1 ds2]
  (when (and (> (ds/column-count ds1) 1)
             (> (ds/column-count ds2) 1))
    (let [x (centered-matrix ds1)
          y (centered-matrix ds2)
          x' (tens/transpose x [1 0])
          y' (tens/transpose y [1 0])
          x'x (matrix/mmul x' x)
          x'y (matrix/mmul x' y)
          y'y (matrix/mmul y' y)
          y'x (matrix/mmul y' x)]
      (/ (trace (matrix/mmul x'y y'x))
         (Math/sqrt
          (* (trace (matrix/mmul x'x x'x))
             (trace (matrix/mmul y'y y'y))))))))


;(rv-coefficient
  ;(dtensor/row-major-tensor->dataset
    ;(tens/->tensor [[1.5 2.8 3] [3 4 4] [1 3.2 9.2]]))
  ;(dtensor/row-major-tensor->dataset
    ;(tens/->tensor [[-2.3 4.2] [10.8 3.2] [3 0.1]])))
;; => 0.217975; same as with r code below
;;
;;    library(FactoMineR)
;;
;;    data(wine)
;;    x <- c(1.5, 3, 1)
;;    y <- c(2.8, 4, 3.2)
;;    z <- c(3,   4, 9.2)
;;    d1 <- data.frame(x, y, z)
;;
;;    x <- c(-2.3, 10.8, 3)
;;    y <- c(4.2, 3.2, 0.1)
;;    z <- c(3.8, -8, 3.4)
;;    d2 <- data.frame(x, y, z)
;;
;;    coeffRV(d1, d2)))

(defn filter-by-topic
  [comments topic]
  (ds/filter
   comments
   (fn [{:keys [topics]}]
     (get topics topic))))

(defn- comment-colnames-by-topic
  [comments topic]
  (map (comp keyword str) (:comment-id (filter-by-topic comments topic))))

(defn topical-rv-analysis
  ([{:as conv :keys [comments matrix]}]
   (let [topics (reduce set/union (:topics comments))
         cat-pairs (set (for [c1 topics
                              c2 topics
                              :when (not= c1 c2)]
                          (sort [c1 c2])))
         results
         (->> cat-pairs
              (map
               (fn [[cat1 cat2]]
               ;; when let strips out topics for which dim < 2
                 (when-let [rv-coeff (topical-rv-analysis conv cat1 cat2)]
                   [{:topic1 cat1 :topic2 cat2 :rv-coefficient rv-coeff}
                    {:topic1 cat2 :topic2 cat1 :rv-coefficient rv-coeff}])))
              (apply concat)
              (vec))
         non-null-topics (set (map :topic1 results))]
     (concat results
             ;; rv-coefficient with self is always 1, but again, only want dim > 1, as above
             (map (fn [topic]
                    {:topic1 topic :topic2 topic :rv-coefficient 1})
                  non-null-topics))))
  ;; Function for computing for a specific pair of topics
  ([{:keys [comments matrix]} cat1 cat2]
   (let [cat1-comments (comment-colnames-by-topic comments cat1)
         cat2-comments (comment-colnames-by-topic comments cat2)
         cat1-mat (ds/select-columns matrix cat1-comments)
         cat2-mat (ds/select-columns matrix cat2-comments)
         rv-coeff (rv-coefficient cat1-mat cat2-mat)]
     rv-coeff)))


(defn window-select
  "Filter a dataset based on a window map of colname->[min,max]"
  [ds window]
  (ds/filter
   ds
   (fn [row]
     (every?
      (fn [[colname val-range]]
        (let [[min-val max-val] (sort val-range)]
          (<= min-val (get row colname) max-val)))
      window))))



(defn binned-votes
  ([{:keys [votes participants]} {:keys [interval-in-hours split-by-keys agg-fn]}]
   (let [interval-in-hours (or interval-in-hours 1)
         interval-ms (int (* interval-in-hours 60 60 1000))
         ptpt-split-keys (set/intersection (set (keys participants)) (set split-by-keys))
         votes (if (seq ptpt-split-keys)
                 (djoin/left-join [:voter-id :participant]
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
                 (djoin/left-join [:author-id :participant]
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

(defn round
  [x dec-places]
  (let [tens (Math/pow 10 dec-places)]
    (double (/ (int (* x tens)) tens))))

(defn agree-prob
  [votes-df]
  (let [n-votes (ds/row-count votes-df)
        n-agree (count (filter #{1} (:vote votes-df)))]
    (round
     (/ (+ 1. n-agree)
        (+ 2. n-votes))
     5)))


(defn column-map
  "Like ds/column-map, except infers output types from return values of f (rather than assuming widened union type of input types).*
  Also relaxes assumptions about missing values, letting f decide what to do.
  If input colnames are passed (as with the signature of ds/column-map), f should take a number of arguments equal to the number of columns selected, and will likely be more efficient.
  If no input colnames are passed, then f must accept maps (via ds/mapseq-reader), and may be less performant.

  * actually, this is false at the moment; simply uses object, but would like to get inference in without having to use maps as intermediaries, but not sure what the right inference approach would be yet...
  "
  ([dataset out-colname f]
   (let [new-column (->> (ds/mapseq-reader dataset)
                         (map f)
                         (dcol/new-column out-colname))]
     (ds/append-columns
      dataset
      [new-column])))
  ([dataset out-colname f colname & colnames]
   (let [new-column (->> (map dataset (concat [colname] colnames))
                         (apply map f)
                         (dcol/new-column out-colname))]
     (ds/append-columns
      dataset
      [new-column]))))


;; quick test
;(column-map (ds/->dataset [{:a 1 :b 2 :f "george"} {:a 3 :b 4 :f "wallace"}])
            ;:name
            ;str
            ;:a :b)

;; repling my way towards inference?
;(ds/->dataset)
;(require '[tech.v3.dataset :as tms])
;(tms/map->row)
;(require '[tech.v3.datatype])

(s/def ::comment-id any?)
(s/def ::author-id? any?)

(s/def ::participants
  (s/keys :req-un [::participant]))
(s/def ::comments
  (s/keys :req-un [::comment-id ::author-id ::moderated]))

(s/def ::conv
  (s/keys :req-un [::participants ::comments]))

(s/def ::conv-or-participants
  (s/or :conv ::conv
        :participants ::participants))


(s/conform ::conv-or-participants {:participants {:participant [1 2 3]}
                                   :comments {:comment-id [1 2] :author-id [2 3] :moderated [-1 0]}})


(defn label-groups
  ([data]
   ;; TODO This should really be setting appropriate datatype, so that we aren't consuming more memory
   ;; than necessary (especially since will tend to be strings)
   (label-groups data {}))
  ([data {:as opts :keys [labels group-key group-key-out]}]
   (assoc data :participants (participants/label-groups (:participants data) opts))))


;; Should group be a group-id,
(defn relative-rate-ratio
  ([{:as conv :keys [participants]} group-id comment-id]
   (let [in-fn (fn [row] (= group-id (:group-id row)))
         out-fn (fn [row] (not= group-id (:group-id row)))
         in-group (ds/filter participants in-fn)
         out-group (ds/filter participants out-fn)]
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

(defn get-vote
  [{:keys [participants]} participant-id comment-id]
  (let [ptpt-row (filter-by-vals participants :participant [participant-id])]
    (when (= (ds/row-count ptpt-row) 1)
      (first (get ptpt-row (id-keyword comment-id))))))

;(relative-rate-ratio analysis :pro-uber 38)
;(relative-rate-ratio analysis :pro-uber 62)

;; Hmnm... realizing it's really inefficient for the function above to do the group ds splitting; probably
;; much better to be able to do this once below, and then pass these in/out groups to the fn above
(defn comments-by-rrr
  ([{:as conv :keys [comments participants]} group-id {:keys [include-meta]}]
   (let [in-fn (fn [row] (= group-id (:group-id row)))
         out-fn (fn [row] (not= group-id (:group-id row)))
         in-group (ds/filter participants in-fn)
         out-group (ds/filter participants out-fn)]
     (-> comments
         (ds/sort-by (comp - (partial relative-rate-ratio conv in-group out-group) :comment-id)))))
  ([conv group-id]
   (comments-by-rrr conv group-id {})))

;(->> (comments-by-rrr analysis 0)
     ;(ds/head 15))


(defn apply-category-from-metadata
  "Applies to participants a category column (named via `:key` opt) defined based on responses to the metadata comments specified in the `:categories`.
  `:categories` is a map of metadata ids to the matching category labels for participants who responded affirmatively to said comment."
  [{:as conv :keys [comments participants]}
   {:keys [categories key exclusive]}]
  (let [metadata-ids (map id-keyword (keys categories))
        label-fn (fn [& votes]
                   (->> categories
                        (map (fn [vote [cmnt-id label]]
                               (when (= 1 vote)
                                 label))
                             votes)
                        (filter identity)
                        set))
        label-fn (if exclusive
                   (comp
                    (fn [labels]
                      (when (> 2 (count labels))
                        (first labels)))
                    label-fn)
                   label-fn)
        participants (apply column-map participants key label-fn metadata-ids)]
    (assoc conv :participants participants)))


(defn wilcoxon-test [a-vals b-vals]
  {:method "Wilcoxon rank-sum"
   :p-value
   (-> (MannWhitneyUTest.)
       (.mannWhitneyUTest (double-array a-vals) (double-array b-vals)))})

(defn g-test [a-counts b-counts]
  {:mehod "G-test"
   :p-value
   (-> (GTest.)
       (.gTestDataSetsComparison (long-array a-counts) (long-array b-counts)))})

(defn t-test [a-vals b-vals]
  {:method "T-test"
   :p-value
   (-> (TTest.)
       (.tTest (double-array a-vals) (double-array b-vals)))})

(defn kendall-test
  [a-vals b-vals]
  (let [result
        (CorTest/kendall (double-array a-vals)
                         (double-array b-vals))]
    {:method (.method result)
     :p-value (.pvalue result)
     :cor (.cor result)}))


(defn dims
  [ds]
  [(ds/row-count ds) (ds/column-count ds)])


;; PIckup TODO

;(defn load-conv [{:keys []}]
  ;(let [votes (db/execute!)]))
