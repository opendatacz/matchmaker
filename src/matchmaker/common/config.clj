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
        [sample-graph metadata-graph] (map (partial util/append-to-uri source-graph)
                                           ["benchmark" "metadata"])]
    (util/deep-merge data {:benchmark {:sample {:graph sample-graph}}
                           :data {:metadata-graph metadata-graph}})))

;; ----- Components -----

(defrecord Config [config-file-path]
  component/Lifecycle
  (start [config] (let [context (util/load-jsonld-context "internal.jsonld")]
                    (merge config
                           {:context context}
                           (load-config config-file-path))))
  (stop [config] config))
