(ns matchmaker.common.config
  (:require [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [matchmaker.lib.util :as util]))

(defn load-config
  "Loads configuration"
  [filename]
  (let [filenames [(util/join-file-path "config" "config-public.edn") filename]
        data (reduce util/deep-merge (map (comp edn/read-string slurp io/resource) filenames))
        source-graph (-> data :data :source-graph)
        sample-graph (str source-graph
                          (if-not (= (last source-graph) \/) \/)
                          "benchmark")]
    (assoc-in data [:benchmark :sample :graph] sample-graph)))

(defonce config (load-config (util/join-file-path "config" "config-private.edn")))
