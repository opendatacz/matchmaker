(ns matchmaker.cli
  (:gen-class)
  (:require [taoensso.timbre :as timbre]
            [clojure.tools.cli :refer [parse-opts]]
            [matchmaker.lib.util :as util]
            [matchmaker.common.config :refer [->Config]]
            [environ.core :refer [env]]
            [matchmaker.benchmark.evaluate :as evaluate]
            [matchmaker.benchmark.core :refer [compute-benchmark]]
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
    :default 10
    :parse-fn #(Integer/parseInt %)
    :validate [pos?]]
   ["-d" "--diagram-path DIAGRAM" "Path to output diagram"
    :default "diagrams"
    :validate [(fn [path] (.exists (clojure.java.io/file path)))]]
   ["-m" "--matchmaker MATCHMAKER" "Matchmaker to benchmark"
    :default "exact-cpv"
    :validate [(partial contains? matchmakers)]
   ]
   ["-h" "--help"]])

; ----- Private functions -----

(defn- benchmark
  [evaluation-metrics {:keys [diagram-path endpoint matchmaker number-of-runs]}]
  (if (util/url-alive? endpoint)
      (do (println "Running the benchmark...")
          (let [results (compute-benchmark endpoint matchmaker number-of-runs)
                metrics (evaluate/compute-metrics results evaluation-metrics) 
                ; TODO: Encode basic params into diagram name
                diagram-path (util/join-file-path diagram-path
                                                  (str (util/date-time-now)
                                                      "-"
                                                      matchmaker
                                                      "-"
                                                      (util/uuid)
                                                      ".png"))]
            (println (str metrics))
            (save (evaluate/top-n-curve-chart results)
                  diagram-path
                  :width 1000
                  :height 800)
            (println (format "Rendered benchmark results into %s" diagram-path))))
      (println (format "Matchmaker's endpoint <%s> isn't available." endpoint))))

(comment
  (def config (component/start (->Config (:matchmaker-config env))))
  (def evaluation-metrics (get-in config [:benchmark :evaluation-metrics]))
  (def endpoint "http://lod2.vse.cz:8080/matchmaker/match/contract/to/business-entity")
  (def results (compute-benchmark endpoint "exact-cpv" 2))
  (def metrics (evaluate/compute-metrics results evaluation-metrics))
  )

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
