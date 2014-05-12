(ns matchmaker.core.sparql
  (:require [matchmaker.lib.sparql :as sparql]))

; Private functions

(defn- match-contract
  "Match @contract using SPARQL query rendered from @template-path using @data."
  [config contract template-path & {:as data}]
  (let [additional-object-inhibition (-> config :matchmaker :sparql :additional-object-inhibition)
        limit (-> config :matchmaker :limit)
        additional-data {:additional-object-inhibition additional-object-inhibition
                         :contract contract
                         :limit limit}]
    (sparql/select-query config
                         template-path
                         :data (merge data additional-data))))

(defn- match-contract-broader-cpv
  "Match @contract using CPV expanded to broader CPV"
  [config contract template-path]
  (let [sparql-params (-> config :matchmaker :sparql)
        {:keys [cpv-broader-steps cpv-graph]} (select-keys sparql-params [:cpv-broader-steps :cpv-graph])]
    (match-contract config
                    contract
                    template-path
                    :cpv-graph cpv-graph
                    :cpv-broader-steps cpv-broader-steps)))
; Public functions

(defn match-contract-exact-cpv
  "Match @contract using exact CPV matches SPARQL query."
  [config contract]
  (match-contract config
                  contract
                  ["matchmaker" "sparql" "exact_cpv"]))

(defn match-contract-bidirectional-broader-cpv
  "Match @contract using CPV codes bidirectionally expanded to broader concepts."
  [config contract]
  (match-contract-broader-cpv config contract ["matchmaker" "sparql" "bidi_broader_cpv"]))

(defn match-contract-unidirectional-broader-cpv
  "Match @contract using CPV codes unidirectionally expanded to broader concepts."
  [config contract]
  (match-contract-broader-cpv config contract ["matchmaker" "sparql" "broader_cpv"]))
