(ns polis.translate
  (:require [google-translate.core :as gt])
  (:import [com.google.cloud.translate TranslateOptions]))

;; Have to install our api key manually, since we don't do this the normal way
(alter-var-root
 (var gt/*get-service-method*)
 (fn [_]
   (fn []
     (-> (TranslateOptions/newBuilder)
         (.setApiKey (System/getenv "GOOGLE_API_KEY"))
         (.build)
         (.getService)))))
