(ns polis.utils
  (:require [dk.ative.docjure.spreadsheet :as xl]
            [taoensso.timbre :as log]))


;(->> (xl/load-workbook (str data-dir "/comments-with-theme-labels.xlxs"))
     ;(xl/sheet-seq)
     ;;(map (comp gt/translate! xl/sheet-name))
     ;(map xl/sheet-name)
     ;(map (fn [sheet-name]
            ;[sheet-name (gt/translate! sheet-name)]))
     ;(into {})))

;(->> (xl/load-workbook (str data-dir "/comments-with-theme-labels.xlxs"))
     ;(xl/sheet-seq)
     ;first
     ;(xl/row-seq)
     ;second
     ;(map xl/read-cell))

(defn try-int
  [x]
  (try (int x)
       (catch Throwable t nil)))

(defn xlsx-comment-metadata
  [filename {:keys [sheet-name sheet-index columns cast-fns]}]
  (let [cast-fns (merge {:comment-id try-int} cast-fns)
        ws (xl/load-workbook filename)
        sheet (case
                sheet-name (xl/select-sheet sheet-name ws)
                sheet-index (nth (xl/sheet-seq ws) sheet-index)
                :else (first (xl/sheet-seq ws)))
        row-seq (xl/row-seq sheet)
        header (map xl/read-cell (first row-seq))
        column-map (->> columns
                        (map (fn [[output-key input-key]]
                               [(.indexOf header input-key) output-key])))]
    (->> (rest row-seq)
         (map (comp (partial map xl/read-cell)
                    xl/cell-seq))
         (map (fn [row]
                (try
                  (->> column-map
                       (map (fn [[i output-key]]
                              (let [val (nth row i)]
                                [output-key
                                 (if-let [caster (get cast-fns output-key)]
                                   (caster val)
                                   val)])))
                       (into {}))
                  (catch Throwable _
                    (log/error "Unable to log row:" (vec row))
                    nil))))
         (filter identity))))


