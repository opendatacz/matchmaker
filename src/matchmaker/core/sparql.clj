(ns matchmaker.core.sparql
  (:require [matchmaker.lib.sparql :as sparql]))

; Private functions

(defn- match-contract
  "Match @contract using SPARQL query rendered from @template-path using @data."
  [config contract template-path & {:as data}]
  (sparql/select-1-variable config
                            template-path
                            :data (assoc data :contract contract)))

; Public functions

(defn create-matchmaker
  "Matchmaker constructor"
  [config]
  {:pre [(sparql/sparql-assert config 
                               true?
                               "Data in source graph isn't sufficient for matchmaking."
                               ["matchmaker" "sparql" "awarded_tender_test"])]}
  {:config config})

(defn match-contract-basic-cpv
  "Match @contract using basic CPV SPARQL query."
  [config contract]
  (let [additional-object-inhibition (-> config :matchmaker :sparql :basic-cpv :additional-object-inhibition)
        limit (-> config :matchmaker :limit)]
    (match-contract config
                    contract
                    ["matchmaker" "sparql" "basic_cpv"]
                    :additional-object-inhibition additional-object-inhibition
                    :limit limit)))

(comment
  (defn match-contract-random
    "Match @contract to random supplier. Used as baseline for evaluation."
    [config contract]
    (let [business-entity-count (:business-entity-count matchmaker)
          limit (-> config :matchmaker :limit)
          offset (rand-int (- business-entity-count limit))]
      (match-contract config
                      contract
                      ["matchmaker" "sparql" "random"]
                      :limit limit
                      :offset offset)))
  )
