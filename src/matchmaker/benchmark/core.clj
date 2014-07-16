(ns matchmaker.benchmark.core
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.lib.sparql :refer [->SparqlEndpoint]]
            [matchmaker.benchmark.setup :as setup]
            [matchmaker.benchmark.evaluate :as evaluate]
            [matchmaker.benchmark.teardown :as teardown]
            [com.stuartsierra.component :as component]
            [incanter.core :refer [save view]]))

(declare ->Benchmark)

; ----- Private functions -----

(defn- format-number
  "Returns @number formatted as float-like string."
  [number]
  (if (number? number)
      (format "%f" (double number))
      number))

(defn- format-numbers
  "Formats numeric values in @results."
  [results]
  (reduce (fn [result [k v]] (assoc result k (format-number v))) {} results))

(defn- load-benchmark
  "Setup benchmark system with its dependencies"
  []
  (let [config-file-path (:matchmaker-config env)
        benchmark-system (component/system-map
                           :config (->Config config-file-path)
                           :sparql-endpoint (component/using (->SparqlEndpoint) [:config])
                           :benchmark (component/using (->Benchmark) [:config :sparql-endpoint]))]
    (component/start benchmark-system)))

; Records

(defrecord Benchmark []
  component/Lifecycle
  (start [{:keys [sparql-endpoint]
           :as benchmark}]
    (timbre/debug "Starting benchmark...")
    (setup/sufficient-data? sparql-endpoint)
    (setup/load-contracts sparql-endpoint)
    (setup/delete-awarded-tenders sparql-endpoint)
    (assoc benchmark :correct-matches (setup/load-correct-matches sparql-endpoint)))
  (stop [{:keys [sparql-endpoint]
          :as benchmark}]
    (timbre/debug "Stopping benchmark...")
    (teardown/return-awarded-tenders sparql-endpoint)
    (teardown/clear-graph sparql-endpoint)
    benchmark))

; Public functions

(defn format-results
  "Aggregate and format @benchmark-results."
  [benchmark-results]
  (format-numbers (evaluate/avg-metrics benchmark-results)))

(defn compute-benchmark
  "Constructs benchmark of @matchmaker (String) on @matchmaking-endpoint (URL), 
  setting up and tearing down whole benchmark.
  May run multiple times, if @number-of-times is provided."
  ([matchmaking-endpoint matchmaker]
    (let [benchmark (load-benchmark)]
      (try
        (evaluate/evaluate-rank benchmark
                                matchmaking-endpoint
                                matchmaker)
        (finally (component/stop benchmark)))))
  ([matchmaking-endpoint
    matchmaker
    ^Integer number-of-runs]
    (flatten (doall (repeatedly number-of-runs
                                #(compute-benchmark matchmaking-endpoint matchmaker))))))
