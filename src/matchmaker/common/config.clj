(ns matchmaker.common.config
  (:require [com.stuartsierra.component :as component] 
            [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [matchmaker.lib.util :as util]))

;; ----- Private functions -----

(defn- load-config
  "Loads configuration"
  [config-file-path]
  (let [filenames [(clojure.java.io/resource (util/join-file-path "config" "config-public.edn"))
                   config-file-path]
        data (reduce util/deep-merge (map (comp edn/read-string slurp) filenames))
        source-graph (get-in data [:data :source-graph])
        [sample-graph metadata-graph withheld-graph
         explicit-cpv-idfs inferred-cpv-idfs] (map (partial util/append-to-uri source-graph)
                                                   ["benchmark" "metadata" "withheld"
                                                    "explicit-cpv-idfs" "inferred-cpv-idfs"])]
    (util/deep-merge data {:benchmark {:sample {:graph sample-graph}}
                           :data {:explicit-cpv-idfs-graph explicit-cpv-idfs
                                  :inferred-cpv-idfs-graph inferred-cpv-idfs
                                  :metadata-graph metadata-graph
                                  :withheld-graph withheld-graph}})))

;; ----- Components -----

(defrecord Config [config-file-path]
  component/Lifecycle
  (start [config] (let [context (util/load-jsonld-context "internal.jsonld")]
                    (util/init-logger)
                    (merge config
                           {:context context}
                           (load-config config-file-path))))
  (stop [config] config))
