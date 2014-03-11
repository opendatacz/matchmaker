(ns matchmaker.benchmark.core
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.sparql :refer [ping-endpoint select-query]]
            [matchmaker.benchmark.setup :as setup]
            [matchmaker.benchmark.evaluate :as evaluate]
            [matchmaker.benchmark.teardown :as teardown]
            [matchmaker.core.sparql :refer [create-matchmaker match-contract-basic-cpv]]
            [com.stuartsierra.component :as component]))

; Private functions

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

(defn- load-correct-matches
  "Load correct contract-supplier pairs into a map"
  [config]
  (let [sparql-results (select-query config ["benchmark" "evaluate" "load_correct_matches"])]
    (into {} (:results sparql-results))))

; Records

(defrecord
  ^{:doc "Setup and teardown a benchmark according to @config."}
  Benchmark [config]
  component/Lifecycle
  (start [benchmark] (do (timbre/debug "Starting benchmark...")
                         (ping-endpoint config)
                         (setup/load-contracts config)
                         (setup/delete-awarded-tenders config)
                         (assoc benchmark :correct-matches (load-correct-matches config))))
  (stop [benchmark] (do (timbre/debug "Stopping benchmark...")
                        (teardown/return-awarded-tenders config)
                        (teardown/clear-graph config)
                        benchmark)))

; Public functions

(defn format-results
  "Aggregate and format @benchmark-results."
  [benchmark-results]
  (format-numbers (evaluate/avg-metrics benchmark-results)))

(defn compute-benchmark
  "Constructs benchmark from @config and tests @matchmaking-fn,
  setting up and tearing down whole benchmark.
  May run multiple times, if @number-of-times is provided."
  ([config matchmaking-fn]
    (let [benchmark (component/start (->Benchmark config))]
      (try
        (evaluate/evaluate-rank config matchmaking-fn (:correct-matches benchmark))
        (finally (component/stop benchmark)))))
  ([config matchmaking-fn
    ^Integer number-of-runs]
    (doall (repeatedly number-of-runs #(compute-benchmark config matchmaking-fn)))))
 
(defn run-benchmark
  "Wrapper function to run benchmark for given @matchmaking-fn.
  Aggregate benchmark results using @aggregation-fn and format them using @formatting-fn."
  [config matchmaking-fn & {:keys [aggregation-fn formatting-fn]
                            :or {aggregation-fn identity
                                 formatting-fn identity}}]
  (let [number-of-runs (-> config :benchmark :number-of-runs)
        matchmaker (create-matchmaker config)]
    (-> (compute-benchmark config matchmaking-fn number-of-runs)
        flatten
        aggregation-fn
        formatting-fn)))
