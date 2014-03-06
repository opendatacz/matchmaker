(ns matchmaker.common.config
  (:require [taoensso.timbre :as timbre]
            [clojure.edn :as edn]
            [com.stuartsierra.component :as component]))

(defrecord Config [filename]
  component/Lifecycle
  (start [config] (let [_ (timbre/debug "Starting config...")
                        data (edn/read-string (slurp filename))
                        source-graph (get-in data [:data :source-graph])
                        sample-graph (str source-graph
                                          (if-not (= (last source-graph) \/) \/)
                                          "benchmark")
                        updated-data (assoc-in data [:benchmark :sample :graph] sample-graph)]
                       (assoc config :config updated-data)))
  (stop [config] config))

(defn load-config
  "Loads configuration"
  [filename]
  (let [data (edn/read-string (slurp filename))
        source-graph (-> data :data :source-graph)
        sample-graph (str source-graph
                          (if-not (= (last source-graph) \/) \/)
                          "benchmark")]
    (assoc-in data [:benchmark :sample :graph] sample-graph)))
