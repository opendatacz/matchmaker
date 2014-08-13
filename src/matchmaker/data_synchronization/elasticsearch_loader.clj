(ns matchmaker.data-synchronization.elasticsearch-loader
  (:gen-class)
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [matchmaker.lib.elasticsearch :as es]))

; ----- Private functions -----

(defn- load-json
  "Load and parse JSON file from @path."
  [path]
  {:pre [(.exists (io/as-file path))]}
  (json/parse-stream (io/reader path)))

; ----- Public functions -----

(defn -main
  "Load JSON-LD files from directory (first of args) into Elasticsearch."
  [& args]
  {:pre [(.exists (io/as-file (first args)))]}
  (let [dirpath (first args)
        files (filter #(.endsWith (str %) ".jsonld") (file-seq (io/file dirpath)))
        es (es/load-elasticsearch)
        index-fn (fn [document] (es/index-document es
                                                   "contract"
                                                   document
                                                   :id (document "@id")))]
    (dorun (map (comp index-fn load-json) files))))
