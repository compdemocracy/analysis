(ns polis.participants
  (:require [clojure.spec.alpha :as s]
            [tech.v3.dataset :as ds]
            [tech.v3.dataset.column :as dcol]))


(defn label-groups
  ([participants]
   (label-groups participants {}))
  ([participants {:as opts :keys [labels group-key group-key-out]}]
   (let [group-key (or group-key :group-id)
         group-key-out (or group-key-out group-key)
         labels (cond
                  ;; vectors have to use get, since groups id ints come out with different precision from
                  ;; tech.ml
                  (vector? labels) (partial get labels)
                  ;; otherwise, accept labels as a map or function like thing
                  labels           labels
                  ;; or just contruct a map if we have nothing to work from
                  :else
                  (into {} (map vector
                                (range)
                                (map (partial str "group-") "abcdefghijklmnopqrstuvwxyz"))))
         new-column (dcol/new-column group-key-out
                                     (map labels (get participants group-key)))]
     (assoc participants group-key-out new-column))))

