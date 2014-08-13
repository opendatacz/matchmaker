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

(defn clear-dir
  "Delete all files in directory on @dirpath."
  [dirpath]
  {:pre [(.exists (io/as-file dirpath))]}
  (dorun (map clojure.java.io/delete-file (file-seq (io/file dirpath)))))

(defn get-missing-uris
  "Returns contract URIs that are present in SPARQL endpoint,
  but missing in Elasticsearch."
  [sparql-extractor es]
  (let [uri-chunks (contract-chunks sparql-extractor)]
    (mapcat (partial es/get-missing-ids es) uri-chunks)))

(defn load-dir
  "Load JSON-LD files from directory on @dirpath into Elasticsearch."
  [dirpath]
  {:pre [(.exists (io/as-file dirpath))]}
  (let [files (filter #(.endsWith (str %) ".jsonld") (file-seq (io/file dirpath)))
        es (:elasticsearch (es/load-elasticsearch))
        index-fn (fn [contract] (do (println (format "Indexing contract <%s>..." (contract "@id")))
                                    (es/index-contract es
                                                       contract
                                                       :id (contract "@id"))))]
    (dorun (map (comp index-fn load-json) files))))

(defn -main
  "The first argument is the path to directory from which JSON-LD files will be loaded in Elasticsearch."
  [& args]
  (let [dirpath (first args)]
    (load-dir dirpath)))
