(ns polis.math
  "Core Polis data analysis API, implemented with tech.ml.dataset and libpython-clj for python interop."
  (:require [tech.ml.dataset :as ds]
            [tech.ml.dataset.pca :as dpca]
            [tech.v2.datatype :as dt]
            [tech.ml.dataset.tensor :as dtensor]
            [libpython-clj.require :refer [require-python]]
            [libpython-clj.python :as py :refer [py. py.. py.-]]
            [semantic-csv.core :as csv]
            [oz.core :as oz]))

;; Require python libraries

(require-python '[sklearn.datasets :as sk-data]
                '[sklearn.model_selection :as sk-model]
                '[numpy :as numpy]
                '[numba :as numba]
                '[pandas :as pandas]
                '[umap :as umap])

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


;; This could likely be made much more performant
(defn impute-means
  "Returns a new dataset with imputed means"
  [dataset]
  (let [stats (column-stats dataset)]
    (-> dataset
        (ds/mapseq-reader)
        (->> (pmap
               (fn [row]
                 (->> row
                      (map
                        (fn [[k v]]
                          (if v
                            [k v]
                            [k (:mean (get stats k))])))
                      (into {})))))
        (ds/->dataset)
        (ds/select-columns
          (sort-by (comp csv/->int name) (vote-column-names dataset))))))


(defn- file-dataset
  [export-dir filename]
  (ds/->dataset (str export-dir "/" filename)
    {:key-fn keyword}))

(defn load-data
  [export-dir]
  (let [load-data (partial file-dataset export-dir)
        ptpts-ds (load-data "participants-votes.csv")]
    {:summary (first (load-data "summary.csv"))
     :comments (load-data "comments.csv")
     :votes (load-data "votes.csv")
     :participants ptpts-ds
     :matrix (-> ptpts-ds select-votes impute-means)}))

(defn apply-pca
  [{:as conv :keys [matrix]}
   {:keys [dimensions] :or {dimensions 2}}]
  (let [pca-results (dpca/pca-dataset matrix)
        pca-proj (dpca/pca-transform-dataset matrix pca-results dimensions :float64)
        pca-proj (ds/rename-columns pca-proj {0 :pc1 1 :pc2})]
    (-> conv
        (assoc :pca 
               (assoc pca-results :projection pca-proj))
        (update :participants ds/append-columns (ds/columns pca-proj)))))


(defn apply-umap
  [{:as conv :keys [matrix]}
   {:keys [dimensions] :or {dimensions 2}}]
  (let [reducer (py/call-kw umap/UMAP [] {:n_components dimensions})
        np-matrix (py/->numpy (dtensor/dataset->row-major-tensor matrix :float64))
        embedding (py. reducer fit_transform np-matrix)]
    (assoc conv :umap embedding)))


