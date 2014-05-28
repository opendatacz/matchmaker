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
        [sample-graph metadata-graph] (map (partial util/append-to-uri source-graph)
                                           ["benchmark" "metadata"])]
    (util/deep-merge data {:benchmark {:sample {:graph sample-graph}}
                           :data {:metadata-graph metadata-graph}})))

(defonce config (load-config (util/join-file-path "config" "config-private.edn")))
