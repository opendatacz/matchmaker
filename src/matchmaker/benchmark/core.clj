(ns matchmaker.benchmark.core
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.benchmark.setup :as setup]
            [matchmaker.benchmark.evaluate :as evaluate]
            [matchmaker.benchmark.teardown :as teardown]
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
  (sparql/select-query config ["benchmark" "evaluate" "load_correct_matches"]))

(defn- sparql-endpoint-alive?
  "Raises an exception if SPARQL endpoint described in @config is not responding."
  [config]
  (let [sparql-endpoint-url (-> config :sparql-endpoint :query-url)]
    (assert (sparql/ping-endpoint config)
            (str "SPARQL endpoint <" sparql-endpoint-url "> is not responding."))))

(defn- sufficient-data?
  "Raises an exception if SPARQL endpoint described in @config provides insufficient data for matchmaking."
  [config]
  (let [source-graph (-> config :data :source-graph)]
    (assert (sparql/sparql-assert config 
                                  true?
                                  (str "Data in source graph <" source-graph "> isn't sufficient for matchmaking.")
                                  ["matchmaker" "sparql" "awarded_tender_test"]))))

; Records

(defrecord
  ^{:doc "Setup and teardown a benchmark according to @config."}
  Benchmark [config]
  component/Lifecycle
  (start [benchmark] (do (timbre/debug "Starting benchmark...")
                         (sparql-endpoint-alive? config)
                         (sufficient-data? config)
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
    (let [benchmark (component/start (->Benchmark config))
          _ (timbre/debug "DEBUG DEBUG DEBUG")
          _ (timbre/debug (:correct-matches benchmark))]
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
  (let [number-of-runs (-> config :benchmark :number-of-runs)]
    (-> (compute-benchmark config matchmaking-fn number-of-runs)
        flatten
        aggregation-fn
        formatting-fn)))
