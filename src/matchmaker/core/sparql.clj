(ns matchmaker.core.sparql
  (:require [matchmaker.lib.sparql :refer [sparql-assert select-1-value select-1-variable]]))

; Private functions

(defn- match-contract
  "Match @contract using SPARQL query rendered from @template-path using @data."
  [matchmaker contract template-path & {:as data}]
  (select-1-variable (:config matchmaker)
                     template-path
                     :data (assoc data :contract contract)))

; Public functions

(defn create-matchmaker
  "Matchmaker constructor"
  [config]
  {:pre [(sparql-assert config 
                        true?
                        "Data in source graph isn't sufficient for matchmaking."
                        ["matchmaker" "sparql" "awarded_tender_test"])]}
  {:config config})

(defn match-contract-basic-cpv
  "Match @contract using basic CPV SPARQL query."
  [matchmaker contract]
  (let [config (:config matchmaker)
        additional-object-inhibition (-> config :matchmaker :sparql :basic-cpv :additional-object-inhibition)
        limit (-> config :matchmaker :limit)]
    (match-contract matchmaker
                    contract
                    ["matchmaker" "sparql" "basic_cpv"]
                    :additional-object-inhibition additional-object-inhibition
                    :limit limit)))

(comment
  (defn match-contract-random
    "Match @contract to random supplier. Used as baseline for evaluation."
    [matchmaker contract]
    (let [config (:config matchmaker)
          business-entity-count (:business-entity-count matchmaker)
          limit (-> config :matchmaker :limit)
          offset (rand-int (- business-entity-count limit))]
      (match-contract matchmaker
                      contract
                      ["matchmaker" "sparql" "random"]
                      :limit limit
                      :offset offset)))
  )
