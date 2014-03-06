(ns matchmaker.system
  (:require [com.stuartsierra.component :as component]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.core.sparql :refer [create-matchmaker match-contract-random match-contract-basic-cpv]]
            [matchmaker.benchmark.evaluate :refer [format-results]]
            [matchmaker.benchmark.core :refer [map->Benchmark run-benchmark]]))

; Public vars

(def ^:private system-components [:config :benchmark :sparql-matchmaker])

; Records

(defrecord MatchmakerSystem [config benchmark sparql-matchmaker]
  component/Lifecycle
  (start [matchmaker]
    (component/start-system (assoc matchmaker :running? true) system-components))
  (stop [matchmaker]
    (component/stop-system (assoc matchmaker :running? false) system-components)))

; Public functions

(defn matchmaker-system
  "Constructor for Matchmaker's system"
  [config-filename]
  (map->MatchmakerSystem {:config (->Config config-filename)
                          :sparql-matchmaker (component/using
                                                (map->SPARQLMatchmaker {})
                                                {:config :config})
                          :benchmark (component/using
                                        (map->Benchmark {})
                                        {:config :config
                                        ; Assoc-in different dependency if benchmarking different matchmaker?
                                        :matchmaker :sparql-matchmaker})}))

(defn run-system
  "Constructs system from @config-filename.
   Benchmarks @matchmaking-fn.
   Runs once, setting up and tearing down whole system."
  [config-filename matchmaking-fn]
  (let [system (component/start (matchmaker-system config-filename))]
    (try
      (run-benchmark (:benchmark system) matchmaking-fn)
      (finally (component/stop system)))))

(defn run-system-times
  "Repeating for @number-of-runs, constructs benchmarking system
   defined by @config-filename and benchmarks @matchmaking-fn."
  [config-filename
   matchmaking-fn
   ^Integer number-of-runs]
  (doall (repeatedly number-of-runs #(run-system config-filename matchmaking-fn))))

(comment
  (println results)

  (def results (run-system-times "config.edn" match-contract-basic-cpv 10)) 
  (def results (run-system-times "config.edn" match-contract-random 10))
  (format-results (avg-metrics results))
  )
