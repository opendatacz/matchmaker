(ns matchmaker.benchmark.core
  (:require [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.lib.util :refer [init-logger]]
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

(defrecord
  ^{:doc "Setup and teardown a benchmark according to @config."}
  Benchmark []
  component/Lifecycle
  (start [{:keys [sparql-endpoint]
           :as benchmark}]
    (init-logger)
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
  "Constructs benchmark for @matchmaking-endpoint (URL), 
  setting up and tearing down whole benchmark.
  May run multiple times, if @number-of-times is provided."
  ([matchmaking-endpoint]
    (let [benchmark (load-benchmark)]
      (try
        (evaluate/evaluate-rank benchmark matchmaking-endpoint)
        (finally (component/stop benchmark)))))
  ([matchmaking-endpoint
    ^Integer number-of-runs]
    (doall (repeatedly number-of-runs #(compute-benchmark matchmaking-endpoint)))))
 
(defn run-benchmark
  "Wrapper function to run benchmark for given @matchmaking-endpoint (URL).
  Aggregate benchmark results using @aggregation-fn and format them using @formatting-fn."
  [matchmaking-endpoint number-of-runs]
  (flatten (compute-benchmark matchmaking-endpoint number-of-runs)))

(comment
  (def results (run-benchmark config "http://localhost:3000/match/contract/to/business-entity"))
  (def results (run-benchmark config "http://lod2.vse.cz:8080/matchmaker/match/contract/to/business-entity"))
  (evaluate/compute-avg-rank-metrics config results)
  (view (evaluate/top-n-curve-chart results))
  (save (evaluate/top-n-curve-chart results)
        "diagrams/fused_vvz_data_exact_CPV.png"
        :width 1000
        :height 800)
  )
