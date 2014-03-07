(ns matchmaker.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [taoensso.timbre :as timbre]
            [matchmaker.lib.util :refer [exit join-file-path]]
            [matchmaker.common.config :refer [load-config]]
            [matchmaker.benchmark.core :refer [format-results run-benchmark]]
            [matchmaker.core.sparql :as sparql-matchmaker])
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
  {:match-contract-basic-cpv sparql-matchmaker/match-contract-basic-cpv})

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
  (let [config (load-config "config.edn")
        matchmaking-fn (resolve-matchmaking-fn matchmaking-fn-key)]
    (run-benchmark config matchmaking-fn format-results)))

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
