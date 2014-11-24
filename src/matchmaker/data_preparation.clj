(ns matchmaker.data-preparation
  (:require [taoensso.timbre :as timbre] 
            [matchmaker.lib.util :refer [rescale]]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.lib.rdf :refer [map->turtle]]))

; ===== CPV IDF =====

; ----- Private functions -----

(defn- compute-explicit-cpv-idfs
  "Compute IDFs for CPV codes present in descriptions of public contracts."
  [sparql-endpoint ->idf]
  (doall (map ->idf
              (sparql/select-query-unlimited sparql-endpoint
                                             ["data_preparation" "weighted_explicit_cpv_frequencies"]
                                             :limit 2000))))

(defn- compute-inferred-cpv-idfs
  "Compute IDFs of CPV codes inferred using skos:broaderTransitive."
  [sparql-endpoint ->idf]
  (let [cpv-graph (get-in sparql-endpoint [:config :matchmaker :sparql :cpv-graph])]
    (map ->idf
         (sparql/select-query sparql-endpoint
                              ["data_preparation" "weighted_inferred_cpv_frequencies"]
                              :data {:cpv-graph cpv-graph}))))

(defn- cpv-frequency->idf
  "Convert CPV frequency to inverse document frequency."
  [contract-count]
  (fn
    [{cpv :cpv
      cpv-frequency :weightedFrequency}]
    [cpv (Math/log10 (/ contract-count (Float/parseFloat cpv-frequency)))]))

(defn- cpv-idfs->rdf
  "Convert @cpv-idfs to RDF/Turtle using the internal JSON-LD context."
  [sparql-endpoint cpv-idfs]
  (let [transform-fn (fn [[cpv idf]]
                       {"@id" cpv
                        "ex:idf" idf})
        data {"@context" (get-in sparql-endpoint [:config :context])
              "@graph" (doall (map transform-fn cpv-idfs))}]
    (map->turtle data)))

(defn- get-contract-count
  "Count weights of all public contracts."
  [sparql-endpoint]
  (Double/parseDouble (first (sparql/select-1-variable sparql-endpoint
                                                       :count
                                                       ["data_preparation" "count_contract_weights"]))))

(defn- load-cpv-idfs
  "Load CPV IDFs into @graph-uri using @sparql-endpoint."
  [sparql-endpoint cpv-idfs graph-uri]
  (when-not (sparql/graph-exists? sparql-endpoint graph-uri)
    (sparql/put-graph sparql-endpoint
                      cpv-idfs
                      graph-uri)))

(defn- normalize-cpv-idfs
  "Divide each CPV code's IDF score by the maximum IDF score
  to rescale it into the [0.1, 1] interval and invert it."
  [cpv-idfs]
  (let [sorted-cpv-idfs (->> cpv-idfs
                             (map second)
                             sort)
        minimum-idf (first sorted-cpv-idfs)
        maximum-idf (last sorted-cpv-idfs)
        transform-fn (fn [[cpv idf]]
                       [cpv (rescale idf
                                     [minimum-idf maximum-idf]
                                     ; Use 0.1 instead of 0 to avoid multiplication by 0 (effectively
                                     ; zeroing score of a match).
                                     [0.1 1])])]
    (map transform-fn cpv-idfs)))

; ----- Public functions -----

(defn compute-cpv-idfs
  "Compute and save CPV IDFs using @sparql-endpoint."
  [sparql-endpoint]
  (let [contract-count (get-contract-count sparql-endpoint)
        ->idf (cpv-frequency->idf contract-count)
        convert-fn (comp (partial cpv-idfs->rdf sparql-endpoint)
                         normalize-cpv-idfs)
        explicit-idfs (convert-fn (compute-explicit-cpv-idfs sparql-endpoint ->idf))
        inferred-idfs (convert-fn (compute-inferred-cpv-idfs sparql-endpoint ->idf))
        {:keys [explicit-cpv-idfs-graph
                inferred-cpv-idfs-graph]} (get-in sparql-endpoint [:config :data])]
    (load-cpv-idfs sparql-endpoint explicit-idfs explicit-cpv-idfs-graph)
    (load-cpv-idfs sparql-endpoint inferred-idfs inferred-cpv-idfs-graph)))

(comment
  (def sparql-endpoint (:sparql-endpoint (sparql/load-endpoint)))
  (compute-cpv-idfs sparql-endpoint)
)
