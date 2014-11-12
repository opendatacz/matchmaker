(ns matchmaker.cli
  (:gen-class)
  (:require [taoensso.timbre :as timbre]
            [clojure.tools.cli :refer [parse-opts]]
            [matchmaker.lib.util :as util]
            [matchmaker.common.config :refer [->Config]]
            [environ.core :refer [env]]
            [matchmaker.benchmark.evaluate :as evaluate]
            [matchmaker.benchmark.core :refer [run-benchmark]]
            [matchmaker.core.common :refer [matchmakers]]
            [incanter.core :refer [save]]
            [schema.core :as s]
            [schema-contrib.core :as sc]
            [com.stuartsierra.component :as component]))

; ----- Private vars -----

(def ^:private
  cli-options
  [["-e" "--endpoint ENDPOINT" "Matchmaker's HTTP endpoint"
    :default "http://localhost:3000/match/contract/to/business-entity"
    :validate [(fn [endpoint] (try
                                (s/validate sc/URI endpoint)
                                (catch Exception e false)))]]
   ["-n" "--number-of-runs RUNS" "Number of benchmark's runs"
    :default 5
    :parse-fn #(Integer/parseInt %)
    :validate [pos?]]
   ["-d" "--diagram-path DIAGRAM" "Path to output diagram"
    :default "diagrams"
    :validate [(fn [path] (.exists (clojure.java.io/file path)))]]
   ["-m" "--matchmaker MATCHMAKER" "Matchmaker to benchmark"
    :default "exact-cpv"
    :validate [(partial contains? matchmakers)]]
   ["-h" "--help"]])

; ----- Private functions -----

(defn- benchmark
  [config {:keys [diagram-path endpoint matchmaker number-of-runs]}]
  (if (util/url-alive? endpoint)
      (do (println "Running the benchmark...")
          (let [evaluation-metrics (get-in config [:benchmark :evaluation-metrics])
                metadata {:config config
                          :matchmaker matchmaker
                          :number-of-runs number-of-runs}
                results (run-benchmark endpoint matchmaker number-of-runs)
                metrics (evaluate/compute-metrics evaluation-metrics results)
                output-name (str (util/date-now)
                                 "-"
                                 matchmaker
                                 "-"
                                 (util/uuid))
                output-path (util/join-file-path diagram-path output-name)
                data-file (str output-path ".edn")
                diagram-file (str output-path ".png")]
            (println (util/format-numbers metrics))
            (spit data-file (pr-str {:metadata metadata
                                     :metrics metrics
                                     :results results}))
            (save (evaluate/top-n-curve-chart (apply concat results))
                  diagram-file
                  :width 1000
                  :height 800)
            (println (format "Rendered benchmark results into %s" diagram-file))))
      (println (format "Matchmaker's endpoint <%s> isn't available." endpoint))))

(defn- error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn- usage
  [options-summary]
  (clojure.string/join \newline
                       ["Matchmaker command-line interface"
                        ""
                        "Usage: program-name [options] command"
                        ""
                        "Options:"
                        options-summary
                        ""
                        "Actions:"]))

; ----- Public functions -----

(defn -main
  [& args]
  (let [{{:keys [help]
          :as options} :options
         :keys [errors summary]} (parse-opts args cli-options)
        config (component/start (->Config (:matchmaker-config env)))]
    (cond help (println summary)
          errors (println (error-msg errors))
          :else (benchmark config 
                           options))))
