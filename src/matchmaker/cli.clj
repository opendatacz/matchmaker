(ns matchmaker.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [matchmaker.lib.util :refer [exit init-logger]]
            [matchmaker.system :as system]
            [matchmaker.common.config :refer [->Config]]
            [matchmaker.benchmark.evaluate :as evaluate]
            [matchmaker.benchmark.core :refer [format-results run-benchmark]]
            [matchmaker.core.sparql :as sparql-matchmaker]
            [matchmaker.lib.sparql :as sparql]
            [clojure.data.zip.xml :as zip-xml]
            [matchmaker.lib.rdf :as rdf]
            [matchmaker.lib.template :refer [render-sparql]]
            [incanter.core :refer [save view]]
            [com.stuartsierra.component :as component]))

(init-logger)

; Private vars

(def ^{:doc "Available command-line sub-commands"
       :private true}
  available-commands
  #{"benchmark"})

(def ^{:doc "Keyword to function lookup for matchmaking functions"
       :private true}
  matchmaking-fns
  {:match-contract-exact-cpv sparql-matchmaker/contract-to-business-entity-exact-cpv})

(def ^:private
  main-cli-options
  [["-c" "--command COMMAND" "Sub-command"
    :validate [#(contains? available-commands %)
               (str "Command not available. Available commands: " (clojure.string/join ", " available-commands))]]
   ["-h" "--help"]])

; Private functions

(defn- resolve-matchmaking-fn
  "Maps matchmaking function string to function."
  [^String matchmaking-fn]
  (let [available-matchmaking-fns (clojure.string/join ", " (map name (keys matchmaking-fns)))
        unavailable-error (str "Matchmaking function not available. Available functions: "
                                available-matchmaking-fns)]
    (or ((keyword matchmaking-fn) matchmaking-fns)
        (exit 1 unavailable-error))))

(defn- error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn- usage
  [options-summary]
  (->> ["Matchmaker command-line interface"
        ""
        "Usage: program-name [options] command"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"]
       (clojure.string/join \newline)))

(defn- benchmark
  [matchmaking-fn-key]
  ;(let [matchmaking-fn (resolve-matchmaking-fn matchmaking-fn-key)]
  ;  (run-benchmark config matchmaking-fn format-results)))
  )

; Public functions

(defn -main
  [& args]
  (let [available-commands-list (clojure.string/join ", " available-commands)]
    (cond
      (not (seq args)) (exit 1 (str "No command to run. Available commands: " available-commands-list))
      (first args) (case (first args)
                         "benchmark" (if-let [matchmaking-fn (second args)]
                                             (benchmark matchmaking-fn)
                                             (exit 1 "Missing matchmaking function to benchmark."))
                         (exit 1 (str "Command not recognized. Available commands: " available-commands-list))))))

(comment
  (def test-data (slurp (clojure.java.io/resource "example.ttl")))
  (def sparql-endpoint (:sparql-endpoint @matchmaker.system/system))
  (def graph-uri (sparql/load-rdf-data sparql-endpoint test-data))
  (sparql/graph-exists? sparql-endpoint graph-uri)
  
  (def matchmaker-results (sparql-matchmaker/business-entity-to-contract-expand-to-narrower-cpv config
                                                                                                "http://linked.opendata.cz/resource/business-entity/CZ60838744"))
  (def matchmaker-results (sparql-matchmaker/contract-to-business-entity-exact-cpv config
                                                                                   "http://linked.opendata.cz/resource/vestnikverejnychzakazek.cz/public-contract/231075-7302041031075"))
  (def matchmaker-results (sparql-matchmaker/contract-to-contract-expand-to-narrower-cpv config
                                                                            "http://linked.opendata.cz/resource/vestnikverejnychzakazek.cz/public-contract/231075-7302041031075"))
  (println matchmaker-results)

  (def benchmark-results (run-benchmark config sparql-matchmaker/contract-to-business-entity-exact-cpv))
  (println benchmark-results)

  (def benchmark-metrics (evaluate/compute-avg-rank-metrics config benchmark-results))
  (println benchmark-metrics)
  (view (evaluate/top-n-curve-chart benchmark-results))
  (save (evaluate/top-n-curve-chart benchmark-results)
        "diagrams/exact_CPV_min_1_main_CPV_min_3_additional_CPV.png"
        :width 1000
        :height 800)
  )
