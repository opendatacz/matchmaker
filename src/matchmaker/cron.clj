(ns matchmaker.cron
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.lib.util :refer [format-date-time]]
            [clj-time.core :as clj-time]
            [com.stuartsierra.component :as component]
            [cronj.core :as cronj]))

;; ----- Private functions -----

(defn- get-creation-date-time
  "Get date time of creation given age in minutes (@age-minutes)."
  [age-minutes]
  {:pre [(integer? age-minutes)
         (pos? age-minutes)]}
  (format-date-time (clj-time/minus (clj-time/now)
                                    (clj-time/minutes age-minutes))))

(defn- get-metadata-graph
  "Get the contents of matchmaker's metadata graph."
  [sparql-endpoint]
  (sparql/read-graph sparql-endpoint (:metadata-graph sparql-endpoint)))

(defn- test-delete-old-graphs
  "Test if all old graphs were propertly deleted."
  [opts]
  (let [max-date-time (get-creation-date-time (:max-age-minutes opts))
        metadata-graph (get-in opts [:sparql-endpoint :metadata-graph])]
    (sparql/sparql-assert (:sparql-endpoint opts)
                          false?
                          "Old graphs weren't properly deleted."
                          ["cron" "test_delete_old_graphs"]
                          :data {:max-date-time max-date-time
                                 :metadata-graph metadata-graph})))

;; ----- Public functions -----

(defn delete-metadata-graph
  "Delete matchmaker's metadata graph."
  [sparql-endpoint]
  (sparql/delete-graph sparql-endpoint (:metadata-graph sparql-endpoint)))

(defn delete-all-graphs
  "Delete all graph's recorded in matchmaker's metadata graph."
  [sparql-endpoint]
  (let [all-graphs (sparql/select-1-variable sparql-endpoint
                                             :graph
                                             ["cron" "get_all_graphs"]
                                             :data {:metadata-graph (:metadata-graph sparql-endpoint)})]
    (doall (map (partial sparql/delete-graph sparql-endpoint) all-graphs))))

(defn delete-old-graphs
  "Delete named graphs older than @max-age-minutes provided in @opts."
  [t opts]
  {:pre [(integer? (:max-age-minutes opts))]
   :post [(test-delete-old-graphs opts)]}
  (let [max-date-time (get-creation-date-time (:max-age-minutes opts)) 
        metadata-graph (get-in opts [:sparql-endpoint :metadata-graph])
        old-graphs (sparql/select-1-variable (:sparql-endpoint opts)
                                             :graph
                                             ["cron" "get_old_graphs"]
                                             :data {:max-date-time max-date-time
                                                    :metadata-graph metadata-graph})]
    (when-not (empty? old-graphs)
      (timbre/debug (format "Deleting %d old graph(s)." (count old-graphs))) 
      (doall (map (partial sparql/delete-graph (:config opts)) old-graphs))
      (sparql/sparql-update (:config opts)
                            ["cron" "delete_records_of_old_graphs"]
                            :data {:metadata-graph metadata-graph
                                   :old-graphs old-graphs}))))

;; ----- Tasks -----

(defn delete-old-graphs-task
  [config sparql-endpoint]
  (let [max-age-minutes (get-in config [:cron :delete-old-graphs :max-age-minutes])]
    {:id "delete-old-graphs-task"
     :handler delete-old-graphs
     :schedule (format "0 /%d * * * * *" max-age-minutes)
     :opts {:max-age-minutes max-age-minutes
            :sparql-endpoint sparql-endpoint}}))

;; ----- Components -----

(defrecord Cron []
  component/Lifecycle
  (start [cron] (let [entries [(delete-old-graphs-task (:config cron)
                                                       (:sparql-endpoint cron))]
                      cj (cronj/cronj :entries entries)] 
                  (cronj/start! cj)
                  (assoc cron :cron cj)))
  (stop [cron] (cronj/stop! (:cron cron))
               (delete-metadata-graph (:sparql-endpoint cron)))) 
