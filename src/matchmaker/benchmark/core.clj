(ns matchmaker.benchmark.core
  (:require [taoensso.timbre :as timbre]
            [matchmaker.lib.sparql :refer [ping-endpoint select-query]]
            [matchmaker.benchmark.setup :as setup]
            [matchmaker.benchmark.evaluate :as evaluate]
            [matchmaker.benchmark.teardown :as teardown]
            [com.stuartsierra.component :as component]))

; Private functions

(defn- load-correct-matches
  "Load correct contract-supplier pairs into a map"
  [config]
  (let [sparql-results (select-query config ["benchmark" "evaluate" "load_correct_matches"])]
    (into {} (:results sparql-results))))

; Records

(defrecord
  ^{:doc "Setup and teardown a benchmark according to @config."}
  Benchmark [config matchmaker]
  component/Lifecycle
  (start [benchmark] (do (let [config-data (:config config)]
                           (timbre/debug "Starting benchmark...")
                           (ping-endpoint config-data)
                           (setup/load-contracts config-data)
                           (setup/delete-awarded-tenders config-data)
                           (assoc benchmark :correct-matches (load-correct-matches config-data)))))
  (stop [benchmark] (do (let [config-data (:config config)]
                          (timbre/debug "Stopping benchmark...")
                          (teardown/return-awarded-tenders config-data)
                          (teardown/clear-graph config-data)
                          benchmark))))

; Public functions

(defn run-benchmark
  "Run matchmaking function @matchmaking-fn using given @benchmark."
  [benchmark matchmaking-fn]
  (let [config (-> benchmark :config :config)
        evaluation-results (evaluate/evaluate-rank benchmark
                                                   matchmaking-fn
                                                   (:correct-matches benchmark))
        evaluation-metrics (-> config :benchmark :evaluation-metrics)]
    (evaluate/compute-metrics evaluation-results evaluation-metrics)))
