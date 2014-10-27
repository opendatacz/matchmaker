(ns matchmaker.data-preparation
  (:require [taoensso.timbre :as timbre] 
            [matchmaker.lib.util :refer [rescale]]
            [matchmaker.lib.sparql :refer [graph-exists? load-endpoint put-graph select-query-unlimited]]
            [matchmaker.lib.rdf :refer [map->turtle]]))

; ===== CPV IDF =====

; ----- Private functions -----

(defn- compute-cpv-frequencies
  "Compute frequencies of CPV codes using @sparql-endpoint."
  [sparql-endpoint]
  (let [transform-fn (fn [{cpv :cpv
                           weighted-frequency :weightedFrequency}]
                       [cpv (Float/parseFloat weighted-frequency)])] ; Does it make sense to add Math/log10? 
    (doall (map transform-fn
                (select-query-unlimited sparql-endpoint
                                        ["data_preparation" "weighted_cpv_frequencies"]
                                        :limit 2000)))))

(defn- normalize-cpv-frequencies
  "Divide each CPV code's IDF score by the maximum IDF score
  to rescale it into the <0, 1> interval and invert it."
  [cpv-frequencies]
  (let [sorted-cpv-frequencies (->> cpv-frequencies
                                    (map second)
                                    sort)
        minimum-frequency (first sorted-cpv-frequencies)
        maximum-frequency (last sorted-cpv-frequencies)
        transform-fn (fn [[cpv frequency]]
                       [cpv (rescale frequency
                                     [minimum-frequency maximum-frequency]
                                     ; Invert the inverval by using maximum as minimum and vice versa.
                                     ; Use 0.1 instead of 0 to avoid multiplication by 0 (effectively
                                     ; zeroing score of a match).
                                     [1 0.1])])]
    (map transform-fn cpv-frequencies)))

(defn- cpv-frequencies->rdf
  "Convert @cpv-frequencies to RDF/Turtle using the internal JSON-LD context."
  [sparql-endpoint cpv-frequencies]
  (let [transform-fn (fn [cpv-freq]
                       {"@id" (first cpv-freq)
                        "ex:frequency" (second cpv-freq)})
        data {"@context" (get-in sparql-endpoint [:config :context])
              "@graph" (doall (map transform-fn cpv-frequencies))}]
    (map->turtle data)))

; ----- Public functions -----

(defn load-cpv-frequencies
  "Load CPV frequencies using the @sparql-endpoint."
  [sparql-endpoint]
  (let [cpv-frequencies-graph (get-in sparql-endpoint [:config :data :cpv-frequencies-graph])]
    (when-not (graph-exists? sparql-endpoint cpv-frequencies-graph)
      (timbre/debug (format "Computing and loading CPV frequencies into <%s>." cpv-frequencies-graph))
      (let [cpv-frequencies (compute-cpv-frequencies sparql-endpoint)
            normalized-cpv-frequencies (normalize-cpv-frequencies cpv-frequencies)
            cpv-frequencies-turtle (cpv-frequencies->rdf sparql-endpoint normalized-cpv-frequencies)]
        (put-graph sparql-endpoint
                   cpv-frequencies-turtle
                   cpv-frequencies-graph)))))
  
(comment
  (def sparql-endpoint (:sparql-endpoint (load-endpoint)))
  (load-cpv-frequencies sparql-endpoint)
  )
