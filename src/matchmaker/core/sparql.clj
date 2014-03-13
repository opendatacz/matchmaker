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

(defn match-contract-exact-cpv
  "Match @contract using basic CPV SPARQL query."
  [config contract]
  (match-contract config
                  contract
                  ["matchmaker" "sparql" "exact_cpv"]))

(defn match-contract-broader-cpv
  "Match @contract using CPV expanded to broader CPV"
  [config contract]
  (let [sparql-params (-> config :matchmaker :sparql)
        {:keys [cpv-broader-steps cpv-graph]} (select-keys sparql-params [:cpv-broader-steps :cpv-graph])]
    (match-contract config
                    contract
                    ["matchmaker" "sparql" "broader_cpv"]
                    :cpv-graph cpv-graph
                    :cpv-broader-steps cpv-broader-steps)))
