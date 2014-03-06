(ns matchmaker.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [taoensso.timbre :as timbre]
            [matchmaker.lib.util :refer [exit join-file-path]]
            [matchmaker.core.sparql :as sparql])
  (:gen-class))

; Disable output to STDOUT
(timbre/set-config! [:appenders :standard-out :enabled?] false)
(timbre/set-config! [:appenders :spit :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename] (join-file-path "log" "logger.log"))

; Private vars

(def ^{:doc "Available command-line sub-commands"
       :private true}
  available-commands
  #{"benchmark"})

(def ^{:doc "Keyword to function lookup for matchmaking functions"
       :private true}
  matchmaking-fns
  {:match-contract-basic-cpv sparql/match-contract-basic-cpv})

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
  (let [available-matchmaking-fns (clojure.string/join ", " (keys matchmaking-fns))
        unavailable-error (str "Matchmaking function not available. Available functions: "
                                available-matchmaking-fns)]
    (or (matchmaking-fns matchmaking-fn)
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

(defn- run-benchmark
  "Wrapper function to run benchmark for given @matchmaking-fn."
  [matchmaking-fn]

  )

; Public functions

(defn -main
  [& args]
  (let [available-commands-list (clojure.string/join ", " available-commands)]
    (cond
      (not (seq args)) (exit 1 (str "No command to run. Available commands: " available-commands-list))
      (first args) (case (first args)
                         "benchmark" (if-let [matchmaking-fn (second args)]
                                             (run-benchmark (resolve-matchmaking-fn matchmaking-fn))
                                             (exit 1 "Missing matchmaking function to benchmark."))
                         (exit 1 (str "Command not recognized. Available commands: " available-commands-list))))))
