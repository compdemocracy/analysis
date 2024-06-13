(ns polis.math
  "Core Polis data analysis API, implemented with tech.ml.dataset and libpython-clj for python interop."
  (:require [tech.ml.dataset :as ds]
            [tech.ml.dataset.pca :as dpca]
            [tech.ml.dataset.column :as dcol]
            [tech.ml.dataset.tensor :as dtensor]
            [tech.v2.datatype :as dt]
            [tech.v2.tensor :as tens]
            [tech.v2.datatype.functional :as dfn]
            [libpython-clj.require :refer [require-python]]
            [libpython-clj.python :as py :refer [py. py.. py.-]]
            [semantic-csv.core :as csv]
            [fastmath.stats :as fast-stats]
            [taoensso.timbre :as log]
            [oz.core :as oz]
            [clojure.set :as set]
            [kixi.stats.core :as kixi]))

;; Require python libraries

(try
  (require-python '[sklearn.datasets :as sk-data]
                  '[sklearn.model_selection :as sk-model]
                  '[sklearn.decomposition :as sk-decomp]
                  '[numpy :as numpy]
                  '[numba :as numba]
                  '[pandas :as pandas]
                  '[umap :as umap]
                  '[umap_metric :as umap_metric :reload])
  (catch Throwable t))

;; Implement some basic column statistics

(defn column-stats
  [dataset]
  (->> dataset
    ds/brief
    (map (fn [stats]
           [(:col-name stats) stats]))
    (into {})))

;(csv/->int "4")

(defn vote-column-names [df]
  (filter
    (comp (partial re-matches #"^?\d*$") name)
    (ds/column-names df)))

(defn without-votes
  [participants-ds]
  (ds/remove-columns participants-ds (vote-column-names participants-ds)))

(defn select-votes
  [participants-ds]
  (ds/select-columns participants-ds (vote-column-names participants-ds)))

(defn viz-values
  [df]
  (->> df ds/mapseq-reader (map #(into {} %))))

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
  [ds colname vals]
  (let [filter-fn (comp (set vals) #(get % colname))]
    (ds/filter filter-fn [colname] ds)))


(defn remove-by-vals
  [ds colname vals]
  (let [filter-fn (comp not (set vals) #(get % colname))]
    (ds/filter filter-fn [colname] ds)))

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
        keep-comments (ds/filter filter-fn [:moderated] comments)
        keep-comment-ids (set (:comment-id keep-comments))]
    (filter-comments conv keep-comment-ids)))


;(defn vote-count-filter
  ;[{:as conv :keys [summary comments votes participants]} min-votes]
  ;(let [keep-participants
        ;(ds/filter (comp (partial <= min-votes) :n-votes)
                   ;[:n-votes]
                   ;participants)]
    ;(update-summary
      ;{})))


(defn load-data
  [export-dir]
  (let [load-data (partial file-dataset export-dir)
        ptpts-ds (load-data "participants-votes.csv")]
    (update-summary
      {:summary (load-summary (str export-dir "/summary.csv"))
       :comments (load-data "comments.csv")
       :votes (load-data "votes.csv")
       :participants ptpts-ds
       :matrix (-> ptpts-ds select-votes impute-means)})))

(defn explained-variance
  [eigenvals]
  (let [sum (reduce + eigenvals)]
    (map (fn [eig] (/ eig sum)) eigenvals)))

(defn apply-pca
  [{:as conv :keys [matrix]}
   {:keys [dimensions flip-axes] :or {dimensions 2}}]
  (let [pca-results (dpca/pca-dataset matrix)
        pca-proj (dpca/pca-transform-dataset matrix pca-results dimensions :float64)
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
   (ds/rename-columns (dtensor/row-major-tensor->dataset tensor)
                      (into {} (map vector (range) colnames))))
  ([tensor]
   (dtensor/row-major-tensor->dataset tensor)))

(defn np-array->dataset
  "Takes a row-major numpy tensor and casts as a dataset with given colnames"
  ([np-array colnames]
   (row-major-tensor->dataset
     (py/->jvm np-array)
     colnames))
  ([np-array]
   (row-major-tensor->dataset
     (py/->jvm np-array))))

(defn apply-scikit-pca
  [{:as conv :keys [matrix]}
   {:keys [dimensions] :or {dimensions 2}}]
  (let [np-matrix (py/->numpy (dtensor/dataset->row-major-tensor matrix :float64))
        sk-pca (py. (sk-decomp/PCA dimensions) fit np-matrix)
        pca-proj (np-array->dataset (py. sk-pca fit_transform np-matrix)
                                    [:pc1 :pc2])
        eigenvectors (py.- sk-pca components_)
        comment-pc-loadings (ds/rename-columns
                              (dtensor/column-major-tensor->dataset
                                (py/->jvm eigenvectors))
                              {0 :pc1
                               1 :pc2})]
    (-> conv
        (assoc :pca
               {:projection pca-proj
                :eigenvectors eigenvectors
                :explained-variance (py.- sk-pca explained_variance_ratio_)})
        (update :participants ds/append-columns (ds/columns pca-proj))
        (update :comments ds/append-columns (ds/columns comment-pc-loadings)))))

;; This umap is broken because missing vals don't make it through as nan as had been hoped
;(defn apply-umap
  ;[{:as conv :keys [participants]}
   ;{:keys [dimensions] :or {dimensions 2}}]
  ;(let [reducer (py/call-kw umap/UMAP [] {:n_components dimensions
                                          ;:n_neighbors 10
                                          ;:metric umap_metric/sparsity_aware_dist})
        ;matrix (select-votes participants)
        ;np-matrix (py/->numpy (dtensor/dataset->row-major-tensor matrix :float64))
        ;embedding (np-array->dataset (py. reducer fit_transform np-matrix)
                                     ;[:umap1 :umap2])]
    ;(update conv :participants ds/append-columns (ds/columns embedding))))


(defn apply-umap
  [{:as conv :keys [participants]}
   {:keys [dimensions] :or {dimensions 2}}]
  (let [reducer (py/call-kw umap/UMAP [] {:n_components dimensions
                                          :n_neighbors 10
                                          :metric umap_metric/sparsity_aware_dist2})
        matrix (ds/replace-missing (select-votes participants) :all :value 0)
        np-matrix (py/->numpy (dtensor/dataset->row-major-tensor matrix :float64))
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
  [{:as conv :keys [participants votes]} keep-pids]
  (let [keep-participants (filter-by-vals participants :participant keep-pids)
        keep-votes (filter-by-vals votes :voter-id keep-pids)
        keep-matrix (impute-means (select-votes keep-participants))]
    (update-summary
      (merge conv
        {:participants keep-participants
         :votes keep-votes
         :matrix keep-matrix}))))

(defn subset-grouped-participants
  [{:as conv :keys [participants]}]
  (let [keep-participants
        (->> participants
             (ds/filter (comp not nil? :group-id)
                        [:group-id]))
        keep-pids (set (:participant keep-participants))]
    (subset-participants conv keep-pids)))


(defn centered-matrix
  [ds]
  (-> ds
      ;; here, we're imputing means for missing values, and then subtracting means; inefficient recomputation
      ;; of the means; could in theory be doing something smarter
      (impute-means2)
      (ds/columns)
      (->> (map (fn [col] (dfn/- col (:mean (dcol/stats col [:mean]))))))
      ;; converting into a dataset just to convert to tensor seems silly and innefficient, but writing this
      ;; way for convenience (and out of ignorance of the finer workings of tech.v2.datatype
      (ds/new-dataset)
      (dtensor/dataset->row-major-tensor :float64)))


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
          x'x (tens/matrix-multiply x' x)
          x'y (tens/matrix-multiply x' y)
          y'y (tens/matrix-multiply y' y)
          y'x (tens/matrix-multiply y' x)]
      (/ (trace (tens/matrix-multiply x'y y'x))
         (Math/sqrt
           (* (trace (tens/matrix-multiply x'x x'x))
              (trace (tens/matrix-multiply y'y y'y))))))))


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
    (fn [{:keys [topics]}]
      (get topics topic))
    [:topics]
    comments))

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
    (fn [row]
      (every?
        (fn [[colname val-range]]
          (let [[min-val max-val] (sort val-range)]
            (<= min-val (get row colname) max-val)))
        window))
    (keys window)
    ds))
