(ns matchmaker.data-preparation
  (:require [taoensso.timbre :as timbre] 
            [matchmaker.lib.util :refer [rescale]]
            [matchmaker.lib.sparql :as sparql]
            [matchmaker.lib.rdf :refer [map->turtle]]))

; ===== CPV IDF =====

; ----- Private functions -----

(defn- add-not-found-cpv-idfs
  "Add default IDF for CPV concepts not found in the used dataset.
  NOTE: This is necessary due to query expansion, which can infer concepts
  that aren't used explicitly, there no IDF would be computed for them."
  [sparql-endpoint]
  (let [cpv-graph (get-in sparql-endpoint [:config :matchmaker :sparql :cpv-graph])
        cpv-idfs-graph (get-in sparql-endpoint [:config :data :explicit-cpv-idfs-graph])]
    (sparql/sparql-update sparql-endpoint
                          ["data_preparation" "add_not_found_cpv_idfs"]
                          :data {:cpv-graph cpv-graph 
                                 :cpv-idfs-graph cpv-idfs-graph})))

(defn- compute-explicit-cpv-idfs
  "Compute IDFs for CPV codes present in descriptions of public contracts."
  [sparql-endpoint]
  (let [results (sparql/select-query-unlimited sparql-endpoint
                                               ["data_preparation" "count_occurrences"]
                                               :limit 2000)
        ; Apply logarithm to smooth the differences in IDFs
        log-fn (fn [idf] (update-in idf [:idf] #(Math/log10 (Float/parseFloat %))))
        idfs (doall (map log-fn results))
        max-idf (apply max (map :idf idfs))]
    ; Normalize by maximum
    (map (fn [idf] (update-in idf [:idf] #(/ % max-idf))) idfs)))

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
    [cpv (/ contract-count (Float/parseFloat cpv-frequency))]))

(defn- cpv-idfs->rdf
  "Convert @cpv-idfs to RDF/Turtle using the internal JSON-LD context."
  [sparql-endpoint cpv-idfs]
  (let [transform-fn (fn [{:keys [cpv idf]}]
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
  to rescale it into the [0, 1] interval and invert it."
  [cpv-idfs]
  (let [sorted-cpv-idfs (->> cpv-idfs
                             (map second)
                             sort)
        minimum-idf (first sorted-cpv-idfs)
        maximum-idf (last sorted-cpv-idfs)
        transform-fn (fn [[cpv idf]]
                       [cpv (- 1 (/ idf (+ maximum-idf 1)))])]
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
    (println explicit-cpv-idfs-graph)
    (println inferred-cpv-idfs-graph)
    (spit "explicit_idfs.ttl" explicit-idfs)
    (spit "inferred_idfs.ttl" inferred-idfs)
    ;(load-cpv-idfs sparql-endpoint explicit-idfs explicit-cpv-idfs-graph)
    ;(load-cpv-idfs sparql-endpoint inferred-idfs inferred-cpv-idfs-graph)
    ))

(comment
  (def sparql-endpoint (:sparql-endpoint (sparql/load-endpoint)))
  (def idfs (compute-explicit-cpv-idfs sparql-endpoint))
  (sparql/delete-graph sparql-endpoint (get-in sparql-endpoint [:config :data :explicit-cpv-idfs-graph]))

  (load-cpv-idfs sparql-endpoint
                 (cpv-idfs->rdf sparql-endpoint idfs)
                 (get-in sparql-endpoint [:config :data :explicit-cpv-idfs-graph]))
  (add-not-found-cpv-idfs sparql-endpoint)

  (spit "ted_idfs.ttl" (cpv-idfs->rdf sparql-endpoint idfs))
)
