(ns matchmaker.tasks
  (:require [taoensso.timbre :as timbre]
            [matchmaker.common.config :refer [config]]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.lib.util :refer [format-date-time]]
            [clj-time.core :as clj-time]))

; Private functions

(defn- get-metadata-graph
  "Get the contents of matchmaker's metadata graph."
  []
  (let [metadata-graph (get-in config [:data :metadata-graph])]
    (sparql/read-graph config metadata-graph)))

; Public functions

(defn delete-metadata-graph
  "Delete matchmaker's metadata graph."
  []
  (let [metadata-graph (get-in config [:data :metadata-graph])]
    (sparql/delete-graph config metadata-graph)))

(defn delete-old-graphs
  "Delete named graphs older than @max-age-minutes."
  [max-age-minutes]
  {:pre [(integer? max-age-minutes)]}
  (let [max-date-time (format-date-time (clj-time/minus (clj-time/now)
                                                        (clj-time/minutes max-age-minutes)))
        metadata-graph (get-in config [:data :metadata-graph])
        old-graphs (map :graph (sparql/select-query config
                                                    ["tasks" "get_old_graphs"]
                                                    :data {:max-date-time max-date-time
                                                          :metadata-graph metadata-graph}))]
    (do (doall (map (partial sparql/delete-graph config) old-graphs))
        (sparql/sparql-update config
                              ["tasks" "delete_records_of_old_graphs"]
                              :data {:metadata-graph metadata-graph
                                      :old-graphs old-graphs}))))
