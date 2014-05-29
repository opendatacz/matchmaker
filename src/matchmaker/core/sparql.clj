(ns matchmaker.core.sparql
  (:require [matchmaker.lib.sparql :as sparql]))

; Public functions

(defn match-resource
  "Matches resource using SPARQL query generated from @template-path by using @data."
  [config template-path & {:keys [data]
                           :or {data {}}}]
  (let [sparql-config (get-in config [:matchmaker :sparql])
        additional-data (select-keys sparql-config [:additional-object-inhibition
                                                    :cpv-graph])]
    (sparql/select-query config
                         template-path
                         :data (merge data additional-data))))

(defn contract-to-business-entity-exact-cpv
  "Match @contract to business entities using exact CPV matches SPARQL query."
  [config contract & {:as data}]
  (match-resource config
                  ["matchmaker" "sparql" "contract" "to" "business_entity" "exact_cpv"]
                  :data (assoc data :contract contract)))

(defn contract-to-business-entity-expand-to-narrower-cpv
  "Match @contract to business entities using CPV codes unidirectionally expanded to narrower concepts."
  [config contract & {:as data}]
  (match-resource config
                  ["matchmaker" "sparql" "contract" "to" "business_entity" "expand_to_narrower_cpv"]
                  :data (assoc data :contract contract)))

(defn contract-to-contract-exact-cpv
  "Match @contract to contracts using exact CPV matches SPARQL query."
  [config contract & {:as data}]
  (match-resource config
                  ["matchmaker" "sparql" "contract" "to" "contract" "exact_cpv"]
                  :data (assoc data :contract contract)))

(defn contract-to-contract-expand-to-narrower-cpv
  "Match @contract to contracts using CPV codes unidirectionally expanded to narrower concepts."
  [config contract & {:as data}]
  (match-resource config
                  ["matchmaker" "sparql" "contract" "to" "contract" "expand_to_narrower_cpv"]
                  :data (assoc data :contract contract)))

(defn business-entity-to-contract-exact-cpv
  "Match @business-entity to public contracts using exact CPV matches SPARQL query."
  [config business-entity & {:as data}]
  (match-resource config
                  ["matchmaker" "sparql" "business_entity" "to" "contract" "exact_cpv"]
                  :data (assoc data :business-entity business-entity)))

(defn business-entity-to-contract-expand-to-narrower-cpv
  "Match @business-entity to public contracts using matches through CPV codes
  expanded to more specific concepts."
  [config business-entity & {:as data}]
  (match-resource config
                  ["matchmaker" "sparql" "business_entity" "to" "contract" "expand_to_narrower_cpv"]
                  :data (assoc data :business-entity business-entity)))
