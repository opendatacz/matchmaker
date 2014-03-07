(ns matchmaker.common.config
  (:require [taoensso.timbre :as timbre]
            [clojure.edn :as edn]))

(defn load-config
  "Loads configuration"
  [filename]
  (let [data (edn/read-string (slurp filename))
        source-graph (-> data :data :source-graph)
        sample-graph (str source-graph
                          (if-not (= (last source-graph) \/) \/)
                          "benchmark")]
    (assoc-in data [:benchmark :sample :graph] sample-graph)))
