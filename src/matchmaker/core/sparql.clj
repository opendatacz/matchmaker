(ns matchmaker.core.sparql
  (:require [matchmaker.lib.sparql :as sparql]))

; Private functions

(defn- match-resource
  "Matches resource using SPARQL query generated from @template-path by using @data."
  [config template-path & {:keys [data]
                           :or {data {}}}]
  (let [additional-object-inhibition (-> config :matchmaker :sparql :additional-object-inhibition)
        additional-data {:additional-object-inhibition additional-object-inhibition}]
    (sparql/select-query config
                         template-path
                         :data (merge data additional-data))))

; Public functions

(defn contract-to-business-entity-exact-cpv
  "Match @contract to business entities using exact CPV matches SPARQL query."
  [config contract & {:keys [limit]}]
  (match-resource config
                  ["matchmaker" "sparql" "contract" "to" "business_entity" "exact_cpv"]
                  :data {:contract contract
                         :limit limit}))

(defn contract-to-business-entity-expand-to-narrower-cpv
  "Match @contract to business entities using CPV codes unidirectionally expanded to narrower concepts."
  [config contract & {:keys [limit]}]
  (match-resource config
                  ["matchmaker" "sparql" "contract" "to" "business_entity" "expand_to_narrower_cpv"]
                  :data {:contract contract
                         :limit limit}))

(defn contract-to-contract-exact-cpv
  "Match @contract to contracts using exact CPV matches SPARQL query."
  [config contract & {:keys [limit]}]
  (match-resource config
                  ["matchmaker" "sparql" "contract" "to" "contract" "exact_cpv"]
                  :data {:contract contract
                         :limit limit}))

(defn contract-to-contract-expand-to-narrower-cpv
  "Match @contract to contracts using CPV codes unidirectionally expanded to narrower concepts."
  [config contract & {:keys [limit]}]
  (match-resource config
                  ["matchmaker" "sparql" "contract" "to" "contract" "expand_to_narrower_cpv"]
                  :data {:contract contract
                         :limit limit}))

(defn business-entity-to-contract-exact-cpv
  "Match @business-entity to public contracts using exact CPV matches SPARQL query."
  [config business-entity & {:keys [limit]}]
  (match-resource config
                  ["matchmaker" "sparql" "business_entity" "to" "contract" "exact_cpv"]
                  :data {:business-entity business-entity
                         :limit limit}))

(defn business-entity-to-contract-expand-to-narrower-cpv
  "Match @business-entity to public contracts using matches through CPV codes
  expanded to more specific concepts."
  [config business-entity & {:keys [limit]}]
  (match-resource config
                  ["matchmaker" "sparql" "business_entity" "to" "contract" "expand_to_narrower_cpv"]
                  :data {:business-entity business-entity
                         :limit limit}))
