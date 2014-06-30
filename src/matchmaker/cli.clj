(ns matchmaker.cli
  (:gen-class)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [matchmaker.lib.util :as util]
            [matchmaker.common.config :refer [->Config]]
            [environ.core :refer [env]]
            [matchmaker.benchmark.evaluate :as evaluate]
            [matchmaker.benchmark.core :refer [format-results run-benchmark]]
            [matchmaker.core.sparql :as sparql-matchmaker]
            [incanter.core :refer [save]]
            [schema.core :as s]
            [schema-contrib.core :as sc]
            [com.stuartsierra.component :as component]))

; ----- Private vars -----

(def ^{:doc "Keyword to function lookup for matchmaking functions"
       :private true}
  matchmaking-fns
  {:match-contract-exact-cpv sparql-matchmaker/contract-to-business-entity-exact-cpv})

(def ^:private
  cli-options
  [["-e" "--endpoint ENDPOINT" "Matchmaker's HTTP endpoint"
    :default "http://localhost:3000/match/contract/to/business-entity"
    :validate [(fn [endpoint] (try
                                (s/validate sc/URI endpoint)
                                (catch Exception e false)))]]
   ["-n" "--number-of-runs RUNS" "Number of benchmark's runs"
    :default 10
    :parse-fn #(Integer/parseInt %)
    :validate [pos?]]
   ["-d" "--diagram-path DIAGRAM" "Path to output diagram"
    :default "diagrams"
    :validate [(fn [path] (.exists (clojure.java.io/file path)))]]
   ["-m" "--matchmaker MATCHMAKER" "Matchmaker to benchmark"
    ;:parse-fn to convert to the actual matchmaking fn
    ;:validate only allow implemented matchmaker fns
    ]
   ["-h" "--help"]])

; ----- Private functions -----

(defn- benchmark
  [{:keys [diagram-path endpoint number-of-runs]} evaluation-metrics]
  (println "Running the benchmark...")
  (let [results (run-benchmark endpoint number-of-runs)
        ; TODO: Encode matchmaker name + basic params into diagram name
        diagram-path (util/join-file-path diagram-path
                                          (str (util/date-time-now)
                                               "-"
                                               (util/uuid)
                                               ".png"))]
    (save (evaluate/top-n-curve-chart results)
          diagram-path
          :width 1000
          :height 800)
    (println (format "Rendered benchmark results into %s" diagram-path))))

(defn- resolve-matchmaking-fn
  "Maps matchmaking function string to function."
  [^String matchmaking-fn]
  (let [available-matchmaking-fns (clojure.string/join ", " (map name (keys matchmaking-fns)))
        unavailable-error (str "Matchmaking function not available. Available functions: "
                                available-matchmaking-fns)]
    (or ((keyword matchmaking-fn) matchmaking-fns)
        (util/exit 1 unavailable-error))))

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

; ----- Public functions -----

(defn -main
  [& args]
  (let [{{:keys [help]
          :as options} :options
         :keys [errors summary]} (parse-opts args cli-options)
        config (component/start (->Config (:matchmaker-config env)))]
    (cond help (println summary)
          errors (println (error-msg errors))
          :else (benchmark (get-in config [:benchmark :evaluation-metrics])
                           options))))
