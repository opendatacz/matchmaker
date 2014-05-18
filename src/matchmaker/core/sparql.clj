(ns matchmaker.core.sparql
  (:require [matchmaker.lib.sparql :as sparql]))

; Private functions

(defn- match-resource
  "Matches resource using SPARQL query generated from @template-path by using @data."
  [config template-path & {:keys [data]
                           :or {data {}}}]
  (let [additional-object-inhibition (-> config :matchmaker :sparql :additional-object-inhibition)
        limit (-> config :matchmaker :limit)
        additional-data {:additional-object-inhibition additional-object-inhibition
                         :limit limit}]
    (sparql/select-query config
                         template-path
                         :data (merge data additional-data))))

(defn- match-contract
  "Matches @contract using SPARQL query rendered from @template-path using @data."
  [config contract template-path & {:keys [data]}]
  (let [additional-data {:contract contract}]
    (match-resource config
                    template-path
                    :data (merge data additional-data))))

(defn- match-contract-broader-cpv
  "Matches @contract using CPV expanded to broader CPV"
  [config contract template-path]
  (let [sparql-params (-> config :matchmaker :sparql)
        {:keys [cpv-broader-steps cpv-graph]} (select-keys sparql-params [:cpv-broader-steps :cpv-graph])]
    (match-contract config
                    contract
                    template-path
                    :data {:cpv-graph cpv-graph
                           :cpv-broader-steps cpv-broader-steps})))

(defn- match-business-entity
  "Matches @business-entity to relevant public contracts."
  [config business-entity template-path & {:keys [data]}]
  (let [additional-data {:business-entity business-entity}]
    (match-resource config
                    template-path
                    :data (merge data additional-data))))

; Public functions

(defn match-contract-exact-cpv
  "Match @contract using exact CPV matches SPARQL query."
  [config contract]
  (match-contract config
                  contract
                  ["matchmaker" "sparql" "contract" "exact_cpv"]))

(defn match-contract-bidirectional-broader-cpv
  "Match @contract using CPV codes bidirectionally expanded to broader concepts."
  [config contract]
  (match-contract-broader-cpv config
                              contract
                              ["matchmaker" "sparql" "contract" "bidi_broader_cpv"]))

(defn match-contract-unidirectional-broader-cpv
  "Match @contract using CPV codes unidirectionally expanded to broader concepts."
  [config contract]
  (match-contract-broader-cpv config
                              contract
                              ["matchmaker" "sparql" "contract" "broader_cpv"]))

(defn match-business-entity-exact-cpv
  "Match @business-entity to relevant public contracts using exact CPV matches SPARQL query."
  [config business-entity]
  (match-business-entity config
                         business-entity
                         ["matchmaker" "sparql" "business_entity" "exact_cpv"]))
