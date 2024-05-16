(ns polis.db
  (:require [babashka.pods :as pods]
            [clojure.pprint :as pp]
            [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [java-time :as t]))

(pods/load-pod 'org.babashka/postgresql "0.1.0")

(require '[pod.babashka.postgresql :as pg]
         '[honey.sql :as hsql]
         '[honey.sql.helpers :as hsqlh])

(def db-url
  (System/getenv "DATABASE_URL"))

(defn heroku-url-spec [db-url]
  (let [[_ user password host port db]
        (re-matches #"postgres://(?:(.+):(.*)@)?([^:]+)(?::(\d+))?/(.+)" db-url)]
    {:dbtype "postgresql"
     :host host
     :dbname db
     :port (or port 80)
     :user user
     :password password}))

(defn execute-sql! [args]
  (println "Executing sql:" args)
  (pg/execute!
    (heroku-url-spec (System/getenv "DATABASE_URL"))
    args))
    
(defn execute!
  [query-or-command]
  (execute-sql! (hsql/format query-or-command)))

;(execute! {:select :* :from :votes})

(defn zinvite->zid
  "Returns the zid given a valid zinvite string"
  [zinvite]
  (:zinvites/zid (first (execute! {:select [:*] :from [:zinvites] :where [:= :zinvite zinvite]}))))

(defn args-zid
  "Given a set of CLI/api arguments as a map, returns the zid for the zinvite or zid as appropriate."
  [{:keys [zinvite zid]}]
  (or zid
     (zinvite->zid zinvite)))
  
(defn add-where
  "Add a where clause to a honeysql query map"
  [query clause]
  (if (:where query)
    (update query :where (fn [clauses] [:and clauses clause]))
    (assoc query :where clause)))

(defn- time-in-ms
  "Converts a time value to a ms-timestamp; prefer ms-timestamp function below"
  [t]
  (.toEpochMilli (t/instant t)))

(defn year-in-ms
  "Returns the ms-timestamp (since epoch) for the start of a given year "
  [year]
  (time-in-ms (t/zoned-date-time year)))


