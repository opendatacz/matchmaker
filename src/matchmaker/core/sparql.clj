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
    (sparql/select-1-variable config
                              template-path
                              :data (merge data additional-data))))

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
  (match-contract config
                  contract
                  ["matchmaker" "sparql" "basic_cpv"]))

(defn match-contract-fuzzy-cpv
  "Match @contract using partial CPV overlap"
  [config contract]
  (let [cpv-overlap-length (-> config :matchmaker :sparql :cpv-overlap-length)
        cpv-overlap-length-str (str "{" cpv-overlap-length "}")]
    (match-contract config
                    contract
                    ["matchmaker" "sparql" "fuzzy_cpv"]
                    :cpv-overlap-length cpv-overlap-length)))
