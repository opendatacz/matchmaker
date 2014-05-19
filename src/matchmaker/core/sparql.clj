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

(defn match-contract-expand-to-narrower-cpv
  "Match @contract using CPV codes unidirectionally expanded to narrower concepts."
  [config contract]
  (match-contract config
                  contract
                  ["matchmaker" "sparql" "contract" "expand_to_narrower_cpv"]))

(defn match-business-entity-exact-cpv
  "Match @business-entity to relevant public contracts using exact CPV matches SPARQL query."
  [config business-entity]
  (match-business-entity config
                         business-entity
                         ["matchmaker" "sparql" "business_entity" "exact_cpv"]))
